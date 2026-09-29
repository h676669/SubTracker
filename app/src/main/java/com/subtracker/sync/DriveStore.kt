package com.subtracker.sync

import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Backup storage in the user's own Google Drive, in the hidden `appDataFolder`.
 *
 * The folder is private to this app: it doesn't appear in the user's Drive listing and
 * no other app can read it. That keeps the only scope we request — `drive.appdata` —
 * off the user's real documents.
 *
 * Access tokens from [AuthorizationClient] live about an hour and there is deliberately
 * no refresh token on the device, so every operation calls [authorize] first. Once the
 * user has granted the scope that returns a fresh token with no UI.
 */
object DriveStore {

    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "subtracker-backup.json"
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    /** The outcome of asking for Drive access. */
    sealed interface Auth {
        data class Granted(val token: String) : Auth
        /** The user has to approve the scope; launch this, then ask again. */
        data class NeedsConsent(val intentSender: IntentSender) : Auth
    }

    suspend fun authorize(context: Context): Auth {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        return result.toAuth()
    }

    /** Reads the result of the consent screen launched for [Auth.NeedsConsent]. */
    fun authFromIntent(context: Context, data: android.content.Intent?): Auth {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return result.toAuth()
    }

    private fun AuthorizationResult.toAuth(): Auth {
        val sender = pendingIntent?.intentSender
        return when {
            hasResolution() && sender != null -> Auth.NeedsConsent(sender)
            else -> Auth.Granted(accessToken ?: error("Drive granted access but returned no token."))
        }
    }

    /** The signed-in account's email, for showing which account is connected. */
    suspend fun accountEmail(token: String): String? = withContext(Dispatchers.IO) {
        val body = runCatching {
            get("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
        }.getOrNull() ?: return@withContext null
        runCatching { JSONObject(body).getJSONObject("user").optString("emailAddress") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** The backup document, or null if this account has never pushed one. */
    suspend fun download(token: String): String? = withContext(Dispatchers.IO) {
        val id = fileId(token) ?: return@withContext null
        get("$FILES/$id?alt=media", token)
    }

    /** Replaces the remote document, creating it on first push. */
    suspend fun upload(token: String, json: String): Unit = withContext(Dispatchers.IO) {
        val id = fileId(token) ?: create(token)
        // uploadType=media replaces the content outright, which is what whole-blob wants.
        send("$UPLOAD/$id?uploadType=media", token, "PATCH", json)
    }

    /** Forgets the remote copy. Local data is untouched. */
    suspend fun deleteRemote(token: String): Boolean = withContext(Dispatchers.IO) {
        val id = fileId(token) ?: return@withContext false
        send("$FILES/$id", token, "DELETE", null)
        true
    }

    private fun fileId(token: String): String? {
        val query = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val body = get("$FILES?spaces=appDataFolder&q=$query&fields=files(id)&pageSize=1", token)
        val files = JSONObject(body).optJSONArray("files") ?: return null
        return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    private fun create(token: String): String {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
        val body = send(FILES, token, "POST", metadata.toString())
        return JSONObject(body).optString("id").takeIf { it.isNotBlank() }
            ?: error("Drive accepted the new file but returned no id.")
    }

    private fun get(url: String, token: String): String = send(url, token, "GET", null)

    private fun send(url: String, token: String, method: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Drive puts a readable reason in the error stream; surface it, not just the code.
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Drive returned HTTP $code${detail.take(300).prependIfNotBlank(": ")}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private fun String.prependIfNotBlank(prefix: String) = if (isBlank()) "" else prefix + this

/** Minimal Task-to-coroutine bridge; avoids pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
