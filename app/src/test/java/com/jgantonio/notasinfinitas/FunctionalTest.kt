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
        // Os testes antigos simulam toques de dedo logo depois da caneta: rejeição de palma
        // desligada aqui; os testes dela ligam explicitamente.
        app.getSharedPreferences("canetas", 0).edit().clear().putBoolean("palm_reject", false).putBoolean("doc_scroll_lock", false).commit()
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

    /** Google Drive falso, em memória. */
    private class FakeDrive : DriveApi {
        class F(var name: String, var parent: String, val mime: String, val props: Map<String, String>, var bytes: ByteArray)
        val files = LinkedHashMap<String, F>()
        var creates = 0
        var updates = 0
        var deletes = 0
        private var next = 0
        override fun folderId() = "raiz"
        override fun createFolder(parent: String, name: String, props: Map<String, String>): String {
            val id = "d${next++}"
            files[id] = F(name, parent, "folder", props, ByteArray(0))
            return id
        }
        override fun create(parent: String, name: String, mime: String, props: Map<String, String>, bytes: ByteArray): String {
            creates++
            val id = "f${next++}"
            files[id] = F(name, parent, mime, props, bytes)
            return id
        }
        override fun update(id: String, bytes: ByteArray) {
            updates++
            (files[id] ?: throw java.io.IOException("404")).bytes = bytes
        }
        override fun move(id: String, name: String, newParent: String, oldParent: String) {
            val f = files[id] ?: throw java.io.IOException("404")
            f.name = name
            f.parent = newParent
        }
        override fun find(key: String, value: String) =
            files.filter { it.value.props[key] == value }.map { (id, f) -> DriveApi.RemoteFile(id, f.name, f.props) }
        override fun children(folder: String) =
            files.filter { it.value.parent == folder }.map { (id, f) -> DriveApi.RemoteFile(id, f.name, f.props) }
        override fun download(id: String) = files.getValue(id).bytes
        override fun delete(id: String) {
            deletes++
            files.remove(id) ?: throw java.io.IOException("404")
            files.entries.removeIf { it.value.parent == id }
        }
        fun named(n: String) = files.values.filter { it.name == n }
        fun pathOf(f: F): String {
            val parts = ArrayList<String>()
            var p = f.parent
            while (p != "raiz") { val d = files.getValue(p); parts.add(0, d.name); p = d.parent }
            return (parts + f.name).joinToString("/")
        }
    }

    @Test
    fun backup_no_drive_vai_nota_por_nota_em_pdf_e_editavel() {
        drag(line(200f, 700f, 800f, 700f))
        controller.pause()
        Library.io.submit {}.get()
        val drive = FakeDrive()
        // No teste não há PdfDocument de verdade: um PDF de mentira, só para notas com conteúdo.
        DriveSync.pdfMaker = { _, id ->
            if (NoteStorage.load(Library.noteFile(id))?.elements.isNullOrEmpty()) null else "%PDF-teste $id".toByteArray()
        }
        // Restos do formato antigo (um arquivo por arquivo do app) são apagados.
        drive.files["velho"] = FakeDrive.F("notes_x.jpg", "raiz", "image/jpeg", mapOf("path" to "notes/x/a.jpg"), ByteArray(3))
        val folderName = Library.folder(note.folder)!!.name
        val r1 = DriveSync.backup(app, drive)
        assertEquals(1, r1.uploaded)
        assertNull("formato antigo removido", drive.files["velho"])
        val bundle = drive.named("Teste.rosanota").single()
        val pdf = drive.named("Teste.pdf").single()
        assertEquals("$folderName/Teste.rosanota", drive.pathOf(bundle))
        assertEquals("$folderName/Teste.pdf", drive.pathOf(pdf))
        assertTrue("é um PDF de verdade", pdf.bytes.decodeToString(0, 5) == "%PDF-")
        assertTrue("nenhuma imagem solta", drive.files.values.none { it.mime.startsWith("image/") })
        assertEquals(1, drive.named(DriveSync.INDEX_NAME).size)

        // Nada mudou: nada sobe.
        assertEquals(0, DriveSync.backup(app, drive).uploaded)

        // Muda a nota: atualiza os mesmos arquivos.
        val nf = Library.noteFile(note.id)
        nf.appendBytes(byteArrayOf(0))
        nf.setLastModified(System.currentTimeMillis() + 5000)
        val before = drive.creates
        assertEquals(1, DriveSync.backup(app, drive).uploaded)
        assertEquals("não cria arquivos novos", before, drive.creates)

        // Renomear a nota e a pasta renomeia lá também, sem reenviar.
        Library.updateNote(Library.note(note.id)!!, title = "Aula 3")
        Library.folder(note.folder)!!.name = "Bioquímica"
        Library.save()
        assertEquals(0, DriveSync.backup(app, drive).uploaded)
        assertEquals("Bioquímica/Aula 3.rosanota", drive.pathOf(drive.named("Aula 3.rosanota").single()))
        assertEquals(1, drive.named("Aula 3.pdf").size)

        // Nota apagada de vez some do Drive.
        val other = Library.createNote(Library.folders.first().id, "Outra")
        NoteStorage.save(Library.noteFile(other.id), NoteData(emptyList(), null))
        DriveSync.backup(app, drive)
        assertEquals("nota vazia: só o editável", 1, drive.named("Outra.rosanota").size)
        assertEquals(0, drive.named("Outra.pdf").size)
        Library.deleteNote(other.id)
        Library.io.submit {}.get()
        DriveSync.backup(app, drive)
        assertEquals(0, drive.named("Outra.rosanota").size)

        // Restaurar num aparelho "limpo" traz a nota de volta, editável.
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        Library.init(app, reload = true)
        assertEquals(1, DriveSync.restore(app, drive))
        assertEquals("Aula 3", Library.note(note.id)?.title)
        assertTrue(NoteStorage.load(Library.noteFile(note.id))!!.elements.isNotEmpty())
        assertEquals(0, DriveSync.backup(app, drive).uploaded)
        assertEquals("sem duplicar no Drive", 1, drive.named("Aula 3.rosanota").size)
    }

    @Test
    fun arquivo_rosanota_abre_como_nota_editavel() {
        drag(line(200f, 700f, 800f, 700f))
        controller.pause()
        Library.io.submit {}.get()
        val bytes = NoteFiles.bundle(Library.note(note.id)!!)
        val n = NoteFiles.import(bytes.inputStream(), Library.folders.first().id)!!
        assertEquals("Teste", n.title)
        assertTrue(n.id != note.id)
        assertEquals(1, NoteStorage.load(Library.noteFile(n.id))!!.elements.size)
    }

    @Test
    fun tela_girada_desenha_ate_os_cantos() {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        for (deg in listOf(20f, 45f, 90f, -135f, 170f)) {
            canvas.setViewRotation(deg)
            val vis = canvas.visibleWorldForTests()
            val v = canvas.viewForTests
            val a = Math.toRadians(deg.toDouble())
            val c = Math.cos(a).toFloat()
            val s = Math.sin(a).toFloat()
            // Cada canto da tela, desgirado e levado para o mundo, tem que estar na área desenhada.
            for ((x, y) in listOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                val dx = x - w / 2f
                val dy = y - h / 2f
                val ux = w / 2f + dx * c + dy * s
                val uy = h / 2f - dx * s + dy * c
                val wx = (ux - v[0]) / v[2]
                val wy = (uy - v[1]) / v[2]
                assertTrue("$deg°: canto ($x,$y) -> ($wx,$wy) fora de $vis", wx >= vis.left - 1f && wx <= vis.right + 1f && wy >= vis.top - 1f && wy <= vis.bottom + 1f)
            }
        }
    }

    // ---- PowerPoint ----------------------------------------------------------------------

    private fun samplePptx(): File {
        val f = File(app.cacheDir, "aula_${System.nanoTime()}.pptx")
        javaClass.classLoader!!.getResourceAsStream("pptx/exemplo.pptx")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    private fun near(c: Int, expected: Int, tol: Int = 18) =
        abs(Color.red(c) - Color.red(expected)) <= tol && abs(Color.green(c) - Color.green(expected)) <= tol &&
            abs(Color.blue(c) - Color.blue(expected)) <= tol

    @Test
    fun powerpoint_offline_desenha_fundo_formas_imagem_texto_e_tabela() {
        val dir = File(app.cacheDir, "slides").apply { deleteRecursively(); mkdirs() }
        val pages = PptxImporter.renderFile(samplePptx(), dir, { _, _ -> }, { false })
        assertEquals(3, pages.size)
        assertEquals("16:9 do sldSz", 960, pages[0].widthPt)
        assertEquals(540, pages[0].heightPt)
        val bmps = pages.map { android.graphics.BitmapFactory.decodeFile(File(dir, it.file).path)!! }
        val k = bmps[0].width / 12192000f
        fun px(b: Bitmap, x: Long, y: Long) = b.getPixel((x * k).toInt(), (y * k).toInt())

        // Slide 1: título escuro no alto, fundo branco do tema.
        val s1 = bmps[0]
        assertTrue(near(s1.getPixel(5, 5), Color.WHITE))
        var dark = 0
        for (y in 0 until s1.height / 3 step 2) for (x in 0 until s1.width step 2) {
            val c = s1.getPixel(x, y)
            if (Color.red(c) + Color.green(c) + Color.blue(c) < 200) dark++
        }
        assertTrue("título desenhado ($dark)", dark > 200)

        // Slide 2: fundo, retângulo verde, imagem vermelha e elipse com a cor do tema (accent1).
        val s2 = bmps[1]
        assertTrue("fundo", near(s2.getPixel(5, 5), 0xFFFFF2CC.toInt()))
        assertTrue("retângulo", near(px(s2, 914400 + 1524000, 914400 + 914400), 0xFF2E7D32.toInt()))
        assertTrue("imagem", near(px(s2, 7315200 + 1371600, 914400 + 1371600), 0xFFDC143C.toInt()))
        assertTrue("elipse do tema", near(px(s2, 4572000 + 914400, 3657600 + 914400), 0xFF4F81BD.toInt()))
        var textPx = 0
        for (y in (4572000 * k).toInt() until (5486400 * k).toInt()) for (x in (914400 * k).toInt() until (6000000 * k).toInt()) {
            val c = s2.getPixel(x, y)
            if (Color.red(c) + Color.green(c) + Color.blue(c) < 200) textPx++
        }
        assertTrue("caixa de texto ($textPx)", textPx > 300)

        // Slide 3: cabeçalho da tabela na cor do tema.
        assertTrue("cabeçalho da tabela", near(px(bmps[2], 914400 + 1524000, 1828800 + 800000), 0xFF4F81BD.toInt()))
    }

    @Test
    fun powerpoint_invalido_ou_ppt_antigo_avisa_sem_travar() {
        val bad = File(app.cacheDir, "velho.ppt").apply { writeBytes(ByteArray(2048) { (it % 7).toByte() }) }
        try {
            PptxImporter.renderFile(bad, File(app.cacheDir, "x"), { _, _ -> }, { false })
            throw AssertionError("deveria recusar")
        } catch (e: PptxImporter.NotPptx) {
        }
    }

    @Test
    fun powerpoint_sem_google_cai_no_leitor_offline() {
        val dir = File(app.cacheDir, "slides2").apply { deleteRecursively(); mkdirs() }
        var asked = false
        val r = PptxImporter.importAny(app, android.net.Uri.fromFile(samplePptx()), "aula.pptx", dir, { _, _ -> }, { false }) { _, _, _ ->
            asked = true
            throw java.io.IOException("sem internet")
        }
        assertTrue(asked)
        assertTrue(!r.viaGoogle)
        assertEquals(3, r.pages.size)
    }

    @Test
    fun powerpoint_recebido_abre_na_nota_como_paginas_travadas() {
        val f = samplePptx()
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, note.id)
                .putExtra(EditorActivity.EXTRA_PPTX, android.net.Uri.fromFile(f).toString())).setup()
        editor = controller.get()
        canvas = editor.canvasForTests
        layout()
        val end = System.currentTimeMillis() + 30000
        while (canvas.lockedPdfPageCount < 3 && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(30)
        }
        assertEquals(3, canvas.lockedPdfPageCount)
    }

    // ---- Sumário dos PDFs/PowerPoints ----------------------------------------------------

    private fun twoDocs() {
        canvas.addPages(PdfImporter.layout((1..3).map { PdfImporter.Page("pdf_aaaa_p$it.jpg", 595, 842) },
            PdfImporter.Layout.VERTICAL, 0f, 0f, "Aula 1.pdf"))
        canvas.addPages(PdfImporter.layout((1..2).map { PdfImporter.Page("pptx_bbbb_s$it.jpg", 960, 540) },
            PdfImporter.Layout.VERTICAL, 2000f, 0f, "Slides.pptx"))
    }

    /** Toca no item clicável (ou num pai clicável) do último painel aberto que tenha [text]. */
    private fun clickInDialog(text: String) {
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
        var v: View? = findAll(d.window!!.decorView).first {
            (it is android.widget.TextView && it.text.toString() == text) || it.contentDescription?.toString() == text
        }
        while (v != null && !v.hasOnClickListeners()) v = v.parent as? View
        v!!.performClick()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun sumario_agrupa_por_documento_numera_e_salva() {
        twoDocs()
        val docs = canvas.pdfDocuments()
        assertEquals(listOf("Aula 1.pdf", "Slides.pptx"), docs.map { it.name })
        assertEquals(listOf(1, 2, 3), docs[0].pages.map { it.pdfPage })
        assertEquals(3, canvas.pageTotalOf(docs[0].pages[1]))
        assertEquals(2, canvas.pageTotalOf(docs[1].pages[0]))
        assertEquals(View.VISIBLE, editor.contentsButtonForTests.visibility)
        controller.pause()
        Library.io.submit {}.get()
        openEditor()
        assertEquals("nome do documento salvo (v9)", listOf("Aula 1.pdf", "Slides.pptx"), canvas.pdfDocuments().map { it.name })
    }

    @Test
    fun sumario_de_notas_antigas_agrupa_pelo_lote() {
        canvas.addPages(PdfImporter.layout((1..2).map { PdfImporter.Page("pdf_x1_p$it.jpg", 595, 842) }, PdfImporter.Layout.VERTICAL, 0f, 0f))
        canvas.addPages(PdfImporter.layout((1..2).map { PdfImporter.Page("pdf_x2_p$it.jpg", 595, 842) }, PdfImporter.Layout.VERTICAL, 3000f, 0f))
        canvas.addPages(PdfImporter.layout((1..4).map { PdfImporter.Page("pptx_y1_s$it.jpg", 960, 540) }, PdfImporter.Layout.VERTICAL, 6000f, 0f))
        val docs = canvas.pdfDocuments()
        assertEquals(listOf("PDF 1", "PDF 2", "PowerPoint 1"), docs.map { it.name })
        assertEquals(4, docs[2].total)
    }

    @Test
    fun sumario_leva_ate_a_pagina_escolhida() {
        assertEquals(View.GONE, editor.contentsButtonForTests.visibility)
        twoDocs()
        editor.showContents()
        ShadowLooper.idleMainLooper()
        clickInDialog("Slides.pptx")
        clickInDialog("Página 2")
        val page = canvas.pdfDocuments()[1].pages[1]
        val vis = canvas.visibleWorldForTests()
        assertTrue("página inteira na tela: $vis x ${page.bounds}", vis.contains(page.bounds))
        assertTrue(abs(vis.centerX() - page.bounds.centerX()) < 5f && abs(vis.centerY() - page.bounds.centerY()) < 5f)
        assertTrue("ocupa boa parte da tela", page.bounds.width() > vis.width() * 0.8f || page.bounds.height() > vis.height() * 0.8f)
    }

    @Test
    fun voltar_depois_do_sumario_restaura_posicao_e_zoom() {
        twoDocs()
        canvas.setViewRotation(12f)
        canvas.goTo(Bookmark("", 500f, 400f, 0.37f))
        val before = canvas.currentSpot()
        assertEquals(View.GONE, editor.backPillForTests.visibility)
        editor.showContents()
        ShadowLooper.idleMainLooper()
        clickInDialog("Slides.pptx")
        clickInDialog("Página 2")
        assertEquals(View.VISIBLE, editor.backPillForTests.visibility)
        assertTrue("a tela mudou", abs(canvas.currentSpot().scale - before.scale) > 0.01f || abs(canvas.currentSpot().offsetX - before.offsetX) > 1f)
        // Segundo pulo: o Voltar continua indo para antes do primeiro.
        editor.showContents()
        ShadowLooper.idleMainLooper()
        clickInDialog("Aula 1.pdf")
        clickInDialog("Página 3")
        clickText("Voltar")
        val after = canvas.currentSpot()
        assertEquals(before.offsetX, after.offsetX, 0.01f)
        assertEquals(before.offsetY, after.offsetY, 0.01f)
        assertEquals(before.scale, after.scale, 0.0001f)
        assertEquals(before.rotation, after.rotation, 0.01f)
        assertEquals(View.GONE, editor.backPillForTests.visibility)
    }

    @Test
    fun fechar_o_voltar_nao_mexe_na_tela() {
        twoDocs()
        editor.showContents()
        ShadowLooper.idleMainLooper()
        clickInDialog("Aula 1.pdf")
        clickInDialog("Página 2")
        val there = canvas.currentSpot()
        findAll(editor.window.decorView).first { it.contentDescription?.toString() == "Fechar Voltar" }.performClick()
        assertEquals(View.GONE, editor.backPillForTests.visibility)
        assertEquals(there, canvas.currentSpot())
    }

    @Test
    fun gravacoes_vao_para_a_pasta_gravacoes_no_drive() {
        drag(line(200f, 700f, 800f, 700f))
        controller.pause()
        Library.io.submit {}.get()
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        // Uma gravação "interrompida" (sem duração salva) e uma antiga em .m4a.
        File(dir, "audio_1.aac").writeBytes(ByteArray(16000) { 1 })
        File(dir, "audio_2.m4a").writeBytes(ByteArray(800) { 2 })
        File(dir, "audio.json").writeText("""{"recordings":[{"file":"audio_1.aac","duration":0,"created":1},""" +
            """{"file":"audio_2.m4a","duration":5000,"created":2,"name":"Aula de sexta"}],"marks":[]}""")
        val a = AudioNotes(dir)
        assertEquals("nome padrão", "Gravação 1", a.recordings[0].name)
        assertTrue("duração recuperada", a.recordings[0].durationMs > 0)
        assertEquals("Aula de sexta", a.recordings[1].name)

        DriveSync.pdfMaker = { _, _ -> "%PDF-teste".toByteArray() }
        val drive = FakeDrive()
        DriveSync.backup(app, drive)
        assertEquals("Gravações/Teste – Gravação 1.aac", drive.pathOf(drive.named("Teste – Gravação 1.aac").single()))
        assertEquals(1, drive.named("Teste – Aula de sexta.m4a").size)
        // O .rosanota não leva os áudios (só a lista deles).
        val u = NoteFiles.unpack(drive.named("Teste.rosanota").single().bytes.inputStream())!!
        assertTrue(u.images.keys.none { it.startsWith("audio_") && !it.endsWith(".json") })
        assertTrue(u.images.containsKey("audio.json"))

        // Renomear a gravação renomeia no Drive sem reenviar.
        a.rename(1, "Revisão")
        val creates = drive.creates
        DriveSync.backup(app, drive)
        assertEquals(1, drive.named("Teste – Revisão.m4a").size)
        assertEquals(creates, drive.creates)

        // Restaurar traz os áudios de volta para a nota.
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        Library.init(app, reload = true)
        DriveSync.restore(app, drive)
        assertEquals(16000L, File(Library.assetsDir(note.id), "audio_1.aac").length())
        assertEquals(2, AudioNotes(Library.assetsDir(note.id)).recordings.size)
        assertEquals("sem duplicar", 1, drive.named("Teste – Gravação 1.aac").size)
        DriveSync.backup(app, drive)
        assertEquals(1, drive.named("Teste – Gravação 1.aac").size)
    }

    @Test
    fun lista_de_gravacoes_abre_com_opcoes() {
        val dir = Library.assetsDir(note.id).apply { mkdirs() }
        File(dir, "audio_9.aac").writeBytes(ByteArray(8000))
        File(dir, "audio.json").writeText("""{"recordings":[{"file":"audio_9.aac","duration":61000,"created":1,"name":"Aula 1"}],"marks":[]}""")
        controller.pause().stop().destroy()
        openEditor()
        assertEquals(View.GONE, editor.audioBarForTests.visibility)
        editor.showRecordingsForTests()
        ShadowLooper.idleMainLooper()
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
        val texts = findAll(d.window!!.decorView).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), texts.contains("Aula 1"))
        assertTrue(texts.any { it.startsWith("1:01") })
        clickInDialog("Opções de Aula 1")
        val opts = findAll(org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView)
            .filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(opts.toString(), opts.containsAll(listOf("Renomear", "Compartilhar / exportar", "Salvar em Downloads", "Apagar gravação")))
    }

    @Test
    fun painel_de_audio_arrasta_e_minimiza() {
        val bar = editor.audioBarForTests
        bar.visibility = View.VISIBLE
        layout()
        val grip = findAll(bar).first { it.contentDescription?.toString() == "Arrastar o painel de áudio" }
        val ev = { action: Int, rx: Float, ry: Float ->
            val e = MotionEvent.obtain(0, 0, action, rx, ry, 0)
            grip.dispatchTouchEvent(e)
            e.recycle()
        }
        ev(MotionEvent.ACTION_DOWN, 500f, 1500f)
        ev(MotionEvent.ACTION_MOVE, 400f, 1000f)
        ev(MotionEvent.ACTION_UP, 400f, 1000f)
        assertEquals(-100f, bar.translationX, 1f)
        assertEquals(-500f, bar.translationY, 1f)
        // Não sai da tela, por mais que arraste.
        ev(MotionEvent.ACTION_DOWN, 500f, 1500f)
        ev(MotionEvent.ACTION_MOVE, -5000f, -9000f)
        ev(MotionEvent.ACTION_UP, -5000f, -9000f)
        val stage = bar.parent as View
        assertTrue("dentro da área: ${bar.left + bar.translationX}", bar.left + bar.translationX >= 0f)
        assertTrue(bar.top + bar.translationY >= 0f && bar.bottom + bar.translationY <= stage.height)

        // Minimizar deixa só o tempo e o botão; fica lembrado ao reabrir a nota.
        editor.setAudioBarMiniForTests(true)
        assertTrue(editor.audioBarMiniForTests)
        assertTrue(findAll(bar).any { it.contentDescription?.toString() == "Expandir o painel de áudio" && (it.parent as View).visibility == View.VISIBLE })
        controller.pause().stop().destroy()
        openEditor()
        assertTrue("minimizado continua", editor.audioBarMiniForTests)
        editor.setAudioBarMiniForTests(false)
        assertTrue(!editor.audioBarMiniForTests)
    }

    @Test
    fun compartilhar_do_samsung_notes_com_tipo_desconhecido_importa() {
        fun res(name: String, to: String) = File(app.cacheDir, to).also { f ->
            javaClass.classLoader!!.getResourceAsStream(name)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        }
        // Sem a extensão .sdocx: tem que reconhecer pelo conteúdo (zip).
        val a = res("sdocx/titulo.sdocx", "compartilhado_1")
        val b = res("sdocx/eg-walker.sdocx", "compartilhado_2")
        val foto = File(app.cacheDir, "foto.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)) }
        val home = Robolectric.buildActivity(FoldersActivity::class.java).setup().get()
        val before = Library.notes.size

        home.handleSharedForTests(listOf(android.net.Uri.fromFile(foto)))
        ShadowLooper.idleMainLooper()
        assertEquals("foto não vira nota", before, Library.notes.size)
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
        assertTrue(findAll(d.window!!.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString().contains("Samsung Notes (.sdocx)") })
        d.dismiss()

        home.handleSharedForTests(listOf(android.net.Uri.fromFile(a), android.net.Uri.fromFile(b)))
        val end = System.currentTimeMillis() + 30000
        while (Library.notes.size < before + 2 && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(30)
        }
        assertEquals(before + 2, Library.notes.size)
    }

    // ---- Rejeição de palma -------------------------------------------------------------------

    private class P(val id: Int, val x: Float, val y: Float, val tool: Int, val major: Float = 0f)

    private fun mev(action: Int, pts: List<P>, down: Long, gap: Long = 16, flags: Int = 0): MotionEvent {
        val props = Array(pts.size) { i -> MotionEvent.PointerProperties().apply { id = pts[i].id; toolType = pts[i].tool } }
        val coords = Array(pts.size) { i ->
            MotionEvent.PointerCoords().apply { x = pts[i].x; y = pts[i].y; pressure = 0.8f; size = 0.1f; touchMajor = pts[i].major; touchMinor = pts[i].major }
        }
        time += gap
        return MotionEvent.obtain(down, time, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, flags)
    }

    private fun idx(action: Int, i: Int) = action or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private val F = MotionEvent.TOOL_TYPE_FINGER
    private val S = MotionEvent.TOOL_TYPE_STYLUS

    private fun palmOn(fingerDraws: Boolean) {
        canvas.palmReject = true
        canvas.fingerDraws = fingerDraws
    }

    @Test
    fun palma_antes_da_caneta_ativa_nao_mexe_e_a_caneta_escreve() {
        palmOn(fingerDraws = false)
        val v0 = canvas.viewForTests.copyOf()
        val down = time
        val palm = P(0, 800f, 1600f, F)
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(palm), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 790f, 1580f, F)), down))
        val line = line(300f, 700f, 600f, 720f, 12)
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_DOWN, 1), listOf(P(0, 790f, 1580f, F), P(1, line[0].first, line[0].second, S)), down))
        for ((k, p) in line.withIndex()) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 790f - k * 3, 1580f, F), P(1, p.first, p.second, S)), down))
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_UP, 1), listOf(P(0, 760f, 1580f, F), P(1, 600f, 720f, S)), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(0, 760f, 1580f, F)), down))
        ShadowLooper.idleMainLooper()
        assertEquals("a caneta escreveu", 1, strokes().size)
        assertTrue(strokes()[0].bounds.width() > 100f)
        // A palma é ignorada logo depois da caneta: a tela não se move.
        drag(line(500f, 1200f, 500f, 900f), F)
        val v1 = canvas.viewForTests
        assertEquals(v0[0], v1[0], 0.5f)
        assertEquals(v0[1], v1[1], 0.5f)
        // Passado um tempo, dois dedos voltam a dar zoom.
        time += 1500
        val z = canvas.zoomPercent
        pinch(540f, 1000f, 100f, 300f)
        assertTrue("zoom com dois dedos", canvas.zoomPercent > z)
    }

    @Test
    fun escrevendo_com_o_dedo_a_palma_e_ignorada() {
        palmOn(fingerDraws = true)
        val down = time
        // Palma encosta embaixo à direita e mexe um pouco...
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, 850f, 1600f, F)), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 845f, 1598f, F)), down))
        // ...e depois a caneta capacitiva, acima e à esquerda, escreve.
        val line = line(300f, 700f, 650f, 700f, 12)
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_DOWN, 1), listOf(P(0, 845f, 1598f, F), P(1, line[0].first, line[0].second, F)), down, gap = 250))
        for (p in line) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 845f, 1598f, F), P(1, p.first, p.second, F)), down))
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_UP, 1), listOf(P(0, 845f, 1598f, F), P(1, 650f, 700f, F)), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(0, 845f, 1598f, F)), down))
        ShadowLooper.idleMainLooper()
        assertEquals("só o traço da caneta", 1, strokes().size)
        val b = strokes()[0].bounds
        val v = canvas.viewForTests
        assertEquals("na altura da caneta", 700f, b.centerY() * v[2] + v[1], 30f)

        // Toque grande demais (lado da mão) não risca.
        val d2 = time
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, 400f, 1200f, F, major = 200f * app.resources.displayMetrics.density)), d2))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 500f, 1250f, F, major = 200f * app.resources.displayMetrics.density)), d2))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(0, 500f, 1250f, F, major = 200f * app.resources.displayMetrics.density)), d2))
        assertEquals(1, strokes().size)

        // Dois dedos juntos: zoom, sem riscar.
        val z = canvas.zoomPercent
        pinch(540f, 1000f, 100f, 300f)
        assertTrue(canvas.zoomPercent > z)
        assertEquals(1, strokes().size)
    }

    @Test
    fun protetor_de_mao_ignora_a_mao_mas_nao_a_caneta() {
        canvas.palmReject = false
        canvas.fingerDraws = true
        canvas.palmGuard = RectF(0f, canvas.height * 0.7f, canvas.width.toFloat(), canvas.height.toFloat())
        val y = canvas.height * 0.85f
        drag(line(200f, y, 600f, y), F)
        assertEquals("mão no protetor não risca", 0, strokes().size)
        drag(line(200f, y, 600f, y), S)
        assertEquals("caneta ativa escreve por cima", 1, strokes().size)
        drag(line(200f, 600f, 600f, 600f), F)
        assertEquals("fora do protetor o dedo escreve", 2, strokes().size)
    }

    @Test
    fun protetor_ilha_move_redimensiona_e_lateral() {
        val d = app.resources.displayMetrics.density
        app.getSharedPreferences("canetas", 0).edit().putString("palm_guard_mode", "free").commit()
        controller.pause().stop().destroy()
        openEditor()
        val g0 = RectF(canvas.palmGuard!!)
        assertTrue("ilha no meio da tela", g0.left > 0f && g0.right < canvas.width && g0.top > 0f)
        // Arrasta pela alça do topo: move inteira.
        val gx = g0.centerX()
        val gy = g0.top + 14f * d
        drag(listOf(gx to gy, gx - 100f to gy - 300f, gx - 200f to gy - 600f), F)
        val g1 = canvas.palmGuard!!
        assertEquals(g0.left - 200f, g1.left, 1f)
        assertEquals(g0.top - 600f, g1.top, 1f)
        assertEquals(g0.width(), g1.width(), 1f)
        assertEquals("nada riscado ao mover", 0, strokes().size)
        // Pelo canto de baixo: muda o tamanho.
        val cx = g1.right - 16f * d
        val cy = g1.bottom - 16f * d
        drag(listOf(cx to cy, cx - 50f to cy + 100f, cx - 100f to cy + 200f), F)
        val g2 = canvas.palmGuard!!
        assertEquals(g1.width() - 100f, g2.width(), 1f)
        assertEquals(g1.height() + 200f, g2.height(), 1f)
        // Fica lembrada ao reabrir a nota.
        controller.pause().stop().destroy()
        openEditor()
        assertEquals(g2.left, canvas.palmGuard!!.left, 2f)
        assertEquals(g2.bottom, canvas.palmGuard!!.bottom, 2f)

        // Faixa na lateral (destro: à direita, de cima a baixo).
        app.getSharedPreferences("canetas", 0).edit().putString("palm_guard_mode", "side").commit()
        controller.pause().stop().destroy()
        openEditor()
        val s = canvas.palmGuard!!
        assertEquals(canvas.width.toFloat(), s.right, 1f)
        assertEquals(0f, s.top, 1f)
        assertTrue(s.height() > s.width())
        // O × fecha.
        val xx = s.right - 18f * d
        val xy = s.top + 16f * d
        tap(xx, xy, F)
        assertNull(canvas.palmGuard)
        assertEquals("off", app.getSharedPreferences("canetas", 0).getString("palm_guard_mode", null))
    }

    @Test
    fun palma_que_encosta_logo_depois_da_caneta_nao_apaga_o_traco() {
        palmOn(fingerDraws = true)
        val z = canvas.zoomPercent
        val down = time
        val pen = line(300f, 700f, 700f, 760f, 20)
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, pen[0].first, pen[0].second, F)), down))
        for (k in 1..2) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, pen[k].first, pen[k].second, F)), down))
        // A mão encosta 50 ms depois, embaixo à direita, e fica parada (tremendo um pouco).
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_DOWN, 1), listOf(P(0, pen[2].first, pen[2].second, F), P(1, 900f, 1500f, F)), down))
        for (k in 3 until pen.size) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE,
            listOf(P(0, pen[k].first, pen[k].second, F), P(1, 900f + (k % 3), 1500f - (k % 2), F)), down))
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_UP, 0), listOf(P(0, 700f, 760f, F), P(1, 900f, 1500f, F)), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(1, 900f, 1500f, F)), down))
        ShadowLooper.idleMainLooper()
        assertEquals("o traço da caneta fica", 1, strokes().size)
        assertTrue(strokes()[0].bounds.width() > 100f)
        assertEquals("sem zoom", z, canvas.zoomPercent)
    }

    @Test
    fun cancelamento_do_sistema_guarda_o_traco_e_palma_marcada_some() {
        palmOn(fingerDraws = true)
        val d1 = time
        val pen = line(300f, 700f, 700f, 700f, 10)
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, pen[0].first, pen[0].second, F)), d1))
        for (p in pen.drop(1)) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, p.first, p.second, F)), d1))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_CANCEL, listOf(P(0, 700f, 700f, F)), d1))
        ShadowLooper.idleMainLooper()
        assertEquals("CANCEL do sistema não apaga", 1, strokes().size)

        // Toque que o próprio sistema solta como cancelado (palma): não fica traço.
        val d2 = time
        val palm = line(400f, 1400f, 500f, 1450f, 6)
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, palm[0].first, palm[0].second, F)), d2))
        for (p in palm.drop(1)) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, p.first, p.second, F)), d2))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(0, 500f, 1450f, F)), d2, flags = MotionEvent.FLAG_CANCELED))
        ShadowLooper.idleMainLooper()
        assertEquals(1, strokes().size)
    }

    @Test
    fun palma_parada_ha_tempo_e_depois_a_caneta_escreve() {
        palmOn(fingerDraws = true)
        val down = time
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_DOWN, listOf(P(0, 850f, 1600f, F)), down))
        // Mão apoiada por 2 s, tremendo uns 25 dp.
        val d = app.resources.displayMetrics.density
        for (k in 0 until 20) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 850f + (k % 5) * 5f * d, 1600f - (k % 4) * 4f * d, F)), down, gap = 100))
        val pen = line(300f, 700f, 650f, 700f, 12)
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_DOWN, 1), listOf(P(0, 850f, 1600f, F), P(1, pen[0].first, pen[0].second, F)), down))
        for (p in pen) canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_MOVE, listOf(P(0, 850f, 1600f, F), P(1, p.first, p.second, F)), down))
        canvas.dispatchTouchEvent(mev(idx(MotionEvent.ACTION_POINTER_UP, 1), listOf(P(0, 850f, 1600f, F), P(1, 650f, 700f, F)), down))
        canvas.dispatchTouchEvent(mev(MotionEvent.ACTION_UP, listOf(P(0, 850f, 1600f, F)), down))
        ShadowLooper.idleMainLooper()
        assertEquals("só o traço da caneta", 1, strokes().size)
        val v = canvas.viewForTests
        assertEquals(700f, strokes()[0].bounds.centerY() * v[2] + v[1], 30f)
    }

    // ---- Rolagem presa em documentos ---------------------------------------------------------

    private fun pdfColumn(layout: PdfImporter.Layout = PdfImporter.Layout.VERTICAL) {
        canvas.addPages(PdfImporter.layout((1..4).map { PdfImporter.Page("pdf_trava_p$it.jpg", 595, 842) }, layout, 0f, 0f, "Aula.pdf"))
        canvas.goToPage(canvas.pdfDocuments()[0].pages[1])
        canvas.docScrollLock = true
    }

    @Test
    fun rolagem_fica_presa_na_vertical_em_cima_do_pdf() {
        pdfColumn()
        val d = app.resources.displayMetrics.density
        val v0 = canvas.viewForTests.copyOf()
        // Sobe a página desviando 30 dp para o lado: só a vertical anda.
        drag(line(540f, 1400f, 540f + 30f * d, 900f, 12), F)
        val v1 = canvas.viewForTests.copyOf()
        assertEquals("X preso", v0[0], v1[0], 0.5f)
        assertEquals("Y andou", v0[1] - 500f, v1[1], 2f)
        // Rolagem longa com o dedo torto (60 dp para o lado ao longo do caminho): continua presa.
        drag(line(540f, 1800f, 540f + 60f * d, 300f, 30), F)
        val v2 = canvas.viewForTests.copyOf()
        assertEquals("rolagem longa não solta", v1[0], v2[0], 0.5f)
        assertEquals(v1[1] - 1500f, v2[1], 2f)
        // Empurrão claro de 130 dp para o lado: solta e acompanha o dedo.
        drag(line(300f, 1400f, 300f + 130f * d, 1400f, 12), F)
        assertEquals("X soltou", v2[0] + 130f * d, canvas.viewForTests[0], 2f)
    }

    @Test
    fun rolagem_livre_fora_do_pdf_e_com_ajuste_desligado() {
        pdfColumn()
        val d = app.resources.displayMetrics.density
        canvas.docScrollLock = false
        val v0 = canvas.viewForTests.copyOf()
        drag(line(540f, 1400f, 540f + 30f * d, 900f, 12), F)
        assertEquals("desligado: X anda", v0[0] + 30f * d, canvas.viewForTests[0], 2f)
        // Ligado, mas com o centro da tela fora do documento: livre.
        canvas.docScrollLock = true
        drag(line(100f, 1000f, 1000f, 1000f, 12), F)
        drag(line(100f, 1000f, 1000f, 1000f, 12), F)
        val v1 = canvas.viewForTests.copyOf()
        drag(line(540f, 1400f, 540f + 30f * d, 900f, 12), F)
        assertEquals("fora do PDF: X anda", v1[0] + 30f * d, canvas.viewForTests[0], 2f)
    }

    @Test
    fun pdf_lado_a_lado_trava_na_horizontal_e_pinca_da_zoom() {
        pdfColumn(PdfImporter.Layout.HORIZONTAL)
        val d = app.resources.displayMetrics.density
        val v0 = canvas.viewForTests.copyOf()
        drag(line(900f, 1000f, 400f, 1000f + 30f * d, 12), F)
        val v1 = canvas.viewForTests.copyOf()
        assertEquals("Y preso", v0[1], v1[1], 0.5f)
        assertEquals("X andou", v0[0] - 500f, v1[0], 2f)
        val z = canvas.zoomPercent
        pinch(540f, 1000f, 100f, 300f)
        assertTrue("pinça dá zoom", canvas.zoomPercent > z)
    }

    // ---- Páginas de PDF agrupadas ------------------------------------------------------------

    @Test
    fun paginas_do_pdf_vem_agrupadas_e_da_para_desagrupar() {
        canvas.addPages(PdfImporter.layout((1..3).map { PdfImporter.Page("pdf_grupo_p$it.jpg", 595, 842) },
            PdfImporter.Layout.VERTICAL, 0f, 0f, "Aula.pdf"))
        val p2 = canvas.pdfDocuments()[0].pages[1]
        canvas.goToPage(p2)
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        val cx = canvas.width / 2f
        val cy = canvas.height / 2f
        tap(cx, cy)
        assertEquals("o documento inteiro", 3, canvas.selectedItems.size)
        assertTrue(canvas.selectionHasPdfGroup)

        canvas.ungroupSelection()
        tap(cx, cy)
        assertEquals("desagrupado: só a página", 1, canvas.selectedItems.size)
        assertEquals(2, (canvas.selectedItems[0] as ImageElement).pdfPage)
        assertTrue(canvas.selectionIsUngroupedPage)

        // Desagrupado continua assim ao reabrir a nota.
        controller.pause()
        Library.io.submit {}.get()
        openEditor()
        canvas.tool = InfiniteCanvasView.Tool.SELECT
        canvas.goToPage(canvas.pdfDocuments()[0].pages[1])
        tap(canvas.width / 2f, canvas.height / 2f)
        assertEquals(1, canvas.selectedItems.size)

        // Agrupar de novo.
        canvas.regroupSelection()
        assertEquals(3, canvas.selectedItems.size)
        canvas.undo()
        tap(canvas.width / 2f, canvas.height / 2f)
        assertEquals("desfazer volta a separar", 1, canvas.selectedItems.size)
    }
}
