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

    @Test
    fun `successful app cancellation preserves current ring before terminal sweep`() {
        val events = mutableListOf<String>()
        DownloadRemovalPolicy.dispatch(
            packageName = "org.mozilla.firefox",
            lastPercent = 65,
            reason = 8,
            updateRemainingProgress = { events += "progress=0" },
            reportComplete = {
                events += "progress=100"
                events += "complete"
            },
            reportCancelled = { events += "cancelled" },
        )
        assertTrue(events == listOf("progress=100", "complete", "progress=0"))
    }

    @Test
    fun `completed progress is dispatched before empty average for any removal reason`() {
        for (reason in listOf(-1, 1, 8, 9)) {
            val events = mutableListOf<String>()
            DownloadRemovalPolicy.dispatch(
                packageName = "com.android.chrome",
                lastPercent = 100,
                reason = reason,
                updateRemainingProgress = { events += "progress=0" },
                reportComplete = { events += "progress=100" },
                reportCancelled = { events += "cancelled" },
            )
            assertTrue(
                "Unexpected event order with reason=$reason: $events",
                events == listOf("progress=100", "progress=0"),
            )
        }
    }

    @Test
    fun `simulator stop never dispatches successful completion`() {
        val events = mutableListOf<String>()
        DownloadRemovalPolicy.dispatch(
            packageName = "eu.hxreborn.downloadsim",
            lastPercent = 65,
            reason = 8,
            updateRemainingProgress = { events += "progress=0" },
            reportComplete = { events += "complete" },
            reportCancelled = { events += "cancelled" },
        )
        assertTrue(events == listOf("progress=0"))
    }

    @Test
    fun `ordinary cancelled notification clears ring before error animation`() {
        val events = mutableListOf<String>()
        DownloadRemovalPolicy.dispatch(
            packageName = "org.mozilla.firefox",
            lastPercent = 55,
            reason = 1,
            updateRemainingProgress = { events += "progress=0" },
            reportComplete = { events += "complete" },
            reportCancelled = { events += "cancelled" },
        )
        assertTrue(events == listOf("progress=0", "cancelled"))
    }

    @Test
    fun `small removed progress clears without pretending completion or failure`() {
        val events = mutableListOf<String>()
        DownloadRemovalPolicy.dispatch(
            packageName = "com.android.chrome",
            lastPercent = 2,
            reason = -1,
            updateRemainingProgress = { events += "progress=0" },
            reportComplete = { events += "complete" },
            reportCancelled = { events += "cancelled" },
        )
        assertTrue(events == listOf("progress=0"))
    }
}
