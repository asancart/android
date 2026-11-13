package io.github.asancart.android.log

import androidx.core.content.FileProvider
import io.github.asancart.android.AsancartApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** REQ-OPS-012: the export is one text file that other apps can be given through the FileProvider. */
@RunWith(RobolectricTestRunner::class)
class LogShareTest {
    @Test
    fun theExportIsOneShareableTextFile() {
        val app = RuntimeEnvironment.getApplication() as AsancartApp
        app.container.log.info("share_test", "rows" to 3)
        val file = LogExport.write(app.container.fileLog, File(app.cacheDir, "exports"), "0.1.0")
        val uri = FileProvider.getUriForFile(app, app.packageName + ".logs", file)
        assertEquals("content", uri.scheme)
        assertEquals(app.packageName + ".logs", uri.authority)
        assertTrue(file.readText().contains("share_test"))
        assertEquals(listOf(file.name), File(app.cacheDir, "exports").list()!!.toList())
    }
}
