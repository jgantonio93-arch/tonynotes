package com.jgantonio.notasinfinitas

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * Nota no tema noturno: o papel claro fica escuro e a tinta se inverte "com
 * inteligência" — o claro vira escuro e o escuro vira claro, mas cada cor mantém o
 * próprio tom (o vermelho continua vermelho, o azul continua azul). É a inversão de
 * negativo seguida de um giro de 180° no matiz.
 *
 * Só muda o que aparece na tela: a nota gravada, as miniaturas e o que se exporta
 * continuam com as cores de verdade.
 */
object Night {
    val matrix: ColorMatrix = ColorMatrix().apply {
        val invert = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ))
        // Giro de matiz de 180° (mesma fórmula do feColorMatrix "hueRotate").
        val hue = ColorMatrix(floatArrayOf(
            -0.574f, 1.430f, 0.144f, 0f, 0f,
            0.426f, 0.430f, 0.144f, 0f, 0f,
            0.426f, 1.430f, -0.856f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        setConcat(hue, invert)
    }

    val filter = ColorMatrixColorFilter(matrix)

    /** A mesma conversão, para uma cor solta (ex.: a tinta molhada da caneta). */
    fun map(color: Int): Int {
        val m = matrix.array
        val r = Color.red(color).toFloat()
        val g = Color.green(color).toFloat()
        val b = Color.blue(color).toFloat()
        fun ch(i: Int) = (m[i] * r + m[i + 1] * g + m[i + 2] * b + m[i + 4]).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), ch(0), ch(5), ch(10))
    }

    /** Papel claro no escuro: a cor convertida, um pouco mais clara que o preto puro. */
    fun paper(color: Int): Int {
        val c = map(color)
        fun lift(v: Int) = (v + (255 - v) * 0.08f).toInt() + 6
        return Color.rgb(lift(Color.red(c)).coerceAtMost(255), lift(Color.green(c)).coerceAtMost(255), lift(Color.blue(c)).coerceAtMost(255))
    }

    /** O papel é claro o bastante para ser convertido (papéis escuros ficam como estão). */
    fun isLightPaper(color: Int) = Color.luminance(color) > 0.5f
}
