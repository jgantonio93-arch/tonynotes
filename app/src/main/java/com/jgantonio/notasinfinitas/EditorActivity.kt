package com.jgantonio.notasinfinitas

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jgantonio.notasinfinitas.Ui.SheetItem
import com.jgantonio.notasinfinitas.Ui.actionSheet
import com.jgantonio.notasinfinitas.Ui.buttonRow
import com.jgantonio.notasinfinitas.Ui.primaryButton
import com.jgantonio.notasinfinitas.Ui.secondaryButton
import com.jgantonio.notasinfinitas.Ui.segmented
import com.jgantonio.notasinfinitas.Ui.sheet
import com.jgantonio.notasinfinitas.Ui.circle
import com.jgantonio.notasinfinitas.Ui.dp
import com.jgantonio.notasinfinitas.Ui.dpi
import com.jgantonio.notasinfinitas.Ui.floating
import com.jgantonio.notasinfinitas.Ui.horizontal
import com.jgantonio.notasinfinitas.Ui.icon
import com.jgantonio.notasinfinitas.Ui.iconButton
import com.jgantonio.notasinfinitas.Ui.label
import com.jgantonio.notasinfinitas.Ui.pill
import com.jgantonio.notasinfinitas.Ui.pillButton
import com.jgantonio.notasinfinitas.Ui.promptText
import com.jgantonio.notasinfinitas.Ui.ripple
import com.jgantonio.notasinfinitas.Ui.switchRow
import com.jgantonio.notasinfinitas.Ui.vertical
import com.jgantonio.notasinfinitas.Ui.rounded
import com.jgantonio.notasinfinitas.Ui.sectionLabel
import com.jgantonio.notasinfinitas.Ui.title
import com.jgantonio.notasinfinitas.Ui.wrapRow
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class EditorActivity : Activity(), InfiniteCanvasView.Listener {

    private lateinit var note: NoteInfo
    private lateinit var prefs: PenPrefs
    private lateinit var canvasView: InfiniteCanvasView
    internal val canvasForTests get() = canvasView
    internal val textEditorForTests get() = textEditor
    internal val dockForTests get() = dock
    internal val dockGripForTests get() = dockGrip
    internal val quickColorsForTests get() = quickColorsBox
    internal val prefsForTests get() = prefs
    internal fun relayoutDockForTests() = layoutDock()
    internal fun closeTrayForTests() = closeTray()

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var undoButton: View
    private lateinit var topBar: View
    internal val topBarForTests get() = topBar
    internal val fullscreenBoxForTests get() = fullscreenBox
    private lateinit var redoButton: View
    private lateinit var dock: LinearLayout
    private lateinit var trayLayer: FrameLayout
    private lateinit var selectionBar: View
    private lateinit var selectionLabel: TextView
    private lateinit var zoomLabel: TextView
    private lateinit var angleLabel: TextView
    private lateinit var viewPills: LinearLayout
    private lateinit var pastePill: TextView
    private lateinit var ephemeralPill: TextView
    private lateinit var audioPill: TextView
    private var audioNotes: AudioNotes? = null
    private val audio: AudioNotes get() = audioNotes ?: AudioNotes(Library.assetsDir(note.id)).also { audioNotes = it }
    private val audioTicker = object : Runnable {
        override fun run() {
            updateAudioPill()
            if (audioNotes?.isRecording == true || audioNotes?.isPlaying == true) main.postDelayed(this, 500)
        }
    }

    private lateinit var textEditor: InlineTextEditor
    private lateinit var stage: FrameLayout

    private val toolButtons = LinkedHashMap<InfiniteCanvasView.Tool, ImageView>()
    private lateinit var penToolButton: ImageView
    private lateinit var highlighterButton: ImageView
    internal val highlighterButtonForTests get() = highlighterButton
    internal val penButtonForTests get() = penToolButton
    private lateinit var colorDot: ColorDot
    private lateinit var favoritesBox: LinearLayout
    private lateinit var colorDivider: View
    private var openTray: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        Ui.styleWindow(this, Ui.SURFACE)
        Library.init(this)
        val n = Library.note(intent.getStringExtra(EXTRA_NOTE))
        if (n == null) {
            finish()
            return
        }
        note = n
        prefs = PenPrefs(this)
        requestMaxRefreshRate()

        canvasView = InfiniteCanvasView(this)
        canvasView.listener = this
        canvasView.assetDir = Library.assetsDir(note.id)
        canvasView.pen = prefs.pen(prefs.currentType)
        applyPrefs()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.SURFACE)
        }
        // Barras do sistema e teclado: a área da nota encolhe quando o teclado abre,
        // para a barra de formatação de texto ficar logo acima dele.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        topBar = buildTopBar()
        root.addView(topBar)

        stage = FrameLayout(this)
        stage.addView(canvasView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Camadas da tinta em andamento (baixa latência): ficam logo acima da nota.
        val live = LiveInkView(this, canvasView)
        stage.addView(live, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        canvasView.liveInk = live
        attachWetInk()

        zoomLabel = TextView(this).apply {
            textSize = 12f
            typeface = Ui.MEDIUM
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            minHeight = dpi(32f)
            setPadding(dpi(12f), 0, dpi(12f), 0)
            background = ripple(floating(100f), pill(Color.WHITE, 0))
            fontFeatureSettings = "tnum"
            Ui.run { softShadow(2f) }
            contentDescription = "Zoom"
            setOnClickListener { showZoomMenu() }
        }
        angleLabel = TextView(this).apply {
            textSize = 12f
            typeface = Ui.MEDIUM
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            minHeight = dpi(32f)
            setPadding(dpi(10f), 0, dpi(12f), 0)
            background = ripple(floating(100f), pill(Color.WHITE, 0))
            fontFeatureSettings = "tnum"
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(Icon.ROTATE, Ui.MUTED, 15f), null, null, null)
            compoundDrawablePadding = dpi(5f)
            Ui.run { softShadow(2f) }
            contentDescription = "Girar a tela"
            setOnClickListener { showRotateMenu() }
        }
        // Zoom e ângulo lado a lado, no canto de baixo.
        pastePill = TextView(this).apply {
            text = "Colar"
            textSize = 12f
            typeface = Ui.MEDIUM
            setTextColor(Ui.ACCENT)
            gravity = Gravity.CENTER
            minHeight = dpi(32f)
            setPadding(dpi(10f), 0, dpi(12f), 0)
            background = ripple(floating(100f), pill(Color.WHITE, 0))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(Icon.COPY, Ui.ACCENT, 15f), null, null, null)
            compoundDrawablePadding = dpi(5f)
            Ui.run { softShadow(2f) }
            setOnClickListener { pasteClipboard() }
        }
        viewPills = horizontal().apply {
            addView(zoomLabel)
            addView(angleLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpi(8f)
            })
            addView(pastePill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpi(8f)
            })
        }
        updatePastePill()
        stage.addView(viewPills, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dpi(14f), 0, 0, dpi(14f))
        })


        ephemeralPill = TextView(this).apply {
            textSize = 14f
            typeface = Ui.MEDIUM
            setTextColor(Ui.ON_ACCENT)
            gravity = Gravity.CENTER
            minHeight = dpi(40f)
            setPadding(dpi(16f), 0, dpi(16f), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon(Icon.CLOSE, Ui.ON_ACCENT, 18f), null)
            compoundDrawablePadding = dpi(8f)
            background = ripple(pill(Ui.ACCENT, 0), pill(Color.WHITE, 0))
            Ui.run { softShadow(6f) }
            visibility = View.GONE
            setOnClickListener { setEphemeral(null) }
        }
        stage.addView(ephemeralPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(18f)
        })

        audioPill = TextView(this).apply {
            textSize = 14f
            typeface = Ui.MEDIUM
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minHeight = dpi(40f)
            setPadding(dpi(16f), 0, dpi(16f), 0)
            fontFeatureSettings = "tnum"
            background = ripple(pill(Color.parseColor("#D7263D"), 0), pill(Color.WHITE, 0))
            Ui.run { softShadow(6f) }
            visibility = View.GONE
            setOnClickListener { audio.stop(); audio.stopPlayback(); updateAudioPill() }
        }
        stage.addView(audioPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpi(14f), dpi(14f))
        })
        canvasView.onStrokeAdded = { s -> audioNotes?.markStroke(s) }
        canvasView.onColorPicked = { col ->
            prefs.addCustomColor(col)
            setPen(canvasView.pen.copy(color = col))
            toast("Cor escolhida.")
        }

        selectionBar = buildSelectionBar()
        selectionBar.visibility = View.GONE
        stage.addView(selectionBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dpi(12f), 0, dpi(12f), dpi(18f))
        })

        trayLayer = FrameLayout(this).apply { visibility = View.GONE }
        stage.addView(trayLayer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        dock = buildDock()
        stage.addView(dock)

        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        textEditor = InlineTextEditor(this, stage, canvasView,
            defaultColor = { canvasView.pen.color.takeIf { canvasView.pen.type != BrushType.HIGHLIGHTER } ?: Ui.INK },
            onFinished = { refresh() })
        stage.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop && textEditor.isActive) stage.post { textEditor.keepVisible() }
        }

        val saved = NoteStorage.load(Library.noteFile(note.id))
        if (saved != null) canvasView.load(saved)
        else PageLayout.decodePref(prefs.defaultPageLayout)?.takeIf { it.paged }?.let { l ->
            // Nota nova: começa no último modo de página escolhido.
            canvasView.setPageLayout(true, l.horizontal, l.landscape, l.endless, 1)
        }
        layoutDock()
        if (prefs.fullscreen) setFullscreen(true)
        // A barra solta depende do tamanho da área da nota.
        stage.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if ((r - l != or - ol || b - t != ob - ot) && prefs.dockMode == "free") stage.post { layoutDock() }
        }
        intent.getStringExtra(EXTRA_PDF)?.let { u ->
            intent.removeExtra(EXTRA_PDF)
            // Espera a tela ter tamanho para enquadrar as páginas.
            canvasView.post { importPdf(Uri.parse(u), PdfImporter.Layout.VERTICAL) }
        }
    }

    /**
     * Camada de latência mínima (opcional). É recriada ao voltar para a nota: a superfície
     * antiga pode ficar inválida depois de sair do app, e a tinta parava de aparecer.
     */
    private fun attachWetInk() {
        canvasView.wetInk?.let { stage.removeView(it) }
        canvasView.wetInk = null
        if (!prefs.lowLatency) return
        val wet = WetInkLayer(this)
        stage.addView(wet, 2, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        canvasView.wetInk = wet
    }

    private var wetStale = false

    override fun onResume() {
        super.onResume()
        updatePastePill()
        if (wetStale) {
            wetStale = false
            attachWetInk()
        }
    }

    override fun onPause() {
        super.onPause()
        wetStale = true
        if (::textEditor.isInitialized && textEditor.isActive) textEditor.finish()
        // A gravação para quando a nota sai da tela (o áudio já fica salvo).
        audioNotes?.let { if (it.isRecording) { it.stop(); updateAudioPill() } }
        if (::canvasView.isInitialized) save()
        Backup.autoBackupIfDue(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            textEditor.isActive -> textEditor.finish()
            openTray != null -> closeTray()
            canvasView.hasSelection -> canvasView.clearSelection()
            isFullscreen -> setFullscreen(false)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun applyPrefs() {
        canvasView.fingerDraws = prefs.fingerDraws
        canvasView.autoShapes = prefs.autoShapes
        canvasView.spenButtonErases = prefs.spenButtonErases
        canvasView.eraserArea = prefs.eraserArea
        canvasView.eraserSize = prefs.eraserSize
        canvasView.eraserHighlighterOnly = prefs.eraserHighlighterOnly
        canvasView.highlighterFitsWriting = prefs.highlighterFitsWriting
        canvasView.lowLatency = prefs.lowLatency
        canvasView.selectRect = prefs.selectRect
        canvasView.selectPartial = prefs.selectPartial
        canvasView.tapeColor = prefs.tapeColor
        canvasView.tapeWidthDp = prefs.tapeWidth
    }

    /** Pede a maior taxa de atualização da tela (ex.: 120 Hz): a tinta acompanha a caneta mais de perto. */
    private fun requestMaxRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val d = display ?: return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        } catch (_: Throwable) {
        }
    }

    // ---- Barra superior ------------------------------------------------------------

    private fun buildTopBar(): View {
        val bar = horizontal().apply {
            setPadding(dpi(4f), dpi(6f), dpi(6f), dpi(6f))
            setBackgroundColor(Ui.SURFACE)
            elevation = dp(1f)
        }
        bar.addView(iconButton(Icon.BACK) { finish() })
        val titles = vertical().apply {
            setPadding(dpi(4f), 0, dpi(8f), 0)
            background = ripple(null, Ui.run { rounded(Color.WHITE, 12f) })
            setOnClickListener { rename() }
        }
        titleView = label("", 19f, Ui.INK, bold = true).apply {
            typeface = Ui.DISPLAY
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        subtitleView = label("", 12f).apply { isSingleLine = true }
        titles.addView(titleView)
        titles.addView(subtitleView)
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        undoButton = iconButton(Icon.UNDO) { finishTyping(); canvasView.undo() }
        redoButton = iconButton(Icon.REDO) { finishTyping(); canvasView.redo() }
        bar.addView(undoButton)
        bar.addView(redoButton)
        bar.addView(iconButton(Icon.FULLSCREEN, desc = "Tela cheia") { finishTyping(); setFullscreen(true) })
        bar.addView(iconButton(Icon.MORE) { finishTyping(); showMoreMenu() })
        return bar
    }

    // ---- Tela cheia --------------------------------------------------------------------
    //
    // Some a barra de cima e as barras do sistema: fica só a barra de ferramentas e, num
    // cantinho, desfazer, refazer e sair. O voltar do celular também sai da tela cheia.

    var isFullscreen = false
        private set
    private var fullscreenBox: LinearLayout? = null

    internal fun setFullscreen(on: Boolean) {
        isFullscreen = on
        prefs.fullscreen = on
        topBar.visibility = if (on) View.GONE else View.VISIBLE
        fullscreenBox?.let { stage.removeView(it) }
        fullscreenBox = null
        if (on) {
            val box = horizontal().apply {
                background = floating(100f)
                setPadding(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
                alpha = 0.9f
            }
            box.addView(iconButton(Icon.UNDO, Ui.INK, 38f, "Desfazer") { finishTyping(); canvasView.undo() })
            box.addView(iconButton(Icon.REDO, Ui.INK, 38f, "Refazer") { finishTyping(); canvasView.redo() })
            box.addView(iconButton(Icon.FULLSCREEN_EXIT, Ui.INK, 38f, "Sair da tela cheia") { setFullscreen(false) })
            // No canto oposto ao da barra de ferramentas em pé.
            val side = if (prefs.dockMode == "right") Gravity.START else Gravity.END
            stage.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or side).apply {
                setMargins(dpi(10f), dpi(10f), dpi(10f), 0)
            })
            fullscreenBox = box
        }
        systemBars(show = !on)
        stage.post { layoutDock() }
    }

    private fun systemBars(show: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val c = window.insetsController ?: return
            if (show) c.show(android.view.WindowInsets.Type.systemBars())
            else {
                c.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(android.view.WindowInsets.Type.systemBars())
            }
        } else if (show) {
            Ui.styleWindow(this, Ui.SURFACE)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Um painel ou diálogo pode trazer as barras de volta: esconde de novo.
        if (hasFocus && isFullscreen) systemBars(show = false)
    }

    private fun finishTyping() {
        if (::textEditor.isInitialized && textEditor.isActive) textEditor.finish()
    }

    // ---- Dock de ferramentas ------------------------------------------------------------
    //
    // A barra flutua sobre a nota e pode ser arrastada pela alça (⋮⋮) para qualquer lugar.
    // Solta perto da borda esquerda ou direita, ela fica em pé, como no Samsung Notes,
    // e mostra 3 cores rápidas (toque longo numa cor para trocá-la).

    private lateinit var dockGrip: ImageView
    private lateinit var quickColorsBox: LinearLayout
    private val toolsInDock = ArrayList<View>()
    private val dockVertical get() = prefs.dockMode == "left" || prefs.dockMode == "right"

    private fun toolButton(d: Drawable, desc: String = "", onClick: () -> Unit) = ImageView(this).apply {
        setImageDrawable(d)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = desc.ifEmpty { "Caneta" }
        Ui.run { pressable(0.9f) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dpi(46f), dpi(46f)).apply { setMargins(dpi(1f), 0, dpi(1f), 0) }
    }

    private fun dockDivider() = View(this).apply {
        setBackgroundColor(Ui.LINE)
        layoutParams = LinearLayout.LayoutParams(dpi(1f), dpi(24f)).apply { setMargins(dpi(6f), 0, dpi(6f), 0) }
    }

    /** Divisória que acompanha a orientação da barra. */
    private fun orientDivider(v: View) {
        v.layoutParams = if (dockVertical) LinearLayout.LayoutParams(dpi(24f), dpi(1f)).apply { setMargins(0, dpi(6f), 0, dpi(6f)) }
        else LinearLayout.LayoutParams(dpi(1f), dpi(24f)).apply { setMargins(dpi(6f), 0, dpi(6f), 0) }
    }

    private fun buildDock(): LinearLayout {
        val bar = horizontal().apply {
            setPadding(dpi(4f), dpi(5f), dpi(6f), dpi(5f))
            background = floating(100f)
            Ui.run { softShadow(10f) }
            isClickable = true
        }
        dockGrip = ImageView(this).apply {
            setImageDrawable(IconDrawable(Icon.GRIP, Ui.MUTED, dpi(20f), dp(1.6f)))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "Arrastar barra (toque para mais opções)"
            setOnTouchListener(DockDrag())
        }
        bar.addView(dockGrip)
        penToolButton = toolButton(PenGlyphDrawable(canvasView.pen.type, canvasView.pen.color, dpi(30f), dp(1f)), "Caneta") {
            if (penActive(highlighter = false)) toggleTray("pen")
            else if (canvasView.pen.type == BrushType.HIGHLIGHTER) setPen(prefs.pen(prefs.lastInkType))
            else setTool(InfiniteCanvasView.Tool.PEN)
        }
        toolButtons[InfiniteCanvasView.Tool.PEN] = penToolButton
        // Marca-texto com botão próprio, separado das canetas.
        val hl = prefs.pen(BrushType.HIGHLIGHTER)
        highlighterButton = toolButton(PenGlyphDrawable(BrushType.HIGHLIGHTER, hl.color, dpi(30f), dp(1f)), "Marca-texto") {
            if (penActive(highlighter = true)) toggleTray("pen") else setPen(prefs.pen(BrushType.HIGHLIGHTER))
        }
        toolButtons[InfiniteCanvasView.Tool.ERASER] = toolButton(icon(Icon.ERASER), "Borracha") {
            if (canvasView.tool == InfiniteCanvasView.Tool.ERASER) toggleTray("eraser") else setTool(InfiniteCanvasView.Tool.ERASER)
        }
        toolButtons[InfiniteCanvasView.Tool.SELECT] = toolButton(icon(if (prefs.selectRect) Icon.RECT_SELECT else Icon.LASSO), "Seleção") {
            if (canvasView.tool == InfiniteCanvasView.Tool.SELECT) toggleTray("select") else setTool(InfiniteCanvasView.Tool.SELECT)
        }
        toolButtons[InfiniteCanvasView.Tool.TEXT] = toolButton(icon(Icon.TEXT), "Texto") { setTool(InfiniteCanvasView.Tool.TEXT) }
        toolButtons[InfiniteCanvasView.Tool.TAPE] = toolButton(icon(Icon.TAPE), "Fita para esconder") {
            if (canvasView.tool == InfiniteCanvasView.Tool.TAPE) toggleTray("tape") else setTool(InfiniteCanvasView.Tool.TAPE)
        }
        toolsInDock.clear()
        toolsInDock.addAll(toolButtons.values)
        toolsInDock.add(1, highlighterButton)
        toolsInDock.add(toolButton(icon(Icon.IMAGE), "Inserir imagem ou PDF") { finishTyping(); closeTray(); showInsertMenu() })
        toolsInDock.forEach { bar.addView(it) }
        colorDivider = dockDivider()
        bar.addView(colorDivider)
        colorDot = ColorDot(this, canvasView.pen.color, false) {
            if (canvasView.tool != InfiniteCanvasView.Tool.PEN) setTool(InfiniteCanvasView.Tool.PEN)
            toggleTray("pen")
        }.apply { contentDescription = "Cor e caneta" }
        bar.addView(colorDot, LinearLayout.LayoutParams(dpi(30f), dpi(30f)).apply { setMargins(dpi(6f), 0, dpi(6f), 0) })
        quickColorsBox = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        bar.addView(quickColorsBox)
        favoritesBox = horizontal()
        bar.addView(favoritesBox)
        return bar
    }

    /** Aplica a orientação e o lugar da barra conforme [PenPrefs.dockMode]. */
    private fun layoutDock(animateFrom: Pair<Float, Float>? = null) {
        val vertical = dockVertical
        dock.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.setPadding(if (vertical) dpi(5f) else dpi(4f), if (vertical) dpi(4f) else dpi(5f), dpi(5f), if (vertical) dpi(8f) else dpi(5f))
        dock.background = floating(if (vertical) 30f else 100f)
        dockGrip.layoutParams = if (vertical) LinearLayout.LayoutParams(dpi(46f), dpi(24f)) else LinearLayout.LayoutParams(dpi(24f), dpi(46f))
        // Deitada, a barra leva as 3 cores: em tela estreita (celular) os botões encolhem um pouco.
        val narrow = !vertical && resources.displayMetrics.widthPixels / density < 480f
        val tool = if (narrow) 36f else 46f
        val toolLp = { LinearLayout.LayoutParams(dpi(tool), dpi(tool)).apply {
            if (vertical) setMargins(0, dpi(1f), 0, dpi(1f)) else setMargins(dpi(1f), 0, dpi(1f), 0)
        } }
        toolsInDock.forEach { it.layoutParams = toolLp() }
        orientDivider(colorDivider)
        favoritesBox.orientation = dock.orientation
        quickColorsBox.orientation = dock.orientation
        quickColorsBox.gravity = Gravity.CENTER

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val edge = dpi(8f)
        when (prefs.dockMode) {
            "left" -> { lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL; lp.marginStart = edge }
            "right" -> { lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL; lp.marginEnd = edge }
            "free" -> {
                lp.gravity = Gravity.TOP or Gravity.START
                dock.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val w = dock.measuredWidth
                val h = dock.measuredHeight
                val sw = stage.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val sh = stage.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                lp.leftMargin = (prefs.dockX * sw - w / 2f).toInt().coerceIn(edge, max(edge, sw - w - edge))
                lp.topMargin = (prefs.dockY * sh - h / 2f).toInt().coerceIn(edge, max(edge, sh - h - edge))
            }
            else -> { lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.topMargin = dpi(10f) }
        }
        dock.layoutParams = lp
        dock.translationX = 0f
        dock.translationY = 0f
        if (animateFrom != null) {
            // Desliza do ponto onde foi solta até o lugar final.
            dock.post {
                dock.translationX = animateFrom.first - dock.left
                dock.translationY = animateFrom.second - dock.top
                if (Ui.animationsOn()) dock.animate().translationX(0f).translationY(0f).setDuration(260).setInterpolator(Ui.EASE).start()
                else { dock.translationX = 0f; dock.translationY = 0f }
            }
        }
        refresh()
    }

    /** Arrastar a barra pela alça; um toque simples abre as opções de posição. */
    private inner class DockDrag : View.OnTouchListener {
        private var rawX0 = 0f
        private var rawY0 = 0f
        private var dragging = false
        private val slop = android.view.ViewConfiguration.get(this@EditorActivity).scaledTouchSlop

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawX0 = e.rawX; rawY0 = e.rawY
                    dragging = false
                    dock.animate().cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - rawX0
                    val dy = e.rawY - rawY0
                    if (!dragging && kotlin.math.hypot(dx, dy) > slop) {
                        dragging = true
                        closeTray()
                        dock.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                    }
                    if (dragging) {
                        dock.translationX = dx
                        dock.translationY = dy
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) dropDock() else showDockMenu()
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) dropDock()
                    dragging = false
                }
            }
            return true
        }
    }

    private fun dropDock() {
        dock.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        val left = dock.left + dock.translationX
        val top = dock.top + dock.translationY
        val cx = left + dock.width / 2f
        val cy = top + dock.height / 2f
        val sw = stage.width.toFloat()
        val sh = stage.height.toFloat()
        val zone = dp(64f)
        prefs.dockMode = when {
            cx < zone || left < dp(4f) -> "left"
            cx > sw - zone || left + dock.width > sw - dp(4f) -> "right"
            top < dp(28f) && kotlin.math.abs(cx - sw / 2f) < dp(56f) -> "top"
            else -> "free"
        }
        if (prefs.dockMode == "free") {
            prefs.dockX = (cx / sw).coerceIn(0f, 1f)
            prefs.dockY = (cy / sh).coerceIn(0f, 1f)
        }
        layoutDock(animateFrom = left to top)
    }

    /** Toque na alça: a bandeja de posição abre colada na barra (como as das canetas). */
    private fun showDockMenu() = toggleTray("dock")

    /** As 3 cores rápidas da barra em pé. */
    private fun fillQuickColors() {
        quickColorsBox.removeAllViews()
        val vertical = dockVertical
        quickColorsBox.addView(dockDivider().also { orientDivider(it) })
        val pen = canvasView.pen
        prefs.quickColors.forEachIndexed { i, col ->
            val dot = ColorDot(this, col, canvasView.tool == InfiniteCanvasView.Tool.PEN && pen.color == col) {
                setPen(canvasView.pen.copy(color = col))
            }
            dot.contentDescription = "Cor rápida ${i + 1} (toque longo para trocar)"
            dot.setOnLongClickListener {
                ColorPicker.show(this, col) { picked ->
                    prefs.quickColors = prefs.quickColors.toMutableList().also { it[i] = picked }
                    prefs.addCustomColor(picked)
                    setPen(canvasView.pen.copy(color = picked))
                }
                true
            }
            Ui.run { dot.pressable(0.9f) }
            val size = if (vertical) 32f else 28f
            quickColorsBox.addView(dot, LinearLayout.LayoutParams(dpi(size), dpi(size)).apply {
                if (vertical) setMargins(0, dpi(4f), 0, dpi(4f)) else setMargins(dpi(3f), 0, dpi(3f), 0)
            })
        }
    }

    private fun refresh() {
        titleView.text = note.title
        subtitleView.text = Library.pathOf(note.folder)
        val tool = canvasView.tool
        val pen = canvasView.pen
        val ink = if (pen.type == BrushType.HIGHLIGHTER) prefs.pen(prefs.lastInkType) else pen
        val hl = if (pen.type == BrushType.HIGHLIGHTER) pen else prefs.pen(BrushType.HIGHLIGHTER)
        penToolButton.setImageDrawable(PenGlyphDrawable(ink.type, ink.color, dpi(30f), dp(1f)))
        highlighterButton.setImageDrawable(PenGlyphDrawable(hl.type, hl.color, dpi(30f), dp(1f)))
        toolButtons[InfiniteCanvasView.Tool.SELECT]?.setImageDrawable(icon(if (prefs.selectRect) Icon.RECT_SELECT else Icon.LASSO))
        for ((t, b) in toolButtons) {
            val sel = t == tool
            b.background = if (sel) circle(Ui.ACCENT_SOFT, 0, 0f) else ripple(null, circle(Color.WHITE, 0, 0f))
            (b.drawable as? IconDrawable)?.color = if (sel) Ui.ACCENT else Ui.INK
        }
        for ((b, sel) in listOf(penToolButton to penActive(false), highlighterButton to penActive(true))) {
            b.background = if (sel) circle(Ui.ACCENT_SOFT, 0, 0f) else ripple(null, circle(Color.WHITE, 0, 0f))
        }
        val vertical = dockVertical
        // As 3 cores rápidas ficam na barra em qualquer posição (a bolinha única saiu).
        colorDot.visibility = View.GONE
        colorDivider.visibility = colorDot.visibility
        colorDot.color = pen.color
        fillQuickColors()

        favoritesBox.removeAllViews()
        // Em pé, os favoritos só aparecem se houver altura de sobra.
        val roomForFavs = if (vertical) stage.height == 0 || stage.height > dpi(600f)
        else resources.displayMetrics.widthPixels / density >= 600f
        val favs = if (roomForFavs) prefs.favorites.asReversed().take(3) else emptyList()
        if (favs.isNotEmpty()) favoritesBox.addView(dockDivider().also { orientDivider(it) })
        for (f in favs) {
            val sel = tool == InfiniteCanvasView.Tool.PEN && f == pen
            favoritesBox.addView(toolButton(PenGlyphDrawable(f.type, f.color, dpi(28f), dp(1f))) { setPen(f) }.apply {
                background = if (sel) circle(Ui.ACCENT_SOFT, 0, 0f) else ripple(null, circle(Color.WHITE, 0, 0f))
                layoutParams = LinearLayout.LayoutParams(dpi(42f), dpi(42f))
            })
        }
        onStateChanged()
    }

    private fun penActive(highlighter: Boolean) =
        canvasView.tool == InfiniteCanvasView.Tool.PEN && (canvasView.pen.type == BrushType.HIGHLIGHTER) == highlighter

    /** Ponteiro laser / tinta que some (null = escrita normal). */
    internal fun setEphemeral(mode: InfiniteCanvasView.Ephemeral?) {
        canvasView.ephemeral = mode
        if (mode != null) {
            if (textEditor.isActive) textEditor.finish()
            closeTray()
            canvasView.tool = InfiniteCanvasView.Tool.PEN
        }
        ephemeralPill.text = when (mode) {
            InfiniteCanvasView.Ephemeral.LASER -> "Ponteiro laser"
            InfiniteCanvasView.Ephemeral.VANISH -> "Tinta que some"
            null -> ""
        }
        ephemeralPill.visibility = if (mode != null) View.VISIBLE else View.GONE
        refresh()
    }

    private fun setTool(tool: InfiniteCanvasView.Tool) {
        if (canvasView.ephemeral != null) setEphemeral(null)
        if (textEditor.isActive) textEditor.finish()
        closeTray()
        canvasView.tool = tool
        refresh()
    }

    private fun setPen(p: PenSettings) {
        if (canvasView.ephemeral != null) setEphemeral(null)
        prefs.savePen(p)
        prefs.currentType = p.type
        canvasView.pen = p
        canvasView.tool = InfiniteCanvasView.Tool.PEN
        closeTray()
        refresh()
    }

    // ---- Bandejas ----------------------------------------------------------------------

    private fun toggleTray(which: String) {
        if (openTray == which) closeTray() else showTray(which)
    }

    fun openPenTray() = showTray("pen")
    fun openEraserTray() = showTray("eraser")
    fun openSelectTray() = showTray("select")
    fun openDockTray() = showTray("dock")
    internal val openTrayForTests get() = openTray

    @SuppressLint("ClickableViewAccessibility")
    private fun showTray(which: String) {
        trayLayer.removeAllViews()
        // Toque fora da bandeja fecha a bandeja (e não risca a nota).
        trayLayer.addView(View(this).apply {
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) closeTray()
                true
            }
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // A bandeja abre ao lado da barra: embaixo, em cima ou ao lado (barra em pé).
        val sw = stage.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val sh = stage.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val gap = dpi(10f)
        val dockRight = dock.left + dock.width
        val dockBottom = dock.top + dock.height
        val width = when (prefs.dockMode) {
            "left" -> min(sw - dockRight - gap - dpi(12f), dpi(440f))
            "right" -> min(dock.left - gap - dpi(12f), dpi(440f))
            else -> min(sw - dpi(24f), dpi(440f))
        }
        // A bandeja tem uma folga em volta para a sombra (TRAY_SHADOW_DP): compensa aqui
        // para o cartão ficar exatamente no lugar.
        val room = dpi(TRAY_SHADOW_DP)
        val trayWidth = if (which == "dock") min(width, dpi(320f)) else width
        val lp = FrameLayout.LayoutParams(trayWidth + room * 2, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dpi(12f) - room
        lp.topMargin = dpi(12f) - room / 2
        var fromX = 0f
        var fromY = -dp(10f)
        when (prefs.dockMode) {
            "left" -> { lp.gravity = Gravity.TOP or Gravity.START; lp.marginStart = dockRight + gap - room; fromX = -dp(10f); fromY = 0f }
            "right" -> { lp.gravity = Gravity.TOP or Gravity.END; lp.marginEnd = sw - dock.left + gap - room; fromX = dp(10f); fromY = 0f }
            else -> {
                val below = dock.top + dock.height / 2 < sh / 2
                if (below) { lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.topMargin = dockBottom + gap - room / 2 }
                else { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.bottomMargin = sh - dock.top + gap - room; fromY = dp(10f) }
            }
        }

        val tray: View = when (which) {
            "pen" -> PenTray(this, prefs, canvasView.pen, onChange = { p ->
                canvasView.pen = p
                applyPrefs()
                refresh()
            }, onFavoritesChanged = { refresh() }, widthPx = width, onPick = { mode ->
                closeTray()
                canvasView.pickMode = mode
                toast(if (mode == InfiniteCanvasView.PickMode.EYEDROPPER) "Toque na cor que quer pegar." else "Toque dentro de uma forma fechada para pintar.")
            })
            "dock" -> DockTray(this, prefs.dockMode) { mode ->
                prefs.dockMode = mode
                closeTray()
                layoutDock(animateFrom = dock.left.toFloat() to dock.top.toFloat())
            }
            "tape" -> TapeTray(this, prefs) { applyPrefs() }
            "select" -> SelectTray(this, prefs) {
                applyPrefs()
                refresh()
            }
            else -> EraserTray(this, prefs, onChange = { applyPrefs() }, onClearAll = {
                canvasView.clearAll()
                closeTray()
            })
        }
        // Bandejas altas (marca-texto, tela deitada) rolam em vez de sair da tela.
        val scroller = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            addView(tray)
        }
        trayLayer.addView(scroller, lp)
        trayLayer.visibility = View.VISIBLE
        openTray = which
        if (Ui.animationsOn()) {
            scroller.alpha = 0f
            scroller.translationX = fromX
            scroller.translationY = fromY
            scroller.scaleX = 0.98f
            scroller.scaleY = 0.98f
            scroller.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(240).setInterpolator(Ui.EASE).start()
        }
    }

    private fun closeTray() {
        trayLayer.visibility = View.GONE
        trayLayer.removeAllViews()
        openTray = null
    }

    // ---- Barra de seleção ----------------------------------------------------------------

    private lateinit var selectionRow: LinearLayout
    private lateinit var selectionTrailing: LinearLayout

    private fun buildSelectionBar(): View {
        selectionRow = horizontal().apply { setPadding(dpi(14f), 0, dpi(4f), 0) }
        selectionTrailing = horizontal().apply { setPadding(0, 0, dpi(6f), 0) }
        selectionLabel = label("", 13f, Ui.ACCENT, bold = true)
        val outer = horizontal().apply {
            background = floating(100f)
            Ui.run { softShadow(12f) }
            isClickable = true
            setPadding(0, dpi(4f), 0, dpi(4f))
        }
        outer.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(selectionRow)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(selectionTrailing)
        return outer
    }

    /** Monta os botões conforme o que está selecionado (imagem, item travado, traços...). */
    private fun fillSelectionBar() {
        val row = selectionRow
        row.removeAllViews()
        selectionTrailing.removeAllViews()
        (selectionLabel.parent as? ViewGroup)?.removeView(selectionLabel)
        if (canvasView.inCropMode) {
            fillCropBar(row)
            return
        }
        val items = canvasView.selectedItems
        val img = canvasView.selectedImage
        val txt = canvasView.selectedText
        selectionLabel.text = when {
            items.isEmpty() -> "Área"
            img != null && img.pdfPage > 0 -> "Página ${img.pdfPage}"
            img != null -> "Imagem"
            txt != null -> "Texto"
            items.size == 1 -> "1 item"
            else -> "${items.size} itens"
        }
        row.addView(selectionLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dpi(6f)
        })
        // Área marcada com o laço/retângulo: print só do que está dentro dela.
        if (canvasView.hasRegion) row.addView(pillButton(Icon.CROP, "Print da área") { printRegion() })
        row.addView(pillButton(Icon.CARDS, if (pendingQuestion == null) "Criar cartão" else "Usar como resposta") { cardStep() })
        // Área sobre um PDF: copiar o recorte (mesmo sem itens) ou a página inteira.
        val pageUnder = canvasView.pdfPageUnderRegion()
        if (items.isEmpty() && canvasView.regionTouchesImages) row.addView(pillButton(Icon.COPY, "Copiar") { copySelection(cut = false) })
        if (pageUnder != null) row.addView(pillButton(Icon.COPY, "Copiar página inteira") { copyWholePage(pageUnder) })
        if (items.isEmpty()) {
            row.addView(pillButton(Icon.CLOSE, "Cancelar") { canvasView.clearSelection() })
            return
        }
        if (pdfOnlyTapped(items)) {
            row.addView(pillButton(Icon.COPY, "Copiar página") { copyWholePage(img!!) })
        } else {
            row.addView(pillButton(Icon.COPY, "Copiar") { copySelection(cut = false) })
            if (!canvasView.selectionLocked) row.addView(pillButton(Icon.COPY, "Recortar") { copySelection(cut = true) })
        }
        // Página de PDF (travada ou não) tem as mesmas ferramentas da imagem; travada, ela só
        // não sai do lugar ao escrever por cima.
        val pdfPage = img != null && img.pdfPage > 0
        if (canvasView.selectionLocked && !pdfPage) {
            row.addView(pillButton(Icon.UNLOCK, "Destravar") { canvasView.setLocked(false) })
            if (canvasView.lockedPdfPageCount > 1 && items.any { it is ImageElement && it.pdfPage > 0 }) {
                row.addView(pillButton(Icon.UNLOCK, "Destravar todas as páginas") { canvasView.setAllPdfPagesLocked(false) })
            }
            row.addView(pillButton(Icon.TRASH, "Excluir") { canvasView.deleteSelection() })
        } else if (txt != null) {
            row.addView(pillButton(Icon.EDIT, "Editar") {
                canvasView.clearSelection()
                textEditor.start(txt, 0f, 0f)
            })
            row.addView(pillButton(Icon.TEXT_BIGGER, "Maior") { canvasView.editTexts { scaleText(it, 1.2f) } })
            row.addView(pillButton(Icon.TEXT_SMALLER, "Menor") { canvasView.editTexts { scaleText(it, 1f / 1.2f) } })
            row.addView(pillButton(Icon.ROTATE, "Girar 90°") { canvasView.rotateSelection(90f) })
            row.addView(pillButton(Icon.FLIP_H, "Espelhar") { canvasView.editTexts { it.with(flipH = !it.flipH) } })
            row.addView(pillButton(Icon.FLIP_V, "Virar") { canvasView.editTexts { it.with(flipV = !it.flipV) } })
            row.addView(pillButton(Icon.PALETTE, "Cor") {
                Panels.showColorChoice(this, prefs, txt.color) { canvasView.recolorSelection(it) }
            })
            row.addView(pillButton(Icon.RESET, "Endireitar") { canvasView.editTexts { it.with(rotation = 0f, flipH = false, flipV = false) } })
            row.addView(pillButton(Icon.TO_FRONT, "Frente") { canvasView.reorderSelection(true) })
            row.addView(pillButton(Icon.TO_BACK, "Trás") { canvasView.reorderSelection(false) })
            row.addView(pillButton(Icon.COPY, "Duplicar") { canvasView.duplicateSelection() })
            row.addView(pillButton(Icon.TRASH, "Excluir") { canvasView.deleteSelection() })
        } else if (img != null) {
            row.addView(pillButton(Icon.CROP, "Recortar") { canvasView.startCrop() })
            row.addView(pillButton(Icon.ADJUST, "Ajustes") { ImageTools.showAdjustments(this, canvasView) })
            row.addView(pillButton(Icon.ROTATE, "Girar 90°") { canvasView.rotateSelection(90f) })
            row.addView(pillButton(Icon.FLIP_H, "Espelhar") { canvasView.editImages { it.with(flipH = !it.flipH) } })
            row.addView(pillButton(Icon.FLIP_V, "Virar") { canvasView.editImages { it.with(flipV = !it.flipV) } })
            row.addView(pillButton(Icon.TO_FRONT, "Frente") { canvasView.reorderSelection(true) })
            row.addView(pillButton(Icon.TO_BACK, "Trás") { canvasView.reorderSelection(false) })
            if (img.locked) row.addView(pillButton(Icon.UNLOCK, "Destravar") { canvasView.setLocked(false) })
            else row.addView(pillButton(Icon.LOCK, "Travar") { canvasView.setLocked(true) })
            if (pdfPage && img.locked && canvasView.lockedPdfPageCount > 1) {
                row.addView(pillButton(Icon.UNLOCK, "Destravar todas as páginas") { canvasView.setAllPdfPagesLocked(false) })
            }
            row.addView(pillButton(Icon.COPY, "Duplicar") { canvasView.duplicateSelection() })
            if (!pdfPage) row.addView(pillButton(Icon.IMAGE, "Substituir") { pickImage(REQ_REPLACE) })
            row.addView(pillButton(Icon.DOWNLOAD, "Salvar") { saveSelectedImage() })
            row.addView(pillButton(Icon.RESET, "Original") {
                canvasView.editImages {
                    ImageElement(it.file, RectF(it.rect), locked = it.locked, pdfPage = it.pdfPage).let { base ->
                        // Volta ao arquivo original inteiro, mantendo o centro e a largura atual.
                        val full = it.fullLocalFrame()
                        val w = full.width()
                        val h = full.height()
                        base.with(rect = RectF(it.centerX - w / 2, it.centerY - h / 2, it.centerX + w / 2, it.centerY + h / 2))
                    }
                }
            })
            row.addView(pillButton(Icon.TRASH, "Excluir") { canvasView.deleteSelection() })
        } else {
            row.addView(pillButton(Icon.TRASH, "Excluir") { canvasView.deleteSelection() })
            row.addView(pillButton(Icon.COPY, "Duplicar") { canvasView.duplicateSelection() })
            audio.markFor(items.filterIsInstance<StrokeElement>())?.let { (rec, t) ->
                row.addView(pillButton(Icon.TOUCH, "Ouvir daqui") { playAudio(rec, t - 2000L) }, 1)
            }
            row.addView(pillButton(Icon.PALETTE, "Cor") {
                Panels.showColorChoice(this, prefs, canvasView.pen.color) { canvasView.recolorSelection(it) }
            })
            row.addView(pillButton(Icon.ROTATE, "Girar 90°") { canvasView.rotateSelection(90f) })
            row.addView(pillButton(Icon.TO_FRONT, "Frente") { canvasView.reorderSelection(true) })
            row.addView(pillButton(Icon.TO_BACK, "Trás") { canvasView.reorderSelection(false) })
            if (items.any { it is ImageElement }) row.addView(pillButton(Icon.LOCK, if (items.all { it is ImageElement && it.pdfPage > 0 }) "Travar páginas" else "Travar imagens") { canvasView.setLocked(true) })
        }
        selectionTrailing.addView(iconButton(Icon.CHECK, Ui.ACCENT, 40f) { canvasView.clearSelection() })
    }

    /** Aumenta/diminui o texto mantendo o centro no lugar. */
    private fun scaleText(t: TextElement, k: Float) =
        t.transformed(Transform(scale = k, ox = t.centerX, oy = t.centerY))

    private fun fillCropBar(row: LinearLayout) {
        val shapes = listOf(
            InfiniteCanvasView.CropShape.RECT to "Livre",
            InfiniteCanvasView.CropShape.SQUARE to "1:1",
            InfiniteCanvasView.CropShape.WIDE to "16:9",
            InfiniteCanvasView.CropShape.ELLIPSE to "Círculo",
            InfiniteCanvasView.CropShape.LASSO to "Mão livre",
        )
        for ((shape, name) in shapes) {
            val sel = canvasView.cropShape == shape
            row.addView(TextView(this).apply {
                text = name
                textSize = 13f
                typeface = Ui.MEDIUM
                gravity = Gravity.CENTER
                minHeight = dpi(36f)
                setPadding(dpi(12f), 0, dpi(12f), 0)
                setTextColor(if (sel) Ui.ACCENT else Ui.INK)
                background = ripple(pill(if (sel) Ui.ACCENT_SOFT else Color.TRANSPARENT, 0), pill(Color.WHITE, 0))
                setOnClickListener {
                    canvasView.setCropShape(shape)
                    fillSelectionBar()
                    if (shape == InfiniteCanvasView.CropShape.LASSO) toast("Contorne com a caneta a parte que quer manter.")
                }
            })
        }
        row.addView(pillButton(Icon.RESET, "Tudo") { canvasView.resetCrop(); fillSelectionBar() })
        selectionTrailing.addView(iconButton(Icon.CLOSE, Ui.MUTED, 40f) { canvasView.cancelCrop() })
        selectionTrailing.addView(TextView(this).apply {
            text = "Aplicar"
            textSize = 14f
            typeface = Ui.MEDIUM
            gravity = Gravity.CENTER
            setTextColor(Ui.ON_ACCENT)
            minHeight = dpi(38f)
            setPadding(dpi(16f), 0, dpi(16f), 0)
            background = ripple(pill(Ui.ACCENT, 0), pill(Color.WHITE, 0), Color.parseColor("#33FFFFFF"))
            setOnClickListener { canvasView.applyCrop() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dpi(4f)
            marginEnd = dpi(4f)
        })
    }

    // ---- Copiar, colar e print da área -------------------------------------------------

    /** Só uma página de PDF selecionada por toque (sem área marcada). */
    private fun pdfOnlyTapped(items: List<Element>) =
        !canvasView.hasRegion && items.size == 1 && (items[0] as? ImageElement)?.pdfPage?.let { it > 0 } == true

    /**
     * Copia a seleção. Com uma área marcada sobre imagens ou PDF, vai junto o pedaço delas
     * recortado pelo laço (por baixo), e a escrita continua editável por cima.
     */
    private fun copySelection(cut: Boolean) {
        val items = canvasView.selectedItems
        val piece = if (canvasView.hasRegion) canvasView.renderRegionImages() else null
        if (items.isEmpty() && piece == null) return
        // Imagens que entram no recorte não vão também inteiras.
        val keep = if (piece != null) items.filter { it !is ImageElement } else items
        Clipboard.copy(this, keep, Library.assetsDir(note.id), piece)
        piece?.first?.recycle()
        if (cut) {
            canvasView.deleteSelectionWhere { piece == null || it !is ImageElement }
        }
        canvasView.clearSelection()
        updatePastePill()
        toast(if (cut) "Recortado. Toque em Colar (aqui ou em outra nota)." else "Copiado. Toque em Colar (aqui ou em outra nota).")
    }

    /** Página inteira do PDF (ou imagem), com a escrita que está em cima dela. */
    private fun copyWholePage(page: ImageElement) {
        Clipboard.copy(this, canvasView.elementsOnImage(page), Library.assetsDir(note.id))
        canvasView.clearSelection()
        updatePastePill()
        toast("Página copiada. Toque em Colar (aqui ou em outra nota).")
    }

    private fun printRegion() {
        val region = canvasView.region ?: return
        val bmp = canvasView.renderRegion()
        if (bmp == null) {
            toast("Área pequena demais.")
            return
        }
        Clipboard.copyImage(this, bmp, region.bounds.width(), region.bounds.height())
        bmp.recycle()
        canvasView.clearSelection()
        updatePastePill()
        toast("Print copiado. Toque em Colar (aqui ou em outra nota).")
    }

    private fun pasteClipboard() {
        val items = Clipboard.paste(this, Library.assetsDir(note.id))
        if (items.isEmpty()) {
            toast("Nada para colar.")
            return
        }
        canvasView.pasteElements(items)
        refresh()
    }

    private fun updatePastePill() {
        if (::pastePill.isInitialized) pastePill.visibility = if (Clipboard.hasContent(this)) View.VISIBLE else View.GONE
    }

    // ---- Compartilhar e imprimir --------------------------------------------------------

    private fun shareFile(file: java.io.File, mime: String) {
        val uri = ShareProvider.uriFor(this, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            clipData = android.content.ClipData.newRawUri(note.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, "Compartilhar nota").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            toast("Nenhum app para compartilhar.")
        }
    }

    private fun shareFileName(ext: String) = fileName(ext).replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")

    private fun shareNote() {
        actionSheet("Compartilhar nota", listOf(
            SheetItem(Icon.PDF, "Como PDF") {
                val doc = buildPdf() ?: return@SheetItem
                val f = java.io.File(ShareProvider.dir(this), shareFileName("pdf"))
                Library.io.execute {
                    val ok = try { f.outputStream().use { doc.writeTo(it) }; true } catch (e: Exception) { false } finally { doc.close() }
                    main.post { if (ok) shareFile(f, "application/pdf") else toast("Não foi possível gerar o PDF.") }
                }
            },
            SheetItem(Icon.IMAGE, "Como imagem") {
                val bmp = canvasView.renderToBitmap(4096, 4096)
                if (bmp == null) { toast("A nota está vazia."); return@SheetItem }
                val f = java.io.File(ShareProvider.dir(this), shareFileName("png"))
                Library.io.execute {
                    val ok = try { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; true } catch (e: Exception) { false } finally { bmp.recycle() }
                    main.post { if (ok) shareFile(f, "image/png") else toast("Não foi possível gerar a imagem.") }
                }
            },
        ))
    }

    private fun printNote() {
        val doc = buildPdf() ?: return
        val f = java.io.File(ShareProvider.dir(this), shareFileName("pdf"))
        Library.io.execute {
            val ok = try { f.outputStream().use { doc.writeTo(it) }; true } catch (e: Exception) { false } finally { doc.close() }
            main.post {
                if (!ok) { toast("Não foi possível gerar o PDF."); return@post }
                val pm = getSystemService(PRINT_SERVICE) as android.print.PrintManager
                pm.print(note.title, PdfPrintAdapter(f, note.title), null)
            }
        }
    }

    /** Manda para a impressora um PDF já pronto. */
    private class PdfPrintAdapter(private val file: java.io.File, private val name: String) : android.print.PrintDocumentAdapter() {
        override fun onLayout(old: android.print.PrintAttributes?, new: android.print.PrintAttributes?, cancel: android.os.CancellationSignal?,
                              cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(android.print.PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }

        override fun onWrite(pages: Array<out android.print.PageRange>?, dest: android.os.ParcelFileDescriptor, cancel: android.os.CancellationSignal?,
                             cb: WriteResultCallback) {
            try {
                file.inputStream().use { input -> java.io.FileOutputStream(dest.fileDescriptor).use { input.copyTo(it) } }
                cb.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            }
        }
    }

    private fun saveSelectedImage() {
        val img = canvasView.selectedImage ?: return
        Library.io.execute {
            val bmp = try { canvasView.images.export(img) } catch (e: Exception) { null }
            val ok = bmp != null && try {
                writeToMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fileName("png"), "image/png",
                    Environment.DIRECTORY_PICTURES + "/RosaNotes") { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            } catch (e: Exception) {
                false
            }
            bmp?.recycle()
            main.post { toast(if (ok) "Imagem salva em Imagens/RosaNotes" else "Não foi possível salvar a imagem.") }
        }
    }

    // ---- Zoom -------------------------------------------------------------------------

    private fun showZoomMenu() {
        val fitPage = if (canvasView.pageLayout.hasPages)
            listOf(SheetItem(Icon.PAGE, "Ajustar à folha") { canvasView.fitToPage(canvasView.currentPage()) }) else emptyList()
        actionSheet("Zoom: ${canvasView.zoomPercent}%", fitPage + listOf(
            SheetItem(Icon.CENTER, "Ajustar à tela (ver tudo)") { canvasView.recenter() },
            SheetItem(Icon.ZOOM, "100% (tamanho real)") { canvasView.zoomTo(1f) },
            SheetItem(Icon.ZOOM, "50%") { canvasView.zoomTo(0.5f) },
            SheetItem(Icon.ZOOM, "200%") { canvasView.zoomTo(2f) },
            SheetItem(Icon.ZOOM, "400%") { canvasView.zoomTo(4f) },
        ))
    }

    // ---- Menu ⋮ --------------------------------------------------------------------

    fun showMoreMenu() {
        closeTray()
        sheet(null) { box, dialog ->
            fun act(block: () -> Unit): () -> Unit = { dialog.dismiss(); block() }
            // Cabeçalho: nome da nota e "Renomear"
            box.addView(title(note.title, 22f).apply {
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            box.addView(label("Renomear nota", 13.5f, Ui.ACCENT, bold = true).apply {
                setPadding(0, dpi(4f), 0, dpi(16f))
                val renameNote = act { rename() }
                setOnClickListener { renameNote() }
            })
            // Atalhos em cartões
            val tiles = mutableListOf(
                menuTile(Icon.PAGE, "Página", act { Panels.showPage(this, canvasView, prefs) { onStateChanged() } }),
                menuTile(Icon.PAGES_V, "Folhas", act { showPageManager() }),
                menuTile(Icon.BOOKMARK, "Marcadores", act { showBookmarks() }),
                menuTile(Icon.RULER, if (canvasView.rulerOn) "Esconder régua" else "Régua", act { canvasView.rulerOn = !canvasView.rulerOn }),
                menuTile(Icon.CARDS, if (dueCards() > 0) "Revisar (${dueCards()})" else "Cartões", act { showCards() }),
                menuTile(if (Ui.isDark) Icon.MOON else Icon.SUN, "Tema", act { Ui.showThemeMenu(this) }),
                menuTile(Icon.TOUCH, "Laser", act { setEphemeral(InfiniteCanvasView.Ephemeral.LASER) }),
                menuTile(Icon.EDIT, "Tinta que some", act { setEphemeral(InfiniteCanvasView.Ephemeral.VANISH) }),
                if (audio.isRecording) menuTile(Icon.CLOSE, "Parar gravação", act { audio.stop(); updateAudioPill() })
                else menuTile(Icon.MIC, "Gravar áudio", act { startRecording() }),
            )
            if (audio.recordings.isNotEmpty()) tiles.add(menuTile(Icon.MIC, "Áudios (${audio.recordings.size})", act { showRecordings() }))
            if (canvasView.tapeCount > 0) {
                val hide = canvasView.hiddenTapeCount == 0
                tiles.add(menuTile(Icon.TAPE, if (hide) "Esconder fitas" else "Mostrar fitas", act { canvasView.setAllTapes(revealed = !hide); refresh() }))
            }
            // Completa a última fileira com espaços do mesmo tamanho (os cartões não esticam).
            while (tiles.size % 3 != 0) tiles.add(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }
            })
            box.addView(wrapRow(tiles, 3))

            box.addView(menuSection("Arquivo"))
            box.addView(menuRow(Icon.PDF, "Importar PDF", act { pickPdf() }))
            box.addView(menuRow(Icon.EXPORT, "Exportar como imagem", act { exportPng() }))
            box.addView(menuRow(Icon.PDF, "Exportar como PDF", act { exportPdf() }))
            box.addView(menuRow(Icon.EXPORT, "Compartilhar (PDF ou imagem)", act { shareNote() }))
            box.addView(menuRow(Icon.PDF, "Imprimir", act { printNote() }))
            if (canvasView.pdfPages().isNotEmpty()) {
                box.addView(menuRow(Icon.PDF, "Exportar PDF anotado (páginas originais)", act { exportAnnotatedPdf() }))
            }
            if (canvasView.pdfPageCount > 0) {
                if (canvasView.lockedPdfPageCount > 0) box.addView(menuRow(Icon.UNLOCK, "Destravar todas as páginas do PDF", act { canvasView.setAllPdfPagesLocked(false) }))
                else box.addView(menuRow(Icon.LOCK, "Travar todas as páginas do PDF", act { canvasView.setAllPdfPagesLocked(true) }))
            }

            box.addView(menuSection("Nota"))
            if (Clipboard.hasContent(this)) box.addView(menuRow(Icon.COPY, "Colar", act { pasteClipboard() }))
            box.addView(menuRow(Icon.SELECT_ALL, "Selecionar tudo", act { canvasView.selectAll(); refresh() }))
            box.addView(menuRow(Icon.CENTER, "Centralizar conteúdo", act { canvasView.recenter() }))
            box.addView(menuRow(Icon.MOVE, "Mover para outra pasta", act { moveToFolder() }))

            box.addView(menuSection("Ajustes"))
            box.addView(switchRow(Icon.TOUCH, "Escrever com o dedo", prefs.fingerDraws) {
                prefs.fingerDraws = it
                applyPrefs()
            })
            box.addView(switchRow(Icon.EDIT, "Tinta de latência mínima (experimental)", prefs.lowLatency) {
                prefs.lowLatency = it
                applyPrefs()
                attachWetInk()
                toast(if (it) "Tinta de latência mínima ligada (experimental)." else "Tinta de latência mínima desligada.")
            })
        }
    }

    /** Cartão de atalho do menu ⋮: ícone num círculo suave e o nome embaixo. */
    private fun menuTile(ic: Icon, text: String, onClick: () -> Unit): View = vertical().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(4f), dpi(12f), dpi(4f), dpi(10f))
        background = ripple(rounded(Ui.FIELD, 16f), rounded(Color.WHITE, 16f))
        contentDescription = text
        setOnClickListener { onClick() }
        Ui.run { pressable(0.96f) }
        addView(ImageView(this@EditorActivity).apply {
            setImageDrawable(icon(ic, Ui.ACCENT, 22f))
            scaleType = ImageView.ScaleType.CENTER
            background = circle(Ui.ACCENT_SOFT, 0, 0f)
        }, LinearLayout.LayoutParams(dpi(42f), dpi(42f)))
        addView(label(text, 12.5f, Ui.INK).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dpi(6f), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
        }
    }

    private fun menuSection(text: String) = sectionLabel(text).apply { setPadding(dpi(4f), dpi(18f), 0, dpi(4f)) }

    /** Linha do menu ⋮: ícone e texto, com toque arredondado. */
    private fun menuRow(ic: Icon, text: String, onClick: () -> Unit): View = horizontal().apply {
        minimumHeight = dpi(50f)
        setPadding(dpi(8f), 0, dpi(8f), 0)
        background = ripple(null, rounded(Color.WHITE, 14f))
        setOnClickListener { onClick() }
        addView(ImageView(this@EditorActivity).apply { setImageDrawable(icon(ic, Ui.INK, 22f)) },
            LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(16f) })
        addView(label(text, 15.5f, Ui.INK))
    }

    // ---- Marcadores -----------------------------------------------------------------------

    private fun showBookmarks() {
        sheet("Marcadores") { box, dialog ->
            if (canvasView.bookmarks.isEmpty()) {
                box.addView(label("Marque partes importantes da nota (por exemplo, “Aula 3 – Cardio”) para voltar a elas com um toque.", 14f).apply {
                    setPadding(0, 0, 0, dpi(8f))
                })
            }
            for (b in canvasView.bookmarks.toList()) {
                val row = horizontal().apply {
                    minimumHeight = dpi(50f)
                    setPadding(dpi(8f), 0, 0, 0)
                    background = ripple(null, rounded(Color.WHITE, 14f))
                    setOnClickListener { dialog.dismiss(); canvasView.goTo(b) }
                }
                row.addView(ImageView(this).apply { setImageDrawable(icon(Icon.BOOKMARK, Ui.ACCENT, 22f)) },
                    LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(14f) })
                row.addView(label(b.title, 15.5f, Ui.INK), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(iconButton(Icon.EDIT, Ui.MUTED, 38f, "Renomear") {
                    dialog.dismiss()
                    promptText("Renomear marcador", b.title) { t -> if (t.isNotBlank()) canvasView.renameBookmark(b, t); showBookmarks() }
                })
                row.addView(iconButton(Icon.TRASH, Ui.MUTED, 38f, "Excluir") {
                    canvasView.removeBookmark(b)
                    dialog.dismiss()
                    showBookmarks()
                })
                box.addView(row)
            }
            box.addView(buttonRow(
                secondaryButton("Fechar") { dialog.dismiss() },
                primaryButton("Marcar aqui") {
                    dialog.dismiss()
                    promptText("Novo marcador", "", "Ex.: Aula 3 – Cardio", "Marcar") { t ->
                        canvasView.addBookmark(t.ifBlank { "Marcador ${canvasView.bookmarks.size + 1}" })
                        toast("Marcador criado.")
                    }
                },
            ))
        }
    }

    // ---- Cartões de estudo (flashcards) ------------------------------------------------

    /** Área da pergunta escolhida, esperando a resposta. */
    private var pendingQuestion: RectF? = null

    private fun dueCards(): Int {
        val now = System.currentTimeMillis()
        return canvasView.cards.count { it.due <= now }
    }

    private fun cardStep() {
        val area = canvasView.selectionArea() ?: return
        val q = pendingQuestion
        if (q == null) {
            pendingQuestion = area
            canvasView.clearSelection()
            toast("Pergunta marcada. Agora selecione a resposta e toque em “Usar como resposta”.")
        } else {
            canvasView.addCard(q, area)
            pendingQuestion = null
            canvasView.clearSelection()
            toast("Cartão criado (${canvasView.cards.size} na nota).")
        }
        refresh()
    }

    private fun cardImage(r: RectF): View = ImageView(this).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        maxHeight = (resources.displayMetrics.heightPixels * 0.32f).toInt()
        canvasView.renderArea(r, 1200)?.let { setImageBitmap(it) }
        background = rounded(Color.WHITE, 12f, Ui.LINE, 1f)
        setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
    }

    private fun showCards() {
        val cards = canvasView.cards.toList()
        val due = dueCards()
        sheet("Cartões de estudo") { box, dialog ->
            box.addView(label(when {
                cards.isEmpty() -> "Selecione um trecho (a pergunta), toque em “Criar cartão”; depois selecione a resposta e toque em “Usar como resposta”."
                due == 0 -> "${cards.size} cartões. Nada para revisar hoje."
                else -> "${cards.size} cartões, $due para revisar hoje."
            }, 14f).apply { setPadding(0, 0, 0, dpi(8f)); setLineSpacing(0f, 1.1f) })
            val now = System.currentTimeMillis()
            cards.forEachIndexed { i, c ->
                val row = horizontal().apply { setPadding(0, dpi(6f), 0, dpi(6f)); gravity = Gravity.CENTER_VERTICAL }
                row.addView(ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    canvasView.renderArea(c.question, 240)?.let { setImageBitmap(it) }
                    background = rounded(Color.WHITE, 8f, Ui.LINE, 1f)
                }, LinearLayout.LayoutParams(dpi(76f), dpi(48f)).apply { marginEnd = dpi(12f) })
                val days = ((c.due - now) / (24L * 60 * 60 * 1000)).toInt()
                row.addView(label("Cartão ${i + 1}\n" + if (c.due <= now) "para revisar hoje" else "volta em ${max(1, days)} dia(s)", 13.5f, Ui.INK),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(iconButton(Icon.TRASH, Ui.MUTED, 38f, "Excluir cartão") {
                    canvasView.removeCard(c)
                    dialog.dismiss()
                    showCards()
                })
                box.addView(row)
            }
            box.addView(buttonRow(
                secondaryButton("Fechar") { dialog.dismiss() },
                primaryButton(if (due > 0) "Revisar agora ($due)" else "Revisar todos") {
                    dialog.dismiss()
                    startReview(all = due == 0)
                },
            ))
        }
    }

    /** Revisão com repetição espaçada simples (caixas de Leitner). */
    private fun startReview(all: Boolean) {
        val now = System.currentTimeMillis()
        val queue = ArrayDeque(canvasView.cards.filter { all || it.due <= now })
        if (queue.isEmpty()) {
            toast("Nenhum cartão para revisar.")
            return
        }
        var reviewed = 0
        val missed = HashSet<Flashcard>()
        fun day(n: Int) = n * 24L * 60 * 60 * 1000
        fun next() {
            val card = queue.removeFirstOrNull()
            if (card == null) {
                toast("Revisão concluída: $reviewed cartões.")
                return
            }
            sheet("Revisar · faltam ${queue.size + 1}") { box, dialog ->
                box.addView(sectionLabel("Pergunta"))
                box.addView(cardImage(card.question))
                val answer = vertical().apply { visibility = View.GONE }
                answer.addView(sectionLabel("Resposta").apply { setPadding(0, dpi(14f), 0, dpi(6f)) })
                answer.addView(cardImage(card.answer))
                box.addView(answer)
                fun grade(box2: Int, again: Boolean) {
                    val t = System.currentTimeMillis()
                    val updated = card.with(box2, if (again) t else t + day(Flashcard.INTERVAL_DAYS[box2]))
                    canvasView.updateCard(card, updated)
                    reviewed++
                    if (again && updated !in missed) { missed.add(updated); queue.addLast(updated) }
                    dialog.dismiss()
                    next()
                }
                val grades = buttonRow(
                    secondaryButton("Errei") { grade(0, again = true) },
                    secondaryButton("Difícil") { grade(max(1, card.box).coerceAtMost(5), again = false) },
                    primaryButton("Fácil") { grade((card.box + 1).coerceAtMost(5), again = false) },
                ).apply { visibility = View.GONE }
                lateinit var show: View
                show = primaryButton("Mostrar resposta") {
                    answer.visibility = View.VISIBLE
                    grades.visibility = View.VISIBLE
                    show.visibility = View.GONE
                }
                box.addView(show, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(14f) })
                box.addView(grades)
            }
        }
        next()
    }

    // ---- Folhas em miniatura ------------------------------------------------------------

    private fun showPageManager() {
        if (!canvasView.pageLayout.hasPages) {
            sheet("Folhas") { box, dialog ->
                box.addView(label("Esta nota é uma tela infinita. Para ver e organizar as folhas em miniatura, mude para folhas.", 15f, Ui.MUTED))
                box.addView(buttonRow(
                    secondaryButton("Agora não") { dialog.dismiss() },
                    primaryButton("Usar folhas") { dialog.dismiss(); Panels.showPage(this, canvasView, prefs) { onStateChanged() } },
                ))
            }
            return
        }
        val n = canvasView.pageCount
        sheet("Folhas ($n)") { box, dialog ->
            box.addView(label("Toque para ir até a folha; use ⋯ para mover, duplicar, inserir ou excluir.", 13f).apply {
                setPadding(0, 0, 0, dpi(8f))
            })
            val cells = ArrayList<View>()
            for (i in 0 until n) {
                val cell = vertical().apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(4f))
                    background = ripple(rounded(Ui.FIELD, 14f), rounded(Color.WHITE, 14f))
                    setOnClickListener { dialog.dismiss(); canvasView.fitToPage(i) }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
                    }
                }
                val thumb = ImageView(this).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    Ui.run { softShadow(2f) }
                }
                cell.addView(thumb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                thumb.post { canvasView.renderPage(i, dpi(110f))?.let { thumb.setImageBitmap(it) } }
                val foot = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                foot.addView(label("${i + 1}", 13f, Ui.INK, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpi(6f)
                })
                foot.addView(iconButton(Icon.MORE, Ui.MUTED, 34f, "Opções da folha ${i + 1}") {
                    dialog.dismiss()
                    pageOptions(i, n)
                })
                cell.addView(foot)
                cells.add(cell)
            }
            while (cells.size % 3 != 0) cells.add(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }
            })
            box.addView(wrapRow(cells, 3))
        }
    }

    private fun pageOptions(i: Int, n: Int) {
        fun again() { refresh(); showPageManager() }
        actionSheet("Folha ${i + 1}", listOfNotNull(
            if (i > 0) SheetItem(Icon.TO_FRONT, "Mover para cima") { canvasView.swapPages(i, i - 1); again() } else null,
            if (i < n - 1) SheetItem(Icon.TO_BACK, "Mover para baixo") { canvasView.swapPages(i, i + 1); again() } else null,
            SheetItem(Icon.COPY, "Copiar folha (para colar aqui ou em outra nota)") {
                val els = canvasView.elementsOnPage(i)
                if (els.isEmpty()) toast("A folha está vazia.") else {
                    Clipboard.copy(this, els, Library.assetsDir(note.id))
                    updatePastePill()
                    toast("Folha copiada. Toque em Colar.")
                }
            },
            SheetItem(Icon.COPY, "Duplicar") { canvasView.duplicatePage(i); again() },
            SheetItem(Icon.PLUS, "Inserir folha em branco depois") { canvasView.insertBlankPageAfter(i); again() },
            if (n > 1) SheetItem(Icon.TRASH, "Excluir folha", danger = true) {
                Ui.run {
                    confirm("Excluir a folha ${i + 1}?", "Tudo o que está nela será apagado (dá para desfazer).", "Excluir") {
                        canvasView.deletePage(i)
                        again()
                    }
                }
            } else null,
        ))
    }

    // ---- Girar a tela -------------------------------------------------------------------

    /** Ímã nos ângulos retos do slider (±2°). */
    private fun snapAngle(a: Float): Float {
        val r = (a / 90f).roundToInt() * 90f
        return if (kotlin.math.abs(a - r) < 2f) r else a
    }

    private fun showRotateMenu() {
        lateinit var heading: TextView
        lateinit var slider: AngleSlider
        fun show() { heading.text = "Girar a tela: ${canvasView.viewRotation.roundToInt()}°" }
        fun apply(deg: Float) {
            canvasView.setViewRotation(deg)
            slider.value = canvasView.viewRotation
            show()
        }
        val dialog = sheet(null) { box, d ->
            heading = title("", 19f)
            box.addView(heading)
            box.addView(label("Só a tela gira: a escrita, o PDF e as imagens exportadas continuam retos.", 13f).apply {
                setPadding(0, dpi(4f), 0, dpi(10f))
            })
            slider = AngleSlider(this, canvasView.viewRotation) { v ->
                canvasView.setViewRotation(snapAngle(v))
                show()
            }
            box.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            box.addView(buttonRow(
                secondaryButton("−90°") { apply(canvasView.viewRotation - 90f) },
                secondaryButton("−15°") { apply(canvasView.viewRotation - 15f) },
                secondaryButton("+15°") { apply(canvasView.viewRotation + 15f) },
                secondaryButton("+90°") { apply(canvasView.viewRotation + 90f) },
            ))
            box.addView(buttonRow(
                secondaryButton("Endireitar (0°)") { apply(0f) },
                primaryButton("Pronto") { d.dismiss() },
            ))
            show()
        }
        // Sem escurecer o fundo: dá para ver a nota girando.
        dialog.window?.setDimAmount(0f)
    }

    private fun rename() {
        promptText("Renomear nota", note.title) { t ->
            if (t.isNotBlank()) {
                Library.updateNote(note, title = t)
                refresh()
            }
        }
    }

    private fun moveToFolder() {
        Pickers.folder(this, "Mover para", allowRoot = false, exclude = null, current = note.folder) { target ->
            if (target != null) {
                Library.updateNote(note, folder = target)
                refresh()
                toast("Nota movida para ${Library.folder(target)?.name}")
            }
        }
    }

    // ---- Listener da tela ---------------------------------------------------------

    override fun onStateChanged() {
        if (::textEditor.isInitialized) textEditor.reposition()
        undoButton.alpha = if (canvasView.canUndo) 1f else 0.3f
        redoButton.alpha = if (canvasView.canRedo) 1f else 0.3f
        zoomLabel.text = "${canvasView.zoomPercent}%"
        angleLabel.text = "${canvasView.viewRotation.roundToInt()}°"
    }

    override fun onSelectionChanged(count: Int) {
        val wasVisible = selectionBar.visibility == View.VISIBLE
        selectionBar.visibility = if (count > 0 || canvasView.inCropMode || canvasView.hasRegion) View.VISIBLE else View.GONE
        if (!wasVisible && selectionBar.visibility == View.VISIBLE && Ui.animationsOn()) {
            // Barra sobe de baixo com a mesma curva das bandejas.
            selectionBar.alpha = 0f
            selectionBar.translationY = dp(16f)
            selectionBar.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Ui.EASE).start()
        }
        // A barra fica embaixo, no lugar do contador de zoom.
        viewPills.visibility = if (selectionBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (selectionBar.visibility == View.VISIBLE) fillSelectionBar()
        refresh()
    }

    override fun onCropModeChanged(active: Boolean) {
        onSelectionChanged(canvasView.selectedItems.size)
    }

    override fun onTextRequest(x: Float, y: Float, existing: TextElement?) {
        closeTray()
        canvasView.clearSelection()
        textEditor.start(existing, x, y)
    }

    // ---- Imagem -----------------------------------------------------------------

    private fun showInsertMenu() {
        actionSheet("Inserir", listOf(
            SheetItem(Icon.IMAGE, "Imagem da galeria") { pickImage(REQ_IMAGE) },
            SheetItem(Icon.PDF, "PDF (todas as páginas)") { pickPdf() },
            SheetItem(Icon.SHAPES, "Forma (retângulo, círculo, seta, tabela…)") { showShapeMenu() },
            SheetItem(Icon.RULER, if (canvasView.rulerOn) "Esconder a régua" else "Régua") { canvasView.rulerOn = !canvasView.rulerOn },
        ))
    }

    // ---- Formas prontas -------------------------------------------------------------------

    private fun showShapeMenu() {
        sheet("Inserir forma") { box, dialog ->
            box.addView(label("Usa a cor e a espessura da caneta atual. Depois é só arrastar e redimensionar.", 13f).apply {
                setPadding(0, 0, 0, dpi(8f))
            })
            val icons = mapOf(
                Shapes.Kind.RECT to Icon.RECT_SELECT, Shapes.Kind.SQUARE to Icon.RECT_SELECT,
                Shapes.Kind.ELLIPSE to Icon.SHAPES, Shapes.Kind.CIRCLE to Icon.SHAPES,
                Shapes.Kind.TRIANGLE to Icon.SHAPES, Shapes.Kind.LINE to Icon.STRAIGHT,
                Shapes.Kind.ARROW to Icon.CHEVRON_RIGHT, Shapes.Kind.DOUBLE_ARROW to Icon.CHEVRON_RIGHT,
                Shapes.Kind.TABLE to Icon.PAGE,
            )
            val tiles: MutableList<View> = Shapes.Kind.entries.map { k ->
                menuTile(icons[k] ?: Icon.SHAPES, k.label) {
                    dialog.dismiss()
                    if (k == Shapes.Kind.TABLE) askTableSize() else insertShape(k)
                }
            }.toMutableList()
            while (tiles.size % 3 != 0) tiles.add(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }
            })
            box.addView(wrapRow(tiles, 3))
        }
    }

    private fun askTableSize() {
        var rows = 3
        var cols = 3
        sheet("Tabela") { box, dialog ->
            fun stepper(title: String, get: () -> Int, set: (Int) -> Unit): View {
                val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(6f), 0, dpi(6f)) }
                row.addView(label(title, 15f, Ui.INK), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val value = label("${get()}", 17f, Ui.INK, bold = true).apply { gravity = Gravity.CENTER }
                row.addView(secondaryButton("−") { set((get() - 1).coerceIn(1, 10)); value.text = "${get()}" })
                row.addView(value, LinearLayout.LayoutParams(dpi(44f), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(secondaryButton("+") { set((get() + 1).coerceIn(1, 10)); value.text = "${get()}" })
                return row
            }
            box.addView(stepper("Linhas", { rows }) { rows = it })
            box.addView(stepper("Colunas", { cols }) { cols = it })
            box.addView(buttonRow(
                secondaryButton("Cancelar") { dialog.dismiss() },
                primaryButton("Inserir") { dialog.dismiss(); insertShape(Shapes.Kind.TABLE, rows, cols) },
            ))
        }
    }

    /** Cria a forma como traços comuns (editáveis), no centro da tela e já selecionada. */
    internal fun insertShape(kind: Shapes.Kind, rows: Int = 3, cols: Int = 3) {
        val pen = canvasView.pen
        val type = if (pen.type == BrushType.HIGHLIGHTER) BrushType.PEN else pen.type
        val width = pen.size * canvasView.unit
        val w = canvasView.width * 0.4f / canvasView.zoom
        val lines = Shapes.build(kind, w, rows, cols)
        val strokes = lines.map { l ->
            StrokeElement(type, pen.color, if (pen.type == BrushType.HIGHLIGHTER) 255 else pen.alpha, l.xs, l.ys, FloatArray(l.xs.size) { width })
        }
        canvasView.pasteElements(strokes)
        refresh()
    }

    private fun pickImage(request: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Escolha uma imagem"), request)
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_PDF)
    }

    @Deprecated("Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PDF -> askPdfLayout(uri)
            REQ_IMAGE, REQ_REPLACE -> Library.io.execute {
                val result = try { importImage(uri) } catch (e: Exception) { null }
                main.post {
                    if (result == null) toast("Não foi possível abrir a imagem.")
                    else if (requestCode == REQ_REPLACE) canvasView.replaceSelectedImage(result.first, result.second, result.third)
                    else {
                        canvasView.addImage(result.first, result.second, result.third)
                        refresh()
                    }
                }
            }
        }
    }

    // ---- PDF ----------------------------------------------------------------------------

    /** Pergunta como organizar as páginas e importa. */
    private fun askPdfLayout(uri: Uri) {
        val name = PdfImporter.displayName(this, uri) ?: "PDF"
        var layout = PdfImporter.Layout.VERTICAL
        Ui.run {
            sheet("Importar PDF") { box, dialog ->
                box.addView(label(name, 14f).apply { setPadding(0, 0, 0, dpi(12f)) })
                box.addView(label("Como organizar as páginas?", 15f, Ui.INK))
                box.addView(segmented(PdfImporter.Layout.entries.map { it.label }, 0) { i ->
                    layout = PdfImporter.Layout.entries[i]
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpi(10f)
                })
                box.addView(label("As páginas entram travadas, como fundo, para você escrever por cima. " +
                    "Toque numa página com a seleção para destravar.", 13f).apply { setPadding(0, dpi(12f), 0, 0) })
                box.addView(buttonRow(
                    secondaryButton("Cancelar") { dialog.dismiss() },
                    primaryButton("Importar") { dialog.dismiss(); importPdf(uri, layout) },
                ))
            }
        }
    }

    private fun importPdf(uri: Uri, layout: PdfImporter.Layout) {
        var cancelled = false
        lateinit var status: TextView
        lateinit var bar: android.widget.ProgressBar
        val dialog = Ui.run {
            sheet("Importando PDF") { box, d ->
                status = label("Preparando…", 15f, Ui.INK)
                box.addView(status)
                bar = android.widget.ProgressBar(this@EditorActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = false
                    max = 100
                    progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
                }
                box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(24f)).apply { topMargin = dpi(8f) })
                box.addView(buttonRow(secondaryButton("Cancelar") { cancelled = true; d.dismiss() }))
            }
        }
        dialog.setCancelable(false)
        val (x0, y0) = canvasView.freeSpotBelow()
        val dir = Library.assetsDir(note.id)
        Thread {
            val result = try {
                PdfImporter.render(this, uri, dir, progress = { i, n ->
                    main.post {
                        status.text = "Página $i de $n"
                        bar.progress = i * 100 / n
                    }
                }, cancelled = { cancelled })
            } catch (e: SecurityException) {
                main.post { toast("Esse PDF tem senha e não pode ser aberto.") }
                null
            } catch (e: Exception) {
                main.post { toast("Não foi possível abrir o PDF.") }
                null
            }
            main.post {
                if (dialog.isShowing) dialog.dismiss()
                if (!result.isNullOrEmpty()) {
                    canvasView.addPages(PdfImporter.layout(result, layout, x0, y0))
                    toast(if (result.size == 1) "1 página importada" else "${result.size} páginas importadas")
                    refresh()
                }
            }
        }.start()
    }

    /** Copia a imagem para a pasta da nota (reduzida a no máximo 2048 px). */
    private fun importImage(uri: Uri): Triple<String, Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val bmp0 = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val longSide = max(bmp0.width, bmp0.height)
        val bmp = if (longSide > 2048) {
            val f = 2048f / longSide
            Bitmap.createScaledBitmap(bmp0, (bmp0.width * f).toInt(), (bmp0.height * f).toInt(), true)
        } else bmp0
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        val png = bmp.hasAlpha()
        val name = UUID.randomUUID().toString().take(12) + if (png) ".png" else ".jpg"
        FileOutputStream(File(dir, name)).use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
        }
        return Triple(name, bmp.width, bmp.height)
    }

    // ---- Salvar e exportar ---------------------------------------------------------

    private fun save() {
        val data = canvasView.snapshot()
        val wasDirty = canvasView.dirty
        canvasView.markSaved()
        if (wasDirty) Library.updateNote(note, touch = true)
        val thumb = if (wasDirty) canvasView.renderToBitmap(480, 360, 1f, thumbnail = true) else null
        val id = note.id
        Library.io.execute {
            try {
                NoteStorage.save(Library.noteFile(id), data)
                if (wasDirty) indexText(id, data)
                if (wasDirty) {
                    val tf = Library.thumbFile(id)
                    if (thumb == null) tf.delete() else {
                        tf.parentFile?.mkdirs()
                        FileOutputStream(tf).use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        thumb.recycle()
                    }
                }
            } catch (e: Exception) {
                main.post { toast("Erro ao salvar a nota.") }
            }
        }
    }

    // ---- Áudio ---------------------------------------------------------------------

    private fun startRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (audio.start(this)) {
            toast("Gravando: o que você escrever fica ligado ao áudio")
            updateAudioPill()
        } else toast("Não foi possível gravar.")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) startRecording()
            else toast("Sem acesso ao microfone, não dá para gravar.")
        }
    }

    private fun playAudio(index: Int, fromMs: Long) {
        if (!audio.play(index, fromMs) { updateAudioPill() }) toast("Não foi possível tocar o áudio.")
        updateAudioPill()
    }

    private fun showRecordings() {
        val df = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
        actionSheet("Áudios da nota", audio.recordings.mapIndexed { i, r ->
            SheetItem(Icon.TOUCH, "Gravação ${i + 1} · ${AudioNotes.format(r.durationMs)} · ${df.format(Date(r.created))}") {
                actionSheet("Gravação ${i + 1}", listOf(
                    SheetItem(Icon.TOUCH, "Tocar do começo") { playAudio(i, 0L) },
                    SheetItem(Icon.TRASH, "Apagar gravação", danger = true) {
                        confirmDelete("Apagar gravação?", "O áudio será apagado; a escrita continua na nota.") {
                            audio.delete(i)
                            updateAudioPill()
                        }
                    },
                ))
            }
        })
    }

    private fun confirmDelete(title: String, msg: String, onOk: () -> Unit) = Ui.run { confirm(title, msg, "Apagar", onOk) }

    private fun updateAudioPill() {
        val a = audioNotes
        val text = when {
            a == null -> null
            a.isRecording -> "●  Gravando ${AudioNotes.format(a.elapsedMs)}  ·  parar"
            a.isPlaying -> "▶  ${AudioNotes.format(a.elapsedMs)}  ·  parar"
            else -> null
        }
        audioPill.text = text ?: ""
        audioPill.visibility = if (text != null) View.VISIBLE else View.GONE
        main.removeCallbacks(audioTicker)
        if (text != null) main.postDelayed(audioTicker, 500)
    }

    override fun onDestroy() {
        audioNotes?.release()
        main.removeCallbacks(audioTicker)
        super.onDestroy()
    }

    /** Guarda o texto digitado da nota para a busca. */
    private fun indexText(id: String, data: NoteData) {
        val typed = data.elements.filterIsInstance<TextElement>().joinToString("\n") { it.text }
        Library.textFile(id).writeText(typed)
    }

    private fun exportPng() {
        val bitmap = canvasView.renderToBitmap(4096, 4096)
        if (bitmap == null) {
            toast("A nota está vazia.")
            return
        }
        val name = fileName("png")
        Library.io.execute {
            val ok = try {
                writeToMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, name, "image/png",
                    Environment.DIRECTORY_PICTURES + "/RosaNotes") { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                false
            } finally {
                bitmap.recycle()
            }
            main.post { toast(if (ok) "Imagem salva em Imagens/RosaNotes" else "Não foi possível salvar a imagem.") }
        }
    }

    /**
     * PDF vetorial (os traços continuam nítidos). Com folhas, cada folha vira uma página
     * do PDF; na tela infinita, sai uma página do tamanho do conteúdo.
     */
    private fun exportPdf() {
        val doc = buildPdf() ?: return
        val name = fileName("pdf")
        Library.io.execute {
            val ok = try {
                writeToMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, name, "application/pdf",
                    Environment.DIRECTORY_DOWNLOADS + "/RosaNotes") { out -> doc.writeTo(out); true }
            } catch (e: Exception) {
                false
            } finally {
                doc.close()
            }
            main.post { toast(if (ok) "PDF salvo em Downloads/RosaNotes" else "Não foi possível salvar o PDF.") }
        }
    }

    /** O PDF da nota (null, com aviso, se ela estiver vazia). Quem chama fecha o documento. */
    private fun buildPdf(): PdfDocument? {
        val content = canvasView.contentBounds()
        if (content == null) {
            toast("A nota está vazia.")
            return null
        }
        val doc = PdfDocument()
        val pages = canvasView.usedPageRects()
        if (pages.isNotEmpty()) {
            // Folha do tamanho A4 (595 pt de largura em retrato).
            val s = 595f / min(pages[0].width(), pages[0].height())
            pages.forEachIndexed { i, r ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder((r.width() * s).toInt(), (r.height() * s).toInt(), i + 1).create())
                val c = page.canvas
                c.drawColor(canvasView.paperColor)
                c.scale(s, s)
                c.translate(-r.left, -r.top)
                c.clipRect(r)
                canvasView.drawTemplateForExport(c, r)
                canvasView.drawContent(c, r)
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
            c.drawColor(canvasView.paperColor)
            c.scale(s, s)
            c.translate(pad - content.left, pad - content.top)
            canvasView.drawContent(c, null)
            doc.finishPage(page)
        }
        return doc
    }

    /**
     * PDF com as mesmas páginas do PDF importado, no tamanho original, e tudo o que foi
     * escrito por cima de cada uma (a escrita continua vetorial).
     */
    private fun exportAnnotatedPdf() {
        val pages = canvasView.pdfPages()
        if (pages.isEmpty()) return
        val doc = PdfDocument()
        pages.forEachIndexed { i, img ->
            val r = img.rect
            val s = 1f / PdfImporter.WORLD_PER_PT
            val page = doc.startPage(PdfDocument.PageInfo.Builder(max(1, (r.width() * s).toInt()), max(1, (r.height() * s).toInt()), i + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            c.scale(s, s)
            c.translate(-r.left, -r.top)
            c.clipRect(r)
            canvasView.drawContent(c, r)
            doc.finishPage(page)
        }
        val name = fileName("pdf").replace(".pdf", "_anotado.pdf")
        Library.io.execute {
            val ok = try {
                writeToMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, name, "application/pdf",
                    Environment.DIRECTORY_DOWNLOADS + "/RosaNotes") { out -> doc.writeTo(out); true }
            } catch (e: Exception) {
                false
            } finally {
                doc.close()
            }
            main.post { toast(if (ok) "PDF anotado salvo em Downloads/RosaNotes" else "Não foi possível salvar o PDF.") }
        }
    }

    private fun fileName(ext: String): String {
        val safe = note.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "nota" }
        return safe + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext
    }

    private fun writeToMediaStore(
        collection: Uri,
        name: String,
        mime: String,
        relativePath: String,
        write: (java.io.OutputStream) -> Boolean,
    ): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values) ?: return false
        val ok = contentResolver.openOutputStream(uri)?.use(write) ?: false
        if (!ok) {
            contentResolver.delete(uri, null, null)
            return false
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_NOTE = "note"
        const val EXTRA_PDF = "pdf"
        private const val REQ_IMAGE = 41
        private const val REQ_REPLACE = 42
        private const val REQ_PDF = 43
        private const val REQ_MIC = 44
    }
}
