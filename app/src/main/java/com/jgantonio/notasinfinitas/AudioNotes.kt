package com.jgantonio.notasinfinitas

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Áudio gravado junto com a escrita, como no Notability: cada traço feito durante a
 * gravação guarda o momento do áudio em que foi escrito. Selecionando a escrita,
 * "Ouvir daqui" toca o áudio a partir daquele ponto.
 *
 * Fica em notes/<id>/audio.json (lista de gravações e marcas) com os .m4a ao lado,
 * então entra no backup e na cópia da nota sem nada especial. A marca de um traço
 * é o seu primeiro ponto (x, y): continua valendo enquanto o traço não for movido.
 */
class AudioNotes(private val dir: File) {

    class Recording(val file: String, var durationMs: Long, val created: Long)
    class Mark(val x: Float, val y: Float, val rec: Int, val t: Long)

    val recordings = ArrayList<Recording>()
    private val marks = ArrayList<Mark>()

    private var recorder: MediaRecorder? = null
    private var recStart = 0L
    private var player: MediaPlayer? = null
    var playing: Int = -1; private set

    val isRecording get() = recorder != null
    val isPlaying get() = player?.isPlaying == true
    val elapsedMs get() = if (isRecording) SystemClock.elapsedRealtime() - recStart else player?.currentPosition?.toLong() ?: 0L

    init { load() }

    // ---- Gravação -------------------------------------------------------------------

    fun start(context: Context): Boolean {
        stopPlayback()
        if (isRecording) return true
        dir.mkdirs()
        val name = "audio_${System.currentTimeMillis()}.m4a"
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(File(dir, name).path)
            r.prepare()
            r.start()
            recorder = r
            recStart = SystemClock.elapsedRealtime()
            recordings.add(Recording(name, 0L, System.currentTimeMillis()))
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    fun stop() {
        val r = recorder ?: return
        recorder = null
        try { r.stop() } catch (_: Exception) { }
        r.release()
        recordings.lastOrNull()?.durationMs = SystemClock.elapsedRealtime() - recStart
        save()
    }

    /** Marca o traço [s] no instante atual da gravação (se estiver gravando). */
    fun markStroke(s: StrokeElement) {
        if (!isRecording || s.n == 0) return
        marks.add(Mark(s.xs[0], s.ys[0], recordings.size - 1, SystemClock.elapsedRealtime() - recStart))
    }

    /** Primeiro momento (gravação, ms) entre os traços dados, ou null se nenhum foi gravado. */
    fun markFor(strokes: List<StrokeElement>): Pair<Int, Long>? {
        var best: Mark? = null
        for (s in strokes) {
            if (s.n == 0) continue
            val m = marks.firstOrNull { kotlin.math.abs(it.x - s.xs[0]) < 0.01f && kotlin.math.abs(it.y - s.ys[0]) < 0.01f } ?: continue
            if (best == null || m.rec < best.rec || (m.rec == best.rec && m.t < best.t)) best = m
        }
        return best?.let { it.rec to it.t }
    }

    // ---- Reprodução ------------------------------------------------------------------

    fun play(index: Int, fromMs: Long = 0L, onEnd: () -> Unit = {}): Boolean {
        stopPlayback()
        val rec = recordings.getOrNull(index) ?: return false
        return try {
            val p = MediaPlayer()
            p.setDataSource(File(dir, rec.file).path)
            p.prepare()
            p.seekTo(fromMs.coerceAtLeast(0L).toInt())
            p.setOnCompletionListener { stopPlayback(); onEnd() }
            p.start()
            player = p
            playing = index
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stopPlayback() {
        player?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
        player = null
        playing = -1
    }

    fun delete(index: Int) {
        val rec = recordings.getOrNull(index) ?: return
        if (playing == index) stopPlayback()
        File(dir, rec.file).delete()
        recordings.removeAt(index)
        marks.removeAll { it.rec == index }
        // As marcas das gravações seguintes descem uma posição.
        val shifted = marks.map { if (it.rec > index) Mark(it.x, it.y, it.rec - 1, it.t) else it }
        marks.clear(); marks.addAll(shifted)
        save()
    }

    fun release() {
        stop()
        stopPlayback()
    }

    // ---- Arquivo ---------------------------------------------------------------------

    private fun file() = File(dir, "audio.json")

    private fun load() {
        val f = file()
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            val ra = o.optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until ra.length()) {
                val r = ra.getJSONObject(i)
                recordings.add(Recording(r.getString("file"), r.optLong("duration"), r.optLong("created")))
            }
            val ma = o.optJSONArray("marks") ?: JSONArray()
            for (i in 0 until ma.length()) {
                val m = ma.getJSONArray(i)
                marks.add(Mark(m.getDouble(0).toFloat(), m.getDouble(1).toFloat(), m.getInt(2), m.getLong(3)))
            }
        } catch (_: Exception) { }
    }

    fun save() {
        dir.mkdirs()
        val o = JSONObject()
        o.put("recordings", JSONArray().apply {
            for (r in recordings) put(JSONObject().put("file", r.file).put("duration", r.durationMs).put("created", r.created))
        })
        o.put("marks", JSONArray().apply {
            for (m in marks) put(JSONArray().put(m.x.toDouble()).put(m.y.toDouble()).put(m.rec).put(m.t))
        })
        file().writeText(o.toString())
    }

    companion object {
        fun format(ms: Long): String {
            val s = ms / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
}
