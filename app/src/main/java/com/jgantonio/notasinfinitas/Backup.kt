package com.jgantonio.notasinfinitas

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cópia de segurança de todas as pastas e notas num único arquivo .zip.
 *
 * O arquivo é criado pelo seletor de arquivos do Android: cada pessoa escolhe a
 * própria conta do Google Drive (ou outro lugar) e o app guarda só a permissão
 * para aquele arquivo. Não há servidor nem chave do TonyNotes no meio: cada
 * cópia fica no Drive de quem usa o app.
 *
 * Com o backup automático ligado, o mesmo arquivo é regravado de tempos em tempos
 * (ao sair de uma nota), sempre com a versão mais nova.
 */
object Backup {

    const val MIME = "application/zip"
    private const val MARK = "tonynotes-backup.txt"
    private val PARTS = listOf("library.json", "notes", "thumbs", "covers")
    /** Intervalo mínimo entre dois backups automáticos. */
    private const val AUTO_INTERVAL = 10 * 60 * 1000L

    private fun sp(c: Context) = c.getSharedPreferences("backup", Context.MODE_PRIVATE)

    /** Arquivo do backup automático (null = desligado). */
    fun autoUri(c: Context): Uri? = sp(c).getString("auto_uri", null)?.let(Uri::parse)

    fun lastBackup(c: Context): Long = sp(c).getLong("last", 0L)

    fun enableAuto(c: Context, uri: Uri) {
        try {
            c.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Alguns provedores não dão permissão permanente: o backup vale até reiniciar.
        }
        sp(c).edit().putString("auto_uri", uri.toString()).apply()
    }

    fun disableAuto(c: Context) {
        val uri = autoUri(c)
        if (uri != null) {
            try {
                c.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
        }
        sp(c).edit().remove("auto_uri").apply()
    }

    /** Backup automático, se ligado e se já passou o intervalo. Roda na fila de disco. */
    fun autoBackupIfDue(context: Context) {
        val c = context.applicationContext
        val uri = autoUri(c) ?: return
        if (System.currentTimeMillis() - lastBackup(c) < AUTO_INTERVAL) return
        Library.io.execute {
            try {
                write(c, uri)
            } catch (_: Exception) {
                // Sem rede ou permissão perdida: tenta de novo na próxima vez.
            }
        }
    }

    /** Grava o backup em [uri]. Chamar fora da thread principal (ex.: na [Library.io]). */
    fun write(c: Context, uri: Uri) {
        val out = c.contentResolver.openOutputStream(uri, "wt") ?: error("Não foi possível abrir o arquivo")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MARK))
            zip.write("TonyNotes 1\n".toByteArray())
            zip.closeEntry()
            val root = c.filesDir
            for (part in PARTS) {
                val f = File(root, part)
                if (!f.exists()) continue
                f.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        sp(c).edit().putLong("last", System.currentTimeMillis()).apply()
    }

    /**
     * Restaura um backup: substitui as pastas e notas atuais pelas do arquivo.
     * Antes de apagar qualquer coisa, confere se o arquivo é mesmo um backup do
     * TonyNotes e o extrai numa pasta temporária. Chamar fora da thread principal.
     */
    fun restore(c: Context, uri: Uri) {
        val root = c.filesDir
        val tmp = File(c.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var valid = false
        val input = c.contentResolver.openInputStream(uri) ?: error("Não foi possível abrir o arquivo")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == MARK) valid = true
                val dest = File(tmp, e.name)
                // Não deixa o .zip escrever fora da pasta (nomes com "..").
                if (!dest.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) continue
                if (e.isDirectory) dest.mkdirs() else {
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        if (!valid || !File(tmp, "library.json").exists()) {
            tmp.deleteRecursively()
            error("Este arquivo não é um backup do TonyNotes")
        }
        for (part in PARTS) File(root, part).deleteRecursively()
        for (part in PARTS) {
            val src = File(tmp, part)
            if (src.exists()) src.copyRecursively(File(root, part), overwrite = true)
        }
        tmp.deleteRecursively()
        Library.init(c, reload = true)
    }
}
