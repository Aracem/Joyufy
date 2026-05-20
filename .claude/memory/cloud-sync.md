---
name: cloud-sync
description: Google Drive OAuth2 flow, auto-sync timing, where credentials live and how tokens are persisted
metadata:
  type: project
---

# Cloud sync

Syncs the same JSON used by manual backup ([[data-layer]] § Backup format) to the user's own Google Drive — single file `joyufy_backup.json` at the Drive root.

## Why Drive (not Firebase / Supabase)

- No server to run or pay for.
- No login UI to build — uses the user's existing Google account.
- Scope `drive.file` only sees files the app itself creates, not the user's other Drive content.
- The downside (no real-time multi-device sync, last-write-wins) is acceptable for a single-user app used on at most a couple of machines.

## File map

| File | Purpose |
|---|---|
| `data/cloud/GoogleDriveRepository.kt` (commonMain) | Interface + `sealed interface AuthState { Unauthenticated; Authenticating; Authenticated(email) }` |
| `data/cloud/GoogleDriveRepositoryImpl.kt` (desktopMain) | Ktor REST client + `Desktop.browse()` + `ServerSocket` to catch the OAuth redirect on localhost |
| `data/cloud/GoogleDriveConfig.kt` (desktopMain) | `CLIENT_ID` + `CLIENT_SECRET` — **git-ignored**, do not commit |
| `data/cloud/GoogleDriveConfig.kt.template` | Versioned template explaining how to create the credentials in Google Cloud Console |
| `di/DriveProvider.kt` (commonMain `expect` / desktopMain `actual`) | Koin factory for the repository |
| `ui/drive/DriveViewModel.kt` | `signIn / signOut / setAutoSync / syncToCloud / syncFromCloud / syncToCloudSuspend`. Koin `single`. |

## OAuth2 flow

1. User clicks **Connect Google Drive** in Settings → `signIn()`.
2. Repository finds a free local TCP port, builds the auth URL with `redirect_uri = http://localhost:<port>` and `Desktop.getDesktop().browse(uri)`.
3. A one-shot `ServerSocket` accepts the redirect, parses `?code=...` out of the request line, sends back a tiny HTML "you can close this window".
4. The code is exchanged at `https://oauth2.googleapis.com/token` for an access + refresh token.
5. User email fetched from `https://www.googleapis.com/oauth2/v3/userinfo`.
6. All four pieces (access token, refresh token, expiry, email) persisted via `PreferencesRepository`.

`validAccessToken()` refreshes automatically if the cached token is within 60s of expiry.

Scope used: `https://www.googleapis.com/auth/drive.file`.

## Drive REST endpoints used

- `GET /drive/v3/files?q=name='joyufy_backup.json' and trashed=false` — locate the existing file.
- `POST /upload/drive/v3/files?uploadType=multipart` — create the file.
- `PATCH /upload/drive/v3/files/{id}?uploadType=multipart` — overwrite content.
- `GET /drive/v3/files/{id}?alt=media` — download contents.

All requests via Ktor `HttpClient(CIO)` with `ContentNegotiation` + `kotlinx.serialization.json`.

## Auto-sync timing

| When | What | Where |
|---|---|---|
| App launch | If `autoSync && authenticated` → `syncFromCloud(silent = true)` | `App.kt` `LaunchedEffect(Unit)` |
| App close | If `autoSync && authenticated` → `runBlocking { withTimeoutOrNull(5_000) { syncToCloudSuspend() } }` | `Main.kt` `onCloseRequest` |
| Manual | `Upload now` / `Restore from Drive` buttons in Settings | `SettingsScreen.kt` Cloud Sync section |

`syncToCloudSuspend()` exists separately from `syncToCloud()` because the latter launches its own coroutine in the ViewModel scope, which doesn't help when `Main.kt` needs to *block* the JVM shutdown until the upload completes (with a 5s ceiling).

## Persisted keys (`PreferencesRepository`)

```
drive_access_token   : String   (empty when not signed in)
drive_refresh_token  : String
drive_token_expiry   : Long     (epoch ms)
drive_user_email     : String
drive_auto_sync      : Boolean  (default true)
drive_last_sync_at   : Long     (epoch ms, shown in Settings)
```

Sign-out clears the four token-related fields and resets `authState` to `Unauthenticated`. Auto-sync preference and last-sync timestamp persist across sign-out so they're remembered on re-connect.

## Credentials setup (one-time, per developer machine)

The repo ships `GoogleDriveConfig.kt.template` — copy it to `GoogleDriveConfig.kt` and fill in real values from Google Cloud Console → APIs & Services → Credentials → OAuth client ID → **Desktop app**. The Drive API must be enabled on the same project. The "client secret" is not truly secret for a desktop app (Google knows this) but we git-ignore it as a basic hygiene step.

See [[data-layer]] for what the JSON payload contains.
