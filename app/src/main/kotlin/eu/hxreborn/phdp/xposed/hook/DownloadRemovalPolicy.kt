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

    /**
     * Do not dispatch the empty remaining average before reporting completion.
     * Otherwise a single removed download generates 60% -> 0% -> 100%;
     * IndicatorView restarts its entire finishing sweep from zero.
     *
     * Keep the existing ordering for cancellations: clear remaining progress
     * before the error effect, and do not report a successful finish on Stop.
     */
    fun dispatch(
        packageName: String,
        lastPercent: Int,
        reason: Int,
        remainingDownloads: Int,
        updateRemainingProgress: () -> Unit,
        reportComplete: () -> Unit,
        reportCancelled: () -> Unit,
    ) {
        when {
            isSilentStop(packageName, lastPercent) -> {
                updateRemainingProgress()
            }

            lastPercent >= 100 ||
                reason == REASON_APP_CANCEL ||
                reason == REASON_APP_CANCEL_ALL -> {
                // A completed notification does not complete the aggregate if
                // other downloads are still tracked. Otherwise SystemUI sees
                // 100% -> remaining average -> 100% after the next completion.
                if (remainingDownloads == 0) reportComplete()
                updateRemainingProgress()
            }

            lastPercent >= LOW_PROGRESS_THRESHOLD -> {
                updateRemainingProgress()
                reportCancelled()
            }

            else -> {
                updateRemainingProgress()
            }
        }
    }

    private const val REASON_APP_CANCEL = 8
    private const val REASON_APP_CANCEL_ALL = 9
    private const val LOW_PROGRESS_THRESHOLD = 5
}
