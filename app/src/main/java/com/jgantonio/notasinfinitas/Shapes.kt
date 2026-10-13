package com.jgantonio.notasinfinitas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Formas prontas (Inserir › Forma). Cada forma é uma lista de traços (xs, ys) numa caixa
 * de largura [w] e altura [h] a partir de (0, 0); quem insere escolhe cor e espessura.
 */
object Shapes {
    enum class Kind(val label: String) {
        RECT("Retângulo"), SQUARE("Quadrado"), ELLIPSE("Elipse"), CIRCLE("Círculo"),
        TRIANGLE("Triângulo"), LINE("Linha"), ARROW("Seta"), DOUBLE_ARROW("Seta dupla"), TABLE("Tabela"),
    }

    class Line(val xs: FloatArray, val ys: FloatArray)

    fun rect(w: Float, h: Float) = listOf(Line(floatArrayOf(0f, w, w, 0f, 0f), floatArrayOf(0f, 0f, h, h, 0f)))

    fun ellipse(w: Float, h: Float, steps: Int = 72): List<Line> {
        val xs = FloatArray(steps + 1) { w / 2f + w / 2f * cos(2 * PI * it / steps).toFloat() }
        val ys = FloatArray(steps + 1) { h / 2f + h / 2f * sin(2 * PI * it / steps).toFloat() }
        return listOf(Line(xs, ys))
    }

    fun triangle(w: Float, h: Float) = listOf(Line(floatArrayOf(w / 2f, w, 0f, w / 2f), floatArrayOf(0f, h, h, 0f)))

    fun line(w: Float) = listOf(Line(floatArrayOf(0f, w), floatArrayOf(0f, 0f)))

    /** Linha com ponta (e, com [double], ponta nos dois lados). */
    fun arrow(w: Float, double: Boolean): List<Line> {
        val head = min(w * 0.18f, 60f).coerceAtLeast(8f)
        val out = mutableListOf(Line(floatArrayOf(0f, w), floatArrayOf(0f, 0f)))
        out.add(Line(floatArrayOf(w - head, w, w - head), floatArrayOf(-head * 0.6f, 0f, head * 0.6f)))
        if (double) out.add(Line(floatArrayOf(head, 0f, head), floatArrayOf(-head * 0.6f, 0f, head * 0.6f)))
        return out
    }

    /** Moldura e linhas internas de uma tabela [rows] × [cols]. */
    fun table(rows: Int, cols: Int, w: Float, h: Float): List<Line> {
        val out = rect(w, h).toMutableList()
        for (r in 1 until rows) {
            val y = h * r / rows
            out.add(Line(floatArrayOf(0f, w), floatArrayOf(y, y)))
        }
        for (c in 1 until cols) {
            val x = w * c / cols
            out.add(Line(floatArrayOf(x, x), floatArrayOf(0f, h)))
        }
        return out
    }

    fun build(kind: Kind, w: Float, rows: Int = 3, cols: Int = 3): List<Line> = when (kind) {
        Kind.RECT -> rect(w, w * 0.62f)
        Kind.SQUARE -> rect(w * 0.7f, w * 0.7f)
        Kind.ELLIPSE -> ellipse(w, w * 0.62f)
        Kind.CIRCLE -> ellipse(w * 0.7f, w * 0.7f)
        Kind.TRIANGLE -> triangle(w * 0.8f, w * 0.7f)
        Kind.LINE -> line(w)
        Kind.ARROW -> arrow(w, double = false)
        Kind.DOUBLE_ARROW -> arrow(w, double = true)
        Kind.TABLE -> table(rows, cols, w, w * 0.12f * rows.coerceAtLeast(1))
    }
}
