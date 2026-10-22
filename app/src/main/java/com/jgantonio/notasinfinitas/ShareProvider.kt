package com.jgantonio.notasinfinitas

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Entrega a outros apps (WhatsApp, e-mail, Drive...) os arquivos gerados para compartilhar,
 * só leitura e só da pasta cache/share. Exceção: a foto da câmera (camera_*.jpg), que o
 * app de câmera grava. O acesso é dado por intent (grantUriPermissions).
 */
class ShareProvider : ContentProvider() {

    override fun onCreate() = true

    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains("..")) return null
        val f = File(dir(context ?: return null), name)
        return f.takeIf { it.exists() }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileFor(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        if (mode.contains('w')) {
            // Só a foto da câmera pode ser gravada (o app de câmera escreve nela).
            if (!isCameraFile(f.name)) throw SecurityException("Somente leitura")
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply {
            addRow(cols.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null })
        }
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment?.substringAfterLast('.')?.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun isCameraFile(name: String) = name.startsWith("camera_") && name.endsWith(".jpg")
        fun dir(c: Context) = File(c.cacheDir, "share").apply { mkdirs() }
        fun uriFor(c: Context, file: File): Uri = Uri.parse("content://${c.packageName}.share/${Uri.encode(file.name)}")
    }
}
