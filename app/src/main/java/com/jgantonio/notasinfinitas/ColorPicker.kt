package com.jgantonio.notasinfinitas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.jgantonio.notasinfinitas.Ui.circle
import com.jgantonio.notasinfinitas.Ui.dp
import com.jgantonio.notasinfinitas.Ui.dpi
import com.jgantonio.notasinfinitas.Ui.horizontal
import com.jgantonio.notasinfinitas.Ui.buttonRow
import com.jgantonio.notasinfinitas.Ui.primaryButton
import com.jgantonio.notasinfinitas.Ui.rounded
import com.jgantonio.notasinfinitas.Ui.secondaryButton
import com.jgantonio.notasinfinitas.Ui.sheet

/**
 * Seletor de cor personalizada: quadrado de saturação/brilho, barra de matiz,
 * fileira de tons da cor escolhida (do mais claro ao mais escuro) e código hex.
 */
object ColorPicker {

    /** Quantos tons aparecem na fileira. */
    private const val TONES = 9

    /**
     * Tons de [color]: mesmo matiz e saturação, luminosidade (HSL) de quase branco
     * a quase preto. Cores cinzentas viram uma escala de cinzas.
     */
    fun tones(color: Int): List<Int> {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        val sat = if (d < 1e-4f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val levels = floatArrayOf(0.93f, 0.85f, 0.75f, 0.64f, 0.53f, 0.43f, 0.33f, 0.23f, 0.14f)
        return levels.take(TONES).map { hsl(hsv[0], sat.coerceIn(0f, 0.92f), it) }
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
    }

    fun show(context: Context, initial: Int, onPick: (Int) -> Unit) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)

        // Tons: calculados a partir da última cor escolhida no quadrado, na barra ou no
        // código. Tocar num tom muda a cor, mas mantém a fileira (dá para comparar).
        val toneDots = List(TONES) { ColorDot(context, 0, false) {} }
        var toneBase = initial
        var refreshTone: () -> Unit = {}
        fun paintTones() {
            val cur = Color.HSVToColor(hsv)
            tones(toneBase).forEachIndexed { i, t ->
                toneDots[i].color = t
                toneDots[i].checked = t == cur
                toneDots[i].contentDescription = "Tom ${i + 1} de $TONES"
                toneDots[i].setOnClickListener {
                    Color.colorToHSV(t, hsv)
                    refreshTone()
                }
            }
        }

        val preview = View(context)
        val hex = EditText(context).apply {
            filters = arrayOf(InputFilter.LengthFilter(7))
            isSingleLine = true
            textSize = 15f
            setTextColor(Ui.INK)
            typeface = Ui.REGULAR
        }
        lateinit var sv: SatValView
        lateinit var hue: HueBar
        var updatingText = false

        fun refresh(fromText: Boolean = false, fromTone: Boolean = false) {
            val c = Color.HSVToColor(hsv)
            if (!fromTone) toneBase = c
            paintTones()
            preview.background = context.circle(c, Ui.LINE, 1f)
            sv.hue = hsv[0]
            sv.invalidate()
            hue.invalidate()
            if (!fromText) {
                updatingText = true
                hex.setText(String.format("#%06X", c and 0xFFFFFF))
                updatingText = false
            }
        }

        refreshTone = { refresh(fromTone = true) }
        sv = SatValView(context, hsv) { refresh() }
        hue = HueBar(context, hsv) { refresh() }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingText) return
                val t = s.toString().trim()
                if (t.length == 7 && t.startsWith("#")) {
                    try {
                        Color.colorToHSV(Color.parseColor(t), hsv)
                        refresh(fromText = true)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
        })

        hex.background = context.rounded(Ui.FIELD, 12f)
        hex.setPadding(context.dpi(14f), context.dpi(10f), context.dpi(14f), context.dpi(10f))
        context.sheet("Cor personalizada") { root, dialog ->
            root.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpi(190f)))
            root.addView(hue, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpi(28f)).apply {
                topMargin = context.dpi(16f)
            })
            root.addView(Ui.run { context.label("Tons desta cor", 13f) }.apply { setPadding(0, context.dpi(16f), 0, context.dpi(6f)) })
            val tonesRow = context.horizontal()
            for (d in toneDots) {
                tonesRow.addView(d, LinearLayout.LayoutParams(0, context.dpi(34f), 1f).apply {
                    val m = context.dpi(2f)
                    setMargins(m, 0, m, 0)
                })
            }
            root.addView(tonesRow)
            val row = context.horizontal().apply { setPadding(0, context.dpi(16f), 0, 0) }
            row.addView(preview, LinearLayout.LayoutParams(context.dpi(40f), context.dpi(40f)).apply { marginEnd = context.dpi(12f) })
            row.addView(hex, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(row)
            root.addView(context.buttonRow(
                context.secondaryButton("Cancelar") { dialog.dismiss() },
                context.primaryButton("Usar cor") { dialog.dismiss(); onPick(Color.HSVToColor(hsv)) },
            ))
            refresh()
        }
    }

    private class SatValView(context: Context, val hsv: FloatArray, val onChange: () -> Unit) : View(context) {
        var hue = hsv[0]
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3f)
            color = Color.WHITE
        }
        private val ringOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1f)
            color = Color.parseColor("#55000000")
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            val sat = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
            val v = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
            paint.shader = ComposeShader(v, sat, PorterDuff.Mode.MULTIPLY)
            canvas.drawRoundRect(rect, context.dp(10f), context.dp(10f), paint)
            val x = hsv[1] * width
            val y = (1f - hsv[2]) * height
            canvas.drawCircle(x, y, context.dp(9f), ring)
            canvas.drawCircle(x, y, context.dp(10.5f), ringOuter)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            hsv[1] = (e.x / width).coerceIn(0f, 1f)
            hsv[2] = 1f - (e.y / height).coerceIn(0f, 1f)
            onChange()
            return true
        }
    }

    private class HueBar(context: Context, val hsv: FloatArray, val onChange: () -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3f)
            color = Color.WHITE
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
            val x = hsv[0] / 360f * width
            canvas.drawCircle(x.coerceIn(height / 2f, width - height / 2f), height / 2f, height / 2f - context.dp(2f), marker)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            hsv[0] = (e.x / width).coerceIn(0f, 1f) * 359.9f
            onChange()
            return true
        }
    }
}
