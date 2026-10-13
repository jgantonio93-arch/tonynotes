package com.jgantonio.notasinfinitas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Importa notas do Samsung Notes (.sdocx) com a escrita à mão editável.
 *
 * O .sdocx é um ZIP: note.note (título, texto e tabela de nomes de caneta),
 * pageIdInfo.dat (ordem das páginas), um <uuid>.page por página e media/.
 * Cada página guarda camadas com uma árvore de objetos; cada objeto é uma cadeia de
 * "frames" (tamanho, tipo, offset dos campos opcionais e máscaras de bits).
 * Lemos traços (tipo 1), caixas de texto (tipo 2) e imagens (tipo 3); o resto é pulado.
 * Formato conforme a engenharia reversa publicada em twangodev/sdocx
 * (docs/reverse-engineering/file-format.md) e bxff/samsung-notes-format.
 */
object SamsungNotesImporter {

    class SStroke(val xs: FloatArray, val ys: FloatArray, val ps: FloatArray, val color: Int, val size: Float, val pen: String?)
    class SText(val text: String, val rect: RectF)
    class SImage(val media: String, val rect: RectF)
    class SPage(val w: Float, val h: Float, val strokes: List<SStroke>, val texts: List<SText>, val images: List<SImage>)
    class SNote(val title: String?, val body: String?, val pages: List<SPage>, val media: Map<String, ByteArray>, val skipped: Int)

    class UnsupportedFile(message: String) : Exception(message)

    fun isSdocx(name: String?) = name?.lowercase()?.endsWith(".sdocx") == true

    // ---- Leitura do arquivo -----------------------------------------------------

    fun read(input: InputStream): SNote {
        val entries = HashMap<String, ByteArray>()
        val order = ArrayList<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                // A miniatura da página (.spi) é um formato próprio da Samsung e não serve aqui.
                if (!e.isDirectory && !name.endsWith(".spi") && !name.contains("..")) {
                    entries[name] = zip.readBytes()
                    order.add(name)
                }
            }
        }
        val noteBytes = entries["note.note"]
        val pageNames = order.filter { it.endsWith(".page") && !it.contains('/') }
        if (noteBytes == null || pageNames.isEmpty()) {
            throw UnsupportedFile("Esse arquivo não parece uma nota do Samsung Notes (.sdocx).")
        }
        val penNames = stringTable(noteBytes)
        var title: String? = null
        var body: String? = null
        try {
            val (t, b) = noteTexts(noteBytes)
            title = t?.trim()?.ifBlank { null }
            body = b?.trim()?.ifBlank { null }
        } catch (_: Exception) {
        }
        val media = try {
            entries["media/mediaInfo.dat"]?.let { mediaNames(it) } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        // Ordem das páginas: a de pageIdInfo.dat (procurando o UUID de cada página nele).
        val info = entries["pageIdInfo.dat"]
        val sorted = if (info == null) pageNames else pageNames.sortedBy { n ->
            val id = n.removeSuffix(".page")
            val i16 = indexOf(info, id.toByteArray(Charsets.UTF_16LE))
            val i8 = if (i16 >= 0) i16 else indexOf(info, id.toByteArray(Charsets.UTF_8))
            if (i8 >= 0) i8 else Int.MAX_VALUE
        }

        var skipped = 0
        val pages = ArrayList<SPage>()
        for (n in sorted) {
            val r = try {
                PageReader(entries.getValue(n), penNames, media).read()
            } catch (_: Exception) {
                null
            }
            if (r == null) skipped++ else {
                pages.add(r.first)
                skipped += r.second
            }
        }
        val files = HashMap<String, ByteArray>()
        for (p in pages) for (img in p.images) {
            entries["media/${img.media}"]?.let { files[img.media] = it }
        }
        if (pages.isEmpty() || (pages.all { it.strokes.isEmpty() && it.texts.isEmpty() && it.images.isEmpty() } && body == null)) {
            throw UnsupportedFile("Não encontramos escrita nessa nota. No Samsung Notes, exporte-a como PDF e use “Importar PDF”.")
        }
        return SNote(title, body, pages, files, skipped)
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return -1
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Leitor little-endian com posição absoluta. */
    private class Bin(val b: ByteArray) {
        private val bb: ByteBuffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val size get() = b.size
        fun u8(o: Int) = b[o].toInt() and 0xFF
        fun u16(o: Int) = bb.getShort(o).toInt() and 0xFFFF
        fun i16(o: Int) = bb.getShort(o).toInt()
        fun i32(o: Int) = bb.getInt(o)
        fun u32(o: Int): Int {
            val v = bb.getInt(o)
            if (v < 0) throw IllegalStateException("tamanho inválido")
            return v
        }
        fun f32(o: Int) = bb.getFloat(o)
        fun f64(o: Int) = bb.getDouble(o)
        fun utf16(o: Int, n: Int): String {
            if (n < 0 || o + 2 * n > b.size) throw IllegalStateException("texto inválido")
            return String(b, o, 2 * n, Charsets.UTF_16LE)
        }

        /** Máscara de bits com tamanho variável: (valor, posição seguinte). */
        fun mask(o: Int): Pair<Long, Int> {
            val n = u8(o)
            if (n > 8) throw IllegalStateException("máscara inválida")
            var v = 0L
            for (i in 0 until n) v = v or ((u8(o + 1 + i).toLong()) shl (8 * i))
            return v to o + 1 + n
        }

        fun frame(o: Int): Frame {
            val size = u32(o)
            val type = i16(o + 4)
            val flex = u32(o + 6)
            val (prop, p1) = mask(o + 10)
            val (field, p2) = mask(p1)
            if (size < 10 || o + size > b.size || flex > size) throw IllegalStateException("frame inválido")
            return Frame(o, size, type, o + flex, prop, field, p2)
        }
    }

    private class Frame(val start: Int, val size: Int, val type: Int, val flex: Int, val props: Long, val fields: Long, val fixed: Int) {
        val end get() = start + size
        fun prop(bit: Int) = (props shr bit) and 1L == 1L
        fun field(bit: Int) = (fields shr bit) and 1L == 1L
    }

    private fun frames(bin: Bin, start: Int, end: Int): List<Frame> {
        val out = ArrayList<Frame>()
        var o = start
        // Depois do último frame vem o hash do objeto: para no primeiro trecho que não fecha.
        while (o + 10 < end && out.size < 8) {
            val f = try { bin.frame(o) } catch (_: Exception) { break }
            if (f.end > end) break
            out.add(f)
            o = f.end
        }
        return out
    }

    /** Valor com sinal no bit 15 e magnitude em ponto fixo. */
    private fun signed(w: Int, div: Float): Float {
        val m = (w and 0x7FFF) / div
        return if (w and 0x8000 != 0) -m else m
    }

    /** Caixa (esquerda, topo, direita, base) gravada no frame base (tipo 0) de todo objeto. */
    private fun bbox(bin: Bin, f0: Frame): RectF {
        var p = f0.fixed + 4 // versão do formato
        val uuidLen = bin.u16(p)
        p += 2 + uuidLen + 8 // UUID + data de modificação
        return RectF(bin.f64(p).toFloat(), bin.f64(p + 8).toFloat(), bin.f64(p + 16).toFloat(), bin.f64(p + 24).toFloat())
    }

    /** Texto "text-common" no frame 7 (bit 0): u32 tamanho, depois o texto com u32 de comprimento. */
    private fun textOf(bin: Bin, f7: Frame): String? {
        if (!f7.field(0)) return null
        val q = f7.flex
        bin.u32(q)
        val n = bin.u32(q + 4)
        return bin.utf16(q + 8, n)
    }

    private class PageReader(b: ByteArray, val penNames: Map<Int, String>, val media: Map<Int, String>) {
        private val bin = Bin(b)
        private val strokes = ArrayList<SStroke>()
        private val texts = ArrayList<SText>()
        private val images = ArrayList<SImage>()
        private var skipped = 0

        fun read(): Pair<SPage, Int> {
            val layerOffset = bin.u32(0)
            val (_, p1) = bin.mask(8)
            val (_, p2) = bin.mask(p1)
            val w = bin.u32(p2 + 4).toFloat()
            val h = bin.u32(p2 + 8).toFloat()
            if (w <= 0f || h <= 0f) throw IllegalStateException("página sem tamanho")
            val layers = bin.u16(layerOffset)
            var o = layerOffset + 4
            for (l in 0 until layers) {
                val headerSize = bin.u32(o)
                val countAt = o + headerSize
                val count = bin.u32(countAt)
                o = objects(countAt + 4, count, 0)
                o += 32 // hash da camada
            }
            return SPage(w, h, strokes, texts, images) to skipped
        }

        /** Lê [count] objetos a partir de [o] (cada um seguido dos filhos) e devolve a posição seguinte. */
        private fun objects(start: Int, count: Int, depth: Int): Int {
            if (depth > 32) throw IllegalStateException("objetos aninhados demais")
            var o = start
            for (i in 0 until count) {
                val type = bin.u8(o)
                val children = bin.u16(o + 1)
                val size = bin.u32(o + 3)
                val body = o + 7
                val end = body + size
                if (end > bin.size) throw IllegalStateException("objeto cortado")
                try {
                    when (type) {
                        1 -> stroke(body, end)
                        2 -> textBox(body, end)
                        3 -> image(body, end)
                        4, 100 -> Unit // grupos: os filhos vêm logo depois
                        else -> skipped++
                    }
                } catch (_: Exception) {
                    skipped++
                }
                o = objects(end, children, depth + 1)
            }
            return o
        }

        private fun stroke(body: Int, end: Int) {
            val fr = frames(bin, body, end)
            val f = fr.getOrNull(1)?.takeIf { it.type == 1 } ?: throw IllegalStateException("traço sem dados")
            if (f.prop(3)) return // borracha
            var p = f.fixed
            val n = bin.u16(p)
            p += 2
            if (n == 0) return
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            val ps = FloatArray(n)
            if (f.prop(0)) {
                // Pontos comprimidos: primeiro ponto em f64, depois deltas de 16 bits (Q10.5).
                var x = bin.f64(p)
                var y = bin.f64(p + 8)
                p += 16
                xs[0] = x.toFloat(); ys[0] = y.toFloat()
                for (i in 1 until n) {
                    x += signed(bin.u16(p), 32f)
                    y += signed(bin.u16(p + 2), 32f)
                    p += 4
                    xs[i] = x.toFloat(); ys[i] = y.toFloat()
                }
                var pr = bin.f32(p)
                p += 4
                ps[0] = pr
                for (i in 1 until n) {
                    pr += signed(bin.u16(p), 4096f)
                    p += 2
                    ps[i] = pr
                }
            } else {
                for (i in 0 until n) {
                    xs[i] = bin.f64(p).toFloat(); ys[i] = bin.f64(p + 8).toFloat()
                    p += 16
                }
                for (i in 0 until n) {
                    ps[i] = bin.f32(p)
                    p += 4
                }
            }
            if (p > f.end) throw IllegalStateException("traço cortado")

            // Campos opcionais, na ordem dos bits: nome de caneta antigo (0), ajustes (1),
            // cor ARGB (2), tamanho (3), propriedade de 1 byte (4) ... nome da caneta (7).
            var color = Color.BLACK
            var size = 4f
            var penId = -1
            var q = f.flex
            for (bit in 0..7) {
                if (!f.field(bit)) continue
                when (bit) {
                    0 -> { if (penId < 0) penId = bin.i32(q); q += 4 }
                    1 -> q += 4
                    2 -> { color = bin.i32(q); q += 4 }
                    3 -> { size = bin.f32(q); q += 4 }
                    4 -> q += 1
                    7 -> { penId = bin.i32(q); q += 4 }
                    else -> break // campo de tamanho desconhecido: para aqui
                }
            }
            if (!size.isFinite() || size <= 0f) size = 4f
            strokes.add(SStroke(xs, ys, ps, color, size, penNames[penId]))
        }

        private fun textBox(body: Int, end: Int) {
            val fr = frames(bin, body, end)
            val f7 = fr.firstOrNull { it.type == 7 } ?: return
            val text = textOf(bin, f7)?.trim().orEmpty()
            if (text.isEmpty()) return
            texts.add(SText(text, bbox(bin, fr[0])))
        }

        private fun image(body: Int, end: Int) {
            val fr = frames(bin, body, end)
            val f7 = fr.firstOrNull { it.type == 7 } ?: return
            // Campos do frame 7 até o preenchimento (bit 5), que guarda o id da imagem.
            var q = f7.flex
            if (f7.field(0)) q += 4 + bin.u32(q)
            if (f7.field(1)) q += 1
            if (f7.field(2)) q += 4
            if (f7.field(3)) return
            if (f7.field(4)) q += 4
            if (!f7.field(5)) return
            bin.u32(q)
            val effect = bin.u8(q + 4)
            if (effect != 2) return
            val bind = bin.i32(q + 6)
            val name = media[bind] ?: return
            images.add(SImage(name, bbox(bin, fr[0])))
        }
    }

    /** Título e corpo do note.note: cada um é um objeto com o texto no frame 7. */
    private fun noteTexts(b: ByteArray): Pair<String?, String?> {
        val bin = Bin(b)
        val (_, p1) = bin.mask(4)
        var (_, p) = bin.mask(p1)
        p += 4 // versão do formato
        p += 2 + 2 * bin.u16(p) // id da nota
        p += 4 + 8 + 8 // revisão, criação, modificação
        p += 4 * 4 + 4 // largura, altura, margens, versão mínima
        val titleSize = bin.u32(p)
        val titleAt = p + 4
        p = titleAt + titleSize
        val bodySize = bin.u32(p)
        val bodyAt = p + 4
        fun text(at: Int, size: Int): String? {
            if (size <= 0 || at + size > b.size) return null
            val f7 = frames(bin, at, at + size).firstOrNull { it.type == 7 } ?: return null
            return textOf(bin, f7)
        }
        return text(titleAt, titleSize) to text(bodyAt, bodySize)
    }

    /**
     * Tabela de strings do note.note (nomes de caneta): u32 tamanho, u16 quantidade e
     * pares (u32 id, u16 comprimento, texto UTF-16). Procuramos o trecho em que tudo fecha
     * exatamente, sem depender dos outros campos opcionais do documento.
     */
    private fun stringTable(b: ByteArray): Map<Int, String> {
        val bin = Bin(b)
        var best: Map<Int, String> = emptyMap()
        for (o in 0 until b.size - 12) {
            val size = bin.i32(o)
            if (size < 8 || o + 4 + size > b.size) continue
            val count = bin.u16(o + 4)
            if (count !in 1..256) continue
            var p = o + 6
            val map = HashMap<Int, String>()
            var ok = true
            for (i in 0 until count) {
                if (p + 6 > o + 4 + size) { ok = false; break }
                val id = bin.i32(p)
                val len = bin.u16(p + 4)
                if (id !in 0..100_000 || p + 6 + 2 * len > o + 4 + size) { ok = false; break }
                val s = bin.utf16(p + 6, len)
                if (s.any { it.code < 0x20 }) { ok = false; break }
                map[id] = s
                p += 6 + 2 * len
            }
            if (ok && p == o + 4 + size && map.values.any { it.contains("pen", ignoreCase = true) }) {
                if (map.size > best.size) best = map
            }
        }
        return best
    }

    /** media/mediaInfo.dat: bind id -> nome do arquivo em media/. */
    private fun mediaNames(b: ByteArray): Map<Int, String> {
        val bin = Bin(b)
        val out = HashMap<Int, String>()
        val first = bin.i32(0)
        if (first > 3001) {
            val count = bin.u16(4)
            var o = 6
            for (i in 0 until count) {
                val size = bin.u32(o)
                val bind = bin.i32(o + 4)
                val len = bin.u16(o + 8)
                out[bind] = bin.utf16(o + 10, len)
                o += 4 + size
            }
        } else {
            val count = bin.u16(0)
            var o = 2
            for (i in 0 until count) {
                val bind = bin.i32(o)
                val len = bin.u16(o + 4)
                out[bind] = bin.utf16(o + 6, len)
                o += 6 + 2 * len + 64 + 2 + 8
            }
        }
        return out
    }

    // ---- Conversão para nota do RosaNotes ------------------------------------------

    /** Tipo de pincel a partir do nome da caneta do Samsung Notes. */
    fun brushFor(pen: String?, color: Int): BrushType {
        val n = pen?.substringAfterLast('.')?.lowercase()
        return when {
            n == null -> if (Color.alpha(color) < 250) BrushType.HIGHLIGHTER else BrushType.PEN
            "marker" in n || "highlight" in n -> BrushType.HIGHLIGHTER
            "pencil" in n || "crayon" in n -> BrushType.PENCIL
            "fountain" in n -> BrushType.FOUNTAIN
            "oblique" in n || "calligraph" in n -> BrushType.CALLIGRAPHY
            "brush" in n -> BrushType.BRUSH
            else -> BrushType.PEN
        }
    }

    /**
     * Monta a nota: cada página do Samsung vira uma folha (ou, nas notas de rolagem
     * comprida, um trecho contínuo da tela infinita). [sheetWidth] é a largura da folha
     * em unidades do mundo (a mesma das folhas criadas no app).
     */
    fun toNoteData(note: SNote, assetsDir: File, sheetWidth: Float, textSize: Float): NoteData {
        val first = note.pages.first()
        val s = sheetWidth / first.w
        val sheetH = first.h * s
        val ratio = first.h / first.w
        val paged = ratio <= 1.6f
        val layout = if (paged) {
            PageLayout(paged = true, horizontal = false, landscape = first.w > first.h, endless = false,
                count = note.pages.size, w = sheetWidth, h = sheetH, ox = 0f, oy = 0f)
        } else PageLayout(w = sheetWidth, h = sheetH)
        val elements = ArrayList<Element>()
        var y0 = 0f
        note.pages.forEachIndexed { i, page ->
            val k = sheetWidth / page.w
            val ox = 0f
            val oy = if (paged) layout.pageRect(i).top else y0
            y0 += page.h * k
            if (i == 0 && note.body != null) {
                elements.add(TextElement(note.body, ox + 48f * k, oy + 48f * k, textSize, Color.BLACK))
            }
            for (img in page.images) {
                val bytes = note.media[img.media] ?: continue
                val ext = img.media.substringAfterLast('.', "jpg").lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: continue
                assetsDir.mkdirs()
                val name = "sn_${UUID.randomUUID().toString().take(8)}.$ext"
                File(assetsDir, name).writeBytes(bytes)
                val r = img.rect
                elements.add(ImageElement(name, RectF(ox + r.left * k, oy + r.top * k, ox + r.right * k, oy + r.bottom * k)))
            }
            for (st in page.strokes) {
                val type = brushFor(st.pen, st.color)
                val n = st.xs.size
                val base = max(0.6f, st.size * k)
                val xs = FloatArray(n) { ox + st.xs[it] * k }
                val ys = FloatArray(n) { oy + st.ys[it] * k }
                // No Samsung Notes, as canetas sensíveis à pressão desenham com diâmetro de
                // "tamanho × pressão" (e a pressão gravada costuma ficar entre 0,1 e 0,4).
                val ws = when (type) {
                    BrushType.HIGHLIGHTER -> FloatArray(n) { base }
                    BrushType.PEN -> FloatArray(n) { base * 0.8f }
                    else -> FloatArray(n) { base * (0.3f + 1.5f * st.ps[it]).coerceIn(0.3f, 1f) }
                }
                val alpha = if (type == BrushType.HIGHLIGHTER) min(Color.alpha(st.color), 115) else Color.alpha(st.color).coerceAtLeast(12)
                val color = st.color or (0xFF shl 24)
                elements.add(StrokeElement(type, color, alpha, xs, ys, ws))
            }
            for (t in page.texts) {
                elements.add(TextElement(t.text, ox + t.rect.left * k, oy + t.rect.top * k, textSize, Color.BLACK))
            }
        }
        return NoteData(elements, null, PageStyle.BLANK, Color.WHITE, layout)
    }

    /** Miniatura para a lista de notas: o topo da primeira página, com os traços. */
    fun thumbnail(data: NoteData, w: Int = 480, h: Int = 360): Bitmap? {
        val strokes = data.elements.filterIsInstance<StrokeElement>()
        if (strokes.isEmpty()) return null
        val c = RectF(strokes[0].bounds)
        strokes.forEach { c.union(it.bounds) }
        // Só o começo da nota, como numa folha vista de cima.
        c.bottom = min(c.bottom, c.top + c.width() * h / w)
        val k = min(w / max(1f, c.width()), h / max(1f, c.height())).coerceAtMost(1f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.translate((w - c.width() * k) / 2f, (h - c.height() * k) / 2f)
        canvas.scale(k, k)
        canvas.translate(-c.left, -c.top)
        strokes.forEach { StrokeRenderer.drawElement(canvas, it) }
        return bmp
    }
}
