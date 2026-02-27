package com.aracem.joyufy.update

import com.aracem.joyufy.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun checkForUpdate(): UpdateInfo? = UpdateChecker.check()

private object UpdateChecker {
    private const val API_URL =
        "https://api.github.com/repos/Aracem/nexlify/releases/latest"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().readText()
            val json = Json.parseToJsonElement(body).jsonObject
            val tag = json["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
            val url = json["html_url"]?.jsonPrimitive?.content ?: return@runCatching null
            val latestVersion = tag.trimStart('v')
            if (isNewer(latestVersion, AppVersion.NAME)) UpdateInfo(latestVersion, url)
            else null
        }.getOrNull()
    }

    /** Returns true if [candidate] is strictly newer than [current] (semver major.minor.patch). */
    private fun isNewer(candidate: String, current: String): Boolean {
        val c = candidate.split(".").mapNotNull { it.toIntOrNull() }.padded()
        val v = current.split(".").mapNotNull { it.toIntOrNull() }.padded()
        for (i in 0..2) {
            if (c[i] > v[i]) return true
            if (c[i] < v[i]) return false
        }
        return false
    }

    private fun List<Int>.padded(): List<Int> =
        this + List(maxOf(0, 3 - size)) { 0 }
}
