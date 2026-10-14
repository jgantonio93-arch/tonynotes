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
 * Fica em notes/<id>/audio.json (lista de gravações e marcas) com os áudios ao lado
 * (.aac; os antigos são .m4a). No Drive, os áudios vão para a pasta "Gravações". A marca de um traço
 * é o seu primeiro ponto (x, y): continua valendo enquanto o traço não for movido.
 */
class AudioNotes(private val dir: File) {

    class Recording(val file: String, var durationMs: Long, val created: Long, var name: String)
    class Mark(val x: Float, val y: Float, val rec: Int, val t: Long)

    val recordings = ArrayList<Recording>()
    private val marks = ArrayList<Mark>()

    private var recorder: MediaRecorder? = null
    /** Início do trecho atual e tempo já gravado antes dele (pausas não contam). */
    private var segStart = 0L
    private var recordedBefore = 0L
    var recordingPaused = false; private set
    private var lastSave = 0L

    private var player: MediaPlayer? = null
    var playing: Int = -1; private set
    /** Velocidade da reprodução (0,5x a 2x). */
    var speed = 1f; private set

    val isRecording get() = recorder != null
    /** Há uma gravação carregada no tocador (tocando ou pausada). */
    val hasPlayer get() = player != null
    val isPlaying get() = player?.isPlaying == true
    val recordedMs get() = recordedBefore + if (isRecording && !recordingPaused) SystemClock.elapsedRealtime() - segStart else 0L
    val positionMs get() = player?.currentPosition?.toLong() ?: 0L
    val playDurationMs get() = player?.duration?.toLong()?.takeIf { it > 0 } ?: recordings.getOrNull(playing)?.durationMs ?: 0L
    val elapsedMs get() = if (isRecording) recordedMs else positionMs

    init { load() }

    // ---- Gravação -------------------------------------------------------------------

    /**
     * Grava em AAC "ADTS": o arquivo é uma sequência de quadros independentes, então se o app
     * fechar, o celular desligar ou a bateria acabar no meio, tudo o que já foi gravado continua
     * tocando (no .m4a o arquivo inteiro se perdia). A lista é salva já no começo.
     */
    fun start(context: Context): Boolean {
        stopPlayback()
        if (isRecording) return true
        dir.mkdirs()
        val name = "audio_${System.currentTimeMillis()}.aac"
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(File(dir, name).path)
            r.prepare()
            r.start()
            recorder = r
            active = File(dir, name).path
            segStart = SystemClock.elapsedRealtime()
            recordedBefore = 0L
            recordingPaused = false
            recordings.add(Recording(name, 0L, System.currentTimeMillis(), "Gravação ${recordings.size + 1}"))
            save()
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    fun pauseRecording() {
        val r = recorder ?: return
        if (recordingPaused) return
        try {
            r.pause()
            recordedBefore += SystemClock.elapsedRealtime() - segStart
            recordingPaused = true
            recordings.lastOrNull()?.durationMs = recordedBefore
            save()
        } catch (_: Exception) {
        }
    }

    fun resumeRecording() {
        val r = recorder ?: return
        if (!recordingPaused) return
        try {
            r.resume()
            segStart = SystemClock.elapsedRealtime()
            recordingPaused = false
        } catch (_: Exception) {
        }
    }

    fun stop() {
        val r = recorder ?: return
        val total = recordedMs
        recorder = null
        active = null
        recordingPaused = false
        try { r.stop() } catch (_: Exception) { }
        r.release()
        recordings.lastOrNull()?.durationMs = total
        save()
    }

    /** Marca o traço [s] no instante atual da gravação (se estiver gravando). */
    fun markStroke(s: StrokeElement) {
        if (!isRecording || recordingPaused || s.n == 0) return
        marks.add(Mark(s.xs[0], s.ys[0], recordings.size - 1, recordedMs))
        // Salva de tempos em tempos: se algo acontecer, as marcas também ficam.
        val now = SystemClock.elapsedRealtime()
        if (now - lastSave > 5000) {
            recordings.lastOrNull()?.durationMs = recordedMs
            save()
            lastSave = now
        }
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
            p.setOnCompletionListener { onEnd() }
            p.start()
            applySpeed(p)
            player = p
            playing = index
            if (p.duration > 0 && rec.durationMs <= 0) { rec.durationMs = p.duration.toLong(); save() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applySpeed(p: MediaPlayer) {
        try {
            if (p.isPlaying) p.playbackParams = p.playbackParams.setSpeed(speed)
        } catch (_: Exception) {
        }
    }

    fun pausePlayback() {
        try { player?.takeIf { it.isPlaying }?.pause() } catch (_: Exception) { }
    }

    fun resumePlayback() {
        val p = player ?: return
        try {
            if (p.currentPosition >= p.duration - 300) p.seekTo(0)
            p.start()
            applySpeed(p)
        } catch (_: Exception) {
        }
    }

    fun seekTo(ms: Long) {
        try { player?.seekTo(ms.coerceIn(0L, playDurationMs).toInt()) } catch (_: Exception) { }
    }

    fun setSpeed(x: Float) {
        speed = x
        player?.let { applySpeed(it) }
    }

    fun stopPlayback() {
        player?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
        player = null
        playing = -1
    }

    fun rename(index: Int, name: String) {
        recordings.getOrNull(index)?.name = name.trim().ifEmpty { "Gravação ${index + 1}" }
        save()
    }

    fun fileOf(index: Int): File? = recordings.getOrNull(index)?.let { File(dir, it.file) }?.takeIf { it.isFile }

    fun delete(index: Int) {
        val rec = recordings.getOrNull(index) ?: return
        if (playing == index) stopPlayback()
        File(dir, rec.file).delete()
        recordings.removeAt(index)
        marks.removeAll { it.rec == index }
        // As marcas das gravações seguintes descem uma posição.
        val shifted = marks.map { if (it.rec > index) Mark(it.x, it.y, it.rec - 1, it.t) else it }
        marks.clear(); marks.addAll(shifted)
        if (playing > index) playing--
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
                val rec = Recording(r.getString("file"), r.optLong("duration"), r.optLong("created"),
                    r.optString("name").ifEmpty { "Gravação ${i + 1}" })
                // Gravação interrompida (app fechado, bateria...): mede o que ficou salvo.
                val audio = File(dir, rec.file)
                if (rec.durationMs <= 0 && audio.isFile && audio.path != active) rec.durationMs = measure(audio)
                recordings.add(rec)
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
            for (r in recordings) put(JSONObject().put("file", r.file).put("duration", r.durationMs).put("created", r.created).put("name", r.name))
        })
        o.put("marks", JSONArray().apply {
            for (m in marks) put(JSONArray().put(m.x.toDouble()).put(m.y.toDouble()).put(m.rec).put(m.t))
        })
        val tmp = File(dir, "audio.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file())
    }

    companion object {
        /** Arquivo sendo gravado agora (o backup não envia ele pela metade). */
        @Volatile var active: String? = null
            private set

        fun isAudioFile(name: String) = name.startsWith("audio_") && (name.endsWith(".aac") || name.endsWith(".m4a"))

        fun mimeOf(name: String) = if (name.endsWith(".aac")) "audio/aac" else "audio/mp4"

        /** Duração de um arquivo de áudio (estimada pelo tamanho se o Android não souber). */
        fun measure(f: File): Long {
            try {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(f.path)
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { if (it > 0) return it }
                } finally {
                    r.release()
                }
            } catch (_: Exception) {
            }
            // 64 kbit/s = 8 bytes por milissegundo.
            return f.length() / 8
        }

        fun format(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        }
    }
}
