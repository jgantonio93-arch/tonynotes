package com.jgantonio.notasinfinitas

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import java.util.Locale
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
    private val views = StringBuilder("[")
    private val markers = JSONObject()
    private val anchors = JSONObject()
    /** Em modo silencioso as ações acontecem no app, mas nenhum quadro é gravado. */
    var quiet = false
    /** Traços feitos até agora (para desfazer um trecho exato). */
    var strokesDone = 0; private set

    val editor get() = act as EditorActivity
    val canvas get() = editor.canvasForTests

    fun quiet(block: () -> Unit) {
        val was = quiet
        quiet = true
        try { block() } finally { quiet = was }
    }

    // ---- Quadros -----------------------------------------------------------------------

    fun layout() {
        ShadowLooper.idleMainLooper()
        val dm = act.resources.displayMetrics
        val decor = act.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /** Painel inferior aberto (Dialog), se houver. */
    private fun openDialog(): Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    /** Onde o painel aberto é desenhado no quadro (centralizado, encostado embaixo). */
    private fun layoutDialog(d: Dialog): Pair<Int, Int> {
        val dm = act.resources.displayMetrics
        val content = d.window!!.decorView
        val w = min(dm.widthPixels, (600 * dm.density).toInt())
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST))
        content.layout(0, 0, w, content.measuredHeight)
        return (dm.widthPixels - w) / 2 to (dm.heightPixels - content.measuredHeight)
    }

    /** Posição de [v] no quadro (inclusive se estiver dentro do painel aberto). */
    private fun origin(v: View): Pair<Int, Int> {
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val d = openDialog()
        if (d != null && v.rootView === d.window!!.decorView) {
            val (dx, dy) = layoutDialog(d)
            return loc[0] + dx to loc[1] + dy
        }
        return loc[0] to loc[1]
    }

    fun capture(kind: String = "none", pts: List<Pair<Float, Float>> = emptyList(), up: Boolean = false, on: View? = null) {
        if (quiet) return
        layout()
        val decor = act.window.decorView
        val bmp = Bitmap.createBitmap((decor.width * scale).toInt(), (decor.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        decor.draw(c)
        openDialog()?.let { d ->
            val (dx, dy) = layoutDialog(d)
            c.drawColor(Color.argb(82, 0, 0, 0))
            c.save(); c.translate(dx.toFloat(), dy.toFloat())
            d.window!!.decorView.draw(c)
            c.restore()
        }
        FileOutputStream(File(out, "f%04d.jpg".format(frame))).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        val (ox, oy) = if (on != null) origin(on) else 0 to 0
        val p = pts.joinToString(",") { (x, y) -> "[${((x + ox) * scale).toInt()},${((y + oy) * scale).toInt()}]" }
        touches.append("""{"k":"$kind","up":$up,"p":[$p]},""")
        // Câmera da nota (para a edição prender a grade de pontos ao mundo).
        val ed = act as? EditorActivity
        if (ed != null) {
            val v = ed.canvasForTests.viewForTests
            val (cx, cy) = origin(ed.canvasForTests)
            views.append("[${"%.2f".format(Locale.US, (v[0] + cx) * scale)},${"%.2f".format(Locale.US, (v[1] + cy) * scale)},${"%.4f".format(Locale.US, v[2] * scale)}],")
        } else views.append("null,")
        frame++
    }

    fun hold(frames: Int) = repeat(frames) { capture() }

    fun mark(name: String) { markers.put(name, frame) }

    fun note(name: String, value: Any) { markers.put(name, value) }

    /** Guarda o retângulo de [v] no quadro (x, y, largura, altura), para a edição. */
    fun anchor(name: String, v: View) {
        layout()
        val (x, y) = origin(v)
        anchors.put(name, JSONArray(listOf(x * scale, y * scale, v.width * scale, v.height * scale).map { it.toDouble() }))
    }

    fun anchorRect(name: String, x: Float, y: Float, w: Float, h: Float, on: View) {
        val (ox, oy) = origin(on)
        anchors.put(name, JSONArray(listOf((x + ox) * scale, (y + oy) * scale, w * scale, h * scale).map { it.toDouble() }))
    }

    fun finish() {
        if (touches.endsWith(",")) touches.setLength(touches.length - 1)
        touches.append("]")
        if (views.endsWith(",")) views.setLength(views.length - 1)
        views.append("]")
        File(out, "touches.json").writeText(touches.toString())
        File(out, "view.json").writeText(views.toString())
        File(out, "markers.json").writeText(markers.toString())
        File(out, "anchors.json").writeText(anchors.toString())
        val decor = act.window.decorView
        File(out, "info.json").writeText("""{"frames":$frame,"width":${(decor.width * scale).toInt()},"height":${(decor.height * scale).toInt()},"fps":30,"scale":$scale}""")
        println("demo: $frame quadros em ${out.path}")
    }

    // ---- Encontrar e tocar em vistas --------------------------------------------------

    private fun all(v: View): List<View> =
        if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    /** Vista com esse texto (ou descrição), no painel aberto ou na tela; sobe até a clicável. */
    fun find(text: String): View {
        layout()
        val roots = listOfNotNull(openDialog()?.window?.decorView, act.window.decorView)
        for (r in roots) {
            if (r !== act.window.decorView) openDialog()?.let { layoutDialog(it) }
            val hit = all(r).firstOrNull { v ->
                v.isShown && (v.contentDescription?.toString() == text ||
                    (v is TextView && v.text.toString().trim().startsWith(text)))
            } ?: continue
            var c: View = hit
            while (!c.isClickable && c.parent is View) c = c.parent as View
            return c
        }
        error("Não achei \"$text\" na tela")
    }

    /** Toque com o dedo numa vista: mostra o toque alguns quadros e clica. */
    fun tap(v: View, frames: Int = 6, click: Boolean = true) {
        val cx = v.width / 2f
        val cy = v.height / 2f
        repeat(frames / 2) { capture("tap", listOf(cx to cy), on = v) }
        if (click) v.performClick()
        ShadowLooper.idleMainLooper()
        repeat(frames - frames / 2) { capture("tap", listOf(cx to cy), up = it == frames - frames / 2 - 1, on = v) }
    }

    fun tap(text: String, frames: Int = 6) = tap(find(text), frames)

    fun dismissDialog() {
        openDialog()?.dismiss()
        ShadowLooper.idleMainLooper()
    }

    // ---- Câmera da nota ---------------------------------------------------------------

    /** Canto do canvas na janela (para converter coordenadas do canvas em coordenadas do quadro). */
    fun originX() = origin(canvas).first
    fun originY() = origin(canvas).second

    fun setView(ox: Float, oy: Float, s: Float) { canvas.setViewForTests(ox, oy, s); layout() }
    val view get() = canvas.viewForTests
    /** Mundo -> tela (coordenadas do canvas). */
    fun screen(wx: Float, wy: Float): Pair<Float, Float> { val v = view; return wx * v[2] + v[0] to wy * v[2] + v[1] }
    /** Tela -> mundo. */
    fun world(sx: Float, sy: Float): Pair<Float, Float> { val v = view; return (sx - v[0]) / v[2] to (sy - v[1]) / v[2] }

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
        strokesDone++
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

    // ---- Ferramentas e barra ------------------------------------------------------------

    /** Troca a ferramenta como o botão da barra faria (a barra se atualiza). */
    fun useTool(tool: InfiniteCanvasView.Tool) {
        canvas.tool = tool
        editor.relayoutDockForTests()
        ShadowLooper.idleMainLooper()
    }

    fun usePen(p: PenSettings) {
        canvas.pen = p
        canvas.tool = InfiniteCanvasView.Tool.PEN
        editor.relayoutDockForTests()
        ShadowLooper.idleMainLooper()
    }

    /** Laço de seleção (caneta) pelos pontos dados; o fecho volta ao primeiro ponto. */
    fun lasso(pts: List<Pair<Float, Float>>, pxPerFrame: Float = 40f) {
        useTool(InfiniteCanvasView.Tool.SELECT)
        stroke(pts + listOf(pts.first()), pxPerFrame = pxPerFrame, kind = "pen")
    }

    /** Arrasta a seleção a partir de um ponto dentro dela. */
    fun dragSelection(x: Float, y: Float, dx: Float, dy: Float, frames: Int) {
        val down = time
        val t = MotionEvent.TOOL_TYPE_STYLUS
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(x to y), t, down))
        for (i in 1..frames) {
            val k = i / frames.toFloat()
            val e = k * k * (3 - 2 * k)
            val p = x + dx * e to y + dy * e
            canvas.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(p), t, down))
            capture("pen", listOf(p), on = canvas)
        }
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(x + dx to y + dy), t, down))
        ShadowLooper.idleMainLooper()
        capture("pen", listOf(x + dx to y + dy), up = true, on = canvas)
    }

    /** Arrasta a barra de ferramentas pela alça (perto da borda ela fica em pé). */
    fun dragDock(dx: Float, dy: Float, frames: Int) {
        val grip = editor.dockGripForTests
        val down = time
        val f = MotionEvent.TOOL_TYPE_FINGER
        val x = grip.width / 2f
        val y = grip.height / 2f
        grip.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, listOf(x to y), f, down))
        val (ox, oy) = origin(grip)
        for (i in 1..frames) {
            val k = i / frames.toFloat()
            val e = k * k * (3 - 2 * k)
            grip.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, listOf(x + dx * e to y + dy * e), f, down))
            // o toque acompanha o dedo (a alça se move junto com a barra)
            val p = listOf((ox + x + dx * e) to (oy + y + dy * e))
            capture("finger", p)
        }
        grip.dispatchTouchEvent(event(MotionEvent.ACTION_UP, listOf(x + dx to y + dy), f, down))
        ShadowLooper.idleMainLooper()
        layout()
        capture("finger", listOf((ox + x + dx) to (oy + y + dy)), up = true)
    }

    /** Gira a seleção aos poucos (um passo por quadro), como girando pela alça. */
    fun rotateSelection(degrees: Float, frames: Int) {
        var done = 0f
        for (i in 1..frames) {
            val k = i / frames.toFloat()
            val target = degrees * (k * k * (3 - 2 * k))
            canvas.rotateSelection(target - done)
            done = target
            capture()
        }
    }

    /** Aumenta a seleção aos poucos (fator total [k]). */
    fun scaleSelection(k: Float, frames: Int) {
        var done = 1f
        for (i in 1..frames) {
            val e = i / frames.toFloat()
            val target = 1f + (k - 1f) * (e * e * (3 - 2 * e))
            canvas.scaleSelectionForTests(target / done)
            done = target
            capture()
        }
    }

    // ---- Traços prontos (sem gravar), em coordenadas do mundo ------------------------

    /** Traço pronto pelo construtor de pincéis do app (mesma tinta de quando se escreve). */
    fun bakeStroke(pts: List<Pair<Float, Float>>, pen: PenSettings): StrokeElement {
        val b = StrokeBuilder(pen, canvas.unit)
        val path = resample(pts, 2f)
        path.forEachIndexed { i, (x, y) -> b.add(x, y, 0.6f + 0.3f * sin(i / 9f).toFloat(), 0.3f) }
        return b.build()
    }

    /** Texto cursivo pronto no mundo (linha de base em [wy]). */
    fun bakeText(text: String, wx: Float, wy: Float, cap: Float, pen: PenSettings): List<StrokeElement> {
        val out = ArrayList<StrokeElement>()
        glyphStrokes(text, wx, wy, cap).forEach { out.add(bakeStroke(it, pen)) }
        return out
    }

    fun bake(list: List<StrokeElement>) { canvas.addForTests(list); layout() }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotDeg: Float = 0f, n: Int = 64, overlap: Float = 0.06f): List<Pair<Float, Float>> {
        val r = Math.toRadians(rotDeg.toDouble())
        return (0..n).map {
            val a = -0.5 + it / n.toDouble() * (2 * PI * (1 + overlap))
            val x = rx * kotlin.math.cos(a); val y = ry * kotlin.math.sin(a)
            (cx + x * kotlin.math.cos(r) - y * kotlin.math.sin(r)).toFloat() to (cy + x * kotlin.math.sin(r) + y * kotlin.math.cos(r)).toFloat()
        }
    }

    /** Curva de Bézier cúbica amostrada. */
    fun cubic(p0: Pair<Float, Float>, p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, n: Int = 24) =
        (0..n).map {
            val t = it / n.toFloat(); val u = 1 - t
            (u * u * u * p0.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first) to
                (u * u * u * p0.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second)
        }

    // ---- Letra cursiva -----------------------------------------------------------------

    fun textWidth(text: String, cap: Float) = Cursive.textWidth(text, cap)
    fun glyphStrokes(text: String, x: Float, y: Float, cap: Float, slant: Float = 0f) = Cursive.glyphStrokes(text, x, y, cap, slant)

    /**
     * Escreve [text] com a caneta atual a partir de (x, y) = linha de base (tela do canvas),
     * maiúsculas com [cap] px de altura. Devolve o x onde terminou.
     */
    fun write(text: String, x: Float, y: Float, cap: Float = 64f, pxPerFrame: Float = 26f, slant: Float = 0f): Float {
        glyphStrokes(text, x, y, cap, slant).forEach { stroke(it, pxPerFrame = pxPerFrame) }
        return x + textWidth(text, cap)
    }
}

/** Letra cursiva (fonte Hershey "cursive", domínio público) para escrever no app. */
object Cursive {
    class Glyph(val w: Float, val strokes: List<List<Pair<Float, Float>>>)

    val font: Map<Char, Glyph> by lazy {
        val txt = DemoKit::class.java.classLoader!!.getResourceAsStream("demo/cursive.json")!!.bufferedReader().readText()
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

    val accents = mapOf(
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

    /** Traços (em px) do texto cursivo a partir de (x, y) = linha de base. */
    fun glyphStrokes(text: String, x: Float, y: Float, cap: Float, slant: Float = 0f): List<List<Pair<Float, Float>>> {
        val s = cap / 21f
        var cx = x
        val out = ArrayList<List<Pair<Float, Float>>>()
        for (ch in text) {
            val (base, accent) = accents[ch] ?: (ch to null)
            val g = font[base] ?: font[' ']!!
            for (st in g.strokes) {
                if (st.size < 2) continue
                out.add(st.map { (gx, gy) -> cx + (gx - gy * slant) * s to y + gy * s })
            }
            if (accent != null) {
                // Acento depois da letra, centrado nela.
                val mid = cx + g.w * s * 0.55f
                val top = y + (if (base.isUpperCase()) -25f else -13f) * s
                out.add(when (accent) {
                    "acute" -> listOf(mid - 1.5f * s to top, mid + 2.5f * s to top - 4f * s)
                    "grave" -> listOf(mid - 2f * s to top - 4f * s, mid + 1.5f * s to top)
                    "circ" -> listOf(mid - 3f * s to top, mid to top - 3.5f * s, mid + 3f * s to top)
                    "tilde" -> (0..10).map { mid - 3.5f * s + it * 0.7f * s to top - 1.5f * s - 1.4f * s * sin(it / 10f * 2 * PI).toFloat() }
                    else -> listOf(mid - 1f * s to y + 0.5f * s, mid + 1f * s to y + 2.5f * s, mid - 1.5f * s to y + 4.5f * s)
                })
            }
            cx += g.w * s
        }
        return out
    }

}
