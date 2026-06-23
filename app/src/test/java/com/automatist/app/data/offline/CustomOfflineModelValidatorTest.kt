package com.automatist.app.data.offline

import com.automatist.app.domain.offline.CustomOfflineModelInput
import org.junit.Assert.fail
import org.junit.Test

class CustomOfflineModelValidatorTest {

    private fun validInput() = CustomOfflineModelInput(
        displayName = "Public Gemma test model",
        modelUrl = "https://github.com/atj393/automatist-models/releases/download/offline-models-v1/gemma3-1b-it-int4.task",
        sha256 = "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee",
        downloadSizeMb = 555,
        licenseUrl = "https://ai.google.dev/gemma/terms"
    )

    @Test
    fun validMediaPipeTaskSourceIsAccepted() {
        CustomOfflineModelValidator.validate(validInput())
    }

    @Test
    fun rejectsNonHttpsModelUrl() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "http://example.com/model.task")) }
    }

    @Test
    fun rejectsUnsupportedModelFormat() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "https://example.com/model.gguf")) }
    }

    @Test
    fun rejectsMissingIntegrityHash() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(sha256 = "abc123")) }
    }

    @Test
    fun rejectsOutOfRangeDownloadSize() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(downloadSizeMb = 3_073)) }
    }

    @Test
    fun rejectsNonHttpsLicenseUrl() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(licenseUrl = "http://example.com/license")) }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
