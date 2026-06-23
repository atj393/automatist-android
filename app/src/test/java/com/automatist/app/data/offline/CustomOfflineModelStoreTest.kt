package com.automatist.app.data.offline

import com.automatist.app.domain.offline.CustomOfflineModelInput
import com.automatist.app.domain.offline.OfflineRuntimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure (no Android context) tests for the custom-model store logic that backs
 * [OfflineModelRegistry]. Covers safe ID/file-name generation, normalisation,
 * serialization round-trips (i.e. "survives app restart"), deduplication, and removal.
 */
class CustomOfflineModelStoreTest {

    private fun input(
        url: String = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/m.task",
        sha: String = "E608953F169AEB1BD7B9155FEC2559825E08453FC209B84EDA3A781ED0452FD2"
    ) = CustomOfflineModelInput(
        displayName = "  Qwen 0.5B  ",
        modelUrl = url,
        sha256 = sha,
        downloadSizeMb = 521,
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
    )

    @Test
    fun createGeneratesNamespacedUuidIdAndSafeFileName() {
        val stored = CustomOfflineModelStore.create(input())
        assertTrue("ID should be namespaced with 'custom-'", stored.id.startsWith("custom-"))
        val entry = stored.toEntry()
        // File name must be a single safe path segment derived from the app-generated ID.
        assertEquals("${stored.id}.task", entry.modelFileName)
        val fileName = entry.modelFileName!!
        assertFalse("File name must not contain path separators", fileName.contains('/') || fileName.contains('\\'))
        assertFalse("File name must not contain parent traversal", fileName.contains(".."))
        assertTrue("File name must match safe pattern", fileName.matches(Regex("^custom-[a-f0-9-]{36}\\.task$")))
    }

    @Test
    fun createNormalisesShaToLowercaseAndTrimsName() {
        val stored = CustomOfflineModelStore.create(input())
        assertEquals("e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2", stored.sha256)
        assertEquals("Qwen 0.5B", stored.displayName)
    }

    @Test
    fun createRejectsInvalidInput() {
        try {
            CustomOfflineModelStore.create(input(url = "http://example.com/m.task"))
            fail("Expected IllegalArgumentException for non-HTTPS URL")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toEntryMapsToDownloadableUserAddedMediaPipeModel() {
        val entry = CustomOfflineModelStore.create(input()).toEntry()
        assertEquals(OfflineRuntimeType.DOWNLOADABLE, entry.runtimeType)
        assertTrue("Custom entries must be flagged user-added", entry.isUserAdded)
        assertFalse("Custom entries are not system-managed", entry.isSystemManaged)
        assertEquals(521L * 1_000_000L, entry.downloadSizeBytes)
        assertEquals("e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2", entry.fileSha256)
        assertTrue("Should carry a Custom tag", entry.tags.contains("Custom"))
    }

    @Test
    fun serializeThenParseRoundTripsSurvivingRestart() {
        val a = CustomOfflineModelStore.create(input(url = "https://h.co/a.task"))
        val b = CustomOfflineModelStore.create(input(url = "https://h.co/b.task"))
        val raw = CustomOfflineModelStore.serialize(listOf(a, b))

        val restored = CustomOfflineModelStore.parse(raw)

        assertEquals(listOf(a, b), restored)
    }

    @Test
    fun parseToleratesNullAndGarbage() {
        assertTrue(CustomOfflineModelStore.parse(null).isEmpty())
        assertTrue(CustomOfflineModelStore.parse("").isEmpty())
        assertTrue(CustomOfflineModelStore.parse("not json at all {[").isEmpty())
    }

    @Test
    fun addAppendsDistinctSources() {
        val a = CustomOfflineModelStore.create(input(url = "https://h.co/a.task"))
        val b = CustomOfflineModelStore.create(input(url = "https://h.co/b.task"))
        val result = CustomOfflineModelStore.add(listOf(a), b)
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun addRejectsDuplicateUrl() {
        val a = CustomOfflineModelStore.create(input(url = "https://h.co/same.task"))
        val dup = CustomOfflineModelStore.create(input(url = "https://h.co/same.task"))
        // Distinct IDs but identical normalised URL → duplicate.
        assertNotEquals(a.id, dup.id)
        try {
            CustomOfflineModelStore.add(listOf(a), dup)
            fail("Expected IllegalArgumentException for duplicate URL")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun removeDropsTargetAndIsNoOpForUnknownId() {
        val a = CustomOfflineModelStore.create(input(url = "https://h.co/a.task"))
        val b = CustomOfflineModelStore.create(input(url = "https://h.co/b.task"))

        val afterRemove = CustomOfflineModelStore.remove(listOf(a, b), a.id)
        assertEquals(listOf(b), afterRemove)

        val unchanged = CustomOfflineModelStore.remove(listOf(a, b), "custom-does-not-exist")
        assertEquals(listOf(a, b), unchanged)
    }
}
