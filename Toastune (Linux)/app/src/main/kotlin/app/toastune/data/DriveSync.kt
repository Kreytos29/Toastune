package app.toastune.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sincroniza la biblioteca con la cuenta de Google del usuario sin servidor propio: guarda un
 * JSON en la carpeta oculta de la app dentro de su Google Drive (`appDataFolder`). La app solo
 * ve ese archivo; no puede leer nada más del Drive.
 */
class DriveSync(context: Context, private val client: OkHttpClient) {

    private val auth = Identity.getAuthorizationClient(context.applicationContext)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    suspend fun authorize(): AuthorizationResult = auth.authorize(request).await()

    fun resultFromIntent(data: Intent?): AuthorizationResult = auth.getAuthorizationResultFromIntent(data)

    /** Trae lo de la nube, lo une con lo local y sube el resultado. */
    suspend fun sync(token: String, library: LibraryRepository) {
        val id = withContext(Dispatchers.IO) { findFileId(token) }
        if (id != null) {
            val remote = withContext(Dispatchers.IO) { download(token, id) }
            library.importJson(remote)
        }
        val merged = library.exportJson()
        withContext(Dispatchers.IO) { upload(token, id, merged) }
    }

    private fun call(builder: Request.Builder, token: String): String =
        client.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Google Drive respondió ${r.code}")
            body
        }

    private fun findFileId(token: String): String? {
        val q = URLEncoder.encode("name='$FILE'", "UTF-8")
        val body = call(
            Request.Builder().url("$API/files?spaces=appDataFolder&fields=files(id)&q=$q"),
            token,
        )
        val files = JSONObject(body).optJSONArray("files")
        return if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun download(token: String, id: String): String =
        call(Request.Builder().url("$API/files/$id?alt=media"), token)

    private fun upload(token: String, id: String?, json: String) {
        val jsonType = "application/json; charset=UTF-8".toMediaType()
        if (id == null) {
            val meta = JSONObject().put("name", FILE).put("parents", org.json.JSONArray().put("appDataFolder"))
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(meta.toString().toRequestBody(jsonType))
                .addPart(json.toRequestBody(jsonType))
                .build()
            call(Request.Builder().url("$UPLOAD/files?uploadType=multipart").post(body), token)
        } else {
            call(
                Request.Builder().url("$UPLOAD/files/$id?uploadType=media").patch(json.toRequestBody(jsonType)),
                token,
            )
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE = "toastune-sync.json"
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
