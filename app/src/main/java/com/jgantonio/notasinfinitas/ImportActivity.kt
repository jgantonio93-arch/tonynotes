package com.jgantonio.notasinfinitas

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Porta de entrada dos arquivos que chegam de outros apps (Compartilhar / Abrir com) e do
 * "Importar PDF" da tela inicial. Copia o arquivo na hora (enquanto ainda há permissão),
 * sobrevive a girar a tela ou trocar o tema, e pergunta nome, pasta e como abrir o PDF
 * (páginas do PDF ou tela infinita). Samsung Notes e .rosanota seguem para a tela inicial.
 */
class ImportActivity : Activity() {

    /** Arquivo já copiado para o app. */
    private var file: File? = null
    private var fileName = ""
    private var slides = false
    private var extraCount = 0
    private var folderId: String? = null
    private var sheets = true
    private lateinit var root: FrameLayout
    private val prefs by lazy { PenPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        Ui.styleWindow(this, Ui.BG)
        Library.init(this)
        root = FrameLayout(this).apply {
            fitsSystemWindows = true
            background = Ui.run { paperBackground() }
        }
        setContentView(root)
        val saved = savedInstanceState?.getString(STATE_FILE)?.let(::File)?.takeIf { it.isFile }
        if (saved != null) {
            // Recriada (girou, trocou o tema...): o arquivo já está copiado, só mostra de novo.
            file = saved
            fileName = savedInstanceState.getString(STATE_NAME) ?: saved.name
            slides = savedInstanceState.getBoolean(STATE_SLIDES)
            folderId = savedInstanceState.getString(STATE_FOLDER)
            sheets = savedInstanceState.getBoolean(STATE_SHEETS, true)
            extraCount = savedInstanceState.getInt(STATE_EXTRA)
            showForm()
        } else receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        file = null
        receive(intent)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        file?.let {
            out.putString(STATE_FILE, it.path)
            out.putString(STATE_NAME, fileName)
            out.putBoolean(STATE_SLIDES, slides)
            out.putString(STATE_FOLDER, folderId)
            out.putBoolean(STATE_SHEETS, sheets)
            out.putInt(STATE_EXTRA, extraCount)
        }
    }

    @Suppress("DEPRECATION")
    private fun urisOf(i: Intent?): List<Uri> = when (i?.action) {
        Intent.ACTION_SEND -> listOfNotNull(i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        Intent.ACTION_SEND_MULTIPLE -> i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
        Intent.ACTION_VIEW -> listOfNotNull(i.data)
        else -> listOfNotNull(i?.data)
    }

    private fun nameOf(u: Uri) = PdfImporter.displayName(this, u) ?: u.lastPathSegment?.substringAfterLast('/') ?: ""

    private fun isPdf(u: Uri) = contentResolver.getType(u) == "application/pdf" || nameOf(u).lowercase().endsWith(".pdf") ||
        (contentResolver.getType(u) == null && startsWith(u, "%PDF"))

    private fun startsWith(u: Uri, magic: String): Boolean = try {
        contentResolver.openInputStream(u)?.use { i -> String(ByteArray(magic.length).also { i.read(it) }) == magic } ?: false
    } catch (e: Exception) {
        false
    }

    private fun receive(i: Intent?) {
        val uris = urisOf(i)
        if (uris.isEmpty()) {
            finish()
            return
        }
        val docs = uris.filter { u -> isPdf(u) || PptxImporter.isPowerPoint(nameOf(u), contentResolver.getType(u)) }
        val others = uris - docs.toSet()
        if (others.isNotEmpty()) {
            // Samsung Notes, .rosanota e o resto: a tela inicial já sabe tratar.
            val fwd = Intent(this, FoldersActivity::class.java).apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(others))
                clipData = android.content.ClipData.newRawUri("arquivos", others[0]).apply {
                    for (u in others.drop(1)) addItem(android.content.ClipData.Item(u))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fwd)
            if (docs.isEmpty()) {
                finish()
                return
            }
        }
        val u = docs[0]
        extraCount = docs.size - 1
        val full = nameOf(u).ifEmpty { "Documento.pdf" }
        slides = !isPdf(u)
        fileName = full
        sheets = when (prefs.pdfOpenMode) { "infinite" -> false; else -> true }
        folderId = (i?.getStringExtra(EXTRA_FOLDER) ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER, null))
            ?.takeIf { Library.folder(it) != null }
        showMessage(if (slides) "Recebendo a apresentação…" else "Recebendo o PDF…")
        val ext = if (!slides) "pdf" else if (full.lowercase().endsWith(".ppt")) "ppt" else "pptx"
        val dest = File(File(cacheDir, "recebidos").apply { mkdirs() }, "rec_${System.currentTimeMillis()}.$ext")
        Library.io.execute {
            val ok = try {
                (contentResolver.openInputStream(u) ?: throw java.io.IOException("sem acesso")).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                dest.length() > 0
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!ok) {
                    showMessage(if (slides) "Não foi possível abrir a apresentação: o app que compartilhou não liberou o arquivo."
                        else "Não foi possível abrir o PDF: o app que compartilhou não liberou o arquivo.", close = true)
                    return@runOnUiThread
                }
                file = dest
                // Modo já escolhido nas Configurações: só pergunta nome e pasta.
                showForm()
            }
        }
    }

    private fun card(build: LinearLayout.() -> Unit) {
        root.removeAllViews()
        val box = Ui.run {
            vertical().apply {
                background = floating(26f)
                setPadding(dpi(22f), dpi(20f), dpi(22f), dpi(20f))
                build()
            }
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val holder = FrameLayout(this)
        val m = Ui.run { dpi(16f) }
        holder.addView(box, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels - 2 * m, Ui.run { dpi(560f) }),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { setMargins(m, m, m, m) })
        scroll.addView(holder)
        root.addView(scroll)
    }

    private fun showMessage(text: String, close: Boolean = false) = card {
        Ui.run {
            addView(title("RosaNotes", 22f))
            addView(label(text, 15f, Ui.MUTED).apply { setPadding(0, dpi(10f), 0, 0) })
            if (close) addView(buttonRow(primaryButton("OK") { finish() }))
        }
    }

    internal val formShownForTests get() = formShown
    private var formShown = false

    private fun showForm() {
        val f = file ?: return
        formShown = true
        lateinit var nameField: EditText
        lateinit var folderLabel: TextView
        fun folderText() = folderId?.let { Library.pathOf(it) }?.takeIf { it.isNotBlank() }
            ?: Library.folders.firstOrNull()?.let { Library.pathOf(it.id) } ?: "Geral"
        card {
            Ui.run {
                addView(title("Abrir no RosaNotes", 22f))
                addView(label(fileName, 13f).apply {
                    setPadding(0, dpi(4f), 0, dpi(14f))
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                })
                addView(sectionLabel("Nome da nota"))
                nameField = textField(fileName.substringBeforeLast('.').ifEmpty { fileName }, "Nome")
                addView(nameField)
                addView(sectionLabel("Pasta").apply { setPadding(0, dpi(14f), 0, 0) })
                val row = horizontal().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dpi(50f)
                    setPadding(dpi(12f), 0, dpi(8f), 0)
                    background = ripple(rounded(Ui.FIELD, 14f), rounded(android.graphics.Color.WHITE, 14f))
                    contentDescription = "Escolher pasta"
                }
                row.addView(ImageView(this@ImportActivity).apply { setImageDrawable(icon(Icon.FOLDER, Ui.ACCENT, 22f)) },
                    LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(12f) })
                folderLabel = label(folderText(), 15f, Ui.INK)
                row.addView(folderLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(ImageView(this@ImportActivity).apply { setImageDrawable(icon(Icon.CHEVRON_RIGHT, Ui.MUTED, 18f)) },
                    LinearLayout.LayoutParams(dpi(20f), dpi(20f)))
                row.setOnClickListener {
                    Pickers.folder(this@ImportActivity, "Salvar em", allowRoot = false, exclude = null, current = null) { fid ->
                        if (fid != null) {
                            folderId = fid
                            folderLabel.text = folderText()
                        }
                    }
                }
                addView(row)
                if (prefs.pdfOpenMode == "ask") {
                    addView(sectionLabel(if (slides) "Como abrir os slides" else "Como abrir o PDF").apply { setPadding(0, dpi(14f), 0, 0) })
                    val hint = label("", 13f).apply { setPadding(dpi(4f), dpi(8f), 0, 0) }
                    fun hintText() {
                        hint.text = if (sheets) "Cada página vira uma folha do tamanho dela, como num leitor de PDF. Dá para trocar depois em ⋮ › Página."
                        else "As páginas ficam numa tela infinita, com espaço livre em volta para anotar. Dá para trocar depois em ⋮ › Página."
                    }
                    addView(segmented(listOf("Páginas do PDF", "Tela infinita"), if (sheets) 0 else 1) { i ->
                        sheets = i == 0
                        hintText()
                    })
                    hintText()
                    addView(hint)
                }
                if (extraCount > 0) addView(label("Chegaram ${extraCount + 1} arquivos: abrindo o primeiro.", 13f).apply { setPadding(0, dpi(10f), 0, 0) })
                addView(buttonRow(
                    secondaryButton("Cancelar") { f.delete(); finish() },
                    primaryButton("Abrir") { open(nameField.text.toString().trim().ifEmpty { fileName }) },
                ))
            }
        }
    }

    private fun open(title: String) {
        val f = file ?: return
        val folder = folderId?.takeIf { Library.folder(it) != null } ?: Library.folders.firstOrNull()?.id
        if (folder == null) {
            Toast.makeText(this, "Crie uma pasta primeiro.", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, folder).apply()
        val n = Library.createNote(folder, title)
        startActivity(Intent(this, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_NOTE, n.id)
            .putExtra(if (slides) EditorActivity.EXTRA_PPTX else EditorActivity.EXTRA_PDF, Uri.fromFile(f).toString())
            .putExtra(EditorActivity.EXTRA_DOC_NAME, fileName)
            .putExtra(EditorActivity.EXTRA_PDF_MODE, if (sheets) "pages" else "infinite")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        file = null
        finish()
    }

    internal fun openForTests(title: String) = open(title)
    internal fun chooseSheetsForTests(on: Boolean) { sheets = on }

    companion object {
        /** Pasta já escolhida (importar de dentro de uma pasta). */
        const val EXTRA_FOLDER = "folder"
        private const val STATE_FILE = "file"
        private const val STATE_NAME = "name"
        private const val STATE_SLIDES = "slides"
        private const val STATE_FOLDER = "folder"
        private const val STATE_SHEETS = "sheets"
        private const val STATE_EXTRA = "extra"
        private const val PREFS = "importar"
        private const val KEY_FOLDER = "ultima_pasta"
    }
}
