package com.automatist.app.domain.offline

import org.junit.Assert.*
import org.junit.Test

class OfflineModelCatalogTest {

    @Test
    fun `catalog contains exactly one model`() {
        assertEquals(
            "Exactly one offline model should be defined in this release",
            1, OfflineModelCatalog.ALL_MODELS.size
        )
    }

    @Test
    fun `first model has stable ID gemini-nano`() {
        val model = OfflineModelCatalog.ALL_MODELS.first()
        assertEquals(OfflineModelCatalog.GEMINI_NANO_ID, model.id)
        assertEquals("gemini-nano", model.id)
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
    fun `findById returns correct model for known ID`() {
        val found = OfflineModelCatalog.findById(OfflineModelCatalog.GEMINI_NANO_ID)
        assertNotNull(found)
        assertEquals(OfflineModelCatalog.GEMINI_NANO_ID, found!!.id)
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
    fun `gemini nano is system managed`() {
        val model = OfflineModelCatalog.ALL_MODELS.first()
        assertTrue(
            "Gemini Nano is managed by Android AICore and must be marked isSystemManaged = true",
            model.isSystemManaged
        )
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
}
