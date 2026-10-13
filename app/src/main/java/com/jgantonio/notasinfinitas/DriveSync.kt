package com.jgantonio.notasinfinitas

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** O que o backup precisa do Google Drive (a versão de verdade é [DriveRest]; os testes usam uma falsa). */
interface DriveApi {
    class RemoteFile(val id: String, val path: String)

    /** Pasta "RosaNotes" no Drive (criada na primeira vez). */
    fun folderId(): String
    fun list(folder: String): List<RemoteFile>
    fun create(folder: String, name: String, path: String, bytes: ByteArray): String
    fun update(id: String, bytes: ByteArray)
    fun download(id: String): ByteArray
    fun delete(id: String)
}

/**
 * Backup no Google Drive arquivo por arquivo: cada arquivo do app (índice, notas, imagens,
 * miniaturas e capas) vira um arquivo na pasta "RosaNotes" do Drive. Só sobe o que mudou
 * desde o último backup e apaga do Drive o que foi apagado de vez no celular.
 */
object DriveSync {
    /** Intervalo mínimo entre dois backups automáticos. */
    private const val AUTO_INTERVAL = 10 * 60 * 1000L

    class Result(val uploaded: Int, val deleted: Int, val total: Int)

    private fun sp(c: Context) = c.getSharedPreferences("drive", Context.MODE_PRIVATE)

    var lastError: String? = null
        private set

    fun connected(c: Context) = sp(c).getBoolean("connected", false)
    fun setConnected(c: Context, on: Boolean) {
        val e = sp(c).edit().putBoolean("connected", on)
        if (!on) e.remove("state").remove("auto")
        e.apply()
    }
    fun autoOn(c: Context) = sp(c).getBoolean("auto", false)
    fun setAuto(c: Context, on: Boolean) = sp(c).edit().putBoolean("auto", on).apply()
    fun lastBackup(c: Context) = sp(c).getLong("last", 0L)

    /** caminho -> (id no Drive, data de modificação, tamanho) do último envio. */
    private class Sent(val id: String, val mtime: Long, val size: Long)

    private fun loadState(c: Context): MutableMap<String, Sent> {
        val out = HashMap<String, Sent>()
        val json = sp(c).getString("state", null) ?: return out
        try {
            val o = JSONObject(json)
            for (k in o.keys()) {
                val v = o.getJSONArray(k)
                out[k] = Sent(v.getString(0), v.getLong(1), v.getLong(2))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveState(c: Context, state: Map<String, Sent>) {
        val o = JSONObject()
        for ((k, v) in state) o.put(k, JSONArray().put(v.id).put(v.mtime).put(v.size))
        sp(c).edit().putString("state", o.toString()).apply()
    }

    /** Arquivos do app, pelo caminho relativo (notes/abc.ninf...). */
    private fun localFiles(c: Context): Map<String, File> {
        val root = c.filesDir
        val out = LinkedHashMap<String, File>()
        for (part in Backup.PARTS) {
            val f = File(root, part)
            if (!f.exists()) continue
            f.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { out[it.relativeTo(root).invariantSeparatorsPath] = it }
        }
        return out
    }

    /** Nome legível no Drive (o caminho de verdade fica guardado à parte). */
    private fun displayName(path: String): String {
        val m = Regex("""^notes/([^/]+)\.ninf$""").find(path)
        if (m != null) {
            val title = Library.note(m.groupValues[1])?.title
            if (title != null) return "$title (${m.groupValues[1]}).ninf"
        }
        return path.replace('/', '_')
    }

    /** Envia o que mudou. Chamar fora da thread principal. */
    fun backup(c: Context, api: DriveApi): Result {
        val state = loadState(c)
        val local = localFiles(c)
        val folder = api.folderId()
        var up = 0
        for ((path, f) in local) {
            val prev = state[path]
            if (prev != null && prev.mtime == f.lastModified() && prev.size == f.length()) continue
            val bytes = f.readBytes()
            val id = if (prev == null) api.create(folder, displayName(path), path, bytes) else {
                try {
                    api.update(prev.id, bytes)
                    prev.id
                } catch (e: IOException) {
                    // Apagado no Drive por fora: cria de novo.
                    api.create(folder, displayName(path), path, bytes)
                }
            }
            state[path] = Sent(id, f.lastModified(), f.length())
            up++
            if (up % 20 == 0) saveState(c, state)
        }
        var del = 0
        for (path in state.keys - local.keys) {
            try {
                api.delete(state.getValue(path).id)
            } catch (_: IOException) {
            }
            state.remove(path)
            del++
        }
        saveState(c, state)
        sp(c).edit().putLong("last", System.currentTimeMillis()).apply()
        lastError = null
        return Result(up, del, local.size)
    }

    /** Troca a biblioteca deste aparelho pela do Drive. Chamar fora da thread principal. */
    fun restore(c: Context, api: DriveApi): Int {
        val folder = api.folderId()
        val remote = api.list(folder).filter { r ->
            Backup.PARTS.any { p -> r.path == p || r.path.startsWith("$p/") } && !r.path.contains("..")
        }
        if (remote.none { it.path == "library.json" }) throw IOException("Não há backup do RosaNotes no seu Drive.")
        val root = c.filesDir
        val tmp = File(c.cacheDir, "restore-drive").apply { deleteRecursively(); mkdirs() }
        for (r in remote) {
            val dest = File(tmp, r.path)
            dest.parentFile?.mkdirs()
            dest.writeBytes(api.download(r.id))
        }
        for (part in Backup.PARTS) File(root, part).deleteRecursively()
        for (part in Backup.PARTS) {
            val src = File(tmp, part)
            if (src.exists()) src.copyRecursively(File(root, part), overwrite = true)
        }
        tmp.deleteRecursively()
        // O que veio do Drive já está lá: o próximo backup só envia o que mudar.
        val state = HashMap<String, Sent>()
        for (r in remote) {
            val f = File(root, r.path)
            if (f.exists()) state[r.path] = Sent(r.id, f.lastModified(), f.length())
        }
        saveState(c, state)
        Library.init(c, reload = true)
        return remote.size
    }

    /** Backup automático ao sair de uma nota (silencioso; precisa já estar conectado). */
    fun autoIfDue(context: Context) {
        val c = context.applicationContext
        if (!connected(c) || !autoOn(c)) return
        if (System.currentTimeMillis() - lastBackup(c) < AUTO_INTERVAL) return
        Library.io.execute {
            try {
                val token = DriveAuth.silentToken(c) ?: run {
                    lastError = "Entre de novo na sua conta Google para continuar o backup."
                    return@execute
                }
                backup(c, DriveRest(token))
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }
}

/** Login do Google (só o escopo drive.file: o app vê apenas os arquivos que ele criou). */
object DriveAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /**
     * Pede autorização; se o Google precisar mostrar a tela de escolha de conta, abre e o
     * resultado volta em onActivityResult([requestCode]) (use [tokenFromResult]).
     */
    fun connect(activity: Activity, requestCode: Int, done: (token: String?, error: String?) -> Unit) {
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    try {
                        @Suppress("DEPRECATION")
                        activity.startIntentSenderForResult(pi.intentSender, requestCode, null, 0, 0, 0)
                    } catch (e: Exception) {
                        done(null, e.message)
                    }
                } else {
                    DriveSync.setConnected(activity, true)
                    done(r.accessToken, null)
                }
            }
            .addOnFailureListener { e -> done(null, friendly(e)) }
    }

    fun tokenFromResult(activity: Activity, data: Intent?): String? = try {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
            ?.also { DriveSync.setConnected(activity, true) }
    } catch (e: Exception) {
        null
    }

    /** Token sem mostrar nada (para o backup automático). Null se precisar entrar de novo. */
    fun silentToken(c: Context): String? = try {
        val r = Tasks.await(Identity.getAuthorizationClient(c).authorize(request()), 30, TimeUnit.SECONDS)
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) {
        null
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return if ("10:" in m || "DEVELOPER_ERROR" in m || "16:" in m)
            "O acesso ao Google Drive ainda não foi liberado para o app (projeto no Google Cloud)."
        else "Não foi possível entrar na conta Google: $m"
    }
}

/** API REST do Google Drive (v3) com o token do login. */
class DriveRest(private val token: String) : DriveApi {
    private val api = "https://www.googleapis.com/drive/v3/files"
    private val upload = "https://www.googleapis.com/upload/drive/v3/files"

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 60000
        if (method == "PATCH") {
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    private fun HttpURLConnection.result(): ByteArray {
        val code = responseCode
        if (code !in 200..299) {
            val err = try { errorStream?.readBytes()?.decodeToString() } catch (_: Exception) { null }
            disconnect()
            throw IOException("Drive respondeu $code${if (err != null) ": " + err.take(200) else ""}")
        }
        return inputStream.use { it.readBytes() }.also { disconnect() }
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    override fun folderId(): String {
        val query = "name='RosaNotes' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val found = JSONObject(open("$api?q=${q(query)}&fields=files(id)&spaces=drive", "GET").result().decodeToString())
            .optJSONArray("files")
        if (found != null && found.length() > 0) return found.getJSONObject(0).getString("id")
        val c = open("$api?fields=id", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(JSONObject().put("name", "RosaNotes").put("mimeType", "application/vnd.google-apps.folder").toString().toByteArray()) }
        return JSONObject(c.result().decodeToString()).getString("id")
    }

    override fun list(folder: String): List<DriveApi.RemoteFile> {
        val out = ArrayList<DriveApi.RemoteFile>()
        var page: String? = null
        do {
            val url = "$api?q=${q("'$folder' in parents and trashed=false")}&pageSize=1000" +
                "&fields=${q("nextPageToken,files(id,name,appProperties)")}" + (page?.let { "&pageToken=${q(it)}" } ?: "")
            val o = JSONObject(open(url, "GET").result().decodeToString())
            val files = o.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val path = f.optJSONObject("appProperties")?.optString("path")?.takeIf { it.isNotEmpty() } ?: continue
                out.add(DriveApi.RemoteFile(f.getString("id"), path))
            }
            page = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    override fun create(folder: String, name: String, path: String, bytes: ByteArray): String {
        val boundary = "rosanotes" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(folder))
            .put("appProperties", JSONObject().put("path", path)).toString()
        val c = open("$upload?uploadType=multipart&fields=id", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { o ->
            o.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            o.write(bytes)
            o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        return JSONObject(c.result().decodeToString()).getString("id")
    }

    override fun update(id: String, bytes: ByteArray) {
        val c = open("$upload/$id?uploadType=media&fields=id", "PATCH")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { it.write(bytes) }
        c.result()
    }

    override fun download(id: String): ByteArray = open("$api/$id?alt=media", "GET").result()

    override fun delete(id: String) {
        open("$api/$id", "DELETE").result()
    }
}
