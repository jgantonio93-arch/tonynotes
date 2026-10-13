package com.jgantonio.notasinfinitas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Área de transferência do app: guarda os itens copiados (e uma cópia das imagens que
 * eles usam) em disco, para colar na mesma nota ou em outra, mesmo depois de fechar.
 */
object Clipboard {
    private fun dir(c: Context) = File(c.filesDir, "clipboard")
    private fun file(c: Context) = File(dir(c), "clip.ninf")

    fun hasContent(c: Context) = file(c).exists()

    /** Copia [items]; as imagens são lidas de [assetDir] (a pasta da nota de origem). */
    fun copy(c: Context, items: List<Element>, assetDir: File?) {
        if (items.isEmpty()) return
        val d = dir(c)
        d.deleteRecursively()
        d.mkdirs()
        for (img in items.filterIsInstance<ImageElement>()) {
            val src = assetDir?.let { File(it, img.file) } ?: continue
            if (src.exists()) src.copyTo(File(d, img.file), overwrite = true)
        }
        val unlocked = items.map { if (it is ImageElement && it.locked) it.with(locked = false) else it }
        NoteStorage.save(file(c), NoteData(unlocked, null))
    }

    /** Print de uma área: a imagem [bmp] ocupando [w]×[h] no mundo. */
    fun copyImage(c: Context, bmp: Bitmap, w: Float, h: Float) {
        val d = dir(c)
        d.deleteRecursively()
        d.mkdirs()
        val name = "print_${UUID.randomUUID().toString().take(8)}.png"
        FileOutputStream(File(d, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        NoteStorage.save(file(c), NoteData(listOf(ImageElement(name, RectF(0f, 0f, w, h))), null))
    }

    /** Itens para colar, com as imagens já copiadas (com nomes novos) para [assetDir]. */
    fun paste(c: Context, assetDir: File): List<Element> {
        val data = NoteStorage.load(file(c)) ?: return emptyList()
        assetDir.mkdirs()
        return data.elements.mapNotNull { e ->
            if (e !is ImageElement) return@mapNotNull e
            val src = File(dir(c), e.file)
            if (!src.exists()) return@mapNotNull null
            val name = "clip_${UUID.randomUUID().toString().take(8)}_${e.file}"
            src.copyTo(File(assetDir, name), overwrite = true)
            e.with(file = name)
        }
    }
}
