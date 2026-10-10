package com.jgantonio.notasinfinitas

import android.content.Intent
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Gravações do app de verdade para os vídeos (quadro a quadro, com DemoKit).
 * Só rodam quando pedido (-Ddemo.dir=...), pelo workflow "Vídeo de demonstração";
 * cada cenário grava numa subpasta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DemoRecordingTest {

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun outDir(name: String): File {
        val dir = System.getProperty("demo.dir")
        assumeTrue("só roda no workflow do vídeo", !dir.isNullOrBlank())
        return File(dir!!, name).apply { deleteRecursively(); mkdirs() }
    }

    private fun freshEditor(title: String): EditorActivity {
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        app.getSharedPreferences("canetas", 0).edit().clear().commit()
        Ui.setThemeChoice(app, "light")
        Library.init(app, reload = true)
        val note = Library.createNote(Library.folders.first().id, title)
        val ed = Robolectric.buildActivity(EditorActivity::class.java,
            Intent(app, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_NOTE, note.id)).setup().get()
        ed.canvasForTests.lowLatency = false
        return ed
    }

    private val ink = Color.parseColor("#1B1B1F")
    private val red = Color.parseColor("#D7263D")
    private val blue = Color.parseColor("#1F5FD1")

    /** Teste da letra: título e uma frase com acentos. */
    @Test
    fun letra() {
        val out = outDir("letra")
        val ed = freshEditor("Biologia")
        val k = DemoKit(ed, out)
        k.layout()
        val w = k.canvas.width.toFloat()
        k.canvas.pen = PenSettings(BrushType.FOUNTAIN, ink, 5f, 100)
        k.hold(4)
        k.mark("titulo")
        k.write("Mitocôndria", w * 0.07f, 380f, cap = 92f)
        k.canvas.pen = PenSettings(BrushType.PEN, blue, 4f, 100)
        k.mark("frase")
        k.write("a usina de energia da célula", w * 0.07f, 560f, cap = 52f)
        k.hold(8)
        k.finish()
    }
}
