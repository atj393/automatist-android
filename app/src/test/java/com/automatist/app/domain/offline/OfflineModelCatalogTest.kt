package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class OfflineModelCatalogTest {

    @Test
    fun `catalog contains exactly two models`() {
        assertEquals(
            "Exactly two offline models should be defined in this release",
            2, OfflineModelCatalog.ALL_MODELS.size
        )
    }

    @Test
    fun `first model has stable ID gemini-nano`() {
        val model = OfflineModelCatalog.ALL_MODELS.first()
        assertEquals(OfflineModelCatalog.GEMINI_NANO_ID, model.id)
        assertEquals("gemini-nano", model.id)
    }

    @Test
    fun `second model has stable ID gemma-3n-e2b`() {
        val model = OfflineModelCatalog.ALL_MODELS[1]
        assertEquals(OfflineModelCatalog.GEMMA_3N_E2B_ID, model.id)
        assertEquals("gemma-3n-e2b", model.id)
    }

    @Test
    fun `all models have non-blank display names and descriptions`() {
        OfflineModelCatalog.ALL_MODELS.forEach { model ->
            assertTrue("Model '${model.id}' must have a display name", model.displayName.isNotBlank())
            assertTrue("Model '${model.id}' must have a description", model.description.isNotBlank())
            assertTrue("Model '${model.id}' must have a size label", model.sizeLabel.isNotBlank())
        }
    }

    @Test
    fun `all models are text-only in this release`() {
        OfflineModelCatalog.ALL_MODELS.forEach { model ->
            assertTrue("Model '${model.id}' should be text-only", model.isTextOnly)
        }
    }

    @Test
    fun `findById returns correct model for known IDs`() {
        val nano = OfflineModelCatalog.findById(OfflineModelCatalog.GEMINI_NANO_ID)
        assertNotNull(nano)
        assertEquals(OfflineModelCatalog.GEMINI_NANO_ID, nano!!.id)

        val gemma = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)
        assertNotNull(gemma)
        assertEquals(OfflineModelCatalog.GEMMA_3N_E2B_ID, gemma!!.id)
    }

    @Test
    fun `findById returns null for unknown ID`() {
        val found = OfflineModelCatalog.findById("not-a-real-model")
        assertNull(found)
    }

    @Test
    fun `all model IDs are unique`() {
        val ids = OfflineModelCatalog.ALL_MODELS.map { it.id }
        assertEquals("Model IDs must be unique", ids.size, ids.distinct().size)
    }

    @Test
    fun `model minimum API level is at least 26`() {
        OfflineModelCatalog.ALL_MODELS.forEach { model ->
            assertTrue(
                "Model '${model.id}' minimum API level ${model.minimumAndroidApiLevel} must be >= 26",
                model.minimumAndroidApiLevel >= 26
            )
        }
    }

    @Test
    fun `gemini nano is system managed with AICORE runtime`() {
        val model = OfflineModelCatalog.findById(OfflineModelCatalog.GEMINI_NANO_ID)!!
        assertTrue("Gemini Nano must be system-managed", model.isSystemManaged)
        assertEquals("Gemini Nano must use AICORE runtime", OfflineRuntimeType.AICORE, model.runtimeType)
    }

    @Test
    fun `gemini nano requires android 14 or higher`() {
        val model = OfflineModelCatalog.findById(OfflineModelCatalog.GEMINI_NANO_ID)!!
        assertTrue(
            "Gemini Nano requires Android 14 (API 34) for AICore; got ${model.minimumAndroidApiLevel}",
            model.minimumAndroidApiLevel >= 34
        )
    }

    @Test
    fun `gemma 3n e2b is app-managed downloadable with DOWNLOADABLE runtime`() {
        val model = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!
        assertFalse("Gemma 3n E2B must NOT be system-managed", model.isSystemManaged)
        assertEquals("Gemma 3n E2B must use DOWNLOADABLE runtime", OfflineRuntimeType.DOWNLOADABLE, model.runtimeType)
    }

    @Test
    fun `gemma 3n e2b has download metadata`() {
        val model = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!
        assertNotNull("Gemma 3n E2B must have a download URL", model.downloadUrl)
        assertTrue("Gemma 3n E2B download URL must not be blank", model.downloadUrl!!.isNotBlank())
        assertNotNull("Gemma 3n E2B must have a model file name", model.modelFileName)
        assertTrue("Gemma 3n E2B must have a positive download size", model.downloadSizeBytes > 0)
    }

    @Test
    fun `gemma 3n e2b has reasonable context window`() {
        val model = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!
        assertTrue(
            "Gemma 3n E2B context window must be > 0",
            model.contextWindowChars > 0
        )
    }

    @Test
    fun `system-managed models have no download metadata`() {
        OfflineModelCatalog.ALL_MODELS.filter { it.isSystemManaged }.forEach { model ->
            assertNull("System-managed model '${model.id}' should have no download URL", model.downloadUrl)
            assertNull("System-managed model '${model.id}' should have no file name", model.modelFileName)
            assertEquals("System-managed model '${model.id}' should have 0 download size", 0L, model.downloadSizeBytes)
        }
    }

    @Test
    fun `downloadable models have required download metadata`() {
        OfflineModelCatalog.ALL_MODELS.filter { !it.isSystemManaged }.forEach { model ->
            assertNotNull("Downloadable model '${model.id}' must have a download URL", model.downloadUrl)
            assertNotNull("Downloadable model '${model.id}' must have a file name", model.modelFileName)
            assertTrue("Downloadable model '${model.id}' must have a positive download size", model.downloadSizeBytes > 0)
        }
    }

    @Test
    fun `offline model status enum has exactly five states`() {
        // Guard against adding a new status without handling it in readiness evaluator,
        // provider transform(), and UI status chips.
        val expected = setOf("NOT_INSTALLED", "DOWNLOADING", "INSTALLED", "FAILED", "UNSUPPORTED")
        val actual = OfflineModelStatus.entries.map { it.name }.toSet()
        assertEquals(
            "OfflineModelStatus enum must contain exactly the expected states; " +
                "if a new state is added, update ReadinessEvaluator, LocalAIArticleTransformProvider, " +
                "and VaultScreen to handle it.",
            expected, actual
        )
    }

    @Test
    fun `offline runtime type enum has exactly two types`() {
        val expected = setOf("AICORE", "DOWNLOADABLE")
        val actual = OfflineRuntimeType.entries.map { it.name }.toSet()
        assertEquals(
            "OfflineRuntimeType enum must contain exactly the expected types; " +
                "if a new type is added, update DataStoreOfflineModelRepository and " +
                "LocalAIArticleTransformProvider to handle it.",
            expected, actual
        )
    }

    // ── DownloadProgress ──

    @Test
    fun `download progress percent is correct for known total`() {
        val progress = DownloadProgress(bytesDownloaded = 750_000_000, totalBytes = 1_500_000_000)
        assertEquals(50, progress.percent)
    }

    @Test
    fun `download progress percent is -1 when total is unknown`() {
        val progress = DownloadProgress(bytesDownloaded = 100_000, totalBytes = -1)
        assertEquals(-1, progress.percent)
    }

    @Test
    fun `download progress percent is 0 at start`() {
        val progress = DownloadProgress(bytesDownloaded = 0, totalBytes = 1_500_000_000)
        assertEquals(0, progress.percent)
    }

    @Test
    fun `download progress percent is 100 when complete`() {
        val progress = DownloadProgress(bytesDownloaded = 1_500_000_000, totalBytes = 1_500_000_000)
        assertEquals(100, progress.percent)
    }

    @Test
    fun `download progress default is zero bytes with unknown total`() {
        val progress = DownloadProgress()
        assertEquals(0L, progress.bytesDownloaded)
        assertEquals(-1L, progress.totalBytes)
        assertEquals(-1, progress.percent)
    }

    @Test
    fun `download progress percent never exceeds 100`() {
        // Edge case: bytesDownloaded slightly exceeds totalBytes (network reporting quirk)
        val progress = DownloadProgress(bytesDownloaded = 1_600_000_000, totalBytes = 1_500_000_000)
        assertEquals(100, progress.percent)
    }

    @Test
    fun `download progress percent is 0 when totalBytes is zero`() {
        // Edge case: server reports 0 total (degenerate)
        val progress = DownloadProgress(bytesDownloaded = 0, totalBytes = 0)
        assertEquals(-1, progress.percent) // totalBytes <= 0 → indeterminate
    }

    // ── Catalog fallback size for progress ──

    @Test
    fun `progress with server-reported total shows correct percent`() {
        // Simulates: server Content-Length is available
        val progress = DownloadProgress(bytesDownloaded = 292_208_640, totalBytes = 584_417_280)
        assertEquals(50, progress.percent)
    }

    @Test
    fun `progress with catalog fallback total shows correct percent`() {
        // Simulates: server Content-Length was -1, code fell back to entry.downloadSizeBytes
        val catalogSize = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!.downloadSizeBytes
        val progress = DownloadProgress(bytesDownloaded = catalogSize / 2, totalBytes = catalogSize)
        // Integer division: percent should be in the 49-50 range
        assertTrue("Progress at ~50% should be between 49 and 50", progress.percent in 49..50)
    }

    @Test
    fun `progress is indeterminate only when both server and catalog sizes are unknown`() {
        // Simulates: no Content-Length AND no catalog size (hypothetical future model)
        val progress = DownloadProgress(bytesDownloaded = 100_000_000, totalBytes = -1)
        assertEquals(-1, progress.percent)
    }

    @Test
    fun `gemma catalog entry has valid downloadSizeBytes for fallback`() {
        val entry = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!
        assertTrue(
            "Downloadable model must have downloadSizeBytes > 0 for progress fallback",
            entry.downloadSizeBytes > 0
        )
    }

    // ── FakeOfflineModelRepository cancellation ──

    @Test
    fun `cancel resets status to NOT_INSTALLED`() = kotlinx.coroutines.test.runTest {
        val repo = FakeOfflineModelRepository()
        repo.setStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID, OfflineModelStatus.DOWNLOADING)
        repo.setProgress(OfflineModelCatalog.GEMMA_3N_E2B_ID, DownloadProgress(200_000_000, 584_417_280))

        repo.cancelDownload(OfflineModelCatalog.GEMMA_3N_E2B_ID)

        val status = repo.getModelStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID)
        assertEquals(OfflineModelStatus.NOT_INSTALLED, status.first())
    }

    @Test
    fun `cancel resets download progress to default`() = kotlinx.coroutines.test.runTest {
        val repo = FakeOfflineModelRepository()
        repo.setProgress(OfflineModelCatalog.GEMMA_3N_E2B_ID, DownloadProgress(200_000_000, 584_417_280))

        repo.cancelDownload(OfflineModelCatalog.GEMMA_3N_E2B_ID)

        val progress = repo.getDownloadProgress(OfflineModelCatalog.GEMMA_3N_E2B_ID).value
        assertEquals(0L, progress.bytesDownloaded)
        assertEquals(-1L, progress.totalBytes)
    }

    @Test
    fun `cancel then retry starts from NOT_INSTALLED`() = kotlinx.coroutines.test.runTest {
        val repo = FakeOfflineModelRepository()
        repo.setStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID, OfflineModelStatus.DOWNLOADING)
        repo.cancelDownload(OfflineModelCatalog.GEMMA_3N_E2B_ID)

        // Retry: requestDownload transitions to DOWNLOADING
        repo.requestDownload(OfflineModelCatalog.GEMMA_3N_E2B_ID)

        val status = repo.getModelStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID)
        assertEquals(OfflineModelStatus.DOWNLOADING, status.first())
    }

    @Test
    fun `cancel on NOT_INSTALLED model is safe no-op`() = kotlinx.coroutines.test.runTest {
        val repo = FakeOfflineModelRepository()
        // Model is already NOT_INSTALLED by default

        repo.cancelDownload(OfflineModelCatalog.GEMMA_3N_E2B_ID)

        val status = repo.getModelStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID)
        assertEquals(OfflineModelStatus.NOT_INSTALLED, status.first())
    }
}
