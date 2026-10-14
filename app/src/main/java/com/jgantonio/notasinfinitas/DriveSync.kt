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
    /** Arquivo ou pasta criado pelo app, com as marcas que ele guardou (appProperties). */
    class RemoteFile(val id: String, val name: String, val props: Map<String, String>)

    /** Pasta "RosaNotes" no Drive (criada na primeira vez). */
    fun folderId(): String
    fun createFolder(parent: String, name: String, props: Map<String, String>): String
    fun create(parent: String, name: String, mime: String, props: Map<String, String>, bytes: ByteArray): String
    fun update(id: String, bytes: ByteArray)
    /** Renomeia e/ou move (de [oldParent] para [newParent]). */
    fun move(id: String, name: String, newParent: String, oldParent: String)
    /** Arquivos do app com a marca key=value, em qualquer pasta (fora da lixeira). */
    fun find(key: String, value: String): List<RemoteFile>
    /** O que está direto dentro de [folder]. */
    fun children(folder: String): List<RemoteFile>
    fun download(id: String): ByteArray
    fun delete(id: String)
}

/**
 * Backup no Google Drive: na pasta "RosaNotes" ficam subpastas iguais às do app e, para
 * cada nota, um arquivo editável (.rosanota, que volta para o RosaNotes) e um PDF para ler
 * no próprio Drive. Só sobe o que mudou; nota apagada ou renomeada é apagada ou renomeada
 * lá também. Um arquivo de índice guarda pastas, cores e ordem para o Restaurar.
 */
object DriveSync {
    /** Intervalo mínimo entre dois backups automáticos. */
    private const val AUTO_INTERVAL = 10 * 60 * 1000L
    const val INDEX_NAME = "RosaNotes – índice (não apagar).rosanotes"

    class Result(val uploaded: Int, val deleted: Int, val total: Int)

    /** Gera o PDF de uma nota (trocável nos testes, onde o PdfDocument do Android não existe). */
    internal var pdfMaker: (Context, String) -> ByteArray? = NoteFiles::pdfBytes

    private fun sp(c: Context) = c.getSharedPreferences("drive", Context.MODE_PRIVATE)

    var lastError: String? = null
        private set

    fun connected(c: Context) = sp(c).getBoolean("connected", false)
    fun setConnected(c: Context, on: Boolean) {
        val e = sp(c).edit().putBoolean("connected", on)
        if (!on) e.remove("state").remove("state2").remove("auto")
        e.apply()
    }
    fun autoOn(c: Context) = sp(c).getBoolean("auto", false)
    fun setAuto(c: Context, on: Boolean) = sp(c).edit().putBoolean("auto", on).apply()
    fun lastBackup(c: Context) = sp(c).getLong("last", 0L)

    // ---- Estado do último envio ------------------------------------------------------------

    private class FolderState(val id: String, val name: String, val parent: String)
    private class NoteState(val bundle: String, val pdf: String?, val sig: String, val name: String, val parent: String)
    private class State(
        val folders: MutableMap<String, FolderState> = HashMap(),
        val notes: MutableMap<String, NoteState> = HashMap(),
        var index: String? = null,
        var indexSig: String = "",
    )

    private fun loadState(c: Context): State {
        val st = State()
        val json = sp(c).getString("state2", null) ?: return st
        try {
            val o = JSONObject(json)
            o.optJSONObject("folders")?.let { f ->
                for (k in f.keys()) f.getJSONArray(k).let { st.folders[k] = FolderState(it.getString(0), it.getString(1), it.getString(2)) }
            }
            o.optJSONObject("notes")?.let { n ->
                for (k in n.keys()) n.getJSONArray(k).let {
                    st.notes[k] = NoteState(it.getString(0), it.optString(1).ifEmpty { null }, it.getString(2), it.getString(3), it.getString(4))
                }
            }
            st.index = o.optString("index").ifEmpty { null }
            st.indexSig = o.optString("indexSig")
        } catch (_: Exception) {
        }
        return st
    }

    private fun saveState(c: Context, st: State) {
        val f = JSONObject()
        for ((k, v) in st.folders) f.put(k, JSONArray().put(v.id).put(v.name).put(v.parent))
        val n = JSONObject()
        for ((k, v) in st.notes) n.put(k, JSONArray().put(v.bundle).put(v.pdf ?: "").put(v.sig).put(v.name).put(v.parent))
        val o = JSONObject().put("folders", f).put("notes", n).put("index", st.index ?: "").put("indexSig", st.indexSig)
        sp(c).edit().putString("state2", o.toString()).apply()
    }

    // ---- Nomes e assinaturas ---------------------------------------------------------------

    /** Nome que o Drive aceita bem (sem barras nem quebras de linha). */
    private fun safe(name: String) = name.replace(Regex("""[/\\\n\r\t]"""), "-").trim().ifEmpty { "Sem título" }.take(120)

    /** Muda quando a nota, as imagens dela ou o título mudam. */
    private fun noteSig(n: NoteInfo): String {
        val f = Library.noteFile(n.id)
        val imgs = Library.assetsDir(n.id).listFiles().orEmpty()
        return "${f.lastModified()}:${f.length()}:${imgs.size}:${imgs.maxOfOrNull { it.lastModified() } ?: 0}"
    }

    private fun indexFiles(c: Context): List<Pair<String, File>> {
        val root = c.filesDir
        val out = ArrayList<Pair<String, File>>()
        File(root, "library.json").takeIf { it.isFile }?.let { out.add("library.json" to it) }
        File(root, "covers").listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { out.add("covers/${it.name}" to it) }
        return out
    }

    private fun indexBytes(files: List<Pair<String, File>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            for ((name, f) in files) {
                z.putNextEntry(java.util.zip.ZipEntry(name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---- Backup ----------------------------------------------------------------------------

    /** Envia o que mudou. Chamar fora da thread principal. */
    fun backup(c: Context, api: DriveApi): Result {
        val st = loadState(c)
        val root = api.folderId()
        cleanupOldFormat(c, api, root)
        val folders = Library.folders.toList()
        val notes = Library.notes.toList()

        // Pastas do app viram subpastas (os pais primeiro).
        fun driveFolder(f: Folder, depth: Int = 0): String {
            val parent = f.parent?.let { pid -> folders.firstOrNull { it.id == pid } }
                ?.takeIf { depth < 32 }?.let { driveFolder(it, depth + 1) } ?: root
            val name = safe(f.name)
            val prev = st.folders[f.id]
            if (prev != null) {
                if (prev.name == name && prev.parent == parent) return prev.id
                try {
                    api.move(prev.id, name, parent, prev.parent)
                    st.folders[f.id] = FolderState(prev.id, name, parent)
                    return prev.id
                } catch (_: IOException) {
                    // Apagada no Drive por fora: cria de novo.
                }
            }
            val id = api.createFolder(parent, name, mapOf("kind" to "folder", "folder" to f.id))
            st.folders[f.id] = FolderState(id, name, parent)
            return id
        }

        var up = 0
        for (n in notes) {
            val folder = folders.firstOrNull { it.id == n.folder }
            val parent = folder?.let { driveFolder(it) } ?: root
            val name = safe(n.title)
            val sig = noteSig(n)
            val prev = st.notes[n.id]
            if (prev != null && prev.sig == sig) {
                if (prev.name != name || prev.parent != parent) {
                    try {
                        api.move(prev.bundle, "$name.${NoteFiles.EXT}", parent, prev.parent)
                        prev.pdf?.let { api.move(it, "$name.pdf", parent, prev.parent) }
                        st.notes[n.id] = NoteState(prev.bundle, prev.pdf, sig, name, parent)
                        saveState(c, st)
                    } catch (_: IOException) {
                        st.notes.remove(n.id)
                        uploadNote(c, api, st, n, sig, name, parent, null)
                        up++
                    }
                }
                continue
            }
            uploadNote(c, api, st, n, sig, name, parent, prev)
            up++
        }

        // Notas apagadas (ou na lixeira) saem do Drive.
        var del = 0
        val alive = notes.map { it.id }.toSet()
        for (id in st.notes.keys - alive) {
            val s = st.notes.getValue(id)
            for (fid in listOfNotNull(s.bundle, s.pdf)) try { api.delete(fid) } catch (_: IOException) {}
            st.notes.remove(id)
            del++
        }
        // Pastas apagadas no app (depois de as notas terem saído delas).
        val aliveFolders = folders.map { it.id }.toSet()
        for (id in st.folders.keys - aliveFolders) {
            try { api.delete(st.folders.getValue(id).id) } catch (_: IOException) {}
            st.folders.remove(id)
        }

        // Índice: pastas, cores, ordem e capas (para o Restaurar).
        val idx = indexFiles(c)
        val idxSig = idx.joinToString("|") { (n, f) -> "$n:${f.lastModified()}:${f.length()}" }
        if (st.index == null || st.indexSig != idxSig) {
            val bytes = indexBytes(idx)
            st.index = st.index?.let { id ->
                try { api.update(id, bytes); id } catch (_: IOException) { null }
            } ?: api.create(root, INDEX_NAME, "application/zip", mapOf("kind" to "index"), bytes)
            st.indexSig = idxSig
        }
        saveState(c, st)
        sp(c).edit().putLong("last", System.currentTimeMillis()).apply()
        lastError = null
        return Result(up, del, notes.size)
    }

    private fun uploadNote(c: Context, api: DriveApi, st: State, n: NoteInfo, sig: String, name: String, parent: String, prev: NoteState?) {
        val bundle = NoteFiles.bundle(n)
        val pdf = try { pdfMaker(c, n.id) } catch (e: Throwable) { null }
        val props = mapOf("kind" to "note", "note" to n.id)
        val pdfProps = mapOf("kind" to "pdf", "note" to n.id)
        fun put(id: String?, file: String, mime: String, p: Map<String, String>, bytes: ByteArray): String {
            if (id != null && prev != null) {
                try {
                    api.update(id, bytes)
                    if (prev.name != name || prev.parent != parent) api.move(id, file, parent, prev.parent)
                    return id
                } catch (_: IOException) {
                }
            }
            return api.create(parent, file, mime, p, bytes)
        }
        val bundleId = put(prev?.bundle, "$name.${NoteFiles.EXT}", NoteFiles.MIME, props, bundle)
        val pdfId = if (pdf != null) put(prev?.pdf, "$name.pdf", "application/pdf", pdfProps, pdf) else {
            prev?.pdf?.let { try { api.delete(it) } catch (_: IOException) {} }
            null
        }
        st.notes[n.id] = NoteState(bundleId, pdfId, sig, name, parent)
        saveState(c, st)
    }

    /** Apaga do Drive os arquivos soltos do formato antigo (um por arquivo do app). */
    private fun cleanupOldFormat(c: Context, api: DriveApi, root: String) {
        if (sp(c).getBoolean("v2", false)) return
        try {
            for (r in api.children(root)) if (r.props.containsKey("path")) {
                try { api.delete(r.id) } catch (_: IOException) {}
            }
        } catch (_: IOException) {
        }
        sp(c).edit().putBoolean("v2", true).remove("state").apply()
    }

    // ---- Restaurar -------------------------------------------------------------------------

    /** Troca a biblioteca deste aparelho pela do Drive. Devolve o número de notas. Chamar fora da thread principal. */
    fun restore(c: Context, api: DriveApi): Int {
        val index = api.find("kind", "index").firstOrNull() ?: throw IOException("Não há backup do RosaNotes no seu Drive.")
        val notes = api.find("kind", "note")
        val pdfs = api.find("kind", "pdf").associateBy { it.props["note"] }
        val root = c.filesDir
        val tmp = File(c.cacheDir, "restore-drive").apply { deleteRecursively(); mkdirs() }
        java.util.zip.ZipInputStream(api.download(index.id).inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || e.name.contains("..")) continue
                if (e.name != "library.json" && !e.name.startsWith("covers/")) continue
                File(tmp, e.name).apply { parentFile?.mkdirs() }.writeBytes(z.readBytes())
            }
        }
        if (!File(tmp, "library.json").isFile) throw IOException("O índice do backup no Drive está danificado.")
        val unpacked = ArrayList<Pair<String, NoteFiles.Unpacked>>()
        for (r in notes) {
            val id = r.props["note"]?.takeIf { it.isNotBlank() && !it.contains('/') } ?: continue
            NoteFiles.unpack(api.download(r.id).inputStream())?.let { unpacked.add(id to it) }
        }
        for (part in Backup.PARTS) File(root, part).deleteRecursively()
        File(tmp, "library.json").copyTo(File(root, "library.json"), overwrite = true)
        File(tmp, "covers").takeIf { it.exists() }?.copyRecursively(File(root, "covers"), overwrite = true)
        tmp.deleteRecursively()
        Library.init(c, reload = true)
        for ((id, u) in unpacked) NoteFiles.writeNote(u, id)

        // O que veio do Drive já está lá: o próximo backup só envia o que mudar.
        val st = State()
        for (r in api.find("kind", "folder")) {
            val fid = r.props["folder"] ?: continue
            val f = Library.folder(fid) ?: continue
            st.folders[fid] = FolderState(r.id, "\u0000", "")   // nome/pai conferidos no próximo backup
        }
        for (r in notes) {
            val id = r.props["note"] ?: continue
            val n = Library.note(id) ?: continue
            st.notes[id] = NoteState(r.id, pdfs[id]?.id, noteSig(n), "\u0000", "")
        }
        st.index = index.id
        saveState(c, st)
        sp(c).edit().putBoolean("v2", true).remove("state").apply()
        return unpacked.size
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
        val query = "name='RosaNotes' and mimeType='application/vnd.google-apps.folder' and 'root' in parents and trashed=false"
        val found = JSONObject(open("$api?q=${q(query)}&fields=files(id)&spaces=drive", "GET").result().decodeToString())
            .optJSONArray("files")
        if (found != null && found.length() > 0) return found.getJSONObject(0).getString("id")
        val c = open("$api?fields=id", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(JSONObject().put("name", "RosaNotes").put("mimeType", "application/vnd.google-apps.folder").toString().toByteArray()) }
        return JSONObject(c.result().decodeToString()).getString("id")
    }

    private fun listQuery(query: String): List<DriveApi.RemoteFile> {
        val out = ArrayList<DriveApi.RemoteFile>()
        var page: String? = null
        do {
            val url = "$api?q=${q(query)}&pageSize=1000&spaces=drive" +
                "&fields=${q("nextPageToken,files(id,name,appProperties)")}" + (page?.let { "&pageToken=${q(it)}" } ?: "")
            val o = JSONObject(open(url, "GET").result().decodeToString())
            val files = o.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val props = HashMap<String, String>()
                f.optJSONObject("appProperties")?.let { p -> for (k in p.keys()) props[k] = p.optString(k) }
                out.add(DriveApi.RemoteFile(f.getString("id"), f.optString("name"), props))
            }
            page = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    private fun esc(v: String) = v.replace("\\", "\\\\").replace("'", "\\'")

    override fun find(key: String, value: String) =
        listQuery("appProperties has { key='${esc(key)}' and value='${esc(value)}' } and trashed=false")

    override fun children(folder: String) = listQuery("'${esc(folder)}' in parents and trashed=false")

    private fun meta(name: String, parent: String?, mime: String?, props: Map<String, String>): String {
        val o = JSONObject().put("name", name)
        if (parent != null) o.put("parents", JSONArray().put(parent))
        if (mime != null) o.put("mimeType", mime)
        if (props.isNotEmpty()) o.put("appProperties", JSONObject(props as Map<*, *>))
        return o.toString()
    }

    override fun createFolder(parent: String, name: String, props: Map<String, String>): String {
        val c = open("$api?fields=id", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(meta(name, parent, "application/vnd.google-apps.folder", props).toByteArray()) }
        return JSONObject(c.result().decodeToString()).getString("id")
    }

    override fun create(parent: String, name: String, mime: String, props: Map<String, String>, bytes: ByteArray): String {
        val boundary = "rosanotes" + System.nanoTime()
        val c = open("$upload?uploadType=multipart&fields=id", "POST")
        c.doOutput = true
        c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { o ->
            o.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${meta(name, parent, null, props)}\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            o.write(bytes)
            o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        return JSONObject(c.result().decodeToString()).getString("id")
    }

    override fun update(id: String, bytes: ByteArray) {
        val c = open("$upload/$id?uploadType=media&fields=id", "PATCH")
        c.doOutput = true
        c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { it.write(bytes) }
        c.result()
    }

    override fun move(id: String, name: String, newParent: String, oldParent: String) {
        var url = "$api/$id?fields=id"
        if (newParent != oldParent) {
            url += "&addParents=${q(newParent)}"
            if (oldParent.isNotEmpty()) url += "&removeParents=${q(oldParent)}"
        }
        val c = open(url, "PATCH")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(JSONObject().put("name", name).toString().toByteArray()) }
        c.result()
    }

    override fun download(id: String): ByteArray = open("$api/$id?alt=media", "GET").result()

    /**
     * Converte uma apresentação do PowerPoint em PDF pelo Google: sobe como Google
     * Apresentações, exporta em PDF e apaga o arquivo temporário do Drive.
     */
    fun convertToPdf(name: String, bytes: ByteArray, mime: String): ByteArray {
        val boundary = "rosanotes" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.presentation").toString()
        val c = open("$upload?uploadType=multipart&fields=id", "POST")
        c.doOutput = true
        c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { o ->
            o.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            o.write(bytes)
            o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val id = JSONObject(c.result().decodeToString()).getString("id")
        try {
            val e = open("$api/$id/export?mimeType=${q("application/pdf")}", "GET")
            e.readTimeout = 180000
            return e.result()
        } finally {
            try {
                delete(id)
            } catch (_: Exception) {
            }
        }
    }

    override fun delete(id: String) {
        open("$api/$id", "DELETE").result()
    }
}
