package eu.hxreborn.phdp.xposed.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRemovalPolicyTest {
    @Test
    fun `stop at partial progress removes simulator without completion effect`() {
        for (percent in listOf(0, 1, 15, 65, 99)) {
            assertTrue(
                "Simulator removal at $percent should be silent",
                DownloadRemovalPolicy.isSilentStop("eu.hxreborn.downloadsim", percent),
            )
        }
    }

    @Test
    fun `completed simulator and other applications retain completion policy`() {
        assertFalse(DownloadRemovalPolicy.isSilentStop("eu.hxreborn.downloadsim", 100))
        assertFalse(DownloadRemovalPolicy.isSilentStop("com.android.chrome", 65))
        assertFalse(DownloadRemovalPolicy.isSilentStop("org.mozilla.firefox", 99))
    }
}
