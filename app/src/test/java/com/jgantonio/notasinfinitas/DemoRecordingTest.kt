package com.jgantonio.notasinfinitas

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Gravações do app de verdade para o vídeo (quadro a quadro, com DemoKit), seguindo o
 * roteiro "Escreva fora da margem". Só rodam quando pedido (-Ddemo.dir=...), pelo
 * workflow "Vídeo de demonstração"; cada gravação vai para uma subpasta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DemoRecordingTest {

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val ink = Color.parseColor("#1B1B1F")
    private val red = Color.parseColor("#D7263D")
    private val blue = Color.parseColor("#1F5FD1")
    private val grey = Color.parseColor("#5F6368")
    private val purple = Color.parseColor("#8E44AD")
    private val marker = Color.parseColor("#FFE45C")

    private fun fountain(c: Int = ink) = PenSettings(BrushType.FOUNTAIN, c, 5f, 100)
    private fun pen(c: Int, size: Float = 4f) = PenSettings(BrushType.PEN, c, size, 100)

    private fun outDir(name: String): File {
        val dir = System.getProperty("demo.dir")
        assumeTrue("só roda no workflow do vídeo", !dir.isNullOrBlank())
        return File(dir!!, name).apply { deleteRecursively(); mkdirs() }
    }

    private fun resetLibrary(dark: Boolean) {
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        app.getSharedPreferences("canetas", 0).edit().clear().commit()
        app.getSharedPreferences("escrita", 0).edit().clear().commit()
        Ui.setThemeChoice(app, if (dark) "dark" else "light")
        Library.init(app, reload = true)
    }

    private fun folder(name: String, color: Int): Folder =
        Library.folders.firstOrNull { it.name == name } ?: Library.createFolder(name, color, null)

    private fun openEditor(note: NoteInfo): EditorActivity {
        val ed = Robolectric.buildActivity(EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, note.id)).setup().get()
        ed.canvasForTests.lowLatency = false
        return ed
    }

    private fun freshEditor(title: String, dark: Boolean = false): EditorActivity {
        resetLibrary(dark)
        val bio = folder("Biologia", Library.folderColors[2])
        return openEditor(Library.createNote(bio.id, title))
    }

    private fun quickDots(k: DemoKit): List<ColorDot> {
        val box = k.editor.quickColorsForTests
        return (0 until box.childCount).map { box.getChildAt(it) }.filterIsInstance<ColorDot>()
    }

    // =================================================================================
    // R1 — margem, folha Universitário, um toque em "Infinita" e a nota sem fim
    // =================================================================================
    @Test
    fun r1() {
        val k = DemoKit(freshEditor("Biologia celular"), outDir("r1"), scale = 1.25f)
        k.layout()
        val c = k.canvas
        val W = c.width.toFloat()
        val H = c.height.toFloat()
        k.quiet {
            c.setPageLayout(true, false, false, true, 1)
            c.pageStyle = PageStyle.COLLEGE
        }
        val page = c.pageLayout.pageRect(0)
        val gap = min(page.width(), page.height()) / 24f
        fun line(i: Int) = page.top + gap * 3 + i * gap
        val z = 1.4f
        k.setView(0.80f * W - page.right * z, 0.42f * H - line(8) * z, z)
        val yBody = 0.42f * H
        val (_, yTitle) = k.screen(0f, line(4))
        val body = "a usina de ener"
        val x1 = 0.64f * W
        val xStart = x1 - k.textWidth(body, 44f)
        k.quiet {
            val (twx, twy) = k.world(xStart, yTitle)
            k.bake(k.bakeText("Mitocôndria", twx, twy, 64f / z, fountain()))
            val (bwx, bwy) = k.world(xStart, yBody)
            k.bake(k.bakeText(body, bwx, bwy, 44f / z, pen(blue)))
        }
        k.note("margem", JSONArray(listOf(((0.80f * W) + k.originX()) * 1.25f, 0.0)))
        k.usePen(pen(blue))
        k.hold(4)

        // 1. termina "energia" e espreme "da célula" na borda da folha (escadinha)
        k.mark("gancho")
        val x2 = k.write("gia", x1, yBody, cap = 44f, pxPerFrame = 30f)
        k.mark("aperta")
        val before = k.strokesDone
        var x = x2 + 6f
        var y = yBody
        val edge = 0.80f * W - 6f
        "dacélula".forEachIndexed { i, ch ->
            val cap = (22f - i * 1.3f).coerceAtLeast(11f)
            if (x + k.textWidth(ch.toString(), cap) * 0.6f > edge) x = edge - k.textWidth(ch.toString(), cap) * 0.6f
            k.write(ch.toString(), x, y, cap = cap, pxPerFrame = 24f)
            x += 0.55f * k.textWidth(ch.toString(), 22f)
            y += 16f
        }
        k.mark("socorro")
        k.hold(12)
        val squeezed = k.strokesDone - before
        k.quiet { repeat(squeezed) { c.undo() } }

        // 2. ⋮ > Página > Infinita
        k.mark("pagina")
        k.tap(k.find("Mais opções"), 4)
        k.tap("Página", 4)
        k.hold(6)
        k.mark("infinita")
        k.tap("Infinita", 6)
        k.hold(4)
        k.dismissDialog()
        k.hold(4)

        // 3. a nota inteira, além da tela (em coordenadas do mundo a partir da vista atual)
        val (bx, by) = k.world(0f, 0f)
        fun P(dx: Float, dy: Float) = bx + dx to by + dy
        k.quiet {
            val list = ArrayList<StrokeElement>()
            fun text(t: String, dx: Float, dy: Float, cap: Float, p: PenSettings) {
                val (wx, wy) = P(dx, dy); list += k.bakeText(t, wx, wy, cap, p)
            }
            val calli = PenSettings(BrushType.CALLIGRAPHY, red, 9f, 100)
            text("Biologia celular", 0f, -700f, 110f, calli)
            text("módulo 1", 0f, -580f, 46f, pen(ink))
            text("Membrana", 2400f, 300f, 90f, fountain())
            text("externa lisa", 2400f, 440f, 40f, pen(ink))
            text("interna com cristas", 2400f, 540f, 40f, pen(ink))
            text("matriz com DNA próprio", 2400f, 640f, 40f, pen(ink))
            val (hx, hy) = P(2390f, 528f)
            list += k.bakeStroke(listOf(hx to hy, hx + Cursive.textWidth("interna com cristas", 40f) + 20f to hy), PenSettings(BrushType.HIGHLIGHTER, marker, 26f, 45))
            // ciclo de Krebs: círculo com 4 setas e rótulos (o centro fica vazio)
            val (cx, cy) = P(1500f, 1700f)
            list += k.bakeStroke(k.ellipse(cx, cy, 300f, 300f, n = 90, overlap = 0f), pen(ink, 6f))
            for (a in listOf(0.0, PI / 2, PI, 3 * PI / 2)) {
                val ax = cx + 300f * kotlin.math.cos(a).toFloat(); val ay = cy + 300f * kotlin.math.sin(a).toFloat()
                val tx = -kotlin.math.sin(a).toFloat(); val ty = kotlin.math.cos(a).toFloat()
                list += k.bakeStroke(listOf(ax - tx * 34f + ty * 26f to ay - ty * 34f - tx * 26f, ax to ay, ax - tx * 34f - ty * 26f to ay - ty * 34f + tx * 26f), pen(ink, 6f))
            }
            text("citrato", 1450f, 1330f, 40f, pen(blue))
            text("NADH", 1830f, 1720f, 40f, pen(red))
            text("FADH2", 1450f, 2090f, 40f, pen(red))
            text("oxaloacetato", 950f, 1760f, 40f, pen(blue))
            text("Glicólise", -1000f, 400f, 80f, fountain())
            text("glicose", -1000f, 520f, 40f, pen(blue))
            text("piruvato", -1000f, 610f, 40f, pen(blue))
            text("saldo: 2 ATP", -1000f, 700f, 40f, pen(blue))
            // ATP sintase: faixa de membrana + "cogumelo"
            val (sx, sy) = P(2400f, 1500f)
            list += k.bakeStroke(listOf(sx to sy, sx + 600f to sy, sx + 600f to sy + 90f, sx to sy + 90f, sx to sy), pen(red, 5f))
            list += k.bakeStroke(k.ellipse(sx + 300f, sy - 70f, 80f, 60f, n = 40, overlap = 0f), pen(red, 5f))
            list += k.bakeStroke(listOf(sx + 280f to sy - 12f, sx + 280f to sy + 90f), pen(red, 5f))
            list += k.bakeStroke(listOf(sx + 320f to sy - 12f, sx + 320f to sy + 90f), pen(red, 5f))
            text("ATP sintase", 2400f, 1700f, 44f, pen(red))
            text("Cai na prova!", 100f, 2500f, 110f, PenSettings(BrushType.BRUSH, red, 10f, 100))
            val (ex, ey) = P(100f + Cursive.textWidth("Cai na prova!", 110f) / 2, 2470f)
            list += k.bakeStroke(k.ellipse(ex, ey, Cursive.textWidth("Cai na prova!", 110f) * 0.62f, 110f, rotDeg = -4f), pen(red, 5f))
            text("Resumo", 2400f, 2500f, 80f, fountain())
            text("tem DNA próprio", 2400f, 2620f, 40f, pen(ink))
            text("herança materna", 2400f, 2710f, 40f, pen(ink))
            text("produz ATP", 2400f, 2800f, 40f, pen(ink))
            text("sem fim", 900f, 3300f, 100f, calli)
            k.bake(list)
        }

        // 4. escreve atravessando a antiga borda, arrasta, continua
        k.mark("drop")
        var xEnd = k.write(" da célula", x2, yBody, cap = 44f, pxPerFrame = 40f)
        k.mark("arrasta1")
        k.pan(0.85f * W, 0.62f * H, -0.60f * W, 0f, 12)
        xEnd -= 0.60f * W
        k.mark("atp")
        xEnd = k.write(" produz ATP", xEnd, yBody, cap = 44f, pxPerFrame = 45f)
        k.mark("arrasta2")
        k.pan(0.85f * W, 0.62f * H, -0.60f * W, 0f, 12)
        xEnd -= 0.60f * W
        k.mark("vermelho")
        k.tap(quickDots(k)[1], 4)
        k.write(" cai na prova!", xEnd, yBody, cap = 44f, pxPerFrame = 45f)
        k.hold(4)

        // 5. pra baixo também: o ciclo aparece; "Krebs" no meio
        k.mark("desce")
        k.pan(0.50f * W, 0.80f * H, 0f, -0.45f * H, 12)
        k.mark("krebs")
        k.tap(quickDots(k)[2], 4)
        val (kx, ky) = k.screen(bx + 1500f, by + 1700f)
        k.write("Krebs", kx - k.textWidth("Krebs", 70f) / 2, ky + 24f, cap = 70f, pxPerFrame = 45f)

        // 6. pinça: a aula inteira numa tela só
        k.mark("zoom")
        k.pinch(0.5f * W, 0.5f * H, 0.36f * W, 0.057f * W, 30)
        k.mark("fim")
        k.hold(18)
        k.finish()
    }

    // =================================================================================
    // R2 — marca-texto que se ajusta, barra em pé, 3 cores, 6 pincéis, laço e borracha
    // =================================================================================
    @Test
    fun r2() {
        val k = DemoKit(freshEditor("Resumo P1"), outDir("r2"), scale = 1.0f)
        k.layout()
        val c = k.canvas
        val W = c.width.toFloat()
        val H = c.height.toFloat()
        k.quiet {
            val s = k.view[2]
            val (tx, ty) = k.world(0.08f * W, 0.30f * H)
            k.bake(k.bakeText("Mitocôndria", tx, ty, 110f / s, fountain()))
            val (lx, ly) = k.world(0.08f * W, 0.40f * H)
            k.bake(k.bakeText("gera NADH e FADH", lx, ly, 40f / s, pen(ink)))
            k.editor.prefsForTests.currentType = BrushType.FOUNTAIN
            k.editor.prefsForTests.savePen(fountain())
            k.usePen(fountain())
        }
        k.anchor("marca", k.editor.highlighterButtonForTests)
        k.anchor("alca", k.editor.dockGripForTests)
        k.hold(3)

        // marca-texto: botão próprio, faixa fina na letra pequena, alta no título
        k.mark("mt")
        k.tap(k.editor.highlighterButtonForTests, 6)
        k.hold(3)
        k.mark("fina")
        k.stroke((0..24).map { 0.06f * W + it * (0.56f * W / 24) to 0.40f * H - 12f + sin(it * 1.3f) * 2f }, pxPerFrame = 40f)
        k.hold(6)
        k.mark("alta")
        k.stroke((0..30).map { 0.06f * W + it * (0.80f * W / 30) to 0.30f * H - 36f + sin(it * 1.1f) * 2f }, pxPerFrame = 50f)
        k.hold(10)

        // barra: volta à caneta e arrasta a barra para a borda esquerda (fica em pé)
        k.mark("barra")
        k.tap(k.editor.penButtonForTests, 4)
        val grip = IntArray(2).also { k.editor.dockGripForTests.getLocationInWindow(it) }
        k.dragDock(20f - grip[0], 0.45f * k.editor.window.decorView.height - grip[1], 16)
        k.mark("empe")
        k.hold(6)
        k.anchor("barraEmPe", k.editor.dockForTests)

        // 3 cores na batida
        k.quiet { k.pan(0.5f * W, 0.8f * H, 0f, -0.30f * H, 1) }
        k.mark("cores")
        k.tap(quickDots(k)[0], 4)
        k.write("ATP", 0.35f * W, 0.62f * H, cap = 80f, pxPerFrame = 45f)
        k.tap(quickDots(k)[1], 4)
        k.stroke(k.ellipse(0.46f * W, 0.60f * H, 0.17f * W, 0.045f * H, n = 46), pxPerFrame = 70f)
        k.tap(quickDots(k)[2], 4)
        k.stroke((0..48).map { 0.32f * W + it * (0.30f * W / 48) to 0.645f * H + 8f * sin(it / 48f * 4 * 2 * PI).toFloat() }, pxPerFrame = 60f)
        k.hold(6)

        // bandeja de canetas
        k.mark("bandeja")
        k.tap(k.editor.penButtonForTests, 6)
        k.hold(8)
        k.editor.closeTrayForTests()

        // 6 pincéis numa grade 2x3
        k.quiet { k.pan(0.5f * W, 0.85f * H, 0f, -0.75f * H, 1) }
        val cols = listOf(0.16f * W, 0.58f * W)
        val rows = listOf(0.28f * H, 0.48f * H, 0.68f * H)
        val cells = listOf(
            Triple("b1", "resumo", fountain()),
            Triple("b2", "revisão", pen(blue)),
            Triple("b3", "rascunho", PenSettings(BrushType.PENCIL, grey, 5f, 90)),
            Triple("b4", "fórmulas", PenSettings(BrushType.CALLIGRAPHY, red, 9f, 100)),
            Triple("b5", "mapas", PenSettings(BrushType.BRUSH, purple, 12f, 100)),
        )
        cells.forEachIndexed { i, (name, word, p) ->
            val cx = cols[i % 2]; val cy = rows[i / 2]
            k.anchorRect(name, cx - 24f, cy - 110f, 0.40f * W, 160f, c)
            k.mark(name)
            k.usePen(p)
            k.write(word, cx, cy, cap = 70f, pxPerFrame = 50f)
        }
        val (px, py) = cols[1] to rows[2]
        k.quiet { k.usePen(pen(ink)); k.write("prova", px, py, cap = 70f) }
        k.anchorRect("b6", px - 24f, py - 110f, 0.40f * W, 160f, c)
        k.mark("b6")
        k.usePen(PenSettings(BrushType.HIGHLIGHTER, marker, 20f, 45))
        k.stroke(listOf(px - 10f to py - 26f, px + 0.22f * W to py - 26f), pxPerFrame = 40f)
        k.hold(6)

        // laço: mitocôndria vermelha, "mitocondira" escrito errado
        k.quiet {
            k.pan(0.5f * W, 0.85f * H, 0f, -0.75f * H, 1)
            k.usePen(pen(red, 6f))
            k.stroke(k.ellipse(0.50f * W, 0.38f * H, 0.22f * W, 0.08f * H, rotDeg = -10f))
            k.stroke((0..60).map { 0.32f * W + it * (0.36f * W / 60) to 0.38f * H + 0.035f * H * sin(it / 60f * 4.5f * 2 * PI).toFloat() })
            k.usePen(pen(ink))
            k.write("mitocondira", 0.24f * W, 0.62f * H, cap = 60f)
        }
        k.mark("laco")
        k.lasso((0 until 40).map {
            val a = it / 40.0 * 2 * PI - 0.6
            0.50f * W + 0.30f * W * kotlin.math.cos(a).toFloat() + (if (it % 3 == 0) 8f else -6f) to
                0.38f * H + 0.12f * H * kotlin.math.sin(a).toFloat() + (if (it % 4 == 0) -7f else 5f)
        }, pxPerFrame = 70f)
        k.hold(3)
        k.mark("move")
        k.dragSelection(0.50f * W, 0.38f * H, 0.10f * W, -0.05f * H, 12)
        k.hold(2)
        k.mark("gira")
        k.rotateSelection(-20f, 10)
        k.hold(2)
        k.mark("aumenta")
        k.scaleSelection(1.3f, 10)
        k.hold(3)
        k.quiet { c.clearSelection() }

        // borracha (toque real no botão da barra em pé)
        k.mark("borracha")
        k.tap(k.find("Borracha"), 4)
        val ex0 = 0.22f * W
        val ex1 = 0.24f * W + k.textWidth("mitocondira", 60f) + 10f
        k.stroke((0..20).map { i -> (if (i % 2 == 0) ex0 else ex1) + i * 2f to 0.62f * H - 40f + i * 3f }, pxPerFrame = 80f)
        k.hold(8)
        k.finish()
    }

    // =================================================================================
    // R3 — tela inicial: pastas com capa, tema claro -> noturno, rolagem e #prova
    // =================================================================================
    @Test
    fun r3() {
        val out = outDir("r3")
        resetLibrary(dark = false)
        Library.folders.toList().forEach { Library.deleteFolder(it.id) }
        val bio = Library.createFolder("Biologia", Library.folderColors[2], null)
        val qui = Library.createFolder("Química", Library.folderColors[0], null)
        val fis = Library.createFolder("Física", Library.folderColors[5], null)
        val red = Library.createFolder("Redação", Library.folderColors[4], null)
        Library.createFolder("Anatomia", Library.folderColors[7], null)
        Library.io.submit {}.get()
        writeCover(Library.coverFile(bio.id))

        val cel = Library.createNote(bio.id, "Biologia celular")
        writeNote(cel, listOf("Mitocôndria" to fountain(), "a usina de energia" to pen(blue), "cai na prova!" to pen(this.red)))
        val res = Library.createNote(bio.id, "Resumo P1")
        writeNote(res, listOf("Resumo P1" to fountain(), "membrana dupla" to pen(ink), "DNA próprio" to pen(ink)))
        Library.setPinned(res, true)
        Library.setTags(res, listOf("prova", "resumo"))
        val krebs = Library.createNote(bio.id, "Ciclo de Krebs")
        writeNote(krebs, listOf("Krebs" to PenSettings(BrushType.CALLIGRAPHY, this.red, 9f, 100), "citrato" to pen(blue), "NADH" to pen(this.red)))
        Library.setFavorite(krebs, true)
        Library.setTags(krebs, listOf("prova"))
        val enem = Library.createNote(red.id, "Redação ENEM")
        writeNote(enem, listOf("Redação" to fountain(), "tese + 2 argumentos" to pen(ink)))
        val ondas = Library.createNote(fis.id, "Ondas")
        writeNote(ondas, listOf("Ondas" to fountain(), "frequência e período" to pen(blue)))
        val mols = Library.createNote(qui.id, "Ligações")
        writeNote(mols, listOf("Ligações" to fountain(), "iônica e covalente" to pen(ink)))
        Library.updateNote(res, touch = true)
        Library.io.submit {}.get()

        val home = Robolectric.buildActivity(FoldersActivity::class.java).setup().get()
        val k = DemoKit(home, out, scale = 0.9f)
        k.layout()
        Thread.sleep(300); ShadowLooper.idleMainLooper()
        fun card(act: android.app.Activity, name: String): android.view.View {
            var v: android.view.View = allViews(act.window.decorView).filterIsInstance<android.widget.TextView>().last { it.text.toString() == name }
            while (!v.isClickable && v.parent is android.view.View) v = v.parent as android.view.View
            return v
        }
        fun anchors(suffix: String) {
            for (n in listOf("Biologia", "Química", "Física", "Redação")) runCatching { k.anchor(n + suffix, card(k.act, n)) }
            runCatching { k.anchor("tema$suffix", k.find("Tema")) }
            runCatching { k.anchor("chipProva$suffix", k.find("#prova")) }
        }
        anchors("")
        k.mark("claro")
        k.hold(36)

        // tema: toque no sol, escolhe Noturno
        k.mark("sol")
        k.tap(k.find("Tema"), 4)
        k.hold(4)
        k.tap(k.find("Noturno"), 4, click = false)
        Ui.setThemeChoice(app, "dark")
        k.dismissDialog()
        val dark = Robolectric.buildActivity(FoldersActivity::class.java).setup().get()
        k.act = dark
        k.layout()
        Thread.sleep(300); ShadowLooper.idleMainLooper()
        k.mark("escuro")
        anchors("Escuro")
        k.hold(16)

        // rolagem até as etiquetas
        k.mark("rola")
        val sv = allViews(dark.window.decorView).filterIsInstance<ScrollView>().first()
        val chip = k.find("#prova")
        val loc = IntArray(2).also { chip.getLocationInWindow(it) }
        val screenH = dark.window.decorView.height.toFloat()
        val maxScroll = (sv.getChildAt(0).height - sv.height).coerceAtLeast(0)
        val dist = (loc[1] - 0.30f * screenH).toInt().coerceIn(0, maxScroll)
        val fx = 0.62f * dark.window.decorView.width
        val fy0 = 0.78f * screenH
        for (i in 1..20) {
            val e = i / 20f
            val s = e * e * (3 - 2 * e)
            sv.scrollTo(0, (dist * s).toInt())
            k.capture("finger", listOf(fx to fy0 - dist * s))
        }
        k.capture("finger", listOf(fx to fy0 - dist), up = true)
        k.hold(6)
        anchors("Rola")

        k.mark("prova")
        k.tap(k.find("#prova"), 4)
        k.hold(14)
        k.finish()
        Ui.setThemeChoice(app, "light")
    }

    // =================================================================================
    // R4 — noturno: a mesma anotação em 6 modelos de folha; PDF do slide com escrita
    // =================================================================================
    @Test
    fun r4() {
        val ed = freshEditor("Aula de Biologia", dark = true)
        val noteId = Library.notes.first { it.title == "Aula de Biologia" }.id
        val k = DemoKit(ed, outDir("r4"), scale = 1.0f)
        k.layout()
        val c = k.canvas
        val W = c.width.toFloat()
        val H = c.height.toFloat()
        k.quiet { c.setPageLayout(true, false, false, true, 1) }
        val page = c.pageLayout.pageRect(0)
        val z = W / page.width()
        k.setView(-page.left * z, 0.02f * H - page.top * z, z)
        k.quiet {
            fun text(t: String, fx: Float, fy: Float, cap: Float, p: PenSettings) {
                val (wx, wy) = k.world(fx * W, fy * H); k.bake(k.bakeText(t, wx, wy, cap / z, p))
            }
            text("Mitocôndria", 0.34f, 0.22f, 80f, fountain())
            text("a usina de energia", 0.34f, 0.30f, 42f, pen(ink))
            text("da célula", 0.34f, 0.36f, 42f, pen(ink))
            text("cai na prova!", 0.34f, 0.46f, 48f, pen(red))
            val (ex, ey) = k.world(0.34f * W + k.textWidth("cai na prova!", 48f) / 2, 0.46f * H - 16f)
            k.bake(listOf(k.bakeStroke(k.ellipse(ex, ey, (k.textWidth("cai na prova!", 48f) * 0.62f) / z, 50f / z, rotDeg = -3f), pen(red))))
            text("tem DNA próprio", 0.34f, 0.54f, 42f, pen(blue))
        }
        for ((st, name) in listOf(PageStyle.LINES to "lines", PageStyle.GRID to "grid", PageStyle.COLLEGE to "college",
                PageStyle.CORNELL to "cornell", PageStyle.MUSIC to "music", PageStyle.PLANNER to "planner")) {
            k.quiet { c.pageStyle = st; c.invalidate() }
            k.mark(name)
            k.hold(6)
        }

        // PDF do slide (página importada, travada, como o importador cria)
        val dir = Library.assetsDir(noteId).apply { mkdirs() }
        writeSlide(File(dir, "slide_krebs.jpg"))
        k.quiet {
            c.setPageLayout(false, false, false, true, 1)
            c.pageStyle = PageStyle.DOTS
            val (wx, wy) = k.world(0f, 0f)
            val left = wx + 4000f
            val top = wy
            val sw = 1600f
            val sh = 900f
            c.addPages(listOf(ImageElement("slide_krebs.jpg", RectF(left, top, left + sw, top + sh), shadow = true, locked = true, pdfPage = 48)))
            val zz = 0.92f * W / sw
            k.setView(0.04f * W - left * zz, 0.25f * H - top * zz, zz)
            c.tool = InfiniteCanvasView.Tool.PEN
        }
        Thread.sleep(700); ShadowLooper.idleMainLooper()
        k.mark("pdf")
        k.hold(3)
        k.tap(quickDots(k)[1], 4)
        val textY = 0.64f * H
        val endX = k.write("cai na prova!", 0.12f * W, textY, cap = 56f, pxPerFrame = 45f)
        // elipse no 2º tópico do slide e seta do texto até ela
        val (sl, stp) = k.world(0.04f * W, 0.25f * H)
        val (tx, ty) = k.screen(sl + 440f, stp + 452f)
        k.stroke(k.ellipse(tx, ty, 0.21f * W, 0.035f * H, rotDeg = -2f, n = 32), pxPerFrame = 70f)
        val ax0 = 0.20f * W to textY - 70f
        val ax1 = tx - 0.10f * W to ty + 0.045f * H
        k.stroke(listOf(ax0, ax1), pxPerFrame = 60f)
        k.stroke(listOf(ax1.first - 4f to ax1.second + 40f, ax1.first to ax1.second, ax1.first + 38f to ax1.second + 12f), pxPerFrame = 60f)
        k.note("pdfEnd", endX)
        k.mark("fim")
        k.hold(8)
        k.finish()
        Ui.setThemeChoice(app, "light")
    }

    // =================================================================================
    // R5 — a espiral da rosa da logo, desenhada com a caneta (para o match cut)
    // =================================================================================
    @Test
    fun r5() {
        val ed = freshEditor("Rosa", dark = true)
        val k = DemoKit(ed, outDir("r5"), scale = 1.25f)
        k.layout()
        val c = k.canvas
        val W = c.width.toFloat()
        val H = c.height.toFloat()
        k.quiet { c.pageStyle = PageStyle.BLANK; c.invalidate() }
        val unit = 14f
        val ox = W / 2
        val oy = 0.47f * H
        fun m(lx: Float, ly: Float) = ox + (lx - 55.5f) * unit to oy + (ly - 42f) * unit
        k.note("logoMap", JSONArray(listOf((ox + k.originX()) * 1.25, (oy + k.originY()) * 1.25, unit * 1.25, 55.5, 42.0)))
        k.usePen(PenSettings(BrushType.PEN, red, 22f, 100))
        k.hold(6)
        val spiral = ArrayList<Pair<Float, Float>>()
        val segs = listOf(
            listOf(54.5f, 37f, 52.5f, 36f, 50.5f, 38f, 51.8f, 40.2f),
            listOf(51.8f, 40.2f, 53.3f, 42.6f, 58f, 41.6f, 58f, 37.6f),
            listOf(58f, 37.6f, 58f, 33.2f, 52.5f, 31.6f, 49.6f, 34.6f),
            listOf(49.6f, 34.6f, 46.4f, 38f, 47.6f, 44.6f, 54f, 45.6f),
            listOf(54f, 45.6f, 60.5f, 46.5f, 64.8f, 42f, 64.6f, 36f),
        )
        for (sg in segs) spiral += k.cubic(m(sg[0], sg[1]), m(sg[2], sg[3]), m(sg[4], sg[5]), m(sg[6], sg[7]), 30)
        k.mark("espiral")
        k.stroke(spiral, pxPerFrame = 30f)
        k.mark("arco")
        val arc = k.cubic(m(42.5f, 40f), m(42.5f, 49.5f), m(48.5f, 53.8f), m(55f, 53.8f), 30) +
            k.cubic(m(55f, 53.8f), m(62f, 53.8f), m(67.5f, 49.5f), m(68.5f, 42.5f), 30)
        k.stroke(arc, pxPerFrame = 30f)
        k.mark("fim")
        k.hold(6)
        k.finish()
        Ui.setThemeChoice(app, "light")
    }

    // ---- Apoio ------------------------------------------------------------------------

    private fun allViews(v: android.view.View): List<android.view.View> =
        if (v is android.view.ViewGroup) listOf(v) + (0 until v.childCount).flatMap { allViews(v.getChildAt(it)) } else listOf(v)

    /** Nota com algumas linhas manuscritas e miniatura (como se tivesse sido escrita). */
    private fun writeNote(n: NoteInfo, lines: List<Pair<String, PenSettings>>) {
        val unit = app.resources.displayMetrics.density * 0.5f
        val els = ArrayList<Element>()
        lines.forEachIndexed { i, (t, p) ->
            val cap = if (i == 0) 70f else 40f
            val y = 90f + i * 90f
            for (st in Cursive.glyphStrokes(t, 40f, y, cap)) {
                val b = StrokeBuilder(p, unit)
                st.forEachIndexed { j, (x, yy) -> b.add(x, yy, 0.7f + 0.2f * sin(j / 5f), 0.3f) }
                els.add(b.build())
            }
        }
        val data = NoteData(els, ViewState(60f, 230f, 1.15f), PageStyle.DOTS, Color.WHITE)
        NoteStorage.save(Library.noteFile(n.id), data)
        val view = InfiniteCanvasView(app)
        view.assetDir = Library.assetsDir(n.id)
        view.load(data)
        view.renderToBitmap(480, 360, 1f, thumbnail = true)?.let { b ->
            Library.thumbFile(n.id).apply { parentFile?.mkdirs() }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        Library.textFile(n.id).writeText(lines.joinToString("\n") { it.first })
    }

    /** Capa da pasta Biologia: mitocôndria rosa sobre creme. */
    private fun writeCover(f: File) {
        val w = 900; val h = 600
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.parseColor("#FBEAEC"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#F6C6CE")
        c.drawCircle(760f, 110f, 160f, p)
        c.save(); c.rotate(-12f, 450f, 320f)
        p.color = Color.parseColor("#E8404F")
        c.drawOval(RectF(150f, 170f, 750f, 470f), p)
        p.color = Color.parseColor("#FFD7DD")
        c.drawOval(RectF(185f, 200f, 715f, 440f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 18f; p.strokeCap = Paint.Cap.ROUND; p.color = Color.parseColor("#C2253B")
        val path = android.graphics.Path()
        path.moveTo(220f, 320f)
        for (i in 0..40) { val x = 220f + i * 11.5f; path.lineTo(x, 320f + 70f * sin(i / 40f * 5 * 2 * PI).toFloat()) }
        c.drawPath(path, p)
        c.restore()
        f.parentFile?.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    /** "Slide" de aula (o que seria uma página de PDF importada). */
    private fun writeSlide(f: File) {
        val w = 1600; val h = 900
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#1E3A6B"); c.drawRect(0f, 0f, w.toFloat(), 18f, p)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 92f; p.color = Color.parseColor("#1E3A6B")
        c.drawText("Ciclo de Krebs", 120f, 210f, p)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        p.textSize = 54f; p.color = Color.parseColor("#2B2B30")
        c.drawText("•  Acetil-CoA entra no ciclo", 120f, 360f, p)
        c.drawText("•  gera 3 NADH + 1 FADH2", 120f, 470f, p)
        c.drawText("•  ocorre na matriz mitocondrial", 120f, 580f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 10f; p.color = Color.parseColor("#8FA8D6")
        c.drawCircle(1340f, 520f, 150f, p)
        p.style = Paint.Style.FILL; p.textSize = 34f; p.color = Color.parseColor("#8A8F98")
        c.drawText("48/112", 1440f, 860f, p)
        c.drawText("Bioquímica — Aula 6", 120f, 860f, p)
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}
