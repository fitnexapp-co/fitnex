package com.fitnex.app

import com.fitnex.app.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json is Android's, hence Robolectric; a plain Application so Firebase/Hilt don't start.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UpdateCheckerTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.7.2", "1.7.1"))
        assertTrue(UpdateChecker.isNewer("1.7.10", "1.7.9"))
        assertTrue(UpdateChecker.isNewer("1.8", "1.7.9"))
        assertFalse(UpdateChecker.isNewer("1.7.1", "1.7.1"))
        assertFalse(UpdateChecker.isNewer("1.7.0", "1.7.1"))
        assertFalse("unparseable is never offered", UpdateChecker.isNewer("beta", "1.7.1"))
    }

    @Test
    fun readsTheApkFromTheLatestRelease() {
        val json = """
            {"tag_name":"v1.7.2","draft":false,"prerelease":false,
             "html_url":"https://github.com/fitnexapp-co/fitnex/releases/tag/v1.7.2",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                       {"name":"FITNEX-1.7.2-arm64.apk","browser_download_url":"https://x/FITNEX-1.7.2-arm64.apk"}]}
        """.trimIndent()
        val update = UpdateChecker.parseRelease(json)!!
        assertEquals("1.7.2", update.version)
        assertEquals("https://x/FITNEX-1.7.2-arm64.apk", update.downloadUrl)
    }

    @Test
    fun withoutApkOpensTheReleasePageAndSkipsPrereleases() {
        val page = UpdateChecker.parseRelease("""{"tag_name":"1.8.0","html_url":"https://github.com/r","assets":[]}""")!!
        assertEquals("https://github.com/r", page.downloadUrl)
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v2.0.0","prerelease":true,"html_url":"https://github.com/r"}"""))
    }
}
