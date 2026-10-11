package com.jgantonio.notasinfinitas

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.jgantonio.notasinfinitas.Ui.buttonRow
import com.jgantonio.notasinfinitas.Ui.label
import com.jgantonio.notasinfinitas.Ui.primaryButton
import com.jgantonio.notasinfinitas.Ui.secondaryButton
import com.jgantonio.notasinfinitas.Ui.sheet
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ao abrir o app, confere no GitHub se há uma versão mais nova. Cada versão publicada
 * tem a tag "rosanotes-v<versão>-build<N>", e o APK dela carrega o mesmo N em
 * BuildConfig.BUILD_NUMBER. Se a última versão tiver N maior, aparece o painel pedindo
 * para atualizar; se estiver em dia (ou sem internet), nada aparece.
 */
object Updates {
    private const val REPO = "jgantonio93-arch/tonynotes"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val DOWNLOAD_URL = "https://github.com/$REPO/releases/latest/download/RosaNotes.apk"

    /** Uma conferência por abertura do app (o processo é novo a cada abertura). */
    private var checked = false

    /** Número do build na tag ("rosanotes-v0.11.0-build123" → 123), ou null. */
    fun buildOf(tag: String?): Int? =
        tag?.let { Regex("""build(\d+)$""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** A versão publicada é mais nova que a instalada? Builds locais (0) nunca avisam. */
    fun isNewer(remote: Int?, local: Int): Boolean = local > 0 && remote != null && remote > local

    fun check(activity: Activity) {
        if (checked || BuildConfig.BUILD_NUMBER <= 0) return
        checked = true
        Thread {
            val tag = try {
                val conn = URL(LATEST_API).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (conn.responseCode != 200) null
                    else JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("tag_name")
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            }
            val remote = buildOf(tag)
            if (isNewer(remote, BuildConfig.BUILD_NUMBER)) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) show(activity, remote!!)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun show(activity: Activity, remote: Int) {
        activity.sheet("Nova versão disponível") { box, dialog ->
            box.addView(activity.label(
                "Seu RosaNotes está desatualizado (build ${BuildConfig.BUILD_NUMBER}; a mais nova é a build $remote).\n\n" +
                    "Toque em Atualizar para baixar e depois abra o arquivo para instalar por cima: suas notas continuam lá.",
                15f, Ui.MUTED,
            ).apply { setLineSpacing(0f, 1.15f) })
            box.addView(activity.buttonRow(
                activity.secondaryButton("Agora não") { dialog.dismiss() },
                activity.primaryButton("Atualizar") {
                    dialog.dismiss()
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)))
                    } catch (_: Exception) {
                    }
                },
            ))
        }
    }
}
