package eu.hxreborn.phdp.xposed.hook

/**
 * The standalone simulator's explicit Stop action removes its foreground
 * notification before reaching 100%. Android reports this removal as an
 * app cancellation, which must never be interpreted as download success.
 *
 * Deliberately scoped to the test simulator: some real download applications
 * historically use app-cancel removals as their only completion signal.
 */
internal object DownloadRemovalPolicy {
    private const val SIMULATOR_PACKAGE = "eu.hxreborn.downloadsim"

    fun isSilentStop(
        packageName: String,
        lastPercent: Int,
    ): Boolean = packageName == SIMULATOR_PACKAGE && lastPercent < 100
}
