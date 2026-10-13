package com.jgantonio.notasinfinitas

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class Folder(val id: String, var name: String, var color: Int, var parent: String?, val created: Long)

class NoteInfo(
    val id: String, var title: String, var folder: String, val created: Long, var modified: Long,
    /** Fica no topo da página inicial e da pasta. */
    var pinned: Boolean = false,
    var favorite: Boolean = false,
    var tags: List<String> = emptyList(),
)

/** Nota na lixeira: guarda o nome da pasta de origem (para recriar se ela não existir mais). */
class TrashedNote(val note: NoteInfo, val folderName: String, val trashedAt: Long)

/**
 * Biblioteca de pastas e notas. O índice fica em library.json; cada nota tem
 * seu arquivo em notes/<id>.ninf, suas imagens em notes/<id>/ e a miniatura
 * em thumbs/<id>.png.
 */
object Library {

    val folderColors = listOf(
        "#F2B705", "#F27405", "#D7263D", "#E84A8A", "#8E44AD",
        "#1F5FD1", "#1A9FB5", "#1E9E5A", "#7A8B99", "#6D4C41",
    ).map(Color::parseColor)

    /** Fila única para gravações em disco (notas, miniaturas). */
    val io = Executors.newSingleThreadExecutor()

    val folders = ArrayList<Folder>()
    val notes = ArrayList<NoteInfo>()
    /** Lixeira: notas excluídas ficam aqui por [TRASH_DAYS] dias, com os arquivos intactos. */
    val trash = ArrayList<TrashedNote>()
    const val TRASH_DAYS = 30

    private lateinit var dir: File
    private var loaded = false

    fun init(context: Context, reload: Boolean = false) {
        val d = context.filesDir
        if (loaded && dir == d && !reload) return
        folders.clear()
        notes.clear()
        trash.clear()
        dir = d
        load()
        loaded = true
        purgeOldTrash()
        migrateFirstPrototype()
        if (folders.isEmpty()) {
            createFolder("Geral", folderColors[5], null)
        }
    }

    // ---- Caminhos ------------------------------------------------------------

    fun noteFile(id: String) = File(dir, "notes/$id.ninf")
    fun assetsDir(id: String) = File(dir, "notes/$id")
    fun thumbFile(id: String) = File(dir, "thumbs/$id.png")
    /** Texto da nota para a busca: o digitado e o reconhecido da escrita à mão. */
    fun textFile(id: String) = File(dir, "notes/$id.txt")
    /** Imagem de capa de uma pasta (opcional). */
    fun coverFile(folderId: String) = File(dir, "covers/$folderId.jpg")

    // ---- Consultas -----------------------------------------------------------

    fun folder(id: String?) = folders.firstOrNull { it.id == id }
    fun note(id: String?) = notes.firstOrNull { it.id == id }

    fun subfolders(parent: String?) =
        folders.filter { it.parent == parent }.sortedBy { it.name.lowercase() }

    fun notesIn(folder: String) =
        notes.filter { it.folder == folder }.sortedWith(compareByDescending<NoteInfo> { it.pinned }.thenByDescending { it.modified })

    /** Todas as etiquetas usadas, em ordem alfabética. */
    fun allTags(): List<String> = notes.flatMap { it.tags }.distinct().sortedBy { it.lowercase() }

    fun setTags(n: NoteInfo, tags: List<String>) {
        n.tags = tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        save()
    }

    fun setPinned(n: NoteInfo, on: Boolean) { n.pinned = on; save() }
    fun setFavorite(n: NoteInfo, on: Boolean) { n.favorite = on; save() }

    fun itemCount(folder: String) =
        folders.count { it.parent == folder } + notes.count { it.folder == folder }

    /** Caminho "Pasta › Subpasta" para mostrar no topo. */
    fun pathOf(id: String?): String {
        val parts = ArrayList<String>()
        var f = folder(id)
        while (f != null) {
            parts.add(0, f.name)
            f = folder(f.parent)
        }
        return parts.joinToString(" › ")
    }

    /** Todas as pastas em ordem de árvore, com a profundidade. */
    fun tree(): List<Pair<Folder, Int>> {
        val out = ArrayList<Pair<Folder, Int>>()
        fun walk(parent: String?, depth: Int) {
            for (f in subfolders(parent)) {
                out.add(f to depth)
                walk(f.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun isInside(folderId: String?, ancestor: String): Boolean {
        var f = folder(folderId)
        while (f != null) {
            if (f.id == ancestor) return true
            f = folder(f.parent)
        }
        return false
    }

    // ---- Alterações -------------------------------------------------------------

    fun createFolder(name: String, color: Int, parent: String?): Folder {
        val f = Folder(newId(), name.ifBlank { "Nova pasta" }, color, parent, System.currentTimeMillis())
        folders.add(f)
        save()
        return f
    }

    fun updateFolder(f: Folder, name: String = f.name, color: Int = f.color, parent: String? = f.parent) {
        f.name = name.ifBlank { f.name }
        f.color = color
        if (parent == null || !isInside(parent, f.id)) f.parent = parent
        save()
    }

    /** Apaga a pasta (e subpastas); as notas dela vão para a lixeira. */
    fun deleteFolder(id: String) {
        for (child in folders.filter { it.parent == id }) deleteFolder(child.id)
        val name = folder(id)?.name ?: ""
        val now = System.currentTimeMillis()
        for (n in notes.filter { it.folder == id }) trash.add(TrashedNote(n, name, now))
        notes.removeAll { it.folder == id }
        folders.removeAll { it.id == id }
        io.execute { coverFile(id).delete() }
        save()
    }

    fun createNote(folder: String, title: String): NoteInfo {
        val now = System.currentTimeMillis()
        val n = NoteInfo(newId(), title, folder, now, now)
        notes.add(n)
        save()
        return n
    }

    fun updateNote(n: NoteInfo, title: String = n.title, folder: String = n.folder, touch: Boolean = false) {
        n.title = title.ifBlank { n.title }
        n.folder = folder
        if (touch) n.modified = System.currentTimeMillis()
        save()
    }

    // ---- Lixeira -------------------------------------------------------------------

    fun trashNote(id: String) {
        val n = notes.firstOrNull { it.id == id } ?: return
        trash.add(TrashedNote(n, folder(n.folder)?.name ?: "", System.currentTimeMillis()))
        notes.remove(n)
        save()
    }

    /** Volta a nota para a pasta de origem; se ela sumiu, para uma pasta com o mesmo nome (criada se preciso). */
    fun restore(t: TrashedNote): NoteInfo {
        val n = t.note
        if (folder(n.folder) == null) {
            val name = t.folderName.ifBlank { "Recuperadas" }
            n.folder = (folders.firstOrNull { it.parent == null && it.name == name }
                ?: createFolder(name, folderColors[folders.size % folderColors.size], null)).id
        }
        trash.remove(t)
        notes.add(n)
        save()
        return n
    }

    fun deleteForever(t: TrashedNote) {
        trash.remove(t)
        deleteNoteFiles(t.note.id)
        save()
    }

    fun emptyTrash() {
        for (t in trash) deleteNoteFiles(t.note.id)
        trash.clear()
        save()
    }

    /** Some de vez o que está na lixeira há mais de [TRASH_DAYS] dias. */
    internal fun purgeOldTrash(now: Long = System.currentTimeMillis()) {
        val limit = now - TRASH_DAYS * 24L * 60 * 60 * 1000
        val old = trash.filter { it.trashedAt < limit }
        if (old.isEmpty()) return
        for (t in old) deleteNoteFiles(t.note.id)
        trash.removeAll(old.toSet())
        save()
    }

    /** Apaga de vez (sem lixeira). */
    fun deleteNote(id: String) {
        deleteNoteFiles(id)
        notes.removeAll { it.id == id }
        save()
    }

    fun duplicateNote(src: NoteInfo): NoteInfo {
        val copy = createNote(src.folder, src.title + " (cópia)")
        io.execute {
            noteFile(src.id).takeIf { it.exists() }?.copyTo(noteFile(copy.id), overwrite = true)
            assetsDir(src.id).takeIf { it.exists() }?.copyRecursively(assetsDir(copy.id), overwrite = true)
            thumbFile(src.id).takeIf { it.exists() }?.copyTo(thumbFile(copy.id), overwrite = true)
            textFile(src.id).takeIf { it.exists() }?.copyTo(textFile(copy.id), overwrite = true)
        }
        return copy
    }

    private fun deleteNoteFiles(id: String) {
        io.execute {
            noteFile(id).delete()
            assetsDir(id).deleteRecursively()
            thumbFile(id).delete()
            textFile(id).delete()
        }
    }

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(16)

    // ---- Persistência -------------------------------------------------------

    private fun indexFile() = File(dir, "library.json")

    private fun load() {
        val f = indexFile()
        if (!f.exists()) return
        try {
            val json = JSONObject(f.readText())
            val fa = json.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until fa.length()) {
                val o = fa.getJSONObject(i)
                folders.add(Folder(
                    o.getString("id"), o.getString("name"), o.optInt("color", folderColors[5]),
                    if (o.isNull("parent")) null else o.optString("parent"),
                    o.optLong("created"),
                ))
            }
            val na = json.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until na.length()) notes.add(noteFrom(na.getJSONObject(i)))
            val ta = json.optJSONArray("trash") ?: JSONArray()
            for (i in 0 until ta.length()) {
                val o = ta.getJSONObject(i)
                trash.add(TrashedNote(noteFrom(o), o.optString("trashFolderName"), o.optLong("trashedAt")))
            }
            // Notas cuja pasta sumiu vão para a primeira pasta existente.
            val ids = folders.map { it.id }.toSet()
            val orphanTarget = folders.firstOrNull { it.parent == null }?.id
            if (orphanTarget != null) notes.filter { it.folder !in ids }.forEach { it.folder = orphanTarget }
        } catch (e: Exception) {
            // Índice corrompido: mantém o arquivo como backup e começa do zero.
            f.renameTo(File(dir, "library.json.bak"))
            folders.clear()
            notes.clear()
            trash.clear()
        }
    }

    private fun noteFrom(o: JSONObject): NoteInfo {
        val tags = o.optJSONArray("tags")
        return NoteInfo(
            o.getString("id"), o.getString("title"), o.getString("folder"),
            o.optLong("created"), o.optLong("modified"),
            pinned = o.optBoolean("pinned"), favorite = o.optBoolean("favorite"),
            tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
        )
    }

    private fun noteJson(n: NoteInfo) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("folder", n.folder)
        put("created", n.created); put("modified", n.modified)
        if (n.pinned) put("pinned", true)
        if (n.favorite) put("favorite", true)
        if (n.tags.isNotEmpty()) put("tags", JSONArray(n.tags))
    }

    fun save() {
        val json = JSONObject()
        json.put("version", 1)
        json.put("folders", JSONArray().apply {
            for (f in folders) put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("color", f.color)
                put("parent", f.parent ?: JSONObject.NULL); put("created", f.created)
            })
        })
        json.put("notes", JSONArray().apply { for (n in notes) put(noteJson(n)) })
        json.put("trash", JSONArray().apply {
            for (t in trash) put(noteJson(t.note).apply { put("trashFolderName", t.folderName); put("trashedAt", t.trashedAt) })
        })
        val tmp = File(dir, "library.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(indexFile())) {
            indexFile().delete()
            tmp.renameTo(indexFile())
        }
    }

    /** Traz a nota única do primeiro protótipo (nota.ninf) para a pasta "Geral". */
    private fun migrateFirstPrototype() {
        val old = File(dir, "nota.ninf")
        if (!old.exists()) return
        val folder = folders.firstOrNull { it.parent == null }
            ?: createFolder("Geral", folderColors[5], null)
        val note = createNote(folder.id, "Minha primeira nota")
        noteFile(note.id).parentFile?.mkdirs()
        if (old.renameTo(noteFile(note.id)).not()) {
            old.copyTo(noteFile(note.id), overwrite = true)
            old.delete()
        }
    }
}
