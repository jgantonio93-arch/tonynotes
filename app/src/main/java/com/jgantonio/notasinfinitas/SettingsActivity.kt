package com.jgantonio.notasinfinitas

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Configurações do app (tela inicial › engrenagem): predefinições das notas novas, PDF e
 * PowerPoint, caneta e toque, aparência, backup e versão. Usa as mesmas preferências do
 * ⋮ › Ajustes da nota: mudar num lugar muda no outro.
 */
class SettingsActivity : Activity() {

    private val prefs by lazy { PenPrefs(this) }
    private var builtTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        builtTheme = Ui.themeVersion
        Ui.styleWindow(this, Ui.BG)
        Library.init(this)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeVersion != builtTheme) recreate()
    }

    private fun build() = Ui.run {
        val root = FrameLayout(this@SettingsActivity).apply {
            fitsSystemWindows = true
            background = paperBackground()
        }
        val box = vertical().apply { setPadding(dpi(18f), dpi(8f), dpi(18f), dpi(40f)) }
        val top = horizontal().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(6f), 0, dpi(10f)) }
        top.addView(iconButton(Icon.BACK, Ui.INK, 44f, "Voltar") { finish() })
        top.addView(title("Configurações", 26f).apply { setPadding(dpi(6f), 0, 0, 0) })
        box.addView(top)

        fun section(name: String, body: LinearLayout.() -> Unit) {
            box.addView(sectionLabel(name).apply { setPadding(dpi(4f), dpi(18f), 0, dpi(6f)) })
            box.addView(vertical().apply {
                background = floating(20f)
                setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(10f))
                body()
            })
        }

        fun LinearLayout.caption(text: String) =
            addView(label(text, 13.5f, Ui.MUTED).apply { setPadding(0, dpi(10f), 0, dpi(6f)) })

        fun LinearLayout.choice(icon: Icon, text: String, value: () -> String, onClick: () -> Unit): TextView {
            val v = label(value(), 14f, Ui.ACCENT, bold = true)
            addView(horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dpi(52f)
                background = ripple(null, rounded(Color.WHITE, 12f))
                contentDescription = text
                addView(ImageView(this@SettingsActivity).apply { setImageDrawable(icon(icon, Ui.MUTED, 22f)) },
                    LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(14f) })
                addView(label(text, 15f, Ui.INK), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(v)
                setOnClickListener { onClick() }
            })
            return v
        }

        // ---- Notas novas ----
        section("Notas novas") {
            lateinit var styleValue: TextView
            styleValue = choice(Icon.PAGE, "Tipo de página", { prefs.defaultStyle.label }) {
                actionSheet("Tipo de página", PageStyle.entries.map { st ->
                    Ui.SheetItem(if (st == prefs.defaultStyle) Icon.CHECK else Icon.PAGE, st.label) {
                        prefs.defaultStyle = st
                        styleValue.text = st.label
                    }
                })
            }
            lateinit var paperValue: TextView
            fun paperName() = Panels.PAPER_COLORS.firstOrNull { it.second == prefs.defaultPaper }?.first ?: "Personalizada"
            paperValue = choice(Icon.PALETTE, "Cor do papel", ::paperName) {
                actionSheet("Cor do papel", Panels.PAPER_COLORS.map { (name, col) ->
                    Ui.SheetItem(if (col == prefs.defaultPaper) Icon.CHECK else Icon.PALETTE, name) {
                        prefs.defaultPaper = col
                        paperValue.text = name
                    }
                })
            }
            val l0 = PageLayout.decodePref(prefs.defaultPageLayout) ?: PageLayout()
            var paged = l0.paged
            var horizontal = l0.horizontal
            var landscape = l0.landscape
            var endless = l0.endless
            fun save() { prefs.defaultPageLayout = PageLayout(paged, horizontal, landscape, endless).encodePref() }
            caption("Modo")
            addView(segmented(listOf("Tela infinita", "Folhas em coluna", "Folhas lado a lado"),
                if (!paged) 0 else if (horizontal) 2 else 1) { i ->
                paged = i != 0
                horizontal = i == 2
                save()
            })
            caption("Folha")
            addView(segmented(listOf("Em pé", "Deitada"), if (landscape) 1 else 0) { i -> landscape = i == 1; save() })
            addView(switchRow(Icon.INFINITE, "Folhas infinitas (cria a próxima sozinha)", endless) { endless = it; save() })
        }

        // ---- PDF e PowerPoint ----
        section("PDF e PowerPoint") {
            caption("Ao abrir ou encaminhar um PDF")
            val modes = listOf("ask", "pages", "infinite")
            addView(segmented(listOf("Perguntar", "Páginas do PDF", "Tela infinita"),
                modes.indexOf(prefs.pdfOpenMode).coerceAtLeast(0)) { i -> prefs.pdfOpenMode = modes[i] })
            caption("Ao inserir numa nota de tela infinita")
            addView(segmented(PdfImporter.Layout.entries.map { it.label }, prefs.pdfArrange) { i -> prefs.pdfArrange = i })
            addView(switchRow(Icon.PAGES_V, "Páginas agrupadas (andam juntas)", prefs.pdfGrouped) { prefs.pdfGrouped = it })
            addView(switchRow(Icon.PAGES_V, "Rolagem presa no documento", prefs.docScrollLock) { prefs.docScrollLock = it })
        }

        // ---- Caneta e toque ----
        section("Caneta e toque") {
            addView(switchRow(Icon.TOUCH, "Escrever com o dedo", prefs.fingerDraws) { prefs.fingerDraws = it })
            addView(switchRow(Icon.TOUCH, "Rejeição de palma (ignorar a mão)", prefs.palmReject) { prefs.palmReject = it })
            addView(switchRow(Icon.TOUCH, "Escrevo com a mão esquerda", prefs.leftHanded) { prefs.leftHanded = it })
            lateinit var guardValue: TextView
            fun guardName() = when (prefs.palmGuardMode) {
                "bottom" -> "Faixa embaixo"
                "side" -> "Lateral"
                "free" -> "Ilha livre"
                else -> "Desligado"
            }
            guardValue = choice(Icon.PAGE, "Protetor de mão", ::guardName) {
                actionSheet("Protetor de mão", listOf("off" to "Desligado", "bottom" to "Faixa embaixo (horizontal)",
                    "side" to "Faixa na lateral (vertical)", "free" to "Ilha livre (arraste e mude o tamanho)").map { (k, n) ->
                    Ui.SheetItem(if (prefs.palmGuardMode == k) Icon.CHECK else Icon.PAGE, n) {
                        prefs.palmGuardMode = k
                        guardValue.text = guardName()
                    }
                })
            }
            addView(switchRow(Icon.ERASER, "Botão da caneta apaga", prefs.spenButtonErases) { prefs.spenButtonErases = it })
            addView(switchRow(Icon.SHAPES, "Formas automáticas", prefs.autoShapes) { prefs.autoShapes = it })
            addView(switchRow(Icon.MARKER, "Marca-texto acompanha a escrita", prefs.highlighterFitsWriting) { prefs.highlighterFitsWriting = it })
            addView(switchRow(Icon.EDIT, "Tinta de latência mínima (experimental)", prefs.lowLatency) { prefs.lowLatency = it })
            addView(switchRow(Icon.TOUCH, "Mostrar toques (diagnóstico da palma)", prefs.showTouches) { prefs.showTouches = it })
        }

        // ---- Aparência ----
        section("Aparência") {
            caption("Tema")
            val themes = listOf("system", "light", "dark")
            addView(segmented(listOf("Igual ao celular", "Claro", "Noturno"),
                themes.indexOf(Ui.themeChoice(this@SettingsActivity)).coerceAtLeast(0)) { i ->
                Ui.setThemeChoice(this@SettingsActivity, themes[i])
                if (Ui.themeVersion != builtTheme) recreate()
            })
            caption("Barra de ferramentas")
            val docks = listOf("top", "left", "right", "free")
            addView(segmented(listOf("Topo", "Esquerda", "Direita", "Solta"),
                docks.indexOf(prefs.dockMode).coerceAtLeast(0)) { i -> prefs.dockMode = docks[i] })
        }

        // ---- Backup ----
        section("Backup") {
            choice(Icon.EXPORT, "Google Drive e arquivo .zip", { "Abrir" }) {
                startActivity(Intent(this@SettingsActivity, FoldersActivity::class.java)
                    .putExtra(FoldersActivity.EXTRA_OPEN_BACKUP, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }

        // ---- Sobre ----
        section("Sobre") {
            addView(label("RosaNotes ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD_NUMBER})", 15f, Ui.INK).apply {
                setPadding(0, dpi(10f), 0, dpi(4f))
            })
            choice(Icon.DOWNLOAD, "Procurar atualização", { "" }) {
                Toast.makeText(this@SettingsActivity, "Procurando… se houver versão nova, aparece um aviso.", Toast.LENGTH_SHORT).show()
                Updates.checkNow(this@SettingsActivity)
            }
        }

        root.addView(ScrollView(this@SettingsActivity).apply {
            isVerticalScrollBarEnabled = false
            addView(box)
        })
        setContentView(root)
    }
}
