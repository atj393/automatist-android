package com.automatist.app.feature.workflow.run

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * User-facing elapsed-time formatter for the currently running stage row.
 *
 * Keeps wording short and human-readable:
 *  - `0 ms`            → "0s elapsed"
 *  - `14 s`            → "14s elapsed"
 *  - `68 s`            → "1m 08s elapsed"   (seconds zero-padded once minutes appear)
 *  - negative / absurd → "0s elapsed"       (defensive, never crash the UI)
 *
 * Intentionally single-second precision. Millisecond jitter is noise at this
 * granularity and avoiding it means we only need a 1 Hz ticker in the UI.
 */
internal fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes == 0) {
        "${seconds}s elapsed"
    } else {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s elapsed"
    }
}

/**
 * Returns a live-updating "Ns elapsed" string for a stage that is currently
 * running. Ticks at 1 Hz while composed; stops automatically when the
 * composable leaves composition or [startedAtMillis] changes.
 *
 * Scope is deliberately narrow — only the row composable that calls this
 * observes the ticker state, so the per-second tick only recomposes that
 * single row, not the whole run screen. Completed/failed rows don't call
 * this at all and therefore don't tick.
 *
 * Returns an empty string if [startedAtMillis] is `0L` (no anchor — e.g.
 * the purely-cosmetic completed "compaction info" row inserted mid-run).
 */
@Composable
internal fun rememberRunningElapsedLabel(startedAtMillis: Long): String {
    if (startedAtMillis <= 0L) return ""
    var now by remember(startedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }
    return formatElapsed(now - startedAtMillis)
}
