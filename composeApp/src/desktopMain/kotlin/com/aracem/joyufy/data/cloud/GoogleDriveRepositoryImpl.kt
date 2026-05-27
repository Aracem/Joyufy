package com.aracem.joyufy.data.cloud

import com.aracem.joyufy.data.repository.PreferencesRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.ServerSocket
import java.net.URI

private const val BACKUP_FILE_NAME = "joyufy_backup.json"
private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file openid email"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

class GoogleDriveRepositoryImpl(
    private val prefs: PreferencesRepository,
    private val clientId: String,
    private val clientSecret: String,
) : GoogleDriveRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    init {
        val accessToken = prefs.getDriveAccessToken()
        val refreshToken = prefs.getDriveRefreshToken()
        val email = prefs.getDriveUserEmail()
        // Restore session whenever we have any credential that lets us call Drive again.
        // Email may be empty if it wasn't granted at sign-in time — the session is still valid.
        if (accessToken.isNotEmpty() || refreshToken.isNotEmpty()) {
            _authState.value = AuthState.Authenticated(email)
        }
    }

    override suspend fun signIn() = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Authenticating

        val port = findFreePort()
        val redirectUri = "http://localhost:$port"
        val authUrl = buildAuthUrl(redirectUri)

        Desktop.getDesktop().browse(URI(authUrl))

        val code = listenForAuthCode(port)
        if (code == null) {
            _authState.value = AuthState.Unauthenticated
            return@withContext
        }

        val tokenResponse = exchangeCodeForTokens(code, redirectUri)
        if (tokenResponse == null) {
            _authState.value = AuthState.Unauthenticated
            return@withContext
        }

        val email = fetchUserEmail(tokenResponse.accessToken) ?: ""
        val expiryMs = System.currentTimeMillis() + tokenResponse.expiresIn * 1000L

        prefs.setDriveAccessToken(tokenResponse.accessToken)
        // Google only returns refresh_token on first consent — keep the previous one if absent.
        val newRefresh = tokenResponse.refreshToken
        if (!newRefresh.isNullOrEmpty()) {
            prefs.setDriveRefreshToken(newRefresh)
        }
        prefs.setDriveTokenExpiry(expiryMs)
        prefs.setDriveUserEmail(email)

        _authState.value = AuthState.Authenticated(email)
    }

    override suspend fun signOut() {
        prefs.setDriveAccessToken("")
        prefs.setDriveRefreshToken("")
        prefs.setDriveTokenExpiry(0L)
        prefs.setDriveUserEmail("")
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun upload(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = validAccessToken() ?: error("Not authenticated")
            val fileId = findBackupFileId(token)
            if (fileId != null) {
                updateFile(token, fileId, json)
            } else {
                createFile(token, json)
            }
        }
    }

    override suspend fun download(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = validAccessToken() ?: error("Not authenticated")
            val fileId = findBackupFileId(token) ?: error("No backup file found in Drive")
            downloadFile(token, fileId)
        }
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private fun buildAuthUrl(redirectUri: String): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to DRIVE_SCOPE,
            "access_type" to "offline",
            "prompt" to "consent",
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v)}" }
        return "$AUTH_ENDPOINT?$params"
    }

    private fun listenForAuthCode(port: Int): String? {
        return try {
            ServerSocket(port).use { server ->
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader().readLine() ?: return null
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n" +
                        "<html><body><h2>Joyufy conectado a Google Drive</h2>" +
                        "<p>Puedes cerrar esta ventana.</p></body></html>"
                    socket.getOutputStream().write(response.toByteArray())
                    // GET /?code=XXX&... HTTP/1.1
                    Regex("code=([^& ]+)").find(request)?.groupValues?.get(1)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun exchangeCodeForTokens(code: String, redirectUri: String): TokenResponse? {
        return runCatching {
            httpClient.submitForm(
                url = TOKEN_ENDPOINT,
                formParameters = parameters {
                    append("code", code)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("redirect_uri", redirectUri)
                    append("grant_type", "authorization_code")
                },
            ).body<TokenResponse>()
        }.getOrNull()
    }

    private suspend fun refreshAccessToken(): String? {
        val refreshToken = prefs.getDriveRefreshToken().takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val response = httpClient.submitForm(
                url = TOKEN_ENDPOINT,
                formParameters = parameters {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                },
            ).body<TokenResponse>()
            val expiryMs = System.currentTimeMillis() + response.expiresIn * 1000L
            prefs.setDriveAccessToken(response.accessToken)
            prefs.setDriveTokenExpiry(expiryMs)
            response.accessToken
        }.getOrNull()
    }

    private suspend fun validAccessToken(): String? {
        val token = prefs.getDriveAccessToken()
        val expiry = prefs.getDriveTokenExpiry()
        val stillValid = token.isNotEmpty() && System.currentTimeMillis() < expiry - 60_000
        if (stillValid) return token
        val refreshed = refreshAccessToken()
        if (refreshed == null) {
            // Refresh failed and we have no usable token — surface as unauthenticated
            // so the UI reflects reality instead of showing a stale "connected" state.
            if (prefs.getDriveRefreshToken().isEmpty()) {
                _authState.value = AuthState.Unauthenticated
            }
        }
        return refreshed
    }

    private suspend fun fetchUserEmail(accessToken: String): String? {
        return runCatching {
            httpClient.get(USERINFO_ENDPOINT) {
                bearerAuth(accessToken)
            }.body<UserInfoResponse>().email
        }.getOrNull()
    }

    // ── Drive file operations ─────────────────────────────────────────────────

    private suspend fun findBackupFileId(token: String): String? {
        return runCatching {
            val response = httpClient.get(DRIVE_FILES_ENDPOINT) {
                bearerAuth(token)
                parameter("q", "name='$BACKUP_FILE_NAME' and trashed=false")
                parameter("fields", "files(id)")
                parameter("spaces", "drive")
            }.body<DriveFilesResponse>()
            response.files.firstOrNull()?.id
        }.getOrNull()
    }

    private suspend fun createFile(token: String, content: String) {
        val metadata = """{"name":"$BACKUP_FILE_NAME","mimeType":"application/json"}"""
        httpClient.post("$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("metadata", metadata, Headers.build {
                    append(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                })
                append("file", content.toByteArray(), Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
            }))
        }
    }

    private suspend fun updateFile(token: String, fileId: String, content: String) {
        httpClient.patch("$DRIVE_UPLOAD_ENDPOINT/$fileId?uploadType=multipart") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("metadata", """{"name":"$BACKUP_FILE_NAME"}""", Headers.build {
                    append(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                })
                append("file", content.toByteArray(), Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
            }))
        }
    }

    private suspend fun downloadFile(token: String, fileId: String): String {
        return httpClient.get("$DRIVE_FILES_ENDPOINT/$fileId") {
            bearerAuth(token)
            parameter("alt", "media")
        }.bodyAsText()
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
}

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
private data class UserInfoResponse(val email: String = "")

@Serializable
private data class DriveFilesResponse(val files: List<DriveFile> = emptyList())

@Serializable
private data class DriveFile(val id: String)

private object URLEncoder {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
