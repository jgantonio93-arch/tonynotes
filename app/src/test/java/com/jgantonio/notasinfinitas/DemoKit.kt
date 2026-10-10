package com.jgantonio.notasinfinitas

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Ferramentas para gravar o app de verdade quadro a quadro (vídeos de demonstração).
 *
 * Cada quadro é a tela real da atividade [act]; touches.json guarda, por quadro, onde
 * estão a caneta/dedos (coordenadas da imagem gravada) e markers.json marca momentos
 * com nome (para a edição sincronizar textos e efeitos).
 *
 * A letra cursiva vem de demo/cursive.json (fonte Hershey "cursive", domínio público):
 * o app recebe traços de caneta de verdade, então a escrita sai legível.
 */
class DemoKit(var act: Activity, private val out: File, private val scale: Float = 0.6f) {

    private var frame = 0
    private var time = 0L
    private val touches = StringBuilder("[")
    private val markers = JSONObject()

    val editor get() = act as EditorActivity
    val canvas get() = editor.canvasForTests

    // ---- Quadros -----------------------------------------------------------------------

    fun layout() {
        ShadowLooper.idleMainLooper()
        val dm = act.resources.displayMetrics
        val decor = act.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /** Posição de [v] na janela (para converter toques locais em coordenadas da imagem). */
    private fun origin(v: View): Pair<Int, Int> {
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        return loc[0] to loc[1]
    }

    fun capture(kind: String = "none", pts: List<Pair<Float, Float>> = emptyList(), up: Boolean = false, on: View? = null) {
        layout()
        val decor = act.window.decorView
        val bmp = Bitmap.createBitmap((decor.width * scale).toInt(), (decor.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        decor.draw(c)
        FileOutputStream(File(out, "f%04d.jpg".format(frame))).use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        bmp.recycle()
        val (ox, oy) = if (on != null) origin(on) else 0 to 0
        val p = pts.joinToString(",") { (x, y) -> "[${((x + ox) * scale).toInt()},${((y + oy) * scale).toInt()}]" }
        touches.append("""{"k":"$kind","up":$up,"p":[$p]},""")
        frame++
    }

    fun hold(frames: Int) = repeat(frames) { capture() }

    fun mark(name: String) { markers.put(name, frame) }

    fun finish() {
        if (touches.endsWith(",")) touches.setLength(touches.length - 1)
        touches.append("]")
        File(out, "touches.json").writeText(touches.toString())
        File(out, "markers.json").writeText(markers.toString())
        val decor = act.window.decorView
        File(out, "info.json").writeText("""{"frames":$frame,"width":${(decor.width * scale).toInt()},"height":${(decor.height * scale).toInt()},"fps":30}""")
        println("demo: $frame quadros em ${out.path}")
    }

    // ---- Toques ------------------------------------------------------------------------

    private fun event(action: Int, pts: List<Pair<Float, Float>>, tool: Int, down: Long, pressure: Float = 0.75f): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = tool } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; this.pressure = pressure; size = 1f } }
        time += 8
        return MotionEvent.obtain(down, time, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** Reamostra uma linha em passos de [step] px (movimento uniforme da caneta). */
    private fun resample(pts: List<Pair<Float, Float>>, step: Float): List<Pair<Float, Float>> {
        if (pts.size < 2) return pts
        val out = ArrayList<Pair<Float, Float>>()
        out.add(pts[0])
        var carry = 0f
        for (i in 1 until pts.size) {
            val (x0, y0) = pts[i - 1]
            val (x1, y1) = pts[i]
            val d = hypot(x1 - x0, y1 - y0)
            var t = step - carry
            while (t <= d) {
                out.add(x0 + (x1 - x0) * t / d to y0 + (y1 - y0) * t / d)
                t += step
            }
            carry = d - (t - step)
        }
        if (out.last() != pts.last()) out.add(pts.last())
        return out
    }

    /**
     * Um traço em [target] (normalmente o canvas). [pxPerFrame] = quanto da linha anda por
     * quadro gravado (velocidade da mão).
     */
    fun stroke(
        pts: List<Pair<Float, Float>>, tool: Int = MotionEvent.TOOL_TYPE_STYLUS, pxPerFrame: Float = 22f,
        target: View = canvas, kind: String? = null,
    ) {
        val path = resample(pts, 3f)
        val per = max(1, (pxPerFrame / 3f).toInt())
        val k = kind ?: if (tool == MotionEvent.TOOL_TYPE_STYLUS) "pen" else "finger"
        val down = time
        target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(path.first()), tool, down, 0.6f))
        for ((i, p) in path.drop(1).withIndex()) {
            val pr = 0.6f + 0.3f * sin(i / 9f).toFloat()
            target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), tool, down, pr))
            if (i % per == per - 1) capture(k, listOf(p), on = target)
        }
        target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(path.last()), tool, down))
        ShadowLooper.idleMainLooper()
        capture(k, listOf(path.last()), up = true, on = target)
    }

    /** Arrasto suave com um dedo (acelera e freia) em [frames] quadros. */
    fun pan(x: Float, y: Float, dx: Float, dy: Float, frames: Int, target: View = canvas) {
        val down = time
        val f = MotionEvent.TOOL_TYPE_FINGER
        target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(x to y), f, down))
        for (i in 1..frames) {
            val k = i / frames.toFloat()
            val e = k * k * (3 - 2 * k)
            val p = x + dx * e to y + dy * e
            target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), f, down))
            capture("finger", listOf(p), on = target)
        }
        target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(x + dx to y + dy), f, down))
        ShadowLooper.idleMainLooper()
        capture("finger", listOf(x + dx to y + dy), up = true, on = target)
    }

    /** Pinça centrada em (cx, cy): a distância entre os dedos vai de [from] a [to]. */
    fun pinch(cx: Float, cy: Float, from: Float, to: Float, frames: Int) {
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
            capture("pinch", at(d), on = canvas)
        }
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), at(to), f, down))
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(at(to)[0]), f, down))
        ShadowLooper.idleMainLooper()
        capture("none")
    }

    /** Toque rápido (dedo) num botão/vista: mostra o toque por alguns quadros e clica. */
    fun tap(v: View, frames: Int = 6) {
        val cx = v.width / 2f
        val cy = v.height / 2f
        repeat(frames / 2) { capture("tap", listOf(cx to cy), on = v) }
        v.performClick()
        ShadowLooper.idleMainLooper()
        repeat(frames - frames / 2) { capture("tap", listOf(cx to cy), up = it == frames - frames / 2 - 1, on = v) }
    }

    // ---- Letra cursiva -----------------------------------------------------------------

    private class Glyph(val w: Float, val strokes: List<List<Pair<Float, Float>>>)

    private val font: Map<Char, Glyph> by lazy {
        val txt = javaClass.classLoader!!.getResourceAsStream("demo/cursive.json")!!.bufferedReader().readText()
        val glyphs = JSONObject(txt).getJSONObject("glyphs")
        val m = HashMap<Char, Glyph>()
        for (key in glyphs.keys()) {
            val g = glyphs.getJSONObject(key)
            val ss = g.getJSONArray("s")
            val strokes = (0 until ss.length()).map { i ->
                val s = ss.getJSONArray(i)
                (0 until s.length()).map { j -> s.getJSONArray(j).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } }
            }
            m[key[0]] = Glyph(g.getDouble("w").toFloat(), strokes)
        }
        m
    }

    private val accents = mapOf(
        'á' to ('a' to "acute"), 'à' to ('a' to "grave"), 'â' to ('a' to "circ"), 'ã' to ('a' to "tilde"),
        'é' to ('e' to "acute"), 'ê' to ('e' to "circ"), 'í' to ('i' to "acute"), 'ó' to ('o' to "acute"),
        'ô' to ('o' to "circ"), 'õ' to ('o' to "tilde"), 'ú' to ('u' to "acute"), 'ç' to ('c' to "cedilla"),
        'Á' to ('A' to "acute"), 'É' to ('E' to "acute"), 'Ó' to ('O' to "acute"),
    )

    /** Largura do texto em px para a altura de maiúscula [cap]. */
    fun textWidth(text: String, cap: Float): Float {
        val s = cap / 21f
        return text.sumOf { ((font[accents[it]?.first ?: it] ?: font[' ']!!).w * s).toDouble() }.toFloat()
    }

    /**
     * Escreve [text] com a caneta atual a partir de (x, y) = linha de base, maiúsculas com
     * [cap] px de altura. Devolve o x onde terminou.
     */
    fun write(text: String, x: Float, y: Float, cap: Float = 64f, pxPerFrame: Float = 26f, slant: Float = 0f): Float {
        val s = cap / 21f
        var cx = x
        for (ch in text) {
            val (base, accent) = accents[ch] ?: (ch to null)
            val g = font[base] ?: font[' ']!!
            for (st in g.strokes) {
                if (st.size < 2) continue
                stroke(st.map { (gx, gy) -> cx + (gx - gy * slant) * s to y + gy * s }, pxPerFrame = pxPerFrame)
            }
            if (accent != null) {
                // Acento desenhado depois da letra, centrado na letra.
                val mid = cx + g.w * s * 0.55f
                val top = y + (if (base.isUpperCase()) -25f else -13f) * s
                val pts = when (accent) {
                    "acute" -> listOf(mid - 1.5f * s to top, mid + 2.5f * s to top - 4f * s)
                    "grave" -> listOf(mid - 2f * s to top - 4f * s, mid + 1.5f * s to top)
                    "circ" -> listOf(mid - 3f * s to top, mid to top - 3.5f * s, mid + 3f * s to top)
                    "tilde" -> (0..10).map { mid - 3.5f * s + it * 0.7f * s to top - 1.5f * s - 1.4f * s * sin(it / 10f * 2 * PI).toFloat() }
                    else -> listOf(mid - 1f * s to y + 0.5f * s, mid + 1f * s to y + 2.5f * s, mid - 1.5f * s to y + 4.5f * s)
                }
                stroke(pts, pxPerFrame = pxPerFrame)
            }
            cx += g.w * s
        }
        return cx
    }
}
