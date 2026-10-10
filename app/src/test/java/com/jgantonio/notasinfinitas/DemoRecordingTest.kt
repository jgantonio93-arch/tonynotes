package com.jgantonio.notasinfinitas

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Grava uma demonstração do app de verdade, quadro a quadro: a caneta escreve até a
 * borda, o dedo arrasta a folha, a escrita continua além dela, o marca-texto passa e o
 * zoom se afasta mostrando a nota sem fim. Cada quadro é a tela real do app.
 *
 * Só roda quando pedido (-Ddemo.dir=...), pelo workflow "Vídeo de demonstração".
 * Grava também touches.json com a posição da caneta/dedo em cada quadro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DemoRecordingTest {

    private lateinit var editor: EditorActivity
    private lateinit var canvas: InfiniteCanvasView
    private lateinit var out: File
    private var frame = 0
    private var time = 0L
    private val touches = StringBuilder("[")
    private var canvasX = 0
    private var canvasY = 0
    private val scale = 0.6f

    @Test
    fun gravarDemonstracao() {
        val dir = System.getProperty("demo.dir")
        assumeTrue("só roda no workflow do vídeo", !dir.isNullOrBlank())
        out = File(dir!!).apply { deleteRecursively(); mkdirs() }

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        app.getSharedPreferences("canetas", 0).edit().clear().commit()
        Library.init(app, reload = true)
        val note = Library.createNote(Library.folders.first().id, "Aula de biologia")
        editor = Robolectric.buildActivity(EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, note.id)).setup().get()
        canvas = editor.canvasForTests
        canvas.lowLatency = false
        layout()
        val loc = IntArray(2)
        canvas.getLocationInWindow(loc)
        canvasX = loc[0]; canvasY = loc[1]
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val ink = Color.parseColor("#1B1B1F")
        canvas.pen = PenSettings(BrushType.FOUNTAIN, ink, 5f, 100)

        hold(18)
        // 1. Escreve a primeira linha até encostar na borda direita.
        write(w * 0.08f, h * 0.18f, w * 0.95f, big = true)
        hold(10)
        // 2. Arrasta a folha para a esquerda com o dedo: aparece espaço novo.
        pan(w * 0.82f, h * 0.45f, -w * 0.62f, 0f, 34)
        hold(6)
        // 3. Continua a mesma linha além da antiga borda.
        write(w * 0.40f, h * 0.18f, w * 0.92f, big = true)
        hold(8)
        // 4. Troca para a cor vermelha rápida e circula uma palavra.
        clickQuickColor(1)
        hold(6)
        loop(w * 0.62f, h * 0.17f, w * 0.17f, h * 0.045f)
        hold(8)
        // 5. Desce a folha (rolagem vertical) e escreve mais linhas em azul.
        pan(w * 0.5f, h * 0.70f, 0f, -h * 0.30f, 26)
        clickQuickColor(2)
        hold(4)
        write(w * 0.10f, h * 0.30f, w * 0.80f, big = false)
        write(w * 0.10f, h * 0.37f, w * 0.65f, big = false)
        hold(6)
        // 6. Marca-texto (botão próprio) por cima de uma linha.
        editor.highlighterButtonForTests.performClick(); ShadowLooper.idleMainLooper()
        hold(6)
        stroke((0..30).map { w * 0.08f + it * (w * 0.55f / 30) to h * 0.30f + sin(it / 5f) }, MotionEvent.TOOL_TYPE_STYLUS, perFrame = 2)
        hold(6)
        editor.penButtonForTests.performClick(); ShadowLooper.idleMainLooper()
        // 7. Seta e uma nota para o lado de baixo, além da borda de baixo.
        clickQuickColor(1)
        stroke((0..24).map { w * 0.70f + it * 3.5f to h * 0.42f + it * 9f }, MotionEvent.TOOL_TYPE_STYLUS, perFrame = 2)
        stroke(listOf(w * 0.70f + 84f to h * 0.42f + 216f, w * 0.70f + 50f to h * 0.42f + 180f), MotionEvent.TOOL_TYPE_STYLUS, perFrame = 1)
        stroke(listOf(w * 0.70f + 84f to h * 0.42f + 216f, w * 0.70f + 96f to h * 0.42f + 168f), MotionEvent.TOOL_TYPE_STYLUS, perFrame = 1)
        clickQuickColor(0)
        write(w * 0.40f, h * 0.60f, w * 0.95f, big = false)
        hold(12)
        // 8. Pinça para afastar: a nota inteira aparece, espalhada além das bordas.
        pinch(w / 2, h / 2, w * 0.36f, w * 0.10f, 40)
        hold(36)

        touches.setLength(touches.length - 1)
        touches.append("]")
        File(out, "touches.json").writeText(touches.toString())
        File(out, "info.json").writeText("""{"frames":$frame,"width":${(editor.window.decorView.width * scale).toInt()},"height":${(editor.window.decorView.height * scale).toInt()},"fps":30}""")
        println("demo: $frame quadros em ${out.path}")
    }

    // ---- Gestos (cada quadro = 1/30 s) ------------------------------------------------

    private fun clickQuickColor(i: Int) {
        val box = editor.quickColorsForTests
        val dots = (0 until box.childCount).map { box.getChildAt(it) }.filterIsInstance<ColorDot>()
        dots.getOrNull(i)?.performClick()
        ShadowLooper.idleMainLooper()
    }

    private fun event(action: Int, pts: List<Pair<Float, Float>>, tool: Int, down: Long): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = tool } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 0.75f; size = 1f } }
        time += 8
        return MotionEvent.obtain(down, time, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** Um traço; grava um quadro a cada [perFrame] pontos. */
    private fun stroke(pts: List<Pair<Float, Float>>, tool: Int, perFrame: Int) {
        val down = time
        val kind = if (tool == MotionEvent.TOOL_TYPE_STYLUS) "pen" else "finger"
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(pts.first()), tool, down))
        for ((i, p) in pts.drop(1).withIndex()) {
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), tool, down))
            if (i % perFrame == 0) capture(kind, listOf(p))
        }
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(pts.last()), tool, down))
        ShadowLooper.idleMainLooper()
        capture(kind, listOf(pts.last()), up = true)
    }

    /** Palavras em letra cursiva (laços) de [x0] até [x1] na altura [y]. */
    private fun write(x0: Float, y: Float, x1: Float, big: Boolean) {
        val rnd = java.util.Random((x0 * 7 + y).toLong())
        var x = x0
        val hBig = if (big) 46f else 32f
        val hSmall = if (big) 26f else 18f
        val step = if (big) 2.4f else 1.9f
        while (x < x1) {
            val letters = 2 + rnd.nextInt(4)
            val pts = ArrayList<Pair<Float, Float>>()
            var t = 0f
            val stop = (letters * 2 * PI).toFloat()
            while (t < stop) {
                val hh = if ((t / (2 * PI)).toInt() % 3 == 1) hBig else hSmall
                pts.add(x + (if (big) 15f else 11f) * sin(t) to y - hh * (0.5f - 0.5f * cos(t)))
                t += 0.32f
                x += step
            }
            stroke(pts, MotionEvent.TOOL_TYPE_STYLUS, perFrame = 3)
            x += (if (big) 40f else 30f) + rnd.nextFloat() * 18f
        }
    }

    private fun loop(cx: Float, cy: Float, rx: Float, ry: Float) =
        stroke((0..58).map { cx + rx * cos(it / 52.0 * 2 * PI - 0.4).toFloat() to cy + ry * sin(it / 52.0 * 2 * PI - 0.4).toFloat() },
            MotionEvent.TOOL_TYPE_STYLUS, perFrame = 2)

    /** Arrasto com um dedo, suave (acelera e freia). */
    private fun pan(x: Float, y: Float, dx: Float, dy: Float, frames: Int) {
        val pts = (0..frames).map {
            val k = it / frames.toFloat()
            val e = k * k * (3 - 2 * k)
            x + dx * e to y + dy * e
        }
        stroke(pts, MotionEvent.TOOL_TYPE_FINGER, perFrame = 1)
    }

    private fun pinch(cx: Float, cy: Float, from: Float, to: Float, frames: Int) {
        val f = MotionEvent.TOOL_TYPE_FINGER
        val down = time
        fun at(d: Float) = listOf(cx - d to cy - d * 0.6f, cx + d to cy + d * 0.6f)
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(at(from)[0]), f, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), at(from), f, down))
        for (i in 1..frames) {
            val k = i / frames.toFloat()
            val e = k * k * (3 - 2 * k)
            val d = from + (to - from) * e
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, at(d), f, down))
            capture("pinch", at(d))
        }
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), at(to), f, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(at(to)[0]), f, down))
        ShadowLooper.idleMainLooper()
        capture("none", emptyList())
    }

    private fun hold(frames: Int) = repeat(frames) { capture("none", emptyList()) }

    // ---- Captura ----------------------------------------------------------------------

    private fun layout() {
        ShadowLooper.idleMainLooper()
        val dm = editor.resources.displayMetrics
        val decor = editor.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun capture(kind: String, pts: List<Pair<Float, Float>>, up: Boolean = false) {
        layout()
        val decor = editor.window.decorView
        val bmp = Bitmap.createBitmap((decor.width * scale).toInt(), (decor.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        decor.draw(c)
        FileOutputStream(File(out, "f%04d.jpg".format(frame))).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        val p = pts.joinToString(",") { (x, y) -> "[${((x + canvasX) * scale).toInt()},${((y + canvasY) * scale).toInt()}]" }
        touches.append("""{"k":"$kind","up":$up,"p":[$p]},""")
        frame++
    }
}
