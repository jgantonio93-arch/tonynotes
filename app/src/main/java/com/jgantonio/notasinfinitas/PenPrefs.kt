package com.jgantonio.notasinfinitas

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** Lembra as configurações de cada pincel, favoritos e cores, como o Samsung Notes. */
class PenPrefs(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("canetas", Context.MODE_PRIVATE)

    fun pen(type: BrushType): PenSettings =
        sp.getString("pen_${type.name}", null)?.let(PenSettings::decode)
            ?: PenSettings.default(type, if (type == BrushType.HIGHLIGHTER) HIGHLIGHT_COLORS[0] else INK_COLORS[0])

    fun savePen(p: PenSettings) = sp.edit().putString("pen_${p.type.name}", p.encode()).apply()

    var currentType: BrushType
        get() = BrushType.byName(sp.getString("current", null)) ?: BrushType.PEN
        set(v) {
            val e = sp.edit().putString("current", v.name)
            if (v != BrushType.HIGHLIGHTER) e.putString("last_ink", v.name)
            e.apply()
        }

    /** Última caneta (sem ser o marca-texto), para o botão da caneta voltar a ela. */
    val lastInkType: BrushType
        get() = BrushType.byName(sp.getString("last_ink", null))?.takeIf { it != BrushType.HIGHLIGHTER } ?: BrushType.PEN

    /** Marca-texto acompanha a altura da escrita que está embaixo dele. */
    var highlighterFitsWriting: Boolean
        get() = sp.getBoolean("hl_fit", true)
        set(v) = sp.edit().putBoolean("hl_fit", v).apply()

    var favorites: List<PenSettings>
        get() = sp.getString("favorites", "")!!.split(';').mapNotNull { if (it.isBlank()) null else PenSettings.decode(it) }
        set(v) = sp.edit().putString("favorites", v.joinToString(";") { it.encode() }).apply()

    var customColors: List<Int>
        get() = sp.getString("custom_colors", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        set(v) = sp.edit().putString("custom_colors", v.take(MAX_CUSTOM).joinToString(",")).apply()

    fun addCustomColor(c: Int) {
        customColors = listOf(c) + customColors.filter { it != c }
    }

    var eraserArea: Boolean
        get() = sp.getBoolean("eraser_area", false)
        set(v) = sp.edit().putBoolean("eraser_area", v).apply()

    var eraserSize: Float
        get() = sp.getFloat("eraser_size", 14f)
        set(v) = sp.edit().putFloat("eraser_size", v).apply()

    var eraserHighlighterOnly: Boolean
        get() = sp.getBoolean("eraser_hl", false)
        set(v) = sp.edit().putBoolean("eraser_hl", v).apply()

    var selectRect: Boolean
        get() = sp.getBoolean("select_rect", false)
        set(v) = sp.edit().putBoolean("select_rect", v).apply()

    /** Fita para esconder: cor e largura (dp). */
    var tapeColor: Int
        get() = sp.getInt("tape_color", TAPE_COLORS[0])
        set(v) = sp.edit().putInt("tape_color", v).apply()

    var tapeWidth: Float
        get() = sp.getFloat("tape_width", 26f)
        set(v) = sp.edit().putFloat("tape_width", v).apply()

    var selectPartial: Boolean
        get() = sp.getBoolean("select_partial", false)
        set(v) = sp.edit().putBoolean("select_partial", v).apply()

    /** Onde fica a barra de ferramentas: "top" (padrão), "free", "left" ou "right". */
    var dockMode: String
        get() = sp.getString("dock_mode", "top")!!
        set(v) = sp.edit().putString("dock_mode", v).apply()

    /** Posição da barra solta (fração da área da nota, 0..1). */
    var dockX: Float
        get() = sp.getFloat("dock_x", 0.5f)
        set(v) = sp.edit().putFloat("dock_x", v).apply()
    var dockY: Float
        get() = sp.getFloat("dock_y", 0f)
        set(v) = sp.edit().putFloat("dock_y", v).apply()

    /** As 3 cores rápidas da barra vertical (podem ser trocadas com toque longo). */
    var quickColors: List<Int>
        get() {
            val saved = sp.getString("quick_colors", "")!!.split(',').mapNotNull { it.toIntOrNull() }
            return if (saved.size == 3) saved else listOf(INK_COLORS[0], INK_COLORS[3], INK_COLORS[8])
        }
        set(v) = sp.edit().putString("quick_colors", v.take(3).joinToString(",")).apply()

    /** Modo de página das notas novas (o último escolhido). */
    var defaultPageLayout: String?
        get() = sp.getString("page_layout", null)
        set(v) = sp.edit().putString("page_layout", v).apply()

    var fingerDraws: Boolean
        get() = sp.getBoolean("finger_draws", false)
        set(v) = sp.edit().putBoolean("finger_draws", v).apply()

    /** Rejeição de palma do próprio app. */
    var palmReject: Boolean
        get() = sp.getBoolean("palm_reject", true)
        set(v) = sp.edit().putBoolean("palm_reject", v).apply()

    var leftHanded: Boolean
        get() = sp.getBoolean("left_handed", false)
        set(v) = sp.edit().putBoolean("left_handed", v).apply()

    /** Protetor de mão: "off", "bottom" (faixa embaixo), "side" (faixa na lateral) ou "free" (ilha). */
    var palmGuardMode: String
        get() = sp.getString("palm_guard_mode", null) ?: if (sp.getBoolean("palm_guard", false)) "bottom" else "off"
        set(v) = sp.edit().putString("palm_guard_mode", v).apply()

    /** Onde o protetor ficou (frações da tela: esquerda, topo, direita, base), por modo. */
    fun palmGuardRect(mode: String): FloatArray? =
        sp.getString("palm_guard_rect_$mode", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray()

    fun setPalmGuardRect(mode: String, r: FloatArray) =
        sp.edit().putString("palm_guard_rect_$mode", r.joinToString(",")).apply()

    var autoShapes: Boolean
        get() = sp.getBoolean("auto_shapes", false)
        set(v) = sp.edit().putBoolean("auto_shapes", v).apply()

    /**
     * Tinta em front buffer (latência mínima). Experimental e desligada por padrão: em alguns
     * aparelhos a tinta parava de aparecer no meio da escrita. Chave nova para desligar também
     * para quem estava com o padrão antigo (ligado).
     */
    var lowLatency: Boolean
        get() = sp.getBoolean("low_latency_v2", false)
        set(v) = sp.edit().putBoolean("low_latency_v2", v).apply()

    /** Tela cheia no editor (só a barra de ferramentas fica na tela). */
    var fullscreen: Boolean
        get() = sp.getBoolean("fullscreen", false)
        set(v) = sp.edit().putBoolean("fullscreen", v).apply()

    var spenButtonErases: Boolean
        get() = sp.getBoolean("spen_button_erases", true)
        set(v) = sp.edit().putBoolean("spen_button_erases", v).apply()

    companion object {
        const val MAX_FAVORITES = 10
        val TAPE_COLORS = listOf("#F4A3B4", "#F9C784", "#FFE680", "#A8DDB5", "#9CC9F0", "#C3B1E1", "#B8B3AA")
            .map(android.graphics.Color::parseColor)
        const val MAX_CUSTOM = 12

        val INK_TYPES = BrushType.entries.filter { it != BrushType.HIGHLIGHTER }

        val INK_COLORS = listOf(
            "#1B1B1F", "#5F6368", "#FFFFFF", "#D7263D", "#F27405", "#F2C230",
            "#1E9E5A", "#1A9FB5", "#1F5FD1", "#0B2A6B", "#8E44AD", "#E84A8A", "#6D4C41",
        ).map(Color::parseColor)

        val HIGHLIGHT_COLORS = listOf(
            "#FFE600", "#9BF26B", "#FF8AD8", "#6BD5FF", "#FFB347", "#C59BFF",
        ).map(Color::parseColor)
    }
}
