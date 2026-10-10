package com.jgantonio.notasinfinitas

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

/**
 * Reconhecimento da escrita à mão (ML Kit, funciona sem internet depois de baixar
 * o modelo de português, ~20 MB, uma vez só).
 *
 * Serve para duas coisas: converter um trecho escrito em texto digitado e guardar
 * o texto de cada nota (notes/<id>.txt) para a busca da página inicial achar a
 * escrita à mão.
 */
object Handwriting {

    private val model: DigitalInkRecognitionModel? by lazy {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("pt-BR")?.let { DigitalInkRecognitionModel.builder(it).build() }
        } catch (e: Exception) {
            null
        }
    }
    private var recognizer: DigitalInkRecognizer? = null

    private fun sp(c: Context) = c.getSharedPreferences("escrita", Context.MODE_PRIVATE)

    /** A pessoa aceitou baixar o modelo (a busca na escrita fica ligada). */
    fun enabled(c: Context) = sp(c).getBoolean("on", false)


    /** Baixa o modelo se preciso; [done] recebe true quando ele está pronto para uso. */
    fun prepare(c: Context, done: (Boolean) -> Unit) {
        val m = model ?: return done(false)
        val app = c.applicationContext
        val manager = RemoteModelManager.getInstance()
        manager.isModelDownloaded(m).addOnSuccessListener { ok ->
            if (ok) {
                sp(app).edit().putBoolean("on", true).apply()
                done(true)
            } else {
                manager.download(m, DownloadConditions.Builder().build())
                    .addOnSuccessListener { sp(app).edit().putBoolean("on", true).apply(); done(true) }
                    .addOnFailureListener { done(false) }
            }
        }.addOnFailureListener { done(false) }
    }

    /**
     * Separa os traços em linhas (pela altura) e reconhece linha por linha.
     * [done] recebe o texto (linhas separadas por \n) ou null se não deu.
     * Só chamar depois de [prepare] ter dado certo.
     */
    fun recognize(strokes: List<StrokeElement>, done: (String?) -> Unit) {
        val m = model ?: return done(null)
        val client = recognizer ?: DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build()).also { recognizer = it }
        val lines = lines(strokes.filter { it.type != BrushType.HIGHLIGHTER && it.n > 0 })
        if (lines.isEmpty()) return done("")
        val out = ArrayList<String>()
        fun next(i: Int) {
            if (i == lines.size) return done(out.joinToString("\n").trim())
            val ink = Ink.builder()
            for (s in lines[i]) {
                val b = Ink.Stroke.builder()
                for (k in 0 until s.n) b.addPoint(Ink.Point.create(s.xs[k], s.ys[k]))
                ink.addStroke(b.build())
            }
            client.recognize(ink.build())
                .addOnSuccessListener { r ->
                    r.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                    next(i + 1)
                }
                .addOnFailureListener { if (out.isEmpty()) done(null) else next(i + 1) }
        }
        next(0)
    }

    /**
     * Indexa todas as notas que ainda não têm a escrita reconhecida (logo depois de
     * ligar a busca na escrita). Uma nota por vez; [done] no fim.
     */
    fun indexAll(c: Context, done: () -> Unit) {
        val pending = Library.notes.map { it.id }.toMutableList()
        fun next() {
            val id = pending.removeFirstOrNull() ?: return done()
            Library.io.execute {
                val data = try { NoteStorage.load(Library.noteFile(id)) } catch (e: Exception) { null }
                val strokes = data?.elements?.filterIsInstance<StrokeElement>().orEmpty()
                val typed = data?.elements?.filterIsInstance<TextElement>()?.joinToString("\n") { it.text }.orEmpty()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (strokes.isEmpty() || strokes.size > 4000) next()
                    else recognize(strokes) { hw ->
                        Library.io.execute { Library.textFile(id).writeText(listOfNotNull(typed, hw).joinToString("\n")) }
                        next()
                    }
                }
            }
        }
        next()
    }

    /** Agrupa traços que dividem a mesma faixa de altura; cada linha sai da esquerda para a direita. */
    internal fun lines(strokes: List<StrokeElement>): List<List<StrokeElement>> {
        class Line(var top: Float, var bottom: Float, val items: ArrayList<StrokeElement> = ArrayList())
        val out = ArrayList<Line>()
        for (s in strokes.sortedBy { it.bounds.centerY() }) {
            val b = s.bounds
            val line = out.lastOrNull()?.takeIf { l ->
                val overlap = minOf(l.bottom, b.bottom) - maxOf(l.top, b.top)
                overlap > 0.4f * minOf(l.bottom - l.top, b.height())
            }
            if (line != null) {
                line.items.add(s)
                line.top = minOf(line.top, b.top)
                line.bottom = maxOf(line.bottom, b.bottom)
            } else out.add(Line(b.top, b.bottom).also { it.items.add(s) })
        }
        return out.map { l -> l.items.sortedBy { it.bounds.left } }
    }
}
