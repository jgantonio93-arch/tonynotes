package com.jgantonio.notasinfinitas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Modelos de folha (universitário, Cornell, partitura e planner semanal).
 *
 * São desenhados em coordenadas do mundo dentro do retângulo da folha: servem
 * igual para a tela (com o canvas já transformado) e para o PDF exportado.
 * Na tela infinita, sem folha, cada modelo usa o fundo simples parecido
 * ([fallback]).
 */
object PageTemplates {

    private val WEEK = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo", "Notas")

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

    /** O modelo precisa de folha (os demais são padrões que repetem). */
    fun isTemplate(s: PageStyle) = s == PageStyle.COLLEGE || s == PageStyle.CORNELL || s == PageStyle.MUSIC || s == PageStyle.PLANNER

    /** Fundo usado na tela infinita, onde não há folha para o modelo. */
    fun fallback(s: PageStyle) = when (s) {
        PageStyle.COLLEGE, PageStyle.CORNELL, PageStyle.MUSIC -> PageStyle.LINES
        PageStyle.PLANNER -> PageStyle.GRID
        else -> s
    }

    /**
     * Desenha o modelo [s] na folha [r]. [px] é a largura de 1 pixel de tela em
     * unidades do mundo (as linhas ficam com 1 px em qualquer zoom; no PDF, 0,5 pt).
     */
    fun draw(canvas: Canvas, s: PageStyle, r: RectF, dark: Boolean, px: Float) {
        val lineC = if (dark) Color.parseColor("#3A4250") else Color.parseColor("#D9DEE6")
        val strongC = if (dark) Color.parseColor("#56607A") else Color.parseColor("#A9B3C2")
        val marginC = if (dark) Color.parseColor("#7A3B44") else Color.parseColor("#F2A7AE")
        line.strokeWidth = px
        val w = r.width()
        val h = r.height()
        val gap = minOf(w, h) / 24f
        when (s) {
            PageStyle.COLLEGE -> {
                ruled(canvas, r, r.top + gap * 3f, r.bottom - gap, gap, lineC)
                line.color = marginC
                line.strokeWidth = px * 1.5f
                canvas.drawLine(r.left + w * 0.12f, r.top, r.left + w * 0.12f, r.bottom, line)
                line.color = lineC
                line.strokeWidth = px * 1.5f
                canvas.drawLine(r.left, r.top + gap * 2f, r.right, r.top + gap * 2f, line)
            }
            PageStyle.CORNELL -> {
                val head = r.top + h * 0.11f
                val foot = r.top + h * 0.80f
                val cue = r.left + w * 0.30f
                ruled(canvas, RectF(cue, r.top, r.right, r.bottom), head + gap, foot - gap * 0.5f, gap, lineC)
                line.color = strongC
                line.strokeWidth = px * 1.5f
                canvas.drawLine(r.left, head, r.right, head, line)
                canvas.drawLine(r.left, foot, r.right, foot, line)
                canvas.drawLine(cue, head, cue, foot, line)
                label(canvas, "Palavras-chave", r.left + gap * 0.6f, head + gap * 0.9f, gap, strongC)
                label(canvas, "Resumo", r.left + gap * 0.6f, foot + gap * 0.9f, gap, strongC)
            }
            PageStyle.MUSIC -> {
                // Pautas de 5 linhas, com folga entre elas.
                val step = gap * 0.5f
                val staff = step * 4f
                val space = step * 6f
                line.color = strongC
                var y = r.top + gap * 2f
                while (y + staff < r.bottom - gap) {
                    for (k in 0 until 5) canvas.drawLine(r.left + gap, y + k * step, r.right - gap, y + k * step, line)
                    y += staff + space
                }
            }
            PageStyle.PLANNER -> {
                val top = r.top + gap * 2.5f
                val cols = if (w > h) 4 else 2
                val rows = WEEK.size / cols
                val cw = (w - gap * 2f) / cols
                val ch = (r.bottom - gap - top) / rows
                line.color = strongC
                line.strokeWidth = px * 1.2f
                for (i in WEEK.indices) {
                    val cx = r.left + gap + (i % cols) * cw
                    val cy = top + (i / cols) * ch
                    val box = RectF(cx + gap * 0.25f, cy + gap * 0.25f, cx + cw - gap * 0.25f, cy + ch - gap * 0.25f)
                    canvas.drawRoundRect(box, gap * 0.4f, gap * 0.4f, line)
                    label(canvas, WEEK[i], box.left + gap * 0.45f, box.top + gap * 0.9f, gap, strongC)
                    ruled(canvas, RectF(box.left + gap * 0.4f, box.top, box.right - gap * 0.4f, box.bottom), box.top + gap * 1.7f, box.bottom - gap * 0.3f, gap, lineC)
                }
                label(canvas, "Semana de ____/____", r.left + gap, r.top + gap * 1.6f, gap * 1.2f, strongC)
            }
            else -> Unit
        }
    }

    private fun ruled(canvas: Canvas, r: RectF, from: Float, to: Float, gap: Float, color: Int) {
        line.color = color
        var y = from
        while (y <= to) {
            canvas.drawLine(r.left, y, r.right, y, line)
            y += gap
        }
    }

    private fun label(canvas: Canvas, s: String, x: Float, y: Float, gap: Float, color: Int) {
        text.color = color
        text.textSize = gap * 0.55f
        text.typeface = Ui.MEDIUM
        canvas.drawText(s, x, y, text)
    }
}
