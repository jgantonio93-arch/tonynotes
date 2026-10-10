package com.jgantonio.notasinfinitas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Desenhos das canetas, no estilo da bandeja do Samsung Notes: corpo claro em pé
 * e ponta com o formato de cada pincel, pintada com a cor atual.
 */
object PenArt {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val BODY = Color.parseColor("#FBFBFD")
    private val BODY_SHADE = Color.parseColor("#E9ECF1")
    private val OUTLINE = Color.parseColor("#C9CED6")
    private val METAL = Color.parseColor("#D5D9E0")
    private val WOOD = Color.parseColor("#F1D3A1")

    /** Cor visível da ponta (branco puro some no fundo branco). */
    private fun tipColor(c: Int): Int {
        val opaque = c or (0xFF shl 24)
        return if (Color.luminance(opaque) > 0.92f) Color.parseColor("#E9ECF1") else opaque
    }

    /**
     * Desenha uma caneta em pé com a ponta em (cx, top), largura [w] e comprimento até [bottom].
     * [u] é a unidade (1dp) para espessuras de contorno.
     */
    fun draw(canvas: Canvas, type: BrushType, color: Int, cx: Float, top: Float, bottom: Float, w: Float, u: Float) {
        val tip = tipColor(color)
        val half = w / 2f
        val tipH = w * 1.25f
        val bodyTop = top + tipH
        line.strokeWidth = u
        line.color = OUTLINE

        // Corpo com leve degradê lateral
        val body = RectF(cx - half, bodyTop, cx + half, bottom + w)
        fill.shader = LinearGradient(body.left, 0f, body.right, 0f,
            intArrayOf(BODY_SHADE, BODY, BODY, BODY_SHADE), floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, half * 0.5f, half * 0.5f, fill)
        fill.shader = null
        canvas.drawRoundRect(body, half * 0.5f, half * 0.5f, line)
        // Anel colorido no corpo
        fill.color = tip
        if (type == BrushType.HIGHLIGHTER) fill.alpha = 220
        canvas.drawRect(cx - half + u * 0.5f, bodyTop + w * 0.55f, cx + half - u * 0.5f, bodyTop + w * 0.85f, fill)
        fill.alpha = 255

        val p = Path()
        when (type) {
            BrushType.PEN -> {
                p.moveTo(cx - half, bodyTop)
                p.lineTo(cx - w * 0.14f, top + w * 0.22f)
                p.lineTo(cx + w * 0.14f, top + w * 0.22f)
                p.lineTo(cx + half, bodyTop)
                p.close()
                fill.color = METAL
                canvas.drawPath(p, fill)
                canvas.drawPath(p, line)
                fill.color = tip
                canvas.drawCircle(cx, top + w * 0.18f, w * 0.13f, fill)
            }
            BrushType.FOUNTAIN -> {
                p.moveTo(cx - half, bodyTop)
                p.cubicTo(cx - half, top + tipH * 0.45f, cx - w * 0.12f, top + tipH * 0.2f, cx, top)
                p.cubicTo(cx + w * 0.12f, top + tipH * 0.2f, cx + half, top + tipH * 0.45f, cx + half, bodyTop)
                p.close()
                fill.color = tip
                canvas.drawPath(p, fill)
                line.color = Color.WHITE
                line.strokeWidth = u * 1.2f
                canvas.drawLine(cx, top + tipH * 0.25f, cx, top + tipH * 0.72f, line)
                canvas.drawCircle(cx, top + tipH * 0.75f, w * 0.07f, line)
                line.color = OUTLINE
                line.strokeWidth = u
            }
            BrushType.PENCIL -> {
                p.moveTo(cx - half, bodyTop)
                p.lineTo(cx, top)
                p.lineTo(cx + half, bodyTop)
                p.close()
                fill.color = WOOD
                canvas.drawPath(p, fill)
                val lead = Path().apply {
                    moveTo(cx - half * 0.36f, top + tipH * 0.36f)
                    lineTo(cx, top)
                    lineTo(cx + half * 0.36f, top + tipH * 0.36f)
                    close()
                }
                fill.color = tip
                canvas.drawPath(lead, fill)
                canvas.drawPath(p, line)
            }
            BrushType.CALLIGRAPHY -> {
                p.moveTo(cx - half, bodyTop)
                p.lineTo(cx - half * 0.55f, top)
                p.lineTo(cx + half * 0.7f, top + tipH * 0.32f)
                p.lineTo(cx + half, bodyTop)
                p.close()
                fill.color = tip
                canvas.drawPath(p, fill)
            }
            BrushType.BRUSH -> {
                // Virola metálica + cerdas em gota
                fill.color = METAL
                val ferrule = RectF(cx - half * 0.8f, bodyTop - tipH * 0.3f, cx + half * 0.8f, bodyTop + u)
                canvas.drawRect(ferrule, fill)
                canvas.drawRect(ferrule, line)
                p.moveTo(cx - half * 0.8f, ferrule.top)
                p.cubicTo(cx - half * 0.9f, top + tipH * 0.35f, cx - w * 0.1f, top + tipH * 0.1f, cx, top)
                p.cubicTo(cx + w * 0.1f, top + tipH * 0.1f, cx + half * 0.9f, top + tipH * 0.35f, cx + half * 0.8f, ferrule.top)
                p.close()
                fill.color = tip
                canvas.drawPath(p, fill)
            }
            BrushType.HIGHLIGHTER -> {
                p.moveTo(cx - half, bodyTop)
                p.lineTo(cx - half * 0.7f, top + tipH * 0.12f)
                p.lineTo(cx + half * 0.7f, top + tipH * 0.42f)
                p.lineTo(cx + half, bodyTop)
                p.close()
                fill.color = tip
                fill.alpha = 225
                canvas.drawPath(p, fill)
                fill.alpha = 255
            }
        }
    }
}

/** Uma caneta pequena e inclinada, para botões e favoritos. */
class PenGlyphDrawable(
    private val type: BrushType,
    private val color: Int,
    private val sizePx: Int,
    private val unit: Float,
) : Drawable() {
    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        canvas.save()
        canvas.translate(b.exactCenterX(), b.exactCenterY())
        canvas.rotate(40f)
        canvas.clipRect(-s, -s * 0.72f, s, s * 0.62f)
        val w = s * 0.24f
        PenArt.draw(canvas, type, color, 0f, -s * 0.62f, s * 0.62f, w, unit * 0.8f)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Pasta colorida (aba de trás mais escura, frente mais clara), como no Samsung Notes. */
class FolderGlyphDrawable(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val l = b.left.toFloat()
        val t = b.top.toFloat()
        val r = w * 0.12f
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val back = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2] * 0.82f))
        val front = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.85f, minOf(1f, hsv[2] * 1.05f)))

        paint.color = back
        val tab = Path().apply {
            moveTo(l, t + r)
            quadTo(l, t, l + r, t)
            lineTo(l + w * 0.36f, t)
            quadTo(l + w * 0.42f, t, l + w * 0.46f, t + h * 0.1f)
            lineTo(l + w * 0.5f, t + h * 0.16f)
            lineTo(l + w - r, t + h * 0.16f)
            quadTo(l + w, t + h * 0.16f, l + w, t + h * 0.16f + r)
            lineTo(l + w, t + h - r)
            lineTo(l, t + h - r)
            close()
        }
        canvas.drawPath(tab, paint)
        // Folha branca aparecendo
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(l + w * 0.1f, t + h * 0.22f, l + w * 0.9f, t + h * 0.6f), r * 0.6f, r * 0.6f, paint)
        // Frente
        paint.shader = LinearGradient(0f, t + h * 0.3f, 0f, t + h, front, color, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(l, t + h * 0.3f, l + w, t + h), r, r, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Logo do RosaNotes: quadrado arredondado vermelho com um "R" branco cuja barriga é
 * uma rosa (miolo em espiral) e a perna é o caule, com uma folha (o mesmo desenho do
 * ícone do app, em 108 x 108).
 */
class LogoDrawable(private val sizePx: Int) : Drawable() {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 108f, 108f, Color.parseColor("#E8404F"), Color.parseColor("#A3162B"), Shader.TileMode.CLAMP)
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC9D1") }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val petal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B81D33")
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 108f
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(s, s)
        canvas.drawRoundRect(0f, 0f, 108f, 108f, 30f, 30f, bg)
        canvas.drawPath(LEAF, leafPaint)
        canvas.drawPath(STEM, stroke)
        canvas.drawPath(LEG, stroke)
        canvas.drawPath(BLOOM, white)
        canvas.drawPath(SPIRAL, petal)
        canvas.drawPath(CUP, petal)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        bg.alpha = alpha; white.alpha = alpha; leafPaint.alpha = alpha; stroke.alpha = alpha; petal.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bg.colorFilter = colorFilter; white.colorFilter = colorFilter; leafPaint.colorFilter = colorFilter
        stroke.colorFilter = colorFilter; petal.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        // Mesmos caminhos de res/drawable/ic_launcher_foreground.xml.
        val LEAF = Path().apply {
            moveTo(61f, 69f); cubicTo(60f, 61f, 67f, 56f, 75f, 57f); cubicTo(75f, 65f, 68f, 70f, 61f, 69f); close()
        }
        val STEM = Path().apply { moveTo(37f, 34f); lineTo(37f, 80f) }
        val LEG = Path().apply { moveTo(50f, 56f); cubicTo(57f, 63f, 62f, 72f, 71f, 80f) }
        val BLOOM = Path().apply {
            moveTo(32.5f, 33f); cubicTo(32.5f, 27f, 37f, 25f, 41f, 26.5f)
            cubicTo(44f, 22f, 51f, 21f, 54.5f, 25.5f)
            cubicTo(59f, 21f, 67f, 22.5f, 69f, 29f)
            cubicTo(73f, 32f, 74f, 37f, 73f, 42f)
            cubicTo(71.5f, 52f, 63.5f, 59f, 52f, 59f)
            lineTo(37f, 59f); cubicTo(34.5f, 59f, 32.5f, 57f, 32.5f, 54.5f); close()
        }
        val SPIRAL = Path().apply {
            moveTo(54.5f, 37f); cubicTo(52.5f, 36f, 50.5f, 38f, 51.8f, 40.2f)
            cubicTo(53.3f, 42.6f, 58f, 41.6f, 58f, 37.6f)
            cubicTo(58f, 33.2f, 52.5f, 31.6f, 49.6f, 34.6f)
            cubicTo(46.4f, 38f, 47.6f, 44.6f, 54f, 45.6f)
            cubicTo(60.5f, 46.5f, 64.8f, 42f, 64.6f, 36f)
        }
        val CUP = Path().apply {
            moveTo(42.5f, 40f); cubicTo(42.5f, 49.5f, 48.5f, 53.8f, 55f, 53.8f); cubicTo(62f, 53.8f, 67.5f, 49.5f, 68.5f, 42.5f)
        }
    }
}

