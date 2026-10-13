package com.jgantonio.notasinfinitas

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs

/**
 * Testes de funcionamento: simulam toques de caneta e dedo na tela e conferem o resultado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FunctionalTest {

    private lateinit var app: android.app.Application
    private lateinit var controller: ActivityController<EditorActivity>
    private lateinit var editor: EditorActivity
    private lateinit var canvas: InfiniteCanvasView
    private lateinit var note: NoteInfo
    private var time = 0L

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        app.getSharedPreferences("canetas", 0).edit().clear().commit()
        Library.init(app, reload = true)
        val folder = Library.folders.first()
        note = Library.createNote(folder.id, "Teste")
        openEditor()
    }

    private fun openEditor() {
        controller = Robolectric.buildActivity(EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, note.id)).setup()
        editor = controller.get()
        canvas = editor.canvasForTests
        layout()
    }

    private fun layout() {
        val dm = app.resources.displayMetrics
        val decor = editor.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        ShadowLooper.idleMainLooper()
    }

    // ---- Toques simulados ---------------------------------------------------------------

    private fun event(action: Int, pts: List<Pair<Float, Float>>, tool: Int, down: Long): MotionEvent {
        val props = Array(pts.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = tool } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 0.8f; size = 1f } }
        time += 16
        return MotionEvent.obtain(down, time, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** Arrasta um dedo/caneta pelos pontos (coordenadas da tela do canvas). */
    private fun drag(pts: List<Pair<Float, Float>>, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val down = time
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(pts.first()), tool, down))
        for (p in pts.drop(1)) canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), tool, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(pts.last()), tool, down))
        ShadowLooper.idleMainLooper()
    }

    private fun tap(x: Float, y: Float, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) = drag(listOf(x to y, x to y), tool)

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 30) =
        (0..n).map { x0 + (x1 - x0) * it / n to y0 + (y1 - y0) * it / n }

    private fun pinch(cx: Float, cy: Float, from: Float, to: Float) {
        val f = MotionEvent.TOOL_TYPE_FINGER
        val down = time
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(cx - from to cy), f, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(cx - from to cy, cx + from to cy), f, down))
        for (k in 1..10) {
            val d = from + (to - from) * k / 10
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(cx - d to cy, cx + d to cy), f, down))
        }
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(cx - to to cy, cx + to to cy), f, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(cx - to to cy), f, down))
    }

    private fun items() = canvas.elementsSnapshot()
    private fun strokes() = items().filterIsInstance<StrokeElement>()

    // ---- Testes ----------------------------------------------------------------------------

    @Test
    fun caneta_desenha_e_desfazer_refazer() {
        drag(line(200f, 600f, 800f, 650f))
        assertEquals(1, strokes().size)
        canvas.undo()
        assertEquals(0, items().size)
        canvas.redo()
        assertEquals(1, strokes().size)
    }

    @Test
    fun dedo_move_a_tela_sem_riscar() {
        val before = canvas.screenX(0f)
        drag(line(300f, 800f, 600f, 900f), MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(0, items().size)
        assertTrue("a tela deve ter rolado", abs(canvas.screenX(0f) - before - 300f) < 2f)
    }

    @Test
    fun dedo_escreve_quando_ativado() {
        canvas.fingerDraws = true
        drag(line(200f, 600f, 700f, 600f), MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(1, strokes().size)
    }

    @Test
    fun pinca_da_zoom_e_contador_acompanha() {
        assertEquals(100, canvas.zoomPercent)
        pinch(500f, 900f, 100f, 250f)
        assertTrue("zoom aumentou: ${canvas.zoomPercent}", canvas.zoomPercent in 200..300)
        pinch(500f, 900f, 250f, 103f)
        assertEquals("ímã em 100%", 100, canvas.zoomPercent)
        canvas.zoomTo(0.5f)
        assertEquals(50, canvas.zoomPercent)
    }

    @Test
    fun borracha_traco_inteiro_e_por_area() {
        drag(line(100f, 600f, 900f, 600f))
        drag(line(100f, 900f, 900f, 900f))
        canvas.tool = InfiniteCanvasView.Tool.ERASER
        canvas.eraserArea = false
        drag(line(500f, 500f, 500f, 700f))
        assertEquals("apagou só a primeira linha", 1, strokes().size)
        canvas.eraserArea = true
        drag(line(500f, 850f, 500f, 950f))
        assertEquals("por área divide a linha em duas", 2, strokes().size)
        canvas.undo()
        assertEquals(1, strokes().size)
    }

    @Test
    fun borracha_so_marca_texto() {
        canvas.pen = PenSettings.default(BrushType.HIGHLIGHTER, Color.YELLOW)
        drag(line(100f, 600f, 900f, 600f))
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        drag(line(100f, 610f, 900f, 610f))
        canvas.tool = InfiniteCanvasView.Tool.ERASER
        canvas.eraserHighlighterOnly = true
        drag(line(500f, 500f, 500f, 700f))
        assertEquals(1, strokes().size)
        assertEquals(BrushType.PEN, strokes()[0].type)
    }

    @Test
    fun formas_automaticas_endireitam_linha() {
        canvas.autoShapes = true
        drag((0..40).map { 150f + it * 18f to 700f + (if (it % 2 == 0) 3f else -3f) })
        assertEquals(2, strokes()[0].n)
    }

    @Test
    fun laco_seleciona_move_e_desfaz() {
        drag(line(300f, 700f, 600f, 700f))
        val before = strokes()[0].bounds.left
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        drag(listOf(250f to 600f, 700f to 600f, 700f to 800f, 250f to 800f, 250f to 610f))
        assertEquals(1, canvas.selectedItems.size)
        drag(line(450f, 700f, 650f, 760f, 10))
        val moved = strokes()[0].bounds.left
        assertTrue("moveu ~200px (zoom 100%): ${moved - before}", abs(moved - before - 200f) < 3f)
        canvas.undo()
        assertTrue(abs(strokes()[0].bounds.left - before) < 0.5f)
    }

    @Test
    fun selecao_gira_duplica_ordena_e_exclui() {
        drag(line(300f, 700f, 600f, 700f))
        canvas.selectAll()
        canvas.rotateSelection(90f)
        val b = strokes()[0].bounds
        assertTrue("ficou vertical", b.height() > b.width())
        canvas.duplicateSelection()
        assertEquals(2, strokes().size)
        canvas.reorderSelection(false)
        assertTrue(items()[0] === canvas.selectedItems[0])
        canvas.deleteSelection()
        assertEquals(1, items().size)
    }

    private fun addPhoto(): ImageElement {
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        val b = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        File(dir, "foto.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        canvas.addImage("foto.jpg", 400, 200)
        return canvas.selectedImage!!
    }

    @Test
    fun imagem_toque_seleciona_gira_espelha_recorta_trava() {
        val img = addPhoto()
        canvas.clearSelection()
        tap(canvas.screenX(img.centerX), canvas.screenY(img.centerY))
        assertNotNull("toque seleciona a imagem", canvas.selectedImage)
        canvas.rotateSelection(90f)
        assertEquals(90f, canvas.selectedImage!!.rotation, 0.01f)
        canvas.editImages { it.with(flipH = true, filter = ImageFilter.SEPIA, brightness = 0.3f) }
        assertTrue(canvas.selectedImage!!.flipH)

        canvas.startCrop()
        assertTrue(canvas.inCropMode)
        canvas.setCropShape(InfiniteCanvasView.CropShape.SQUARE)
        canvas.applyCrop()
        val c = canvas.selectedImage!!
        assertTrue("recorte quadrado", abs(c.rect.width() - c.rect.height()) < 1f)
        assertTrue(c.crop.width() < 1f)

        canvas.setLocked(true)
        assertTrue(canvas.selectionLocked)
        canvas.clearSelection()
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        drag(listOf(0f to 0f, 1200f to 0f, 1200f to 2000f, 0f to 2000f, 0f to 10f))
        assertEquals("laço ignora imagem travada", 0, canvas.selectedItems.size)
        tap(canvas.screenX(c.centerX), canvas.screenY(c.centerY))
        assertNotNull("toque ainda seleciona a travada (para destravar)", canvas.selectedImage)
    }

    @Test
    fun ajustes_de_imagem_viram_uma_acao_de_desfazer() {
        addPhoto()
        canvas.beginDraft()
        canvas.updateDraft { it.with(contrast = 0.2f) }
        canvas.updateDraft { it.with(contrast = 0.4f, shadow = true) }
        canvas.finishDraft()
        assertEquals(0.4f, canvas.selectedImage!!.contrast, 0.001f)
        canvas.undo()
        assertEquals(0f, (items().last() as ImageElement).contrast, 0.001f)
    }

    @Test
    fun texto_na_tela_digita_formata_e_salva() {
        canvas.tool = InfiniteCanvasView.Tool.TEXT
        tap(400f, 700f)
        val te = editor.textEditorForTests
        assertTrue("toque com a ferramenta de texto abre a edição", te.isActive)
        val edit = findEdit()
        edit.setText("Olá mundo")
        edit.setSelection(0, 3)
        // Botão "B" da barra com trecho selecionado: só "Olá" fica em negrito
        clickText("B")
        te.finish()
        val t = items().filterIsInstance<TextElement>().single()
        assertEquals("Olá mundo", t.text)
        assertTrue(t.spans.any { it.type == TextSpan.BOLD && it.start == 0 && it.end == 3 })
        assertTrue(!t.bold)

        // Selecionar, girar, aumentar e espelhar
        canvas.select(t)
        canvas.rotateSelection(45f)
        canvas.editTexts { it.with(flipH = true) }
        val r = canvas.selectedText!!
        assertEquals(45f, r.rotation, 0.01f)
        assertTrue(r.flipH)

        // Persistência
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        val back = items().filterIsInstance<TextElement>().single()
        assertEquals(45f, back.rotation, 0.01f)
        assertTrue(back.spans.any { it.type == TextSpan.BOLD })
    }

    @Test
    fun texto_vazio_nao_cria_caixa_e_apagar_texto_remove() {
        canvas.tool = InfiniteCanvasView.Tool.TEXT
        tap(400f, 700f)
        editor.textEditorForTests.finish()
        assertEquals(0, items().size)
        tap(400f, 700f)
        findEdit().setText("x")
        editor.textEditorForTests.finish()
        val t = items().single() as TextElement
        canvas.tool = InfiniteCanvasView.Tool.TEXT
        tap(canvas.screenX(t.centerX), canvas.screenY(t.centerY))
        assertTrue(editor.textEditorForTests.isActive)
        findEdit().setText("")
        editor.textEditorForTests.finish()
        assertEquals(0, items().size)
    }

    @Test
    fun conversao_de_estilos_do_texto() {
        val sb = SpannableStringBuilder("abcdef")
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(Color.RED), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val spans = InlineTextEditor.spansOf(sb, 6)
        assertTrue(spans.any { it.type == TextSpan.BOLD && it.end == 2 })
        assertTrue(spans.any { it.type == TextSpan.COLOR && it.value == Color.RED })
        val t = TextElement("abcdef", 0f, 0f, 40f, Color.BLACK, spans = spans)
        assertTrue(t.boxWidth > 40f && t.boxHeight >= 40f)
    }

    @Test
    fun nota_salva_e_reabre_com_tudo() {
        drag(line(200f, 600f, 800f, 650f))
        addPhoto()
        canvas.addText(TextElement("oi", 10f, 10f, 40f, Color.BLACK))
        canvas.pageStyle = PageStyle.GRID
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        assertEquals(3, items().size)
        assertEquals(PageStyle.GRID, canvas.pageStyle)
        assertTrue("miniatura gerada", Library.thumbFile(note.id).exists())
    }

    @Test
    fun biblioteca_pastas_e_notas() {
        val a = Library.createFolder("A", Color.RED, null)
        val b = Library.createFolder("B", Color.BLUE, a.id)
        val n = Library.createNote(b.id, "nota")
        Library.updateFolder(a, parent = b.id)
        assertNull("pasta não entra dentro da própria subpasta", a.parent)
        Library.updateNote(n, title = "nova", folder = a.id)
        assertEquals("nova", Library.note(n.id)!!.title)
        assertEquals(a.id, Library.note(n.id)!!.folder)
        val dup = Library.duplicateNote(n)
        assertTrue(dup.title.contains("cópia"))
        Library.deleteFolder(a.id)
        assertNull(Library.folder(b.id))
        assertNull(Library.note(n.id))
        assertNull(Library.note(dup.id))
    }

    @Test
    fun pdf_paginas_entram_por_baixo_e_travadas() {
        drag(line(200f, 600f, 800f, 650f))
        val pages = PdfImporter.layout(listOf(PdfImporter.Page("p1.jpg", 595, 842), PdfImporter.Page("p2.jpg", 595, 842)),
            PdfImporter.Layout.HORIZONTAL, 0f, 0f)
        canvas.addPages(pages)
        val list = items()
        assertTrue(list[0] is ImageElement && list[1] is ImageElement && list[2] is StrokeElement)
        assertTrue(list.take(2).all { (it as ImageElement).locked })
        canvas.undo()
        assertEquals(1, items().size)
    }

    @Test
    fun destrava_todas_as_paginas_do_pdf_de_uma_vez() {
        val pages = PdfImporter.layout((1..3).map { PdfImporter.Page("p$it.jpg", 595, 842) }, PdfImporter.Layout.VERTICAL, 0f, 0f)
        canvas.addPages(pages)
        assertEquals(3, canvas.lockedPdfPageCount)
        canvas.setAllPdfPagesLocked(false)
        assertEquals(0, canvas.lockedPdfPageCount)
        assertEquals("ficam selecionadas juntas", 3, canvas.selectedItems.size)
        canvas.undo()
        assertEquals("desfazer trava de novo", 3, canvas.lockedPdfPageCount)
        canvas.setAllPdfPagesLocked(false)
        canvas.setAllPdfPagesLocked(true)
        assertEquals(3, canvas.lockedPdfPageCount)
    }

    // ---- Ajudantes ----------------------------------------------------------------------

    private fun findEdit(): EditText = findAll(editor.window.decorView).filterIsInstance<EditText>().single()

    private fun clickText(text: String) {
        val v = findAll(editor.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString() == text && it !is EditText }
        v.performClick()
    }

    private fun findAll(v: View): List<View> =
        if (v is android.view.ViewGroup) listOf(v) + (0 until v.childCount).flatMap { findAll(v.getChildAt(it)) } else listOf(v)

    // ---- Seleção por retângulo e parcial --------------------------------------------------

    @Test
    fun selecao_retangulo_e_parcial() {
        drag(line(300f, 700f, 600f, 700f))      // A
        drag(line(550f, 950f, 950f, 950f))      // B (fica metade fora do retângulo grande)
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        canvas.selectRect = true
        drag(line(250f, 640f, 650f, 760f, 10))
        assertEquals("retângulo pequeno pega só A", 1, canvas.selectedItems.size)
        canvas.clearSelection()
        drag(line(250f, 640f, 700f, 1000f, 10))
        assertEquals("B só encosta: fica de fora", 1, canvas.selectedItems.size)
        canvas.clearSelection()
        canvas.selectPartial = true
        drag(line(250f, 640f, 700f, 1000f, 10))
        assertEquals("parcial: B entra", 2, canvas.selectedItems.size)
        canvas.clearSelection()
        // Laço que só cruza o meio da linha B (nenhum ponto de B dentro)
        canvas.selectRect = false
        drag(listOf(740f to 900f, 760f to 900f, 760f to 1000f, 740f to 1000f, 741f to 905f))
        assertEquals(1, canvas.selectedItems.size)
    }

    // ---- Marca-texto -------------------------------------------------------------------------

    @Test
    fun marca_texto_linha_reta_e_ponta_chanfrada() {
        canvas.pen = PenSettings.default(BrushType.HIGHLIGHTER, Color.YELLOW).copy(straight = true)
        drag((0..30).map { 200f + it * 20f to 700f + (if (it % 2 == 0) 12f else -12f) + it * 0.5f })
        val s = strokes().single()
        assertEquals("linha reta: só começo e fim", 2, s.n)
        assertEquals("quase horizontal vira horizontal", s.ys[0], s.ys[1], 0.01f)

        canvas.pen = PenSettings.default(BrushType.HIGHLIGHTER, Color.YELLOW).copy(tip = 20)
        drag(line(500f, 900f, 500f, 1400f))
        val v = strokes().last()
        val full = canvas.pen.size * canvas.unit
        assertTrue("na vertical a faixa fica fina: ${v.ws[v.n / 2]} vs $full", v.ws[v.n / 2] < full * 0.4f)

        // Opções salvas e relidas
        val p = PenSettings.decode(canvas.pen.copy(opacity = 30, straight = true).encode())!!
        assertEquals(20, p.tip)
        assertEquals(30, p.opacity)
        assertTrue(p.straight)
        assertEquals(PenSettings(BrushType.PEN, 1, 4f, 100), PenSettings.decode("PEN,1,4.0,100"))
    }

    @Test
    fun marca_texto_tem_botao_proprio() {
        canvas.pen = PenSettings.default(BrushType.FOUNTAIN, Color.BLUE)
        editor.prefsForTests.currentType = BrushType.FOUNTAIN
        editor.highlighterButtonForTests.performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(BrushType.HIGHLIGHTER, canvas.pen.type)
        assertEquals(InfiniteCanvasView.Tool.PEN, canvas.tool)
        // O botão da caneta volta para a última caneta usada (não para o marca-texto).
        editor.penButtonForTests.performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(BrushType.FOUNTAIN, canvas.pen.type)
    }

    @Test
    fun marca_texto_acompanha_altura_da_escrita() {
        // "Palavras" escritas à mão: zigue-zagues de ~90 px de altura na linha y = 700.
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        for (k in 0 until 5) {
            val x0 = 150f + k * 140f
            drag((0..12).map { x0 + it * 8f to 700f + (if (it % 2 == 0) -45f else 45f) })
        }
        val letters = strokes().map { it.bounds.height() }.sorted()
        val h = letters[letters.size / 2]
        canvas.pen = PenSettings.default(BrushType.HIGHLIGHTER, Color.YELLOW).copy(size = 4f)
        drag(line(120f, 700f, 900f, 700f))
        val hl = strokes().last()
        assertEquals(BrushType.HIGHLIGHTER, hl.type)
        val w = hl.ws[hl.n / 2]
        assertTrue("faixa ($w) cobre a escrita ($h)", w > h * 0.8f && w < h * 1.5f)

        // Longe da escrita, vale o tamanho escolhido.
        drag(line(120f, 1300f, 900f, 1300f))
        assertEquals(4f * canvas.unit, strokes().last().ws[15], 0.5f)

        // Desligado, também vale o tamanho escolhido.
        canvas.highlighterFitsWriting = false
        drag(line(120f, 700f, 900f, 700f))
        assertEquals(4f * canvas.unit, strokes().last().ws[15], 0.5f)
    }

    @Test
    fun backup_salva_e_restaura_pastas_notas_e_capas() {
        val folder = Library.createFolder("Faculdade", Library.folderColors[2], null)
        Library.coverFile(folder.id).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        drag(line(200f, 700f, 900f, 700f))
        controller.pause()
        Library.io.submit {}.get()
        val zip = File(app.cacheDir, "teste-backup.zip")
        Backup.write(app, android.net.Uri.fromFile(zip))
        assertTrue(zip.length() > 0)

        Library.deleteFolder(folder.id)
        Library.io.submit {}.get()
        assertNull(Library.folder(folder.id))

        Backup.restore(app, android.net.Uri.fromFile(zip))
        assertNotNull(Library.folder(folder.id))
        assertNotNull(Library.note(note.id))
        assertTrue(Library.noteFile(note.id).exists())
        assertEquals(3, Library.coverFile(folder.id).readBytes().size)
    }

    @Test
    fun laser_e_tinta_que_some_nao_ficam_na_nota() {
        editor.setEphemeral(InfiniteCanvasView.Ephemeral.LASER)
        drag(line(200f, 700f, 900f, 700f))
        assertEquals(0, strokes().size)
        assertEquals(1, canvas.fadingCountForTests)
        editor.setEphemeral(InfiniteCanvasView.Ephemeral.VANISH)
        drag(line(200f, 800f, 900f, 800f))
        assertEquals(0, strokes().size)
        editor.setEphemeral(null)
        drag(line(200f, 900f, 900f, 900f))
        assertEquals(1, strokes().size)
    }

    @Test
    fun modelo_cornell_salva_e_exporta() {
        canvas.setPageLayout(true, false, false, true, 1)
        canvas.pageStyle = PageStyle.CORNELL
        drag(line(200f, 900f, 700f, 900f))
        val bmp = canvas.renderToBitmap(600, 800)
        assertNotNull(bmp)
        val data = canvas.snapshot()
        assertEquals(PageStyle.CORNELL, data.style)
    }

    @Test
    fun etiquetas_favoritas_e_fixadas_ficam_salvas() {
        Library.setTags(note, listOf(" #Prova", "anatomia", "prova", ""))
        Library.setPinned(note, true)
        Library.setFavorite(note, true)
        Library.init(app, reload = true)
        val n = Library.note(note.id)!!
        assertEquals(listOf("Prova", "anatomia"), n.tags)
        assertTrue(n.pinned && n.favorite)
        val other = Library.createNote(n.folder, "Outra")
        assertEquals("fixada vem primeiro", n.id, Library.notesIn(n.folder).first().id)
        assertTrue(other.tags.isEmpty())
    }

    @Test
    fun texto_digitado_vai_para_a_busca() {
        canvas.addText(TextElement("Mitocôndria", 100f, 100f, 30f, Color.BLACK))
        controller.pause()
        Library.io.submit {}.get()
        assertTrue(Library.textFile(note.id).readText().contains("Mitocôndria"))
    }

    @Test
    fun lapis_desenha_com_textura() {
        canvas.pen = PenSettings.default(BrushType.PENCIL, Color.BLACK).copy(size = 12f)
        drag(line(200f, 700f, 900f, 700f))
        assertEquals(1, strokes().size)
        val bmp = canvas.renderToBitmap(800, 800)!!
        var ink = 0
        for (x in 0 until bmp.width step 2) for (y in 0 until bmp.height step 2) {
            if (Color.red(bmp.getPixel(x, y)) < 160) ink++
        }
        assertTrue("o lápis deixa marca ($ink pixels)", ink > 50)
    }

    // ---- Barra de ferramentas ----------------------------------------------------------------

    private fun dragGrip(dx: Float, dy: Float) {
        val grip = editor.dockGripForTests
        val down = time
        val x = 10f
        val y = 10f
        grip.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(x to y), MotionEvent.TOOL_TYPE_FINGER, down))
        for (k in 1..8) grip.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(x + dx * k / 8 to y + dy * k / 8), MotionEvent.TOOL_TYPE_FINGER, down))
        grip.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(x + dx to y + dy), MotionEvent.TOOL_TYPE_FINGER, down))
        ShadowLooper.idleMainLooper()
        layout()
    }

    @Test
    fun barra_arrasta_para_esquerda_fica_em_pe_com_3_cores() {
        dragGrip(-2000f, 600f)
        assertEquals("left", editor.prefsForTests.dockMode)
        assertEquals(android.widget.LinearLayout.VERTICAL, editor.dockForTests.orientation)
        val dots = (0 until editor.quickColorsForTests.childCount).map { editor.quickColorsForTests.getChildAt(it) }.filterIsInstance<ColorDot>()
        assertEquals(3, dots.size)
        dots[1].performClick()
        assertEquals(editor.prefsForTests.quickColors[1], canvas.pen.color)
        assertTrue("barra encostada na esquerda", editor.dockForTests.left < 40)

        // Arrastar para o meio: volta a ficar deitada, solta onde foi largada
        dragGrip(500f, 0f)
        assertEquals("free", editor.prefsForTests.dockMode)
        assertEquals(android.widget.LinearLayout.HORIZONTAL, editor.dockForTests.orientation)
        // Deitada também mostra as 3 cores, e a barra cabe na largura do celular.
        val flat = (0 until editor.quickColorsForTests.childCount).map { editor.quickColorsForTests.getChildAt(it) }.filterIsInstance<ColorDot>()
        assertEquals(3, flat.size)
        assertEquals(View.VISIBLE, editor.quickColorsForTests.visibility)
        assertTrue("barra cabe na tela: ${editor.dockForTests.width}", editor.dockForTests.width <= app.resources.displayMetrics.widthPixels)
    }

    // ---- Folhas ----------------------------------------------------------------------------

    @Test
    fun folhas_infinitas_fixas_e_salvas() {
        canvas.setPageLayout(true, false, false, true, 1)
        assertTrue(canvas.pageLayout.hasPages)
        assertEquals(1, canvas.pageCount)
        drag(line(300f, 700f, 600f, 700f))
        assertEquals("sempre uma folha em branco no fim", 2, canvas.pageCount)
        val p1 = canvas.pageLayout.pageRect(1)
        canvas.addText(TextElement("folha 2", p1.left + 20f, p1.top + 20f, 40f, Color.BLACK))
        assertEquals(3, canvas.pageCount)
        assertEquals(2, canvas.usedPageRects().size)

        canvas.setPageLayout(true, false, false, false, 2)
        assertEquals(2, canvas.pageCount)

        canvas.setPageLayout(true, true, true, false, 2)
        val l = canvas.pageLayout
        assertTrue("paisagem: mais larga que alta", l.w > l.h)
        assertTrue("lado a lado", canvas.pageLayout.pageRect(1).left > canvas.pageLayout.pageRect(0).right)

        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        val r = canvas.pageLayout
        assertTrue(r.hasPages && r.horizontal && r.landscape && !r.endless)
        assertEquals(2, canvas.pageCount)
        assertEquals(2, items().size)

        canvas.setPageLayout(false, false, false, true, 1)
        assertEquals(0, canvas.pageCount)
    }

    // ---- Tons, tema noturno e página -------------------------------------------------------

    @Test
    fun tons_da_cor_mantem_o_matiz() {
        val base = Color.parseColor("#1F5FD1")
        val t = ColorPicker.tones(base)
        assertEquals(9, t.size)
        assertTrue("do claro ao escuro", Color.luminance(t.first()) > Color.luminance(t.last()))
        val h0 = FloatArray(3).also { Color.colorToHSV(base, it) }
        for (c in t.subList(1, 8)) {
            val h = FloatArray(3).also { Color.colorToHSV(c, it) }
            assertTrue("mesmo matiz: ${h[0]} vs ${h0[0]}", abs(h[0] - h0[0]) < 6f)
        }
        // Cinza vira escala de cinzas
        for (c in ColorPicker.tones(Color.GRAY)) assertTrue(abs(Color.red(c) - Color.blue(c)) <= 1)
    }

    @Test
    fun tema_noturno_inverte_tinta_e_exporta_cores_reais() {
        assertEquals(Color.WHITE, Night.map(Color.BLACK))
        val red = Night.map(Color.parseColor("#D7263D"))
        assertTrue("vermelho continua avermelhado", Color.red(red) > Color.green(red) && Color.red(red) > Color.blue(red))
        try {
            Ui.setThemeChoice(app, "dark")
            controller.pause().stop().destroy()
            openEditor()
            assertTrue(Ui.isDark)
            assertTrue("papel branco aparece escuro", canvas.nightInk)
            drag(line(200f, 700f, 900f, 700f))
            val bmp = canvas.renderToBitmap(800, 800)!!
            assertEquals("exportação com o papel de verdade", Color.WHITE, bmp.getPixel(2, 2))
        } finally {
            Ui.setThemeChoice(app, "light")
        }
        assertTrue(!Ui.isDark)
    }

    @Test
    fun folha_horizontal_em_rolagem_vertical() {
        // Orientação (folha deitada) e apresentação (uma embaixo da outra) são independentes.
        canvas.setPageLayout(true, false, true, false, 3)
        val l = canvas.pageLayout
        assertTrue("folha deitada", l.w > l.h)
        assertTrue("uma embaixo da outra", l.pageRect(1).top > l.pageRect(0).bottom)
        assertEquals(l.pageRect(0).left, l.pageRect(1).left, 0.01f)
    }

    @Test
    fun aviso_de_versao_nova_compara_o_build_da_tag() {
        assertEquals(123, Updates.buildOf("rosanotes-v0.11.0-build123"))
        assertNull(Updates.buildOf("demo-frames"))
        assertNull(Updates.buildOf(null))
        assertTrue(Updates.isNewer(124, 123))
        assertTrue(!Updates.isNewer(123, 123))
        assertTrue("mais antiga não avisa", !Updates.isNewer(100, 123))
        assertTrue("build local nunca avisa", !Updates.isNewer(124, 0))
        assertTrue("sem resposta não avisa", !Updates.isNewer(null, 123))
    }

    @Test
    fun importa_nota_do_samsung_notes_com_escrita_editavel() {
        val sn = javaClass.classLoader!!.getResourceAsStream("sdocx/eg-walker.sdocx")!!.use { SamsungNotesImporter.read(it) }
        assertEquals("Eg walker O(n^2) case", sn.title)
        assertEquals(1, sn.pages.size)
        assertEquals(0, sn.skipped)
        val page = sn.pages[0]
        assertEquals(1440f, page.w, 0.01f)
        assertEquals(47, page.strokes.size)
        // Primeiro traço: caixa gravada no arquivo (498, 403)-(603, 605), caneta-tinteiro azul.
        val first = page.strokes[0]
        assertEquals(498f, first.xs.minOrNull()!!, 1f)
        assertEquals(403f, first.ys.minOrNull()!!, 1f)
        assertEquals(603f, first.xs.maxOrNull()!!, 1f)
        assertEquals(605f, first.ys.maxOrNull()!!, 1f)
        assertEquals(0xFF335FFF.toInt(), first.color)
        assertEquals(BrushType.FOUNTAIN, SamsungNotesImporter.brushFor(first.pen, first.color))
        for (st in page.strokes) {
            assertTrue("traço dentro da página", st.xs.all { it in 0f..page.w } && st.ys.all { it in 0f..page.h })
        }

        // Vira nota do app: abre no editor com os 47 traços, e salva/recarrega igual.
        val data = SamsungNotesImporter.toNoteData(sn, Library.assetsDir(note.id), 400f * app.resources.displayMetrics.density, 60f)
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        NoteStorage.save(Library.noteFile(note.id), data)
        openEditor()
        assertEquals(47, strokes().size)
        assertTrue(strokes().all { it.type == BrushType.FOUNTAIN })
        System.getProperty("shots.dir")?.let { dir ->
            canvas.renderToBitmap(1080, 1080)?.let { b ->
                File(dir, "importado-samsung-notes.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        assertNotNull(SamsungNotesImporter.thumbnail(data))

        // Segundo arquivo: título lido do note.note e três traços longos.
        val t = javaClass.classLoader!!.getResourceAsStream("sdocx/titulo.sdocx")!!.use { SamsungNotesImporter.read(it) }
        assertEquals("ThisIsTheTitle", t.title)
        assertEquals(3, t.pages[0].strokes.size)
    }

    @Test
    fun arquivo_que_nao_e_do_samsung_notes_avisa() {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("qualquer.txt")); z.write(1) }
        }.toByteArray()
        try {
            SamsungNotesImporter.read(bytes.inputStream())
            throw AssertionError("deveria recusar")
        } catch (e: SamsungNotesImporter.UnsupportedFile) {
            assertTrue(e.message!!.contains("Samsung Notes"))
        }
    }

    @Test
    fun girar_selecao_aceita_angulos_pequenos() {
        // Saindo de 0°: inclinar 2° funciona (antes o ímã voltava para 0°).
        assertEquals(2f, InfiniteCanvasView.snapRotation(0f, 2f), 0.001f)
        assertEquals(-1f, InfiniteCanvasView.snapRotation(0f, -1f), 0.001f)
        assertEquals(15f, InfiniteCanvasView.snapRotation(0f, 15f), 0.001f)
        // Algo torto (5°) gruda em 0° ao chegar perto, e em 90° também.
        assertEquals(-5f, InfiniteCanvasView.snapRotation(5f, -3.5f), 0.001f)
        assertEquals(85f, InfiniteCanvasView.snapRotation(5f, 83f), 0.001f)
        // Saindo de 90°: girar 1° a mais também funciona.
        assertEquals(1f, InfiniteCanvasView.snapRotation(90f, 1f), 0.001f)
    }

    @Test
    fun marca_texto_fica_por_baixo_da_escrita() {
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK).copy(size = 12f)
        drag(line(200f, 700f, 800f, 700f))
        canvas.pen = PenSettings.default(BrushType.HIGHLIGHTER, Color.YELLOW).copy(size = 30f)
        drag(line(150f, 700f, 850f, 700f))
        assertEquals(2, strokes().size)
        // Desenha a nota como na tela e confere os pixels.
        val dm = app.resources.displayMetrics
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(Color.WHITE)
        val v = canvas.viewForTests
        c.translate(v[0], v[1])
        c.scale(v[2], v[2])
        canvas.drawContent(c, null)
        val ink = bmp.getPixel(500, 700)
        assertTrue("a escrita continua preta sob o marca-texto: ${Integer.toHexString(ink)}",
            Color.red(ink) < 40 && Color.green(ink) < 40 && Color.blue(ink) < 40)
        val hl = bmp.getPixel(170, 700)
        assertTrue("o marca-texto aparece fora da escrita: ${Integer.toHexString(hl)}", Color.red(hl) > 200 && Color.blue(hl) < 200)
    }

    @Test
    fun segurar_a_caneta_parada_endireita_a_linha() {
        val tool = MotionEvent.TOOL_TYPE_STYLUS
        fun stroke(pts: List<Pair<Float, Float>>, hold: Boolean) {
            val down = time
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(pts.first()), tool, down))
            for (p in pts.drop(1)) canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), tool, down))
            if (hold) ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS)
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(pts.last()), tool, down))
            ShadowLooper.idleMainLooper()
        }
        val wavy = (0..40).map { 150f + it * 18f to 700f + (if (it % 2 == 0) 4f else -4f) }
        stroke(wavy, hold = false)
        assertTrue("sem segurar, o traço fica como foi feito", strokes()[0].n > 2)

        stroke(wavy.map { it.first to it.second + 200f }, hold = true)
        val straight = strokes()[1]
        assertEquals("segurando, vira reta", 2, straight.n)
        assertEquals(straight.ys[0], straight.ys[1], 0.01f)

        // Curva (meio círculo) não vira reta mesmo segurando.
        val arc = (0..40).map {
            val a = Math.PI * it / 40
            (500 + 250 * Math.cos(a)).toFloat() to (1300 - 250 * Math.sin(a)).toFloat()
        }
        stroke(arc, hold = true)
        assertTrue(strokes()[2].n > 2)
    }

    @Test
    fun pagina_de_pdf_travada_tem_as_ferramentas_de_imagem() {
        val page = ImageElement("pdf_teste_p1.jpg", android.graphics.RectF(0f, 0f, 900f, 1270f), shadow = true, locked = true, pdfPage = 1)
        canvas.addPages(listOf(page))
        val v = canvas.viewForTests
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        tap(450f * v[2] + v[0], 635f * v[2] + v[1])
        val sel = canvas.selectedImage
        assertNotNull("toque seleciona a página travada", sel)
        canvas.startCrop()
        assertTrue("recortar funciona com a página travada", canvas.inCropMode)
        canvas.cancelCrop()
        canvas.rotateSelection(90f)
        val rotated = canvas.selectedImage!!
        assertEquals(90f, rotated.rotation, 0.01f)
        assertTrue("continua travada", rotated.locked)
        assertEquals(1, rotated.pdfPage)
    }

    @Test
    fun tela_cheia_deixa_so_a_barra_de_ferramentas() {
        editor.setFullscreen(true)
        layout()
        assertEquals(View.GONE, editor.topBarForTests.visibility)
        assertNotNull(editor.fullscreenBoxForTests)
        assertEquals(View.VISIBLE, editor.dockForTests.visibility)
        assertTrue(editor.prefsForTests.fullscreen)
        // O voltar sai da tela cheia, sem fechar a nota.
        @Suppress("DEPRECATION")
        editor.onBackPressed()
        assertEquals(View.VISIBLE, editor.topBarForTests.visibility)
        assertNull(editor.fullscreenBoxForTests)
        assertTrue(!editor.isFinishing)
        assertTrue(!editor.prefsForTests.fullscreen)
    }

    @Test
    fun tela_girada_desenha_no_lugar_certo_e_fica_salva() {
        canvas.setViewRotation(90f)
        // Traço da esquerda para a direita na tela: com a tela girada 90°, no mundo ele é vertical.
        drag(line(300f, 900f, 700f, 900f))
        val s = strokes()[0]
        val dx = s.xs.maxOrNull()!! - s.xs.minOrNull()!!
        val dy = s.ys.maxOrNull()!! - s.ys.minOrNull()!!
        assertTrue("vertical no mundo: dx=$dx dy=$dy", dy > 5 * dx && dy > 100f)
        canvas.setViewRotation(0f)
        drag(line(300f, 1100f, 700f, 1100f))
        val h = strokes()[1]
        assertTrue("reta de novo na horizontal", h.xs.maxOrNull()!! - h.xs.minOrNull()!! > 5 * (h.ys.maxOrNull()!! - h.ys.minOrNull()!!))

        // O ângulo fica salvo na nota.
        canvas.setViewRotation(30f)
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        assertEquals(30f, canvas.viewRotation, 0.01f)
    }

    @Test
    fun toque_na_alca_abre_a_bandeja_da_barra_ao_lado() {
        editor.openDockTray()
        assertEquals("dock", editor.openTrayForTests)
        editor.closeTrayForTests()
        assertNull(editor.openTrayForTests)
    }

    @Test
    fun copiar_numa_nota_e_colar_em_outra_com_imagem() {
        drag(line(200f, 700f, 800f, 700f))
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).also { b ->
            File(dir, "foto.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        canvas.addImage("foto.png", 40, 30)
        canvas.selectAll()
        assertEquals(2, canvas.selectedItems.size)
        Clipboard.copy(app, canvas.selectedItems, dir)
        assertTrue(Clipboard.hasContent(app))

        // Outra nota: cola os dois itens, com a imagem copiada para a pasta dela.
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        note = Library.createNote(Library.folders.first().id, "Destino")
        openEditor()
        val pasted = Clipboard.paste(app, Library.assetsDir(note.id))
        canvas.pasteElements(pasted)
        assertEquals(1, strokes().size)
        val img = items().filterIsInstance<ImageElement>().single()
        assertTrue("imagem copiada para a nota nova", File(Library.assetsDir(note.id), img.file).exists())
        assertEquals("colados ficam selecionados", 2, canvas.selectedItems.size)
    }

    @Test
    fun print_da_area_recorta_pelo_laco() {
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK).copy(size = 20f)
        drag(line(200f, 800f, 900f, 800f))
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        // Laço em triângulo: só o que está dentro sai no print, o resto fica transparente.
        val tri = listOf(150f to 600f, 950f to 600f, 550f to 1100f, 150f to 600f)
        drag((0 until tri.size - 1).flatMap { k -> line(tri[k].first, tri[k].second, tri[k + 1].first, tri[k + 1].second, 20) })
        assertTrue("área marcada", canvas.hasRegion)
        val bmp = canvas.renderRegion()!!
        assertTrue(bmp.width > 100 && bmp.height > 100)
        assertEquals("canto de baixo à esquerda fica fora do triângulo", 0, Color.alpha(bmp.getPixel(2, bmp.height - 3)))
        assertEquals("centro de cima é papel", 255, Color.alpha(bmp.getPixel(bmp.width / 2, 4)))
        Clipboard.copyImage(app, bmp, 100f, 60f)
        val pasted = Clipboard.paste(app, Library.assetsDir(note.id))
        assertTrue(pasted.single() is ImageElement)

        // Retângulo sobre uma área vazia também marca (sem itens selecionados).
        canvas.clearSelection()
        canvas.selectRect = true
        drag(line(100f, 1300f, 600f, 1600f))
        assertTrue(canvas.hasRegion)
        assertEquals(0, canvas.selectedItems.size)
    }

    @Test
    fun arquivo_para_compartilhar_sai_pelo_provider() {
        Robolectric.setupContentProvider(ShareProvider::class.java, "com.jgantonio.notasinfinitas.share")
        val f = File(ShareProvider.dir(app), "nota.pdf").apply { writeText("%PDF-teste") }
        val uri = ShareProvider.uriFor(app, f)
        assertEquals("application/pdf", app.contentResolver.getType(uri))
        val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals("%PDF-teste", text)
    }

    @Test
    fun lixeira_guarda_restaura_e_limpa_depois_de_30_dias() {
        val folder = Library.folder(note.folder)!!
        Library.trashNote(note.id)
        assertTrue(Library.notes.none { it.id == note.id })
        assertEquals(1, Library.trash.size)
        // Sobrevive a reabrir o app.
        Library.init(app, reload = true)
        assertEquals(1, Library.trash.size)
        val back = Library.restore(Library.trash.single())
        assertEquals(folder.id, back.folder)
        assertTrue(Library.notes.any { it.id == note.id })

        // Pasta apagada: as notas vão para a lixeira e voltam numa pasta com o mesmo nome.
        Library.deleteFolder(folder.id)
        assertTrue(Library.trash.any { it.note.id == note.id })
        val again = Library.restore(Library.trash.first { it.note.id == note.id })
        assertEquals(folder.name, Library.folder(again.folder)!!.name)

        Library.trashNote(note.id)
        Library.purgeOldTrash(System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000)
        assertTrue(Library.trash.isEmpty())
    }

    @Test
    fun folhas_reordenar_inserir_duplicar_e_excluir() {
        canvas.setPageLayout(true, false, false, false, 3)
        val l = canvas.pageLayout
        fun mark(i: Int, t: String) = canvas.addText(TextElement(t, l.pageRect(i).centerX(), l.pageRect(i).centerY(), 30f, Color.BLACK))
        mark(0, "A"); mark(1, "B"); mark(2, "C")
        fun pageOfText(t: String): Int {
            val e = items().filterIsInstance<TextElement>().first { it.text == t }
            return l.indexAt(e.bounds.centerX(), e.bounds.centerY())
        }
        canvas.swapPages(0, 1)
        assertEquals(1, pageOfText("A"))
        assertEquals(0, pageOfText("B"))
        canvas.insertBlankPageAfter(0)
        assertEquals(4, canvas.pageCount)
        assertEquals(2, pageOfText("A"))
        assertEquals(3, pageOfText("C"))
        canvas.duplicatePage(2)
        assertEquals(5, canvas.pageCount)
        assertEquals(2, items().filterIsInstance<TextElement>().count { it.text == "A" })
        canvas.deletePage(1)
        assertEquals(4, canvas.pageCount)
        assertEquals(1, items().filterIsInstance<TextElement>().map { it.text }.count { it == "B" })
        assertNotNull(canvas.renderPage(0, 120))
    }

    @Test
    fun balde_pinta_forma_fechada_e_conta_gotas_pega_a_cor() {
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        val circle = (0..60).map {
            val a = 2 * Math.PI * it / 60
            (500 + 180 * Math.cos(a)).toFloat() to (1000 + 180 * Math.sin(a)).toFloat()
        }
        drag(circle)
        val v = canvas.viewForTests
        fun wx(x: Float) = (x - v[0]) / v[2]
        fun wy(y: Float) = (y - v[1]) / v[2]
        assertTrue(!canvas.fillAt(wx(900f), wy(1500f), Color.RED, 255))
        assertTrue(canvas.fillAt(wx(500f), wy(1000f), Color.RED, 255))
        assertEquals(1, items().filterIsInstance<FillElement>().size)
        // O preenchimento fica por baixo e aparece na tela: o conta-gotas pega o vermelho.
        layout()
        val c = canvas.colorAt(500f, 1000f)
        assertTrue("vermelho: ${Integer.toHexString(c)}", Color.red(c) > 200 && Color.green(c) < 60 && Color.blue(c) < 60)
        // Salva e recarrega o preenchimento (formato v7).
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        assertEquals(1, items().filterIsInstance<FillElement>().size)
    }

    @Test
    fun fita_esconde_e_revela_com_um_toque() {
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        drag(line(250f, 800f, 750f, 800f))
        canvas.tool = InfiniteCanvasView.Tool.TAPE
        drag(line(200f, 800f, 800f, 806f))
        val tape = items().filterIsInstance<TapeElement>().single()
        assertTrue("começa escondendo", !tape.revealed)
        assertEquals("reta na horizontal", tape.y0, tape.y1, 0.01f)
        // Toque com a caneta na fita: revela, sem deixar ponto.
        canvas.tool = InfiniteCanvasView.Tool.PEN
        tap(500f, 800f)
        assertTrue(items().filterIsInstance<TapeElement>().single().revealed)
        assertEquals(1, strokes().size)
        tap(500f, 800f)
        assertTrue(!items().filterIsInstance<TapeElement>().single().revealed)
        canvas.setAllTapes(revealed = true)
        assertEquals(0, canvas.hiddenTapeCount)
        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        assertTrue(items().filterIsInstance<TapeElement>().single().revealed)
    }

    @Test
    fun marcadores_e_cartoes_ficam_salvos_na_nota() {
        canvas.setViewForTests(0f, 0f, 1f)
        canvas.addBookmark("Aula 3 – Cardio")
        val b = canvas.bookmarks.single()
        canvas.setViewForTests(-3000f, -5000f, 2f)
        canvas.goTo(b)
        val v = canvas.viewForTests
        assertEquals(1f, v[2], 0.001f)

        drag(line(200f, 700f, 800f, 700f))
        canvas.selectAll()
        val q = canvas.selectionArea()!!
        canvas.addCard(q, RectF(q).apply { offset(0f, 400f) })
        assertNotNull(canvas.renderArea(q, 300))

        controller.pause().stop().destroy()
        Library.io.submit {}.get()
        openEditor()
        assertEquals("Aula 3 – Cardio", canvas.bookmarks.single().title)
        assertEquals(1, canvas.cards.size)
        val c = canvas.cards.single()
        canvas.updateCard(c, c.with(2, 12345L))
        assertEquals(2, canvas.cards.single().box)
    }

    private fun pdfPageForTests(): ImageElement {
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        Bitmap.createBitmap(90, 127, Bitmap.Config.ARGB_8888).also { b ->
            b.eraseColor(Color.rgb(30, 120, 220))
            File(dir, "pdf_t_p1.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }
        val page = ImageElement("pdf_t_p1.jpg", RectF(0f, 0f, 900f, 1270f), shadow = true, locked = true, pdfPage = 1)
        canvas.addPages(listOf(page))
        return page
    }

    @Test
    fun copiar_area_sobre_pdf_copia_so_o_pedaco_e_a_escrita_editavel() {
        val page = pdfPageForTests()
        val v = canvas.viewForTests
        fun sx(x: Float) = x * v[2] + v[0]
        fun sy(y: Float) = y * v[2] + v[1]
        canvas.pen = PenSettings.default(BrushType.PEN, Color.BLACK)
        drag(line(sx(200f), sy(300f), sx(700f), sy(300f)))
        // Retângulo só na metade de cima da página.
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        canvas.selectRect = true
        drag(line(sx(100f), sy(150f), sx(800f), sy(500f)))
        assertTrue(canvas.hasRegion)
        assertTrue("a página não entra inteira", canvas.selectedItems.none { it is ImageElement })
        assertEquals(1, canvas.selectedItems.count { it is StrokeElement })
        assertTrue(canvas.regionTouchesImages)
        val piece = canvas.renderRegionImages()!!
        assertTrue("recorte do tamanho da área", piece.second.height() < page.rect.height() * 0.4f)
        Clipboard.copy(app, canvas.selectedItems.filter { it !is ImageElement }, Library.assetsDir(note.id), piece)
        val pasted = Clipboard.paste(app, Library.assetsDir(note.id))
        assertEquals(2, pasted.size)
        val img = pasted.filterIsInstance<ImageElement>().single()
        assertEquals(piece.second.width(), img.rect.width(), 0.5f)
        assertTrue("a escrita continua editável", pasted.any { it is StrokeElement })
        assertEquals(page, canvas.pdfPageUnderRegion())
    }

    @Test
    fun copiar_pagina_inteira_leva_a_escrita_de_cima() {
        val page = pdfPageForTests()
        val v = canvas.viewForTests
        drag(line(200f * v[2] + v[0], 300f * v[2] + v[1], 700f * v[2] + v[0], 300f * v[2] + v[1]))
        val all = canvas.elementsOnImage(items().filterIsInstance<ImageElement>().single())
        assertEquals(2, all.size)
        Clipboard.copy(app, all, Library.assetsDir(note.id))
        val pasted = Clipboard.paste(app, Library.assetsDir(note.id))
        val img = pasted.filterIsInstance<ImageElement>().single()
        assertEquals(page.rect.width(), img.rect.width(), 0.5f)
        assertTrue(!img.locked)
        assertEquals(0, img.pdfPage)
        assertEquals(1, pasted.count { it is StrokeElement })

        // Folhas: copiar a folha leva o que está nela.
        canvas.setPageLayout(true, false, false, false, 2)
        assertTrue(canvas.elementsOnPage(0).isNotEmpty() || canvas.elementsOnPage(1).isNotEmpty())
    }

    @Test
    fun formas_prontas_viram_tracos_editaveis() {
        assertEquals(5, Shapes.rect(100f, 50f).single().xs.size)
        assertEquals("moldura + 2 + 3", 1 + 2 + 3, Shapes.table(3, 4, 400f, 300f).size)
        assertEquals(2, Shapes.arrow(200f, double = false).size)
        assertEquals(3, Shapes.arrow(200f, double = true).size)
        editor.insertShape(Shapes.Kind.TABLE, 2, 2)
        assertEquals(3, strokes().size)
        assertEquals("já vem selecionada", 3, canvas.selectedItems.size)
        assertTrue(strokes().all { it.uniform })
    }

    @Test
    fun regua_deixa_o_traco_reto_na_borda() {
        val d = app.resources.displayMetrics.density
        canvas.setRulerForTests(540f, 1000f, 0f)
        // Começa logo abaixo da borda de baixo (meia largura = 32dp) e ondula.
        val y0 = 1000f + 32f * d + 8f * d
        drag((0..30).map { 200f + it * 20f to y0 + (if (it % 2 == 0) 9f else -9f) })
        val s = strokes().single()
        val ys = s.ys
        assertTrue("reto na régua: ${ys.minOrNull()}..${ys.maxOrNull()}", ys.maxOrNull()!! - ys.minOrNull()!! < 0.5f)
        // Longe da régua, o traço fica como foi feito.
        drag((0..30).map { 200f + it * 20f to 1700f + (if (it % 2 == 0) 9f else -9f) })
        val free = strokes()[1]
        assertTrue(free.ys.maxOrNull()!! - free.ys.minOrNull()!! > 3f)
        // Um dedo na régua move a régua, não a nota.
        val before = canvas.viewForTests.toList()
        drag(line(540f, 1000f, 640f, 1100f), MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(before, canvas.viewForTests.toList())
    }
}
