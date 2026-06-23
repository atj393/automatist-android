package com.automatist.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Pure tests for [CustomModelManifestParser]: parsing, validation, the size cap, and the
 * safe mapping from manifest metadata into the existing custom-model import flow.
 */
class CustomModelManifestParserTest {

    private fun manifestJson(
        schemaVersion: Int = 1,
        format: String = "mediapipe-llm-task",
        name: String = "Example Local Model",
        version: String = "1.0.0",
        modelUrl: String = "https://publisher.example/models/example-model.task",
        sha256: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        fileSizeBytes: Long = 734003200L,
        licenseUrl: String = "https://publisher.example/license",
        sourceUrl: String = "https://publisher.example/model-card",
        minimumRamMb: Int? = 4096,
        contextWindowChars: Int? = 2500
    ): String = buildString {
        append("{")
        append("\"schemaVersion\":$schemaVersion,")
        append("\"format\":\"$format\",")
        append("\"name\":\"$name\",")
        append("\"version\":\"$version\",")
        append("\"modelUrl\":\"$modelUrl\",")
        append("\"sha256\":\"$sha256\",")
        append("\"fileSizeBytes\":$fileSizeBytes,")
        append("\"licenseUrl\":\"$licenseUrl\",")
        append("\"sourceUrl\":\"$sourceUrl\"")
        if (minimumRamMb != null) append(",\"minimumRamMb\":$minimumRamMb")
        if (contextWindowChars != null) append(",\"contextWindowChars\":$contextWindowChars")
        append("}")
    }

    // ── Valid parse + mapping ──

    @Test
    fun validManifestParsesAndMapsToInput() {
        val preview = CustomModelManifestParser.parseAndValidate(manifestJson())
        val input = preview.input
        assertEquals("Example Local Model", input.displayName)
        assertEquals("1.0.0", preview.version)
        assertEquals("https://publisher.example/models/example-model.task", input.modelUrl)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", input.sha256)
        assertEquals("https://publisher.example/license", input.licenseUrl)
        assertEquals("https://publisher.example/model-card", input.sourceUrl)
        // 734_003_200 bytes rounds to 734 MB
        assertEquals(734, input.downloadSizeMb)
        assertEquals(4096, input.minimumRamMb)
        assertEquals(2500, input.contextWindowChars)
    }

    @Test
    fun mappingNeverUsesManifestNameForStorageAndGeneratesSafeId() {
        // Even a hostile display name must never influence the stored ID / file name.
        val preview = CustomModelManifestParser.parseAndValidate(manifestJson(name = "../../etc/passwd"))
        assertEquals("../../etc/passwd", preview.input.displayName) // kept verbatim only for display
        val stored = CustomOfflineModelStore.create(preview.input)
        val fileName = stored.toEntry().modelFileName!!
        assertTrue("ID must be app-generated, not derived from the name", stored.id.startsWith("custom-"))
        assertEquals("${stored.id}.task", fileName)
        assertTrue("File name must be a safe single segment", fileName.matches(Regex("^custom-[a-f0-9-]{36}\\.task$")))
    }

    @Test
    fun ramAndContextHintsAreBoundedWhenStored() {
        val json = manifestJson(minimumRamMb = 999_999, contextWindowChars = 1_000_000)
        val input = CustomModelManifestParser.parseAndValidate(json).input
        val entry = CustomOfflineModelStore.create(input).toEntry()
        assertEquals(CustomOfflineModelStore.MAX_MINIMUM_RAM_MB, entry.minimumRamMb)
        assertEquals(CustomOfflineModelStore.MAX_CONTEXT_WINDOW_CHARS, entry.contextWindowChars)
    }

    // ── Rejections ──

    @Test
    fun rejectsUnsupportedSchemaVersion() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(schemaVersion = 2)) }

    @Test
    fun rejectsUnsupportedFormat() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(format = "gguf")) }

    @Test
    fun rejectsNonHttpsModelUrl() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(modelUrl = "http://publisher.example/m.task")) }

    @Test
    fun rejectsNonTaskModelUrl() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(modelUrl = "https://publisher.example/m.gguf")) }

    @Test
    fun rejectsInvalidSha256() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(sha256 = "deadbeef")) }

    @Test
    fun rejectsNonHttpsLicenseUrl() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(licenseUrl = "http://publisher.example/license")) }

    @Test
    fun rejectsNonHttpsSourceUrl() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(sourceUrl = "http://publisher.example/card")) }

    @Test
    fun rejectsMissingSourceUrl() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(sourceUrl = "")) }

    @Test
    fun rejectsZeroFileSize() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(fileSizeBytes = 0L)) }

    @Test
    fun rejectsNegativeFileSize() = assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(fileSizeBytes = -1L)) }

    @Test
    fun rejectsOversizedFileSize() {
        // 4 GB → ~4000 MB, beyond the 3072 MB ceiling enforced by the validator.
        assertInvalid { CustomModelManifestParser.parseAndValidate(manifestJson(fileSizeBytes = 4_000_000_000L)) }
    }

    @Test
    fun rejectsMalformedJson() = assertInvalid { CustomModelManifestParser.parseAndValidate("{ not valid json ]") }

    // ── Size cap ──

    @Test
    fun readCappedReturnsContentWithinLimit() {
        val text = "{\"ok\":true}"
        val result = CustomModelManifestParser.readCapped(ByteArrayInputStream(text.toByteArray()), maxBytes = 1024)
        assertEquals(text, result)
    }

    @Test
    fun readCappedRejectsOversizedStream() {
        val big = ByteArray(2048) { 'a'.code.toByte() }
        assertInvalid { CustomModelManifestParser.readCapped(ByteArrayInputStream(big), maxBytes = 1024) }
    }

    @Test
    fun manifestSizeLimitIsStrict() {
        // A document just over the configured maximum must be refused.
        val oversized = ByteArray(CustomModelManifestParser.MAX_MANIFEST_BYTES + 1) { '{'.code.toByte() }
        assertInvalid { CustomModelManifestParser.readCapped(ByteArrayInputStream(oversized)) }
    }

    @Test
    fun unknownManifestFieldsAreIgnored() {
        val json = manifestJson().dropLast(1) + ",\"futureField\":\"ignored\"}"
        val preview = CustomModelManifestParser.parseAndValidate(json)
        assertEquals("Example Local Model", preview.input.displayName)
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
