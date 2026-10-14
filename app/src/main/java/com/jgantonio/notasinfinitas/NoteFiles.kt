package com.jgantonio.notasinfinitas

import android.content.Context
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Arquivos de uma nota para levar para fora do app:
 *  - PDF (para ler em qualquer lugar);
 *  - pacote editável .rosanota (zip com a nota, as imagens dela e a miniatura), que
 *    volta para o RosaNotes com tudo editável.
 */
object NoteFiles {
    const val EXT = "rosanota"
    const val MIME = "application/zip"

    /** PDF do que está na tela de [view] (null se a nota estiver vazia). Quem chama fecha. */
    fun pdf(view: InfiniteCanvasView): PdfDocument? {
        val content = view.contentBounds() ?: return null
        val doc = PdfDocument()
        val pages = view.usedPageRects()
        if (pages.isNotEmpty()) {
            // Folha do tamanho A4 (595 pt de largura em retrato).
            val s = 595f / min(pages[0].width(), pages[0].height())
            pages.forEachIndexed { i, r ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder((r.width() * s).toInt(), (r.height() * s).toInt(), i + 1).create())
                val c = page.canvas
                c.drawColor(view.paperColor)
                c.scale(s, s)
                c.translate(-r.left, -r.top)
                c.clipRect(r)
                view.drawTemplateForExport(c, r)
                view.drawContent(c, r)
                doc.finishPage(page)
            }
        } else {
            val pad = 40f
            val w = content.width() + pad * 2
            val h = content.height() + pad * 2
            // Limite do PDF: 14400 pt por lado.
            val s = min(1f, min(14400f / w, 14400f / h))
            val page = doc.startPage(PdfDocument.PageInfo.Builder(max(1, (w * s).toInt()), max(1, (h * s).toInt()), 1).create())
            val c = page.canvas
            c.drawColor(view.paperColor)
            c.scale(s, s)
            c.translate(pad - content.left, pad - content.top)
            view.drawContent(c, null)
            doc.finishPage(page)
        }
        return doc
    }

    /** PDF de uma nota salva, sem abrir a tela dela (para o backup). Null se vazia. */
    fun pdfBytes(c: Context, noteId: String): ByteArray? {
        val data = NoteStorage.load(Library.noteFile(noteId)) ?: return null
        // A tela da nota é criada na thread principal (por segurança); o desenho do PDF, aqui.
        val view = onMain {
            InfiniteCanvasView(c.applicationContext).apply {
                assetDir = Library.assetsDir(noteId)
                load(data)
            }
        }
        try {
            val doc = pdf(view) ?: return null
            return try {
                ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
            } finally {
                doc.close()
            }
        } finally {
            view.images.close()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val main = android.os.Looper.getMainLooper()
        if (android.os.Looper.myLooper() == main) return block()
        var result: Result<T>? = null
        val done = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(main).post {
            result = runCatching(block)
            done.countDown()
        }
        if (!done.await(20, java.util.concurrent.TimeUnit.SECONDS)) throw IllegalStateException("tempo esgotado")
        return result!!.getOrThrow()
    }

    /** Pacote editável da nota. */
    fun bundle(note: NoteInfo): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, f: File) {
                if (!f.isFile) return
                z.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            z.putNextEntry(ZipEntry("nota.json"))
            z.write(JSONObject()
                .put("formato", 1)
                .put("id", note.id)
                .put("titulo", note.title)
                .put("pasta", Library.pathOf(note.folder))
                .put("criada", note.created)
                .put("modificada", note.modified)
                .toString().toByteArray())
            z.closeEntry()
            put("nota.ninf", Library.noteFile(note.id))
            // Os áudios vão à parte (pasta "Gravações" no Drive); a lista deles (audio.json) vai junto.
            Library.assetsDir(note.id).listFiles()?.filter { !AudioNotes.isAudioFile(it.name) }?.sortedBy { it.name }
                ?.forEach { put("imagens/${it.name}", it) }
            put("miniatura.png", Library.thumbFile(note.id))
        }
        return out.toByteArray()
    }

    /** Conteúdo de um pacote .rosanota. */
    class Unpacked(val meta: JSONObject, val note: ByteArray, val images: Map<String, ByteArray>, val thumb: ByteArray?)

    fun unpack(input: InputStream): Unpacked? {
        var meta: JSONObject? = null
        var note: ByteArray? = null
        var thumb: ByteArray? = null
        val images = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || name.contains("..")) continue
                val bytes = z.readBytes()
                when {
                    name == "nota.json" -> meta = try { JSONObject(bytes.decodeToString()) } catch (_: Exception) { null }
                    name == "nota.ninf" -> note = bytes
                    name == "miniatura.png" -> thumb = bytes
                    name.startsWith("imagens/") && name.length > 8 && !name.substring(8).contains('/') -> images[name.substring(8)] = bytes
                }
            }
        }
        return Unpacked(meta ?: JSONObject(), note ?: return null, images, thumb)
    }

    /** Grava o conteúdo do pacote como a nota [id] (arquivo, imagens e miniatura). */
    fun writeNote(u: Unpacked, id: String) {
        Library.noteFile(id).apply { parentFile?.mkdirs() }.writeBytes(u.note)
        val dir = Library.assetsDir(id)
        dir.deleteRecursively()
        if (u.images.isNotEmpty()) {
            dir.mkdirs()
            for ((n, b) in u.images) File(dir, n).writeBytes(b)
        }
        u.thumb?.let { Library.thumbFile(id).apply { parentFile?.mkdirs() }.writeBytes(it) }
    }

    /** Abre um .rosanota recebido como nota nova na pasta [folder]. */
    fun import(input: InputStream, folder: String): NoteInfo? {
        val u = unpack(input) ?: return null
        val title = u.meta.optString("titulo").ifBlank { "Nota importada" }
        val n = Library.createNote(folder, title)
        writeNote(u, n.id)
        return n
    }

}
