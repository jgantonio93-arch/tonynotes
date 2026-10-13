package com.jgantonio.notasinfinitas

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jgantonio.notasinfinitas.Ui.SheetItem
import com.jgantonio.notasinfinitas.Ui.actionSheet
import com.jgantonio.notasinfinitas.Ui.buttonRow
import com.jgantonio.notasinfinitas.Ui.card
import com.jgantonio.notasinfinitas.Ui.circle
import com.jgantonio.notasinfinitas.Ui.confirm
import com.jgantonio.notasinfinitas.Ui.dp
import com.jgantonio.notasinfinitas.Ui.dpi
import com.jgantonio.notasinfinitas.Ui.horizontal
import com.jgantonio.notasinfinitas.Ui.icon
import com.jgantonio.notasinfinitas.Ui.iconButton
import com.jgantonio.notasinfinitas.Ui.label
import com.jgantonio.notasinfinitas.Ui.pill
import com.jgantonio.notasinfinitas.Ui.pillButton
import com.jgantonio.notasinfinitas.Ui.primaryButton
import com.jgantonio.notasinfinitas.Ui.promptText
import com.jgantonio.notasinfinitas.Ui.ripple
import com.jgantonio.notasinfinitas.Ui.rounded
import com.jgantonio.notasinfinitas.Ui.secondaryButton
import com.jgantonio.notasinfinitas.Ui.sectionLabel
import com.jgantonio.notasinfinitas.Ui.switchRow
import com.jgantonio.notasinfinitas.Ui.sheet
import com.jgantonio.notasinfinitas.Ui.textField
import com.jgantonio.notasinfinitas.Ui.title
import com.jgantonio.notasinfinitas.Ui.vertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Página inicial: pastas. Dentro de uma pasta aparecem as subpastas e as notas.
 * A mesma tela serve para a raiz (sem EXTRA_FOLDER) e para cada pasta.
 */
class FoldersActivity : Activity() {

    private var folderId: String? = null
    private lateinit var content: LinearLayout
    private val ptBr = Locale("pt", "BR")

    /** Tema com que a tela foi montada: se mudar (ex.: no editor), a tela se recria. */
    private var builtTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        builtTheme = Ui.themeVersion
        Ui.styleWindow(this, Ui.BG)
        Library.init(this)
        folderId = intent.getStringExtra(EXTRA_FOLDER)
        if (folderId != null && Library.folder(folderId) == null) {
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            background = Ui.run { paperBackground() }
        }
        content = vertical().apply {
            setPadding(dpi(16f), 0, dpi(16f), dpi(120f))
            // Segura o foco inicial: o teclado não abre sozinho por causa da busca.
            isFocusableInTouchMode = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        })

        // Botões flutuantes
        val fabs = horizontal()
        if (folderId != null) {
            fabs.addView(ImageView(this).apply {
                setImageDrawable(icon(Icon.PDF, Ui.INK))
                scaleType = ImageView.ScaleType.CENTER
                background = ripple(circle(Ui.SURFACE, 0, 0f), circle(Color.WHITE, 0, 0f))
                Ui.run { softShadow(6f); pressable(0.94f) }
                contentDescription = "Importar PDF ou Samsung Notes"
                setOnClickListener {
                    actionSheet("Importar", listOf(
                        SheetItem(Icon.PDF, "PDF") { pickPdf() },
                        SheetItem(Icon.NOTE, "Notas do Samsung Notes") { samsungImportHelp() },
                    ))
                }
            }, LinearLayout.LayoutParams(dpi(52f), dpi(52f)).apply { marginEnd = dpi(12f) })
            fabs.addView(ImageView(this).apply {
                setImageDrawable(icon(Icon.FOLDER_PLUS, Ui.INK))
                scaleType = ImageView.ScaleType.CENTER
                background = ripple(circle(Ui.SURFACE, 0, 0f), circle(Color.WHITE, 0, 0f))
                Ui.run { softShadow(6f); pressable(0.94f) }
                setOnClickListener { editFolder(null, folderId) }
            }, LinearLayout.LayoutParams(dpi(52f), dpi(52f)).apply { marginEnd = dpi(12f) })
        }
        fabs.addView(TextView(this).apply {
            text = if (folderId == null) "Nova pasta" else "Nova nota"
            textSize = 15f
            typeface = Ui.MEDIUM
            setTextColor(Ui.ON_ACCENT)
            gravity = Gravity.CENTER
            setPadding(dpi(18f), 0, dpi(22f), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(Icon.PLUS, Ui.ON_ACCENT, 22f, 2.2f), null, null, null)
            compoundDrawablePadding = dpi(8f)
            background = ripple(pill(Ui.ACCENT, 0), pill(Color.WHITE, 0), Color.parseColor("#33FFFFFF"))
            Ui.run { softShadow(8f); pressable(0.96f) }
            setOnClickListener { folderId?.let { newNote(it) } ?: editFolder(null, null) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpi(56f)))
        root.addView(fabs, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpi(20f), dpi(24f))
        })

        setContentView(root)
        // Na tela inicial, confere se há versão nova no GitHub (só avisa se houver).
        if (folderId == null) Updates.check(this)
        // Notas compartilhadas pelo Samsung Notes (ou abertas pelo app de arquivos).
        if (savedInstanceState == null && folderId == null) {
            sharedUris(intent).takeIf { it.isNotEmpty() }?.let { uris -> root.post { handleShared(uris) } }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Ui.themeVersion != builtTheme) {
            recreate()
            return
        }
        if (folderId != null && Library.folder(folderId) == null) {
            finish()
            return
        }
        render()
        animateEntry = false
        // Espera as gravações pendentes (miniaturas) e atualiza de novo.
        Library.io.execute { runOnUiThread { if (!isFinishing) render() } }
    }

    // ---- Montagem -----------------------------------------------------------------

    private fun columns(): Int {
        val wDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return ((wDp - 32f) / 170f).toInt().coerceIn(2, 6)
    }

    fun render() {
        val fid = folderId
        if (fid == null) {
            renderHome()
            return
        }
        content.removeAllViews()
        renderFolder(Library.folder(fid) ?: return)
    }

    // Cabeçalho da página inicial (fixo, para a busca não perder o foco) e corpo que muda.
    private var homeHead: LinearLayout? = null
    private var homeBody: LinearLayout? = null
    private var homeCount: TextView? = null
    private var homeSearch: android.widget.EditText? = null
    private var query = ""

    private fun normalize(t: String) =
        java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun countText(folders: Int, notes: Int): String {
        val parts = ArrayList<String>()
        if (folders > 0) parts.add(if (folders == 1) "1 pasta" else "$folders pastas")
        if (notes > 0) parts.add(if (notes == 1) "1 nota" else "$notes notas")
        return if (parts.isEmpty()) "Vazia" else parts.joinToString(" · ")
    }

    private fun renderHome() {
        if (homeHead == null) {
            val head = vertical().apply { setPadding(dpi(6f), dpi(44f), dpi(6f), dpi(8f)) }
            val brand = horizontal()
            brand.addView(label("RosaNotes", 14f, Ui.ACCENT, bold = true).apply {
                typeface = Ui.BOLD
                gravity = Gravity.CENTER_VERTICAL
                setCompoundDrawablesRelativeWithIntrinsicBounds(LogoDrawable(dpi(22f)), null, null, null)
                compoundDrawablePadding = dpi(8f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            brand.addView(iconButton(Icon.DOWNLOAD, Ui.MUTED, 40f, "Importar do Samsung Notes") { samsungImportHelp() })
            brand.addView(iconButton(Icon.EXPORT, Ui.MUTED, 40f, "Backup no Google Drive") { backupMenu() })
            brand.addView(iconButton(if (Ui.isDark) Icon.MOON else Icon.SUN, Ui.MUTED, 40f, "Tema") { Ui.showThemeMenu(this) })
            head.addView(brand)
            head.addView(title("Suas pastas", 40f).apply { setPadding(0, dpi(4f), 0, dpi(2f)) })
            homeCount = label("", 14f)
            head.addView(homeCount)
            val search = Ui.run { textField("", "Buscar notas e pastas") }.apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon(Icon.SEARCH, Ui.MUTED, 20f), null, null, null)
                compoundDrawablePadding = dpi(10f)
                background = Ui.run { rounded(Ui.SURFACE, 16f, Ui.LINE) }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(e: android.text.Editable?) {
                        query = e?.toString()?.trim() ?: ""
                        renderHome()
                    }
                })
            }
            homeSearch = search
            head.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(16f)
            })
            homeHead = head
            homeBody = vertical()
            content.addView(head)
            content.addView(homeBody)
        }
        homeCount?.text = countText(Library.folders.size, Library.notes.size)
        val body = homeBody!!
        body.removeAllViews()
        val target = content
        content = body
        try {
            if (query.isNotEmpty()) renderSearch() else renderHomeBody()
        } finally {
            content = target
        }
    }

    private fun renderSearch() {
        val q = normalize(query)
        // "#etiqueta" mostra só as notas com aquela etiqueta.
        val tag = if (q.startsWith("#")) q.removePrefix("#").trim() else null
        val folders = if (tag != null) emptyList() else Library.folders.filter { normalize(it.name).contains(q) }.sortedBy { it.name.lowercase() }
        val notes = Library.notes.filter { n ->
            if (tag != null) n.tags.any { normalize(it) == tag }
            else normalize(n.title).contains(q) || n.tags.any { normalize(it).contains(q) } || noteText(n.id).contains(q)
        }.sortedByDescending { it.modified }
        if (folders.isEmpty() && notes.isEmpty()) {
            content.addView(label("Nada encontrado para “$query”.", 15f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dpi(48f), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        if (folders.isNotEmpty()) {
            content.addView(sectionLabel("Pastas").apply { setPadding(dpi(6f), dpi(14f), 0, dpi(4f)) })
            addGrid(folders.map { folderCard(it) })
        }
        if (notes.isNotEmpty()) {
            content.addView(sectionLabel("Notas").apply { setPadding(dpi(6f), dpi(14f), 0, dpi(4f)) })
            addGrid(notes.map { noteCard(it, showFolder = true) })
        }
    }

    /** Fileira de cartões de nota que rola para o lado. */
    private fun noteRow(title: String, notes: List<NoteInfo>) {
        content.addView(sectionLabel(title).apply { setPadding(dpi(6f), dpi(18f), 0, dpi(8f)) })
        val row = horizontal().apply { setPadding(dpi(2f), dpi(2f), dpi(2f), dpi(8f)) }
        for (n in notes) {
            row.addView(noteCard(n, showFolder = true), LinearLayout.LayoutParams(dpi(156f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpi(12f)
            })
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        })
    }

    /** Etiquetas como botões: um toque filtra as notas pela etiqueta. */
    private fun tagChips() {
        val tags = Library.allTags()
        if (tags.isEmpty()) return
        content.addView(sectionLabel("Etiquetas").apply { setPadding(dpi(6f), dpi(18f), 0, dpi(8f)) })
        val row = horizontal().apply { setPadding(dpi(2f), 0, dpi(2f), 0) }
        for (t in tags) {
            row.addView(TextView(this).apply {
                text = "#$t"
                textSize = 14f
                typeface = Ui.MEDIUM
                setTextColor(Ui.INK)
                gravity = Gravity.CENTER
                minHeight = dpi(38f)
                setPadding(dpi(14f), 0, dpi(14f), 0)
                background = ripple(pill(Ui.FIELD, 0), pill(Color.WHITE, 0))
                setOnClickListener { homeSearch?.setText("#$t") }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(8f) })
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
    }

    private fun renderHomeBody() {
        val pinned = Library.notes.filter { it.pinned }.sortedByDescending { it.modified }
        if (pinned.isNotEmpty()) noteRow("Fixadas", pinned)
        val favs = Library.notes.filter { it.favorite }.sortedByDescending { it.modified }
        if (favs.isNotEmpty()) noteRow("Favoritas", favs)
        tagChips()
        val recent = Library.notes.sortedByDescending { it.modified }.take(8)
        if (recent.isNotEmpty()) {
            content.addView(sectionLabel("Recentes").apply { setPadding(dpi(6f), dpi(18f), 0, dpi(8f)) })
            val row = horizontal().apply { setPadding(dpi(2f), dpi(2f), dpi(2f), dpi(8f)) }
            for (n in recent) {
                row.addView(noteCard(n, showFolder = true), LinearLayout.LayoutParams(dpi(156f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpi(12f)
                })
            }
            content.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            })
        }

        val folders = Library.subfolders(null)
        if (folders.isEmpty()) {
            content.addView(emptyState("Nenhuma pasta ainda", "Crie uma pasta para começar a organizar suas notas."))
        } else {
            content.addView(sectionLabel("Pastas").apply { setPadding(dpi(6f), dpi(14f), 0, dpi(4f)) })
            addGrid(folders.map { folderCard(it) })
        }
        if (Library.trash.isNotEmpty()) {
            content.addView(horizontal().apply {
                setPadding(dpi(10f), dpi(12f), dpi(10f), dpi(12f))
                background = ripple(null, rounded(Color.WHITE, 14f))
                setOnClickListener { showTrash() }
                addView(ImageView(this@FoldersActivity).apply { setImageDrawable(icon(Icon.TRASH, Ui.MUTED, 22f)) },
                    LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(14f) })
                addView(label("Lixeira (${Library.trash.size})", 15f, Ui.INK))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(16f) })
        }
    }

    /** Lixeira: restaurar ou apagar de vez (some sozinha depois de 30 dias). */
    private fun showTrash() {
        sheet("Lixeira") { box, dialog ->
            box.addView(label("As notas ficam aqui por ${Library.TRASH_DAYS} dias e depois são apagadas de vez.", 13f).apply {
                setPadding(0, 0, 0, dpi(8f))
            })
            val now = System.currentTimeMillis()
            for (t in Library.trash.sortedByDescending { it.trashedAt }) {
                val left = Library.TRASH_DAYS - ((now - t.trashedAt) / (24L * 60 * 60 * 1000)).toInt()
                val row = horizontal().apply { setPadding(0, dpi(8f), 0, dpi(8f)) }
                val texts = vertical()
                texts.addView(label(t.note.title, 15f, Ui.INK, bold = true).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
                texts.addView(label((if (t.folderName.isNotBlank()) t.folderName + " · " else "") +
                    (if (left <= 1) "some amanhã" else "some em $left dias"), 12.5f))
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(pillButton(Icon.RESET, "Restaurar") {
                    Library.restore(t)
                    dialog.dismiss()
                    render()
                    if (Library.trash.isNotEmpty()) showTrash()
                })
                row.addView(iconButton(Icon.TRASH, Ui.DANGER, 40f, "Apagar de vez") {
                    confirm("Apagar de vez?", "“${t.note.title}” não poderá mais ser recuperada.", "Apagar") {
                        Library.deleteForever(t)
                        dialog.dismiss()
                        render()
                        if (Library.trash.isNotEmpty()) showTrash()
                    }
                })
                box.addView(row)
            }
            box.addView(buttonRow(
                secondaryButton("Fechar") { dialog.dismiss() },
                primaryButton("Esvaziar lixeira", Ui.DANGER) {
                    confirm("Esvaziar lixeira?", "As ${Library.trash.size} notas da lixeira serão apagadas para sempre.", "Esvaziar") {
                        Library.emptyTrash()
                        dialog.dismiss()
                        render()
                    }
                },
            ))
        }
    }

    private fun renderFolder(folder: Folder) {
        val bar = horizontal().apply { setPadding(0, dpi(8f), 0, 0) }
        bar.addView(iconButton(Icon.BACK) { finish() })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(iconButton(Icon.MORE) { folderOptions(folder) })
        content.addView(bar)

        val head = horizontal().apply { setPadding(dpi(6f), dpi(10f), dpi(6f), dpi(4f)) }
        val cover = loadCover(folder.id)
        head.addView(ImageView(this).apply {
            if (cover != null) {
                setImageBitmap(cover)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(folder.color, 14f)
                clipToOutline = true
            } else setImageDrawable(FolderGlyphDrawable(folder.color))
        }, LinearLayout.LayoutParams(dpi(54f), dpi(if (cover != null) 54f else 44f)).apply { marginEnd = dpi(14f) })
        val titles = vertical()
        titles.addView(title(folder.name, 28f).apply { isSingleLine = true })
        val subs = Library.subfolders(folder.id)
        val notes = Library.notesIn(folder.id)
        titles.addView(label((folder.parent?.let { Library.pathOf(it) + "  ·  " } ?: "") + countText(subs.size, notes.size), 13f).apply { isSingleLine = true })
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(head)

        if (subs.isNotEmpty()) {
            content.addView(sectionLabel("Pastas").apply { setPadding(dpi(6f), dpi(18f), 0, dpi(4f)) })
            addGrid(subs.map { folderCard(it) })
        }
        if (notes.isNotEmpty()) {
            content.addView(sectionLabel("Notas").apply { setPadding(dpi(6f), dpi(18f), 0, dpi(4f)) })
            addGrid(notes.map { noteCard(it, showFolder = false) })
        }
        if (subs.isEmpty() && notes.isEmpty()) {
            content.addView(emptyState("Pasta vazia", "Toque em “Nova nota” para começar a escrever."))
        }
    }

    private fun emptyState(title: String, text: String): View {
        val box = vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(24f), dpi(70f), dpi(24f), 0)
        }
        box.addView(ImageView(this).apply {
            setImageDrawable(FolderGlyphDrawable(if (Ui.isDark) Color.parseColor("#3A4A80") else Color.parseColor("#C5D3F5")))
        }, LinearLayout.LayoutParams(dpi(96f), dpi(78f)))
        box.addView(label(title, 18f, Ui.INK, bold = true).apply { setPadding(0, dpi(18f), 0, dpi(4f)) })
        box.addView(label(text, 14f).apply { gravity = Gravity.CENTER })
        return box
    }

    /** Entrada escalonada (sobe e aparece), só na primeira montagem e sem "remover animações". */
    private var animateEntry = true

    private fun enter(v: View, index: Int) {
        if (!animateEntry || !Ui.animationsOn()) return
        v.alpha = 0f
        v.translationY = dp(14f)
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(minOf(index, 8) * 40L)
            .setDuration(420)
            .setInterpolator(Ui.EASE)
            .start()
    }

    private fun addGrid(cards: List<View>) {
        val cols = columns()
        var row: LinearLayout? = null
        cards.forEachIndexed { i, card ->
            if (i % cols == 0) {
                row = horizontal().apply { gravity = Gravity.TOP }
                content.addView(row)
            }
            row!!.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dpi(5f), dpi(5f), dpi(5f), dpi(5f))
            })
            enter(card, i)
        }
        val rest = cards.size % cols
        if (rest != 0) repeat(cols - rest) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dpi(5f), 0, dpi(5f), 0) })
        }
    }

    /** Cor da pasta bem diluída no fundo dos cartões (branco no tema claro, escuro no noturno). */
    private fun tint(color: Int, amount: Float): Int {
        val base = Ui.SURFACE
        val a = if (Ui.isDark) amount * 1.6f else amount
        val r = (Color.red(color) * a + Color.red(base) * (1 - a)).toInt()
        val g = (Color.green(color) * a + Color.green(base) * (1 - a)).toInt()
        val b = (Color.blue(color) * a + Color.blue(base) * (1 - a)).toInt()
        return Color.rgb(r, g, b)
    }

    private fun folderCard(f: Folder): View {
        val card = vertical().apply {
            setPadding(dpi(16f), dpi(14f), dpi(6f), dpi(18f))
            background = ripple(rounded(tint(f.color, 0.13f), 24f), rounded(Color.WHITE, 24f))
            Ui.run { pressable(0.97f) }
            setOnClickListener {
                startActivity(Intent(this@FoldersActivity, FoldersActivity::class.java).putExtra(EXTRA_FOLDER, f.id))
            }
            setOnLongClickListener { folderOptions(f); true }
        }
        val cover = loadCover(f.id)
        if (cover != null) {
            // Com capa: a imagem ocupa o topo do cartão, com os cantos acompanhando o cartão.
            card.setPadding(dpi(8f), dpi(8f), dpi(6f), dpi(18f))
            card.addView(ImageView(this).apply {
                setImageBitmap(cover)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(f.color, 17f)
                clipToOutline = true
                contentDescription = "Capa de ${f.name}"
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(96f)).apply { marginEnd = dpi(2f) })
        }
        val nameView = label(f.name, 17f, Ui.INK, bold = true).apply {
            typeface = Ui.BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dpi(if (cover == null) 14f else 6f), dpi(8f), dpi(2f))
        }
        val top = horizontal().apply { gravity = Gravity.TOP }
        if (cover == null) {
            top.addView(ImageView(this).apply { setImageDrawable(FolderGlyphDrawable(f.color)) },
                LinearLayout.LayoutParams(dpi(50f), dpi(41f)).apply { topMargin = dpi(4f) })
            top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        } else {
            // Com capa, o nome fica ao lado do botão de opções (o desenho da pasta sai).
            top.setPadding(dpi(8f), 0, 0, 0)
            top.addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        top.addView(iconButton(Icon.MORE, Ui.MUTED, 36f) { folderOptions(f) })
        card.addView(top)
        if (cover == null) card.addView(nameView)
        card.addView(label(countText(Library.folders.count { it.parent == f.id }, Library.notes.count { it.folder == f.id }), 13f).apply {
            if (cover != null) setPadding(dpi(8f), 0, 0, 0)
        })
        return card
    }

    private fun noteCard(n: NoteInfo, showFolder: Boolean): View {
        val card = vertical().apply {
            setOnClickListener { openNote(n) }
            setOnLongClickListener { noteOptions(n); true }
        }
        card(22f, 2f, card)
        // "Double bezel": a miniatura é uma folha de papel encaixada dentro do cartão,
        // com cantos concêntricos (22dp fora, 15dp dentro).
        card.setPadding(dpi(7f), dpi(7f), dpi(7f), 0)
        val thumbBox = FrameLayout(this).apply {
            background = rounded(Ui.THUMB_BG, 15f, Ui.THUMB_LINE)
            clipToOutline = true
        }
        val f = Library.thumbFile(n.id)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        if (bmp != null) {
            thumbBox.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
                contentDescription = "Miniatura de ${n.title}"
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            thumbBox.addView(ImageView(this).apply {
                setImageDrawable(icon(Icon.NOTE, Ui.HANDLE, 34f, 1.6f))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dpi(40f), dpi(40f), Gravity.CENTER))
        }
        card.addView(thumbBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(124f)))
        val info = horizontal().apply { setPadding(dpi(7f), dpi(9f), 0, dpi(9f)) }
        val texts = vertical()
        texts.addView(label(n.title, 15f, Ui.INK, bold = true).apply {
            typeface = Ui.BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val base = if (showFolder) Library.folder(n.folder)?.name ?: "" else formatDate(n.modified)
        val marks = (if (n.pinned) "📌 " else "") + (if (n.favorite) "★ " else "")
        val sub = marks + base + n.tags.joinToString("") { "  #$it" }
        texts.addView(label(sub, 12.5f).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        info.addView(iconButton(Icon.MORE, Ui.MUTED, 34f) { noteOptions(n) })
        card.addView(info)
        return card
    }

    private fun formatDate(t: Long): String =
        if (DateUtils.isToday(t)) "Hoje, " + SimpleDateFormat("HH:mm", ptBr).format(Date(t))
        else SimpleDateFormat("d 'de' MMM yyyy", ptBr).format(Date(t))

    // ---- Ações -------------------------------------------------------------------

    private fun newNote(folder: String) {
        val title = "Nota " + SimpleDateFormat("dd/MM HH:mm", ptBr).format(Date())
        openNote(Library.createNote(folder, title))
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_PDF)
    }

    @Deprecated("Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DRIVE) {
            val token = if (resultCode == RESULT_OK) DriveAuth.tokenFromResult(this, data) else null
            if (token != null) driveWithToken(token) else { pendingDrive = null; toast("Login no Google cancelado.") }
            return
        }
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_SDOCX) {
            importSamsung(pickedUris(data))
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_COVER -> { coverFor?.let { setCover(it, uri) }; return }
            REQ_BACKUP_NOW -> { runBackup(uri, auto = false); return }
            REQ_BACKUP_AUTO -> { Backup.enableAuto(this, uri); runBackup(uri, auto = true); return }
            REQ_RESTORE -> { confirmRestore(uri); return }
        }
        val fid = folderId ?: return
        if (requestCode != REQ_PDF) return
        // Nota nova com o nome do arquivo; o editor importa as páginas ao abrir.
        val title = (PdfImporter.displayName(this, uri) ?: "PDF").removeSuffix(".pdf").removeSuffix(".PDF")
        val n = Library.createNote(fid, title)
        startActivity(Intent(this, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_NOTE, n.id)
            .putExtra(EditorActivity.EXTRA_PDF, uri.toString())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    // ---- Importar do Samsung Notes ------------------------------------------------------

    private fun samsungImportHelp() {
        sheet("Importar do Samsung Notes") { box, dialog ->
            box.addView(label(
                "A escrita à mão vem editável, com as cores e as folhas na mesma ordem.\n\n" +
                    "No Samsung Notes, selecione as notas, toque em Compartilhar (ou Salvar como arquivo) e escolha " +
                    "“Arquivo do Samsung Notes (.sdocx)”. Compartilhe direto com o RosaNotes ou salve e escolha os arquivos aqui.",
                15f, Ui.MUTED,
            ).apply { setLineSpacing(0f, 1.15f) })
            box.addView(buttonRow(
                secondaryButton("Cancelar") { dialog.dismiss() },
                primaryButton("Escolher arquivos") { dialog.dismiss(); pickSdocx() },
            ))
        }
    }

    private fun pickSdocx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_SDOCX)
    }

    private fun pickedUris(data: Intent?): List<android.net.Uri> {
        val clip = data?.clipData
        if (clip != null) return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        return listOfNotNull(data?.data)
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(i: Intent?): List<android.net.Uri> = when (i?.action) {
        Intent.ACTION_SEND -> listOfNotNull(i.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
        Intent.ACTION_SEND_MULTIPLE -> i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
        Intent.ACTION_VIEW -> listOfNotNull(i.data)
        else -> emptyList()
    }

    /** Arquivos recebidos de outros apps: PDFs viram nota nova; o resto é do Samsung Notes. */
    private fun handleShared(uris: List<android.net.Uri>) {
        val pdfs = uris.filter { u ->
            contentResolver.getType(u) == "application/pdf" ||
                (PdfImporter.displayName(this, u) ?: u.lastPathSegment ?: "").lowercase().endsWith(".pdf")
        }
        val slides = (uris - pdfs.toSet()).filter { u ->
            PptxImporter.isPowerPoint(PdfImporter.displayName(this, u) ?: u.lastPathSegment, contentResolver.getType(u))
        }
        val rest = uris - pdfs.toSet() - slides.toSet()
        if (rest.isNotEmpty()) importSamsung(rest)
        if (pdfs.isNotEmpty()) importSharedPdf(pdfs.first(), pdfs.size)
        else if (slides.isNotEmpty()) importSharedPdf(slides.first(), slides.size, slides = true)
    }

    /** PDF recebido: copia para o app (o acesso compartilhado expira), pergunta a pasta e abre a nota. */
    private fun importSharedPdf(uri: android.net.Uri, count: Int, slides: Boolean = false) {
        val full = PdfImporter.displayName(this, uri) ?: uri.lastPathSegment ?: if (slides) "Apresentação.pptx" else "PDF"
        val name = full.substringBeforeLast('.').ifEmpty { full }
        val ext = if (!slides) "pdf" else if (full.lowercase().endsWith(".ppt")) "ppt" else "pptx"
        val dest = java.io.File(java.io.File(cacheDir, "recebidos").apply { mkdirs() }, "rec_${System.currentTimeMillis()}.$ext")
        Library.io.execute {
            val ok = try {
                contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!ok) {
                    toast(if (slides) "Não foi possível abrir a apresentação." else "Não foi possível abrir o PDF.")
                    return@runOnUiThread
                }
                if (count > 1) toast(if (slides) "Abrindo a primeira apresentação (uma por vez)." else "Abrindo o primeiro PDF (um por vez).")
                Pickers.folder(this, "Salvar “$name” em", allowRoot = false, exclude = null, current = null) { fid ->
                    val folder = fid ?: Library.folders.firstOrNull()?.id ?: return@folder
                    val n = Library.createNote(folder, name)
                    render()
                    startActivity(Intent(this, EditorActivity::class.java)
                        .putExtra(EditorActivity.EXTRA_NOTE, n.id)
                        .putExtra(if (slides) EditorActivity.EXTRA_PPTX else EditorActivity.EXTRA_PDF, android.net.Uri.fromFile(dest).toString()))
                }
            }
        }
    }

    /** Pasta "Samsung Notes" na tela inicial (criada na primeira importação). */
    private fun samsungFolder(): Folder =
        Library.folders.firstOrNull { it.parent == null && it.name == SAMSUNG_FOLDER }
            ?: Library.createFolder(SAMSUNG_FOLDER, Library.folderColors[Library.folders.size % Library.folderColors.size], null)

    /** Cada arquivo .sdocx vira uma nota nova, com a escrita editável. */
    private fun importSamsung(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val target = folderId ?: samsungFolder().id
        var cancelled = false
        lateinit var status: TextView
        lateinit var bar: android.widget.ProgressBar
        val dialog = sheet("Importando do Samsung Notes") { box, d ->
            status = label("Preparando…", 15f, Ui.INK)
            box.addView(status)
            bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
            }
            box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(24f)).apply { topMargin = dpi(8f) })
            box.addView(buttonRow(secondaryButton("Parar") { cancelled = true; d.dismiss() }))
        }
        dialog.setCancelable(false)
        val sheetWidth = 400f * resources.displayMetrics.density
        val textSize = 22f * resources.displayMetrics.density
        Thread {
            val imported = ArrayList<NoteInfo>()
            val failed = ArrayList<String>()
            var partial = 0
            uris.forEachIndexed { i, uri ->
                if (cancelled) return@forEachIndexed
                val fileName = PdfImporter.displayName(this, uri) ?: "Nota"
                runOnUiThread {
                    status.text = if (uris.size == 1) fileName else "Nota ${i + 1} de ${uris.size}: $fileName"
                    bar.progress = i * 100 / uris.size
                }
                var created: NoteInfo? = null
                try {
                    val sn = contentResolver.openInputStream(uri)?.use { SamsungNotesImporter.read(it) }
                        ?: throw SamsungNotesImporter.UnsupportedFile("não foi possível abrir o arquivo.")
                    val title = sn.title ?: fileName.substringBeforeLast('.').substringBeforeLast('_').ifBlank { "Nota do Samsung Notes" }
                    val latch = java.util.concurrent.CountDownLatch(1)
                    runOnUiThread { created = Library.createNote(target, title); latch.countDown() }
                    latch.await()
                    val n = created!!
                    val data = SamsungNotesImporter.toNoteData(sn, Library.assetsDir(n.id), sheetWidth, textSize)
                    NoteStorage.save(Library.noteFile(n.id), data)
                    SamsungNotesImporter.thumbnail(data)?.let { bmp ->
                        val tf = Library.thumbFile(n.id)
                        tf.parentFile?.mkdirs()
                        java.io.FileOutputStream(tf).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                        bmp.recycle()
                    }
                    imported.add(n)
                    if (sn.skipped > 0) partial++
                } catch (e: Exception) {
                    created?.let { n -> runOnUiThread { Library.deleteNote(n.id) } }
                    val why = (e as? SamsungNotesImporter.UnsupportedFile)?.message
                    failed.add(if (why != null) "$fileName: $why" else fileName)
                }
            }
            runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                if (isFinishing) return@runOnUiThread
                render()
                if (imported.size == 1 && failed.isEmpty() && partial == 0) {
                    toast("Nota importada do Samsung Notes")
                    openNote(imported[0])
                    return@runOnUiThread
                }
                importSummary(imported.size, failed, partial)
            }
        }.start()
    }

    private fun importSummary(ok: Int, failed: List<String>, partial: Int) {
        sheet(if (ok > 0) "Importação concluída" else "Nada foi importado") { box, dialog ->
            val msg = StringBuilder(when (ok) {
                0 -> "Nenhuma nota pôde ser lida."
                1 -> "1 nota importada."
                else -> "$ok notas importadas."
            })
            if (partial > 0) msg.append("\n\nAlguns elementos (como formas, tabelas ou áudios) não foram trazidos.")
            if (failed.isNotEmpty()) {
                msg.append("\n\nNão deu para ler:\n").append(failed.take(6).joinToString("\n") { "• $it" })
                if (failed.size > 6) msg.append("\n• e mais ${failed.size - 6}")
                msg.append("\n\nSe precisar, exporte essas notas como PDF no Samsung Notes e use “Importar PDF”.")
            }
            box.addView(label(msg.toString(), 15f, Ui.MUTED).apply { setLineSpacing(0f, 1.15f) })
            box.addView(buttonRow(primaryButton("OK") { dialog.dismiss() }))
        }
    }

    private fun openNote(n: NoteInfo) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, n.id))
    }

    /** Criar (editing == null) ou editar pasta: prévia, nome e cor. */
    fun editFolder(editing: Folder?, parent: String?) {
        var color = editing?.color ?: Library.folderColors[Library.folders.size % Library.folderColors.size]
        sheet(if (editing == null) "Nova pasta" else "Editar pasta") { box, dialog ->
            val glyph = ImageView(this).apply { setImageDrawable(FolderGlyphDrawable(color)) }
            box.addView(glyph, LinearLayout.LayoutParams(dpi(84f), dpi(68f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpi(16f)
            })
            val field = textField(editing?.name ?: "", "Nome da pasta")
            box.addView(field)
            box.addView(sectionLabel("Cor"))
            val dots = ArrayList<ColorDot>()
            val row = horizontal()
            for (c in Library.folderColors) {
                val d = ColorDot(this, c, c == color) {
                    color = c
                    dots.forEach { it.checked = it.color == c }
                    glyph.setImageDrawable(FolderGlyphDrawable(c))
                }
                dots.add(d)
                row.addView(d, LinearLayout.LayoutParams(0, dpi(34f), 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
            }
            box.addView(row)
            box.addView(buttonRow(
                secondaryButton("Cancelar") { dialog.dismiss() },
                primaryButton(if (editing == null) "Criar" else "Salvar") {
                    dialog.dismiss()
                    val name = field.text.toString().trim()
                    if (editing == null) Library.createFolder(name, color, parent)
                    else Library.updateFolder(editing, name = name, color = color)
                    render()
                },
            ))
            if (editing == null) field.requestFocus()
        }
    }

    private fun folderOptions(f: Folder) {
        val count = Library.itemCount(f.id)
        actionSheet(null, listOf(
            SheetItem(Icon.EDIT, "Renomear e mudar cor") { editFolder(f, f.parent) },
            SheetItem(Icon.IMAGE, if (Library.coverFile(f.id).exists()) "Trocar imagem da capa" else "Colocar imagem na capa") {
                coverFor = f.id
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_COVER)
            },
        ) + (if (Library.coverFile(f.id).exists()) listOf(SheetItem(Icon.CLOSE, "Tirar imagem da capa") {
            Library.coverFile(f.id).delete()
            coverCache.remove(f.id)
            render()
        }) else emptyList()) + listOf(
            SheetItem(Icon.MOVE, "Mover") {
                Pickers.folder(this, "Mover para", allowRoot = true, exclude = f.id, current = f.parent) { target ->
                    Library.updateFolder(f, parent = target)
                    render()
                }
            },
            SheetItem(Icon.TRASH, "Excluir pasta", danger = true) {
                confirm("Excluir pasta?", "“${f.name}” será apagada e as notas dela ($count ${if (count == 1) "item" else "itens"}) vão para a lixeira, onde ficam ${Library.TRASH_DAYS} dias.", "Excluir") {
                    Library.deleteFolder(f.id)
                    if (f.id == folderId) finish() else render()
                }
            },
        ), header = Panels.headerRow(this, FolderGlyphDrawable(f.color), f.name, countText(
            Library.folders.count { it.parent == f.id }, Library.notes.count { it.folder == f.id },
        )))
    }

    private fun noteOptions(n: NoteInfo) {
        actionSheet(null, listOf(
            SheetItem(Icon.EDIT, "Renomear") {
                promptText("Renomear nota", n.title) { t ->
                    Library.updateNote(n, title = t)
                    render()
                }
            },
            SheetItem(Icon.LOCK, if (n.pinned) "Desafixar do topo" else "Fixar no topo") {
                Library.setPinned(n, !n.pinned)
                render()
            },
            SheetItem(Icon.STAR, if (n.favorite) "Tirar dos favoritos" else "Favoritar") {
                Library.setFavorite(n, !n.favorite)
                render()
            },
            SheetItem(Icon.EDIT, "Etiquetas") { editTags(n) },
            SheetItem(Icon.MOVE, "Mover para outra pasta") {
                Pickers.folder(this, "Mover para", allowRoot = false, exclude = null, current = n.folder) { target ->
                    if (target != null) Library.updateNote(n, folder = target)
                    render()
                }
            },
            SheetItem(Icon.COPY, "Duplicar") {
                Library.duplicateNote(n)
                render()
            },
            SheetItem(Icon.TRASH, "Mover para a lixeira", danger = true) {
                confirm("Mover para a lixeira?", "“${n.title}” fica ${Library.TRASH_DAYS} dias na lixeira e pode ser recuperada.", "Mover") {
                    Library.trashNote(n.id)
                    render()
                }
            },
        ), header = Panels.headerRow(this, icon(Icon.NOTE, Ui.MUTED, 28f), n.title, formatDate(n.modified)))
    }

    /** Etiquetas separadas por vírgula; as já usadas aparecem para tocar e incluir. */
    private fun editTags(n: NoteInfo) {
        sheet("Etiquetas") { box, dialog ->
            val field = textField(n.tags.joinToString(", "), "Ex.: prova, anatomia, resumo")
            box.addView(field)
            val others = Library.allTags().filter { t -> n.tags.none { it.equals(t, true) } }
            if (others.isNotEmpty()) {
                box.addView(sectionLabel("Já usadas"))
                val row = horizontal()
                for (t in others) row.addView(TextView(this).apply {
                    text = "#$t"
                    textSize = 13f
                    setTextColor(Ui.INK)
                    gravity = Gravity.CENTER
                    minHeight = dpi(36f)
                    setPadding(dpi(12f), 0, dpi(12f), 0)
                    background = ripple(pill(Ui.FIELD, 0), pill(Color.WHITE, 0))
                    setOnClickListener {
                        val cur = field.text.toString().trim().trimEnd(',')
                        field.setText(if (cur.isEmpty()) t else "$cur, $t")
                        field.setSelection(field.text.length)
                        visibility = View.GONE
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(8f) })
                box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            }
            box.addView(buttonRow(
                secondaryButton("Cancelar") { dialog.dismiss() },
                primaryButton("Salvar") {
                    dialog.dismiss()
                    Library.setTags(n, field.text.toString().split(','))
                    render()
                },
            ))
            field.requestFocus()
        }
    }

    /** Texto das notas para a busca (já sem acentos), lido do disco só quando o arquivo muda. */
    private val textCache = HashMap<String, Pair<Long, String>>()

    private fun noteText(id: String): String {
        val f = Library.textFile(id)
        if (!f.exists()) return ""
        textCache[id]?.let { (t, s) -> if (t == f.lastModified()) return s }
        val s = normalize(f.readText())
        textCache[id] = f.lastModified() to s
        return s
    }

    // ---- Capas das pastas ------------------------------------------------------------

    /** Pasta que está escolhendo capa (o seletor de imagens volta por onActivityResult). */
    private var coverFor: String?
        get() = getSharedPreferences("ui", MODE_PRIVATE).getString("cover_for", null)
        set(v) { getSharedPreferences("ui", MODE_PRIVATE).edit().putString("cover_for", v).apply() }

    /** Capas já lidas do disco (chave: id da pasta; vale enquanto o arquivo não mudar). */
    private val coverCache = HashMap<String, Pair<Long, android.graphics.Bitmap>>()

    private fun loadCover(id: String): android.graphics.Bitmap? {
        val f = Library.coverFile(id)
        if (!f.exists()) { coverCache.remove(id); return null }
        coverCache[id]?.let { (t, b) -> if (t == f.lastModified()) return b }
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        coverCache[id] = f.lastModified() to b
        return b
    }

    /** Lê a imagem escolhida, reduz para no máximo 900 px e guarda como capa. */
    private fun setCover(folder: String, uri: android.net.Uri) {
        Library.io.execute {
            val ok = try {
                val src = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    val big = maxOf(info.size.width, info.size.height)
                    if (big > COVER_MAX) {
                        val k = COVER_MAX.toFloat() / big
                        dec.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    }
                    dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val f = Library.coverFile(folder)
                f.parentFile?.mkdirs()
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                coverCache.remove(folder)
                if (!ok) toast("Não foi possível usar essa imagem")
                if (!isFinishing) render()
            }
        }
    }

    // ---- Backup no Google Drive --------------------------------------------------------

    private fun backupMenu() {
        val fmt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBr)
        val connected = DriveSync.connected(this)
        val lastDrive = DriveSync.lastBackup(this)
        sheet("Backup") { box, dialog ->
            fun act(block: () -> Unit): () -> Unit = { dialog.dismiss(); block() }
            fun row(ic: Icon, text: String, onClick: () -> Unit) = horizontal().apply {
                minimumHeight = dpi(50f)
                setPadding(dpi(8f), 0, dpi(8f), 0)
                background = ripple(null, rounded(Color.WHITE, 14f))
                setOnClickListener { onClick() }
                addView(ImageView(this@FoldersActivity).apply { setImageDrawable(icon(ic, Ui.INK, 22f)) },
                    LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { marginEnd = dpi(16f) })
                addView(label(text, 15.5f, Ui.INK))
            }
            box.addView(sectionLabel("Google Drive (arquivo por arquivo)"))
            box.addView(label(when {
                !connected -> "Cada nota, imagem e capa vai como um arquivo na pasta “RosaNotes” do seu Drive. Só sobe o que mudou."
                lastDrive == 0L -> "Conectado. Nenhum backup feito ainda."
                else -> "Conectado. Último backup: " + fmt.format(Date(lastDrive))
            } + (DriveSync.lastError?.let { "\n⚠ $it" } ?: ""), 13f).apply {
                setPadding(dpi(4f), dpi(2f), 0, dpi(6f))
                setLineSpacing(0f, 1.1f)
            })
            if (!connected) {
                box.addView(row(Icon.EXPORT, "Conectar ao Google Drive", act { driveRun(DriveTask.CONNECT) }))
            } else {
                box.addView(row(Icon.EXPORT, "Fazer backup agora", act { driveRun(DriveTask.BACKUP) }))
                box.addView(switchRow(Icon.CHECK, "Backup automático no Drive", DriveSync.autoOn(this)) { on ->
                    DriveSync.setAuto(this, on)
                })
                box.addView(row(Icon.DOWNLOAD, "Restaurar do Drive", act {
                    confirm("Restaurar do Drive?", "As pastas e notas deste aparelho serão trocadas pelas do backup no Drive.", "Restaurar") {
                        driveRun(DriveTask.RESTORE)
                    }
                }))
                box.addView(row(Icon.CLOSE, "Desconectar do Drive", act {
                    DriveSync.setConnected(this, false)
                    toast("Desconectado do Google Drive")
                }))
            }

            box.addView(sectionLabel("Arquivo único (.zip)").apply { setPadding(0, dpi(16f), 0, 0) })
            val auto = Backup.autoUri(this)
            val last = Backup.lastBackup(this)
            box.addView(label(if (last == 0L) "Um .zip com tudo, salvo onde você escolher." else "Último .zip: " + fmt.format(Date(last)), 13f).apply {
                setPadding(dpi(4f), dpi(2f), 0, dpi(6f))
            })
            box.addView(row(Icon.EXPORT, "Exportar backup em arquivo (.zip)", act { pickBackupFile(REQ_BACKUP_NOW) }))
            if (auto == null) box.addView(row(Icon.CHECK, "Ligar .zip automático", act { pickBackupFile(REQ_BACKUP_AUTO) }))
            else box.addView(row(Icon.CLOSE, "Desligar .zip automático", act {
                Backup.disableAuto(this)
                toast("Backup automático em .zip desligado")
            }))
            box.addView(row(Icon.DOWNLOAD, "Restaurar de um .zip", act {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(Backup.MIME, "application/octet-stream"))
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_RESTORE)
            }))
        }
    }

    private enum class DriveTask { CONNECT, BACKUP, RESTORE }
    private var pendingDrive: DriveTask? = null

    /** Pega o token (pedindo login se preciso) e roda a tarefa do Drive. */
    private fun driveRun(task: DriveTask) {
        toast(if (task == DriveTask.RESTORE) "Baixando do Drive…" else "Conectando ao Google Drive…")
        pendingDrive = task
        DriveAuth.connect(this, REQ_DRIVE) { token, error ->
            if (error != null) {
                pendingDrive = null
                sheet("Google Drive") { box, d ->
                    box.addView(label(error, 15f, Ui.MUTED))
                    box.addView(buttonRow(primaryButton("OK") { d.dismiss() }))
                }
            } else if (token != null) driveWithToken(token)
        }
    }

    private fun driveWithToken(token: String) {
        val task = pendingDrive ?: return
        pendingDrive = null
        if (task == DriveTask.CONNECT) {
            toast("Conectado. Enviando o primeiro backup…")
        }
        Library.io.execute {
            val msg = try {
                if (task == DriveTask.RESTORE) {
                    val n = DriveSync.restore(this, DriveRest(token))
                    "Restaurado do Drive ($n arquivos)"
                } else {
                    val r = DriveSync.backup(this, DriveRest(token))
                    if (task == DriveTask.CONNECT) DriveSync.setAuto(this, true)
                    if (r.uploaded == 0 && r.deleted == 0) "Backup em dia: nada mudou."
                    else "Backup no Drive: ${r.uploaded} arquivo(s) enviado(s)" + (if (r.deleted > 0) ", ${r.deleted} removido(s)" else "")
                }
            } catch (e: Exception) {
                "Não deu certo: ${e.message ?: "erro"}"
            }
            runOnUiThread {
                coverCache.clear()
                toast(msg)
                if (!isFinishing) render()
            }
        }
    }

    /** Abre o seletor para criar o arquivo: na lateral dele, escolha "Drive" e a pasta. */
    private fun pickBackupFile(request: Int) {
        toast("Escolha o Google Drive no seletor e toque em Salvar")
        val name = "RosaNotes-backup" + (if (request == REQ_BACKUP_NOW) "-" + SimpleDateFormat("yyyy-MM-dd", ptBr).format(Date()) else "") + ".zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = Backup.MIME
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, name)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, request)
    }

    private fun runBackup(uri: android.net.Uri, auto: Boolean) {
        Library.io.execute {
            val err = try { Backup.write(this, uri); null } catch (e: Exception) { e.message ?: "erro" }
            runOnUiThread {
                toast(when {
                    err != null -> "Backup não concluído: $err"
                    auto -> "Backup automático ligado: suas notas vão para o Drive"
                    else -> "Backup salvo"
                })
            }
        }
    }

    private fun confirmRestore(uri: android.net.Uri) {
        confirm("Restaurar backup?", "As pastas e notas deste aparelho serão trocadas pelas do backup.", "Restaurar") {
            Library.io.execute {
                val err = try { Backup.restore(this, uri); null } catch (e: Exception) { e.message ?: "erro" }
                runOnUiThread {
                    coverCache.clear()
                    toast(err?.let { "Não foi possível restaurar: $it" } ?: "Backup restaurado")
                    if (!isFinishing) render()
                }
            }
        }
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_FOLDER = "folder"
        private const val REQ_PDF = 51
        private const val REQ_COVER = 52
        private const val REQ_BACKUP_NOW = 53
        private const val REQ_BACKUP_AUTO = 54
        private const val REQ_RESTORE = 55
        private const val REQ_SDOCX = 56
        private const val REQ_DRIVE = 57
        private const val SAMSUNG_FOLDER = "Samsung Notes"
        private const val COVER_MAX = 900
    }
}
