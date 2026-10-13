package com.jgantonio.notasinfinitas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Leitor próprio (offline) de apresentações do PowerPoint (.pptx). Desenha cada slide
 * de forma aproximada (fundo, formas simples, textos, imagens e tabelas) e salva como
 * JPEG, igual às páginas de PDF, para entrar na nota como página travada.
 */
object PptxImporter {

    /** Arquivo que não é .pptx (por exemplo, .ppt antigo ou com senha). */
    class NotPptx : Exception("Não é um arquivo .pptx")

    private const val LONG_SIDE = 2000f
    private const val EMU_PER_PT = 12700f

    fun isPowerPoint(name: String?, mime: String?): Boolean {
        val n = (name ?: "").lowercase()
        return n.endsWith(".pptx") || n.endsWith(".ppt") || n.endsWith(".ppsx") ||
            mime == MIME_PPTX || mime == MIME_PPT || mime == "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
    }

    const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val MIME_PPT = "application/vnd.ms-powerpoint"

    fun render(
        context: Context,
        uri: Uri,
        dir: File,
        progress: (Int, Int) -> Unit,
        cancelled: () -> Boolean,
    ): List<PdfImporter.Page> {
        val tmp = File(context.cacheDir, "pptx_${System.nanoTime()}.pptx")
        try {
            (context.contentResolver.openInputStream(uri) ?: throw NotPptx()).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            return renderFile(tmp, dir, progress, cancelled)
        } finally {
            tmp.delete()
        }
    }

    class Imported(val pages: List<PdfImporter.Page>, val viaGoogle: Boolean)

    /**
     * Abre a apresentação: com [convert] (Google Drive conectado), tenta a conversão
     * fiel em PDF; se não der, usa o leitor próprio. .ppt antigo só abre pelo Google.
     */
    fun importAny(
        context: Context,
        uri: Uri,
        name: String,
        dir: File,
        progress: (Int, Int) -> Unit,
        cancelled: () -> Boolean,
        convert: ((name: String, bytes: ByteArray, mime: String) -> ByteArray)?,
    ): Imported {
        val bytes = (context.contentResolver.openInputStream(uri) ?: throw NotPptx()).use { it.readBytes() }
        if (convert != null) {
            val legacy = name.lowercase().endsWith(".ppt")
            val pdf = File(context.cacheDir, "slides_${System.nanoTime()}.pdf")
            try {
                pdf.writeBytes(convert(name, bytes, if (legacy) MIME_PPT else MIME_PPTX))
                val pages = PdfImporter.render(context, Uri.fromFile(pdf), dir, progress, cancelled)
                if (pages.isNotEmpty() || cancelled()) return Imported(pages, true)
            } catch (e: Exception) {
                // Sem internet, sem permissão ou arquivo grande demais: segue offline.
            } finally {
                pdf.delete()
            }
        }
        val tmp = File(context.cacheDir, "pptx_${System.nanoTime()}.pptx")
        try {
            tmp.writeBytes(bytes)
            return Imported(renderFile(tmp, dir, progress, cancelled), false)
        } finally {
            tmp.delete()
        }
    }

    fun renderFile(file: File, dir: File, progress: (Int, Int) -> Unit, cancelled: () -> Boolean): List<PdfImporter.Page> {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw NotPptx()
        }
        zip.use { z ->
            if (z.getEntry("ppt/presentation.xml") == null) throw NotPptx()
            return Deck(z).render(dir, progress, cancelled)
        }
    }

    // ---- XML mínimo ---------------------------------------------------------------------

    internal class Node(val name: String, val attrs: Map<String, String>) {
        val kids = ArrayList<Node>(2)
        val text = StringBuilder()
        operator fun get(n: String): Node? = kids.firstOrNull { it.name == n }
        fun all(n: String): List<Node> = kids.filter { it.name == n }
        fun attr(n: String): String? = attrs[n]
        fun num(n: String): Float? = attrs[n]?.toFloatOrNull()
        /** Atributo com prefixo (r:id, r:embed), seja qual for o prefixo. */
        fun rel(local: String): String? = attrs.entries.firstOrNull { it.key.endsWith(":$local") }?.value
        fun path(vararg ns: String): Node? {
            var c: Node? = this
            for (n in ns) c = c?.get(n)
            return c
        }
        fun find(n: String): Node? {
            for (k in kids) {
                if (k.name == n) return k
                k.find(n)?.let { return it }
            }
            return null
        }
    }

    internal fun parseXml(bytes: ByteArray): Node {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(bytes), null)
        val root = Node("#", emptyMap())
        val stack = arrayListOf(root)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val a = HashMap<String, String>()
                    for (i in 0 until p.attributeCount) a[p.getAttributeName(i)] = p.getAttributeValue(i)
                    val n = Node(p.name.substringAfter(':'), a)
                    stack.last().kids.add(n)
                    stack.add(n)
                }
                XmlPullParser.END_TAG -> if (stack.size > 1) stack.removeAt(stack.size - 1)
                XmlPullParser.TEXT -> stack.last().text.append(p.text)
            }
            ev = p.next()
        }
        return root.kids.firstOrNull() ?: root
    }

    private class Rel(val type: String, val target: String)

    private class Part(val path: String, val root: Node, val rels: Map<String, Rel>) {
        fun target(id: String?): String? = id?.let { rels[it]?.target }
        fun ofType(suffix: String): String? = rels.values.firstOrNull { it.type.endsWith(suffix) }?.target
        val tree: Node? get() = root.path("cSld", "spTree")
    }

    private sealed class Fill {
        class Solid(val color: Int) : Fill()
        class Pic(val path: String) : Fill()
        object None : Fill()
    }

    private class Xf(val x: Float, val y: Float, val w: Float, val h: Float, val rot: Float, val flipH: Boolean, val flipV: Boolean)

    /** Estilos de texto herdados, do mais específico para o mais geral. */
    private class TextChain(val lists: List<Node>, val bodyPrs: List<Node>)

    private val DEFAULT_THEME = mapOf(
        "dk1" to 0xFF000000.toInt(), "lt1" to 0xFFFFFFFF.toInt(), "dk2" to 0xFF44546A.toInt(), "lt2" to 0xFFE7E6E6.toInt(),
        "accent1" to 0xFF4472C4.toInt(), "accent2" to 0xFFED7D31.toInt(), "accent3" to 0xFFA5A5A5.toInt(),
        "accent4" to 0xFFFFC000.toInt(), "accent5" to 0xFF5B9BD5.toInt(), "accent6" to 0xFF70AD47.toInt(),
        "hlink" to 0xFF0563C1.toInt(), "folHlink" to 0xFF954F72.toInt(),
    )
    private val DEFAULT_MAP = mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")
    private val PRESET = mapOf(
        "black" to Color.BLACK, "white" to Color.WHITE, "red" to Color.RED, "green" to 0xFF008000.toInt(),
        "blue" to Color.BLUE, "yellow" to Color.YELLOW, "gray" to Color.GRAY, "grey" to Color.GRAY,
        "darkGray" to Color.DKGRAY, "lightGray" to Color.LTGRAY, "orange" to 0xFFFFA500.toInt(),
    )
    private val LINES = setOf("line", "straightConnector1", "bentConnector2", "bentConnector3", "curvedConnector3")

    private class Deck(val zip: ZipFile) {
        private val parts = HashMap<String, Part?>()
        private var theme: Map<String, Int> = DEFAULT_THEME
        private var clrMap: Map<String, String> = DEFAULT_MAP
        private var k = 1f
        private val pres = part("ppt/presentation.xml") ?: throw NotPptx()
        private val bitmaps = HashMap<String, Bitmap?>()

        fun bytes(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }

        fun part(path: String?): Part? {
            path ?: return null
            if (parts.containsKey(path)) return parts[path]
            val p = try {
                bytes(path)?.let { Part(path, parseXml(it), rels(path)) }
            } catch (e: Exception) {
                null
            }
            parts[path] = p
            return p
        }

        private fun rels(path: String): Map<String, Rel> {
            val dir = path.substringBeforeLast('/', "")
            val name = path.substringAfterLast('/')
            val x = try {
                bytes("$dir/_rels/$name.rels")?.let { parseXml(it) }
            } catch (e: Exception) {
                null
            } ?: return emptyMap()
            val out = HashMap<String, Rel>()
            for (r in x.all("Relationship")) {
                if (r.attr("TargetMode") == "External") continue
                val id = r.attr("Id") ?: continue
                out[id] = Rel(r.attr("Type") ?: "", resolve(dir, r.attr("Target") ?: continue))
            }
            return out
        }

        private fun resolve(dir: String, target: String): String {
            if (target.startsWith("/")) return target.drop(1)
            val parts = dir.split('/').filter { it.isNotEmpty() }.toMutableList()
            for (s in target.split('/')) when (s) {
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                ".", "" -> {}
                else -> parts.add(s)
            }
            return parts.joinToString("/")
        }

        private fun slidePaths(): List<String> {
            val ids = pres.root["sldIdLst"]?.all("sldId").orEmpty().mapNotNull { pres.target(it.rel("id")) }
            if (ids.isNotEmpty()) return ids
            val re = Regex("""ppt/slides/slide(\d+)\.xml""")
            val names = ArrayList<Pair<Int, String>>()
            for (e in zip.entries().toList()) re.matchEntire(e.name)?.let { names.add(it.groupValues[1].toInt() to e.name) }
            return names.sortedBy { it.first }.map { it.second }
        }

        fun render(dir: File, progress: (Int, Int) -> Unit, cancelled: () -> Boolean): List<PdfImporter.Page> {
            dir.mkdirs()
            val sz = pres.root["sldSz"]
            val cx = sz?.num("cx")?.takeIf { it > 0 } ?: 12192000f
            val cy = sz?.num("cy")?.takeIf { it > 0 } ?: 6858000f
            k = LONG_SIDE / max(cx, cy)
            val w = max(1, (cx * k).toInt())
            val h = max(1, (cy * k).toInt())
            val slides = slidePaths()
            val batch = UUID.randomUUID().toString().take(8)
            val out = ArrayList<PdfImporter.Page>()
            for ((i, path) in slides.withIndex()) {
                if (cancelled()) break
                progress(i + 1, slides.size)
                val slide = part(path) ?: continue
                if (slide.root.attr("show") == "0") continue
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    drawSlide(Canvas(bmp), slide, w, h)
                } catch (e: Exception) {
                    // Fica o que deu para desenhar.
                } catch (e: OutOfMemoryError) {
                }
                val name = "pptx_${batch}_s${i + 1}.jpg"
                FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                bitmaps.values.forEach { it?.recycle() }
                bitmaps.clear()
                out.add(PdfImporter.Page(name, (cx / EMU_PER_PT).toInt(), (cy / EMU_PER_PT).toInt()))
            }
            return out
        }

        // ---- Slide ------------------------------------------------------------------------

        private var layout: Part? = null
        private var master: Part? = null

        private fun drawSlide(cv: Canvas, slide: Part, w: Int, h: Int) {
            layout = part(slide.ofType("/slideLayout"))
            master = part(layout?.ofType("/slideMaster"))
            loadTheme(master)
            cv.drawColor(Color.WHITE)
            drawBackground(cv, listOfNotNull(slide, layout, master), w, h)
            val showAll = slide.root.attr("showMasterSp") != "0"
            if (showAll && layout?.root?.attr("showMasterSp") != "0") master?.let { m -> m.tree?.let { drawTree(cv, it, m, false) } }
            if (showAll) layout?.let { l -> l.tree?.let { drawTree(cv, it, l, false) } }
            slide.tree?.let { drawTree(cv, it, slide, true) }
        }

        private fun loadTheme(master: Part?) {
            val th = part(master?.ofType("/theme"))
            val scheme = th?.root?.find("clrScheme")
            val map = HashMap(DEFAULT_THEME)
            scheme?.kids?.forEach { n ->
                val c = n.kids.firstOrNull() ?: return@forEach
                val v = when (c.name) {
                    "srgbClr" -> hex(c.attr("val"))
                    "sysClr" -> hex(c.attr("lastClr")) ?: if (c.attr("val") == "window") Color.WHITE else Color.BLACK
                    else -> null
                }
                if (v != null) map[n.name] = v
            }
            theme = map
            clrMap = master?.root?.get("clrMap")?.attrs ?: DEFAULT_MAP
        }

        private fun drawBackground(cv: Canvas, chain: List<Part>, w: Int, h: Int) {
            for (p in chain) {
                val bg = p.root.path("cSld", "bg") ?: continue
                val fill = bg["bgPr"]?.let { fillOf(it, p) } ?: bg["bgRef"]?.let { r -> color(r)?.let { Fill.Solid(it) } }
                when (fill) {
                    is Fill.Solid -> cv.drawColor(fill.color)
                    is Fill.Pic -> bitmap(fill.path, w, h)?.let { cv.drawBitmap(it, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                    else -> {}
                }
                return
            }
        }

        // ---- Cores ------------------------------------------------------------------------

        private fun hex(v: String?): Int? = v?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

        private fun scheme(v: String?): Int? {
            if (v == null || v == "phClr") return null
            return theme[clrMap[v] ?: v] ?: theme[v]
        }

        /** Cor do primeiro filho de cor de [holder] (srgbClr, schemeClr…), com os ajustes. */
        fun color(holder: Node?): Int? {
            holder ?: return null
            for (c in holder.kids) {
                val base = when (c.name) {
                    "srgbClr" -> hex(c.attr("val"))
                    "schemeClr" -> scheme(c.attr("val"))
                    "sysClr" -> hex(c.attr("lastClr")) ?: if (c.attr("val") == "window") Color.WHITE else Color.BLACK
                    "prstClr" -> PRESET[c.attr("val")]
                    else -> continue
                } ?: continue
                return mods(base, c)
            }
            return null
        }

        private fun mods(c0: Int, n: Node): Int {
            var r = Color.red(c0) / 255f
            var g = Color.green(c0) / 255f
            var b = Color.blue(c0) / 255f
            var a = 1f
            for (m in n.kids) {
                val v = (m.num("val") ?: continue) / 100000f
                when (m.name) {
                    "alpha" -> a = v
                    "shade" -> { r *= v; g *= v; b *= v }
                    "tint" -> { r = r * v + (1 - v); g = g * v + (1 - v); b = b * v + (1 - v) }
                    "lumMod", "lumOff" -> {
                        val hsl = toHsl(r, g, b)
                        hsl[2] = (if (m.name == "lumMod") hsl[2] * v else hsl[2] + v).coerceIn(0f, 1f)
                        val rgb = fromHsl(hsl[0], hsl[1], hsl[2])
                        r = rgb[0]; g = rgb[1]; b = rgb[2]
                    }
                }
            }
            fun ch(x: Float) = (x.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            return Color.argb(ch(a), ch(r), ch(g), ch(b))
        }

        private fun toHsl(r: Float, g: Float, b: Float): FloatArray {
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val l = (mx + mn) / 2
            if (mx == mn) return floatArrayOf(0f, 0f, l)
            val d = mx - mn
            val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
            val h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } / 6
            return floatArrayOf(h, s, l)
        }

        private fun fromHsl(h: Float, s: Float, l: Float): FloatArray {
            if (s == 0f) return floatArrayOf(l, l, l)
            val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun hue(t0: Float): Float {
                var t = t0
                if (t < 0) t += 1
                if (t > 1) t -= 1
                return when {
                    t < 1f / 6 -> p + (q - p) * 6 * t
                    t < 1f / 2 -> q
                    t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
                    else -> p
                }
            }
            return floatArrayOf(hue(h + 1f / 3), hue(h), hue(h - 1f / 3))
        }

        private fun fillOf(n: Node?, part: Part): Fill? {
            n ?: return null
            if (n["noFill"] != null) return Fill.None
            n["solidFill"]?.let { f -> return color(f)?.let { Fill.Solid(it) } }
            n["gradFill"]?.let { g ->
                val cols = g["gsLst"]?.all("gs").orEmpty().mapNotNull { color(it) }
                if (cols.isNotEmpty()) return Fill.Solid(blend(cols.first(), cols.last()))
            }
            n["blipFill"]?.let { b -> part.target(b["blip"]?.rel("embed"))?.let { return Fill.Pic(it) } }
            n["pattFill"]?.let { p -> color(p["fgClr"])?.let { return Fill.Solid(it) } }
            return null
        }

        private fun blend(a: Int, b: Int) = Color.argb(
            (Color.alpha(a) + Color.alpha(b)) / 2, (Color.red(a) + Color.red(b)) / 2,
            (Color.green(a) + Color.green(b)) / 2, (Color.blue(a) + Color.blue(b)) / 2,
        )

        // ---- Formas -----------------------------------------------------------------------

        private fun xfOf(x: Node?): Xf? {
            x ?: return null
            val off = x["off"] ?: return null
            val ext = x["ext"] ?: return null
            return Xf(
                off.num("x") ?: 0f, off.num("y") ?: 0f, ext.num("cx") ?: 0f, ext.num("cy") ?: 0f,
                (x.num("rot") ?: 0f) / 60000f, x.attr("flipH") == "1", x.attr("flipV") == "1",
            )
        }

        private fun rectOf(xf: Xf) = RectF(xf.x * k, xf.y * k, (xf.x + xf.w) * k, (xf.y + xf.h) * k)

        /** Marca de espaço reservado (título, corpo…) de um elemento. */
        private fun phOf(n: Node): Node? = n.kids.firstOrNull { it.name.startsWith("nv") }?.path("nvPr", "ph")

        private fun hidden(n: Node) = n.kids.firstOrNull { it.name.startsWith("nv") }?.get("cNvPr")?.attr("hidden").let { it == "1" || it == "true" }

        private fun phType(ph: Node): String = when (val t = ph.attr("type") ?: "body") {
            "ctrTitle", "title" -> "title"
            "subTitle", "obj", "body", "pic", "tbl", "chart", "clipArt", "dgm", "media" -> "body"
            else -> t
        }

        private fun findPh(part: Part?, ph: Node, byIdx: Boolean): Node? {
            val tree = part?.tree ?: return null
            val all = ArrayList<Node>()
            fun walk(t: Node) {
                for (c in t.kids) {
                    if (c.name == "grpSp") walk(c) else if (phOf(c) != null) all.add(c)
                }
            }
            walk(tree)
            val idx = ph.attr("idx")
            if (byIdx && idx != null) all.firstOrNull { phOf(it)?.attr("idx") == idx }?.let { return it }
            val type = phType(ph)
            return all.firstOrNull { phType(phOf(it)!!) == type }
        }

        private fun drawTree(cv: Canvas, tree: Node, part: Part, slideLevel: Boolean) {
            for (n in tree.kids) {
                try {
                    if (hidden(n)) continue
                    when (n.name) {
                        "sp", "cxnSp" -> if (slideLevel || phOf(n) == null) drawShape(cv, n, part, slideLevel)
                        "pic" -> if (slideLevel || phOf(n) == null) drawPic(cv, n, part, slideLevel)
                        "grpSp" -> drawGroup(cv, n, part, slideLevel)
                        "graphicFrame" -> drawFrame(cv, n, part)
                        "AlternateContent" -> (n["Fallback"] ?: n["Choice"])?.let { drawTree(cv, it, part, slideLevel) }
                    }
                } catch (e: Exception) {
                    // Um elemento estranho não estraga o resto do slide.
                }
            }
        }

        private fun drawGroup(cv: Canvas, g: Node, part: Part, slideLevel: Boolean) {
            val x = g.path("grpSpPr", "xfrm")
            val xf = xfOf(x)
            cv.save()
            if (xf != null) {
                val ch = x?.get("chOff")
                val ce = x?.get("chExt")
                val chX = ch?.num("x") ?: xf.x
                val chY = ch?.num("y") ?: xf.y
                val chW = ce?.num("cx")?.takeIf { it > 0 } ?: xf.w
                val chH = ce?.num("cy")?.takeIf { it > 0 } ?: xf.h
                val r = rectOf(xf)
                if (xf.rot != 0f) cv.rotate(xf.rot, r.centerX(), r.centerY())
                cv.translate(xf.x * k, xf.y * k)
                cv.scale(if (chW > 0) xf.w / chW else 1f, if (chH > 0) xf.h / chH else 1f)
                cv.translate(-chX * k, -chY * k)
            }
            drawTree(cv, g, part, slideLevel)
            cv.restore()
        }

        private fun drawShape(cv: Canvas, sp: Node, part: Part, slideLevel: Boolean) {
            val spPr = sp["spPr"]
            val ph = phOf(sp)
            val lph = if (ph != null && slideLevel) findPh(layout, ph, true) else null
            val mph = if (ph != null) findPh(master, ph, false) else null
            val xf = xfOf(spPr?.get("xfrm")) ?: xfOf(lph?.path("spPr", "xfrm")) ?: xfOf(mph?.path("spPr", "xfrm")) ?: return
            val r = rectOf(xf)
            val style = sp["style"]
            val prst = spPr?.get("prstGeom")?.attr("prst") ?: if (spPr?.get("custGeom") != null) "cust" else "rect"
            val isLine = prst in LINES
            val fill = if (isLine) Fill.None else fillOf(spPr, part)
                ?: style?.get("fillRef")?.takeIf { it.attr("idx") != "0" }?.let { f -> color(f)?.let { Fill.Solid(it) } }
            val ln = spPr?.get("ln")
            val lineColor = when {
                ln?.get("noFill") != null -> null
                ln?.get("solidFill") != null -> color(ln?.get("solidFill"))
                ln?.get("gradFill") != null -> (fillOf(ln, part) as? Fill.Solid)?.color
                else -> style?.get("lnRef")?.takeIf { it.attr("idx") != "0" }?.let { color(it) }
            }
            val lineW = max(1f, (ln?.num("w") ?: 12700f) * k)

            cv.save()
            if (xf.rot != 0f) cv.rotate(xf.rot, r.centerX(), r.centerY())
            cv.save()
            if (xf.flipH || xf.flipV) cv.scale(if (xf.flipH) -1f else 1f, if (xf.flipV) -1f else 1f, r.centerX(), r.centerY())
            val path = geometry(prst, r, spPr)
            when (fill) {
                is Fill.Solid -> cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.color })
                is Fill.Pic -> bitmap(fill.path, r.width().toInt(), r.height().toInt())?.let { bmp ->
                    cv.save()
                    cv.clipPath(path)
                    cv.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                    cv.restore()
                }
                else -> {}
            }
            if (lineColor != null) cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                this.style = Paint.Style.STROKE
                strokeWidth = lineW
                strokeJoin = Paint.Join.ROUND
            })
            cv.restore()

            sp["txBody"]?.let { body ->
                val lists = ArrayList<Node>()
                val bodyPrs = ArrayList<Node>()
                body["lstStyle"]?.let { lists.add(it) }
                if (ph != null) {
                    lph?.get("txBody")?.let { t -> t["lstStyle"]?.let { lists.add(it) }; t["bodyPr"]?.let { bodyPrs.add(it) } }
                    mph?.get("txBody")?.let { t -> t["lstStyle"]?.let { lists.add(it) }; t["bodyPr"]?.let { bodyPrs.add(it) } }
                    val styles = master?.root?.get("txStyles")
                    when (phType(ph)) {
                        "title" -> styles?.get("titleStyle")
                        "body" -> styles?.get("bodyStyle")
                        else -> styles?.get("otherStyle")
                    }?.let { lists.add(it) }
                }
                pres.root["defaultTextStyle"]?.let { lists.add(it) }
                drawText(cv, body, r, TextChain(lists, bodyPrs), null, style?.get("fontRef")?.let { color(it) })
            }
            cv.restore()
        }

        private fun geometry(prst: String, r: RectF, spPr: Node?): Path {
            val p = Path()
            when (prst) {
                "ellipse" -> p.addOval(r, Path.Direction.CW)
                "roundRect" -> {
                    val rad = min(r.width(), r.height()) * 0.1667f
                    p.addRoundRect(r, rad, rad, Path.Direction.CW)
                }
                "triangle" -> { p.moveTo(r.centerX(), r.top); p.lineTo(r.right, r.bottom); p.lineTo(r.left, r.bottom); p.close() }
                "rtTriangle" -> { p.moveTo(r.left, r.top); p.lineTo(r.right, r.bottom); p.lineTo(r.left, r.bottom); p.close() }
                "diamond" -> {
                    p.moveTo(r.centerX(), r.top); p.lineTo(r.right, r.centerY())
                    p.lineTo(r.centerX(), r.bottom); p.lineTo(r.left, r.centerY()); p.close()
                }
                "parallelogram" -> {
                    val d = r.width() * 0.25f
                    p.moveTo(r.left + d, r.top); p.lineTo(r.right, r.top); p.lineTo(r.right - d, r.bottom); p.lineTo(r.left, r.bottom); p.close()
                }
                "rightArrow" -> {
                    val head = min(r.width() * 0.5f, r.height())
                    val t = r.top + r.height() * 0.25f
                    val b = r.bottom - r.height() * 0.25f
                    p.moveTo(r.left, t); p.lineTo(r.right - head, t); p.lineTo(r.right - head, r.top); p.lineTo(r.right, r.centerY())
                    p.lineTo(r.right - head, r.bottom); p.lineTo(r.right - head, b); p.lineTo(r.left, b); p.close()
                }
                "cust" -> custom(p, r, spPr?.get("custGeom"))
                in LINES -> { p.moveTo(r.left, r.top); p.lineTo(r.right, r.bottom) }
                else -> p.addRect(r, Path.Direction.CW)
            }
            return p
        }

        /** Desenho livre (custGeom): moveTo, lnTo, curvas e close. */
        private fun custom(p: Path, r: RectF, cust: Node?) {
            for (path in cust?.get("pathLst")?.all("path").orEmpty()) {
                val pw = path.num("w")?.takeIf { it > 0 } ?: (r.width() / k)
                val ph = path.num("h")?.takeIf { it > 0 } ?: (r.height() / k)
                val sx = r.width() / pw
                val sy = r.height() / ph
                fun pts(c: Node) = c.all("pt").map { (r.left + (it.num("x") ?: 0f) * sx) to (r.top + (it.num("y") ?: 0f) * sy) }
                for (c in path.kids) {
                    val q = pts(c)
                    when (c.name) {
                        "moveTo" -> q.firstOrNull()?.let { p.moveTo(it.first, it.second) }
                        "lnTo" -> q.firstOrNull()?.let { p.lineTo(it.first, it.second) }
                        "cubicBezTo" -> if (q.size == 3) p.cubicTo(q[0].first, q[0].second, q[1].first, q[1].second, q[2].first, q[2].second)
                        "quadBezTo" -> if (q.size == 2) p.quadTo(q[0].first, q[0].second, q[1].first, q[1].second)
                        "close" -> p.close()
                    }
                }
            }
        }

        private fun drawPic(cv: Canvas, pic: Node, part: Part, slideLevel: Boolean) {
            val spPr = pic["spPr"]
            val ph = phOf(pic)
            val xf = xfOf(spPr?.get("xfrm"))
                ?: ph?.let { xfOf((if (slideLevel) findPh(layout, it, true) else null)?.path("spPr", "xfrm")) }
                ?: return
            val r = rectOf(xf)
            val bmp = bitmap(part.target(pic.path("blipFill", "blip")?.rel("embed")) ?: return, r.width().toInt(), r.height().toInt()) ?: return
            val crop = pic.path("blipFill", "srcRect")
            val src = if (crop != null) {
                fun f(a: String) = (crop.num(a) ?: 0f) / 100000f
                Rect((bmp.width * f("l")).toInt(), (bmp.height * f("t")).toInt(),
                    (bmp.width * (1 - f("r"))).toInt(), (bmp.height * (1 - f("b"))).toInt()).takeIf { it.width() > 0 && it.height() > 0 }
            } else null
            cv.save()
            if (xf.rot != 0f) cv.rotate(xf.rot, r.centerX(), r.centerY())
            if (xf.flipH || xf.flipV) cv.scale(if (xf.flipH) -1f else 1f, if (xf.flipV) -1f else 1f, r.centerX(), r.centerY())
            cv.drawBitmap(bmp, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            cv.restore()
        }

        /** Imagem da apresentação, reduzida para o tamanho em que vai aparecer. */
        private fun bitmap(path: String, w: Int, h: Int): Bitmap? {
            val key = "$path@${w}x$h"
            if (bitmaps.containsKey(key)) return bitmaps[key]
            val b = try {
                val data = bytes(path)
                if (data == null) null else {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, o)
                    var sample = 1
                    val want = max(64, max(w, h))
                    while (max(o.outWidth, o.outHeight) / (sample * 2) >= want) sample *= 2
                    BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } catch (e: Throwable) {
                null
            }
            bitmaps[key] = b
            return b
        }

        // ---- Tabelas ----------------------------------------------------------------------

        private fun drawFrame(cv: Canvas, f: Node, part: Part) {
            val xf = xfOf(f["xfrm"]) ?: return
            val tbl = f.path("graphic", "graphicData", "tbl") ?: return
            val cols = tbl["tblGrid"]?.all("gridCol").orEmpty().map { it.num("w") ?: 0f }
            if (cols.isEmpty()) return
            val tblPr = tbl["tblPr"]
            val styled = tblPr?.get("tableStyleId") != null || tblPr?.attr("firstRow") == "1"
            val accent = scheme("accent1") ?: 0xFF4472C4.toInt()
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, 12700f * k)
                color = if (styled) Color.WHITE else Color.BLACK
            }
            var y = xf.y
            for ((ri, tr) in tbl.all("tr").withIndex()) {
                val rh = tr.num("h") ?: 0f
                var x = xf.x
                var ci = 0
                for (tc in tr.all("tc")) {
                    if (ci >= cols.size) break
                    val span = (tc.attr("gridSpan")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val w = (ci until min(cols.size, ci + span)).sumOf { cols[it].toDouble() }.toFloat()
                    if (tc.attr("hMerge") == "1" || tc.attr("vMerge") == "1") {
                        x += w; ci += span
                        continue
                    }
                    val rowSpan = (tc.attr("rowSpan")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val rows = tbl.all("tr")
                    val h = (ri until min(rows.size, ri + rowSpan)).sumOf { (rows[it].num("h") ?: 0f).toDouble() }.toFloat()
                    val rect = RectF(x * k, y * k, (x + w) * k, (y + h) * k)
                    val header = styled && ri == 0 && tblPr?.attr("firstRow") != "0"
                    val tcPr = tc["tcPr"]
                    val fill = fillOf(tcPr, part) ?: if (styled) Fill.Solid(
                        if (header) accent else mods(accent, Node("x", emptyMap()).apply {
                            kids.add(Node("tint", mapOf("val" to if (ri % 2 == 1) "40000" else "20000")))
                        }),
                    ) else null
                    if (fill is Fill.Solid) cv.drawRect(rect, Paint().apply { color = fill.color })
                    cv.drawRect(rect, border)
                    tc["txBody"]?.let { body ->
                        val ins = Node("bodyPr", mapOf(
                            "lIns" to (tcPr?.attr("marL") ?: "91440"), "rIns" to (tcPr?.attr("marR") ?: "91440"),
                            "tIns" to (tcPr?.attr("marT") ?: "45720"), "bIns" to (tcPr?.attr("marB") ?: "45720"),
                            "anchor" to (tcPr?.attr("anchor") ?: "t"),
                        ))
                        drawText(cv, body, rect, TextChain(listOfNotNull(pres.root["defaultTextStyle"]), listOf(ins)),
                            if (header) Color.WHITE else null, null, boldAll = header)
                    }
                    x += w
                    ci += span
                }
                y += rh
            }
        }

        // ---- Texto ------------------------------------------------------------------------

        private fun drawText(
            cv: Canvas, body: Node, box: RectF, chain: TextChain, forced: Int?, fontRef: Int?, boldAll: Boolean = false,
        ) {
            val bodyPrs = listOfNotNull(body["bodyPr"]) + chain.bodyPrs
            fun bp(a: String): String? = bodyPrs.firstNotNullOfOrNull { it.attr(a) }
            val lIns = (bp("lIns")?.toFloatOrNull() ?: 91440f) * k
            val rIns = (bp("rIns")?.toFloatOrNull() ?: 91440f) * k
            val tIns = (bp("tIns")?.toFloatOrNull() ?: 45720f) * k
            val bIns = (bp("bIns")?.toFloatOrNull() ?: 45720f) * k
            val anchor = bp("anchor") ?: "t"
            val wrap = bp("wrap") != "none"
            val autofit = bodyPrs.firstNotNullOfOrNull { it["normAutofit"] }
            var scale = (autofit?.num("fontScale") ?: 100000f) / 100000f
            val avail = box.height() - tIns - bIns
            val width = max(1, (box.width() - lIns - rIns).toInt())
            var lay: StaticLayout? = null
            for (attempt in 0 until 8) {
                val (text, spacing) = buildText(body, chain, forced, fontRef, boldAll, scale)
                if (text.isBlank()) return
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 18f * EMU_PER_PT * k * scale
                    color = forced ?: fontRef ?: scheme("tx1") ?: Color.BLACK
                }
                val w = if (wrap) width else max(width, ceil(Layout.getDesiredWidth(text, paint)).toInt())
                lay = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
                    .setLineSpacing(0f, spacing)
                    .setIncludePad(false)
                    .build()
                if (autofit == null || lay.height <= avail || scale < 0.4f) break
                scale *= 0.9f
            }
            val l = lay ?: return
            val dy = when (anchor) {
                "ctr" -> (avail - l.height) / 2
                "b" -> avail - l.height
                else -> 0f
            }
            cv.save()
            cv.translate(box.left + lIns, box.top + tIns + dy)
            l.draw(cv)
            cv.restore()
        }

        /** Monta o texto com estilos; devolve também o espaçamento entre linhas. */
        private fun buildText(
            body: Node, chain: TextChain, forced: Int?, fontRef: Int?, boldAll: Boolean, scale: Float,
        ): Pair<SpannableStringBuilder, Float> {
            val sb = SpannableStringBuilder()
            var spacing = 1f
            var number = 0
            val px = EMU_PER_PT * k * scale
            for ((pi, p) in body.all("p").withIndex()) {
                val pPr = p["pPr"]
                val lvl = (pPr?.attr("lvl")?.toIntOrNull() ?: 0).coerceIn(0, 8)
                val levels = listOfNotNull(pPr) + chain.lists.mapNotNull { it["lvl${lvl + 1}pPr"] }
                fun pAttr(a: String) = levels.firstNotNullOfOrNull { it.attr(a) }
                val defs = levels.mapNotNull { it["defRPr"] }
                fun sizeOf(rPr: Node?): Float =
                    (rPr?.num("sz") ?: defs.firstNotNullOfOrNull { it.num("sz") } ?: 1800f) / 100f
                if (pi == 0) levels.firstNotNullOfOrNull { it.path("lnSpc", "spcPct")?.num("val") }?.let {
                    spacing = (it / 100000f).coerceIn(0.6f, 3f)
                }
                if (pi > 0) {
                    sb.append('\n')
                    // Espaço antes do parágrafo, como uma linha baixinha.
                    val bef = levels.firstNotNullOfOrNull { it["spcBef"] }
                    val befPt = bef?.get("spcPts")?.num("val")?.div(100f)
                        ?: bef?.get("spcPct")?.num("val")?.let { it / 100000f * sizeOf(null) }
                    if (befPt != null && befPt >= 1f) {
                        val s = sb.length
                        sb.append(" \n")
                        sb.setSpan(AbsoluteSizeSpan(max(1, (befPt * 0.8f * px).toInt())), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                val start = sb.length
                val runs = p.kids.filter { it.name == "r" || it.name == "fld" || it.name == "br" }
                var firstColor: Int? = null
                var firstSize = sizeOf(p["endParaRPr"])
                // Marcador
                val bulletNode = levels.firstOrNull { it["buNone"] != null || it["buChar"] != null || it["buAutoNum"] != null }
                val hasText = runs.any { !(it["t"]?.text.isNullOrEmpty()) }
                var bullet: String? = null
                if (hasText && bulletNode != null) {
                    bulletNode["buChar"]?.attr("char")?.let { c -> bullet = (if (c == "" || c == "§" || c == "Ø") "•" else c) + " " }
                    if (bulletNode["buAutoNum"] != null) {
                        number++
                        bullet = "$number. "
                    }
                }
                if (bulletNode?.get("buAutoNum") == null) number = 0
                for (r in runs) {
                    if (r.name == "br") {
                        sb.append('\n')
                        continue
                    }
                    var t = r["t"]?.text?.toString() ?: continue
                    if (t.isEmpty()) continue
                    val rPr = r["rPr"]
                    val rprs = listOfNotNull(rPr) + defs
                    fun rAttr(a: String) = rprs.firstNotNullOfOrNull { it.attr(a) }
                    val size = sizeOf(rPr)
                    val bold = boldAll || rAttr("b").let { it == "1" || it == "true" }
                    val italic = rAttr("i").let { it == "1" || it == "true" }
                    val col = forced ?: rprs.firstNotNullOfOrNull { n -> n["solidFill"]?.let { color(it) } }
                        ?: fontRef ?: scheme("tx1") ?: Color.BLACK
                    if (rAttr("cap") == "all") t = t.uppercase()
                    if (firstColor == null) { firstColor = col; firstSize = size }
                    val s = sb.length
                    sb.append(t)
                    val e = sb.length
                    fun span(o: Any) = sb.setSpan(o, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val baseline = rAttr("baseline")?.toFloatOrNull() ?: 0f
                    span(AbsoluteSizeSpan(max(1, (size * px * (if (baseline != 0f) 0.65f else 1f)).toInt())))
                    span(ForegroundColorSpan(col))
                    if (bold && italic) span(StyleSpan(Typeface.BOLD_ITALIC))
                    else if (bold) span(StyleSpan(Typeface.BOLD))
                    else if (italic) span(StyleSpan(Typeface.ITALIC))
                    rAttr("u")?.let { if (it != "none") span(UnderlineSpan()) }
                    rAttr("strike")?.let { if (it != "noStrike") span(StrikethroughSpan()) }
                    if (baseline > 0) span(SuperscriptSpan()) else if (baseline < 0) span(SubscriptSpan())
                    val face = rprs.firstNotNullOfOrNull { it["latin"]?.attr("typeface") }?.lowercase() ?: ""
                    if (listOf("times", "georgia", "garamond", "cambria", "serif", "palatino", "book").any { face.contains(it) }) span(TypefaceSpan("serif"))
                    else if (listOf("courier", "consolas", "mono").any { face.contains(it) }) span(TypefaceSpan("monospace"))
                }
                if (sb.length == start) {
                    // Parágrafo vazio ainda ocupa uma linha.
                    sb.append(" ")
                    sb.setSpan(AbsoluteSizeSpan(max(1, (firstSize * px).toInt())), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else bullet?.let { b ->
                    sb.insert(start, b)
                    val bc = bulletNode?.get("buClr")?.let { color(it) } ?: firstColor ?: Color.BLACK
                    sb.setSpan(ForegroundColorSpan(bc), start, start + b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(AbsoluteSizeSpan(max(1, (firstSize * px).toInt())), start, start + b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val end = sb.length
                val align = when (pAttr("algn")) {
                    "ctr" -> Layout.Alignment.ALIGN_CENTER
                    "r" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                sb.setSpan(AlignmentSpan.Standard(align), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val marL = (pAttr("marL")?.toFloatOrNull() ?: 0f) * k
                val indent = (pAttr("indent")?.toFloatOrNull() ?: 0f) * k
                if (marL > 0 || indent != 0f) {
                    sb.setSpan(LeadingMarginSpan.Standard(max(0f, marL + indent).toInt(), max(0f, marL).toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return sb to spacing
        }
    }
}
