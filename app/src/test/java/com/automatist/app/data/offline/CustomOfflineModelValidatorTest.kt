package com.automatist.app.data.offline

import com.automatist.app.domain.offline.CustomOfflineModelInput
import org.junit.Assert.fail
import org.junit.Test

class CustomOfflineModelValidatorTest {

    /**
     * A genuinely independent, ungated, MediaPipe-compatible `.task` model — NOT the
     * built-in Automatist GitHub Gemma. Source: Hugging Face `litert-community/Qwen2.5-0.5B-Instruct`
     * (Apache-2.0, `gated:false`, published per-file SHA-256). Used as the canonical
     * "valid external source" the feature is designed to accept.
     */
    private fun validInput() = CustomOfflineModelInput(
        displayName = "Qwen2.5 0.5B Instruct (q8)",
        modelUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
        downloadSizeMb = 521,
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
    )

    @Test
    fun validIndependentMediaPipeTaskSourceIsAccepted() {
        CustomOfflineModelValidator.validate(validInput())
    }

    @Test
    fun acceptsTaskUrlWithQueryString() {
        CustomOfflineModelValidator.validate(
            validInput().copy(modelUrl = "https://example.com/path/model.task?download=true")
        )
    }

    @Test
    fun trimsSurroundingWhitespaceBeforeValidation() {
        CustomOfflineModelValidator.validate(
            validInput().copy(
                displayName = "  Qwen  ",
                sha256 = "  e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2  "
            )
        )
    }

    @Test
    fun rejectsNonHttpsModelUrl() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "http://example.com/model.task")) }
    }

    @Test
    fun rejectsModelUrlWithEmbeddedCredentials() {
        assertInvalid {
            CustomOfflineModelValidator.validate(
                validInput().copy(modelUrl = "https://user:secret@example.com/model.task")
            )
        }
    }

    @Test
    fun rejectsModelUrlOnNonStandardPort() {
        assertInvalid {
            CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "https://example.com:8443/model.task"))
        }
    }

    @Test
    fun rejectsUnsupportedModelFormatGguf() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "https://example.com/model.gguf")) }
    }

    @Test
    fun rejectsUnsupportedModelFormatSafetensors() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "https://example.com/model.safetensors")) }
    }

    @Test
    fun rejectsExecutableExtensions() {
        listOf("model.apk", "model.so", "model.bin", "model.sh").forEach { name ->
            assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(modelUrl = "https://example.com/$name")) }
        }
    }

    @Test
    fun rejectsMissingIntegrityHash() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(sha256 = "abc123")) }
    }

    @Test
    fun rejectsNonHexIntegrityHash() {
        // 64 chars but contains non-hex 'z'
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(sha256 = "z".repeat(64))) }
    }

    @Test
    fun rejectsOutOfRangeDownloadSizeTooLarge() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(downloadSizeMb = 3_073)) }
    }

    @Test
    fun rejectsOutOfRangeDownloadSizeTooSmall() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(downloadSizeMb = 0)) }
    }

    @Test
    fun rejectsBlankDisplayName() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(displayName = "   ")) }
    }

    @Test
    fun rejectsOverlongDisplayName() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(displayName = "x".repeat(81))) }
    }

    @Test
    fun rejectsNonHttpsLicenseUrl() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(licenseUrl = "http://example.com/license")) }
    }

    @Test
    fun rejectsMissingLicenseUrl() {
        assertInvalid { CustomOfflineModelValidator.validate(validInput().copy(licenseUrl = "")) }
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
