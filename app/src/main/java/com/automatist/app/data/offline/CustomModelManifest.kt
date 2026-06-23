package com.automatist.app.data.offline

import com.automatist.app.domain.offline.CustomOfflineModelInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Versioned JSON manifest a publisher (or advanced user) can host so all the details
 * required to add a compatible model are provided safely from one HTTPS URL.
 *
 * The manifest is *not* trusted blindly: every field is validated and bounded, and the
 * model is never downloaded as a side effect of importing the manifest — the user must
 * review and confirm first. Crucially, the manifest's `name`/`version` are never used to
 * derive local storage paths; the existing import flow generates a safe `custom-<uuid>`
 * ID and file name.
 */
@Serializable
data class CustomModelManifest(
    val schemaVersion: Int = 0,
    val format: String = "",
    val name: String = "",
    val version: String = "",
    val modelUrl: String = "",
    val sha256: String = "",
    val fileSizeBytes: Long = 0L,
    val licenseUrl: String = "",
    val sourceUrl: String = "",
    val minimumRamMb: Int? = null,
    val contextWindowChars: Int? = null
)

/**
 * The validated result of importing a manifest, ready to show on the review screen and
 * then hand to the existing secure import flow ([OfflineModelRegistry.addCustomModel]).
 */
data class ManifestImportPreview(
    val input: CustomOfflineModelInput,
    val version: String
)

/**
 * Pure (no Android, no network) parsing + validation of a [CustomModelManifest].
 *
 * Network fetching lives in [ManifestFetcher]; keeping parsing pure makes every rule
 * unit-testable. All failures surface as [IllegalArgumentException] with a user-readable
 * message.
 */
object CustomModelManifestParser {

    /** Strict upper bound on the manifest document itself (it is tiny JSON). */
    const val MAX_MANIFEST_BYTES = 64 * 1024

    /** The only schema version this app understands. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** The only model package format this runtime can load. */
    const val REQUIRED_FORMAT = "mediapipe-llm-task"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Read at most [maxBytes] from [input], throwing if the stream is larger. Decodes UTF-8.
     * Used by the fetcher to refuse an oversized response before parsing.
     */
    fun readCapped(input: InputStream, maxBytes: Int = MAX_MANIFEST_BYTES): String {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Manifest is too large (limit ${maxBytes / 1024} KB).")
            }
            out.write(chunk, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Decode the raw JSON into a [CustomModelManifest]. Throws on malformed JSON. */
    fun parse(raw: String): CustomModelManifest =
        try {
            json.decodeFromString<CustomModelManifest>(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("This does not look like a valid model manifest (could not parse JSON).")
        }

    /**
     * Validate a decoded [manifest] and map it onto the existing [CustomOfflineModelInput].
     * Manifest-specific rules (schema, format, size) are checked first with clear messages;
     * URL/checksum/name rules are then enforced by [CustomOfflineModelValidator] when the
     * input is built and again when the registry persists it.
     */
    fun toPreview(manifest: CustomModelManifest): ManifestImportPreview {
        require(manifest.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported manifest schemaVersion ${manifest.schemaVersion}; this version of Automatist supports $SUPPORTED_SCHEMA_VERSION."
        }
        require(manifest.format == REQUIRED_FORMAT) {
            "Unsupported manifest format '${manifest.format}'; expected '$REQUIRED_FORMAT'."
        }
        require(manifest.fileSizeBytes > 0L) {
            "Manifest fileSizeBytes must be a positive number of bytes."
        }
        require(manifest.sourceUrl.isNotBlank()) {
            "Manifest must include a public sourceUrl (model/source page)."
        }

        // Convert the authoritative byte size to whole MB (rounded). The existing
        // validator enforces the supported 1..3072 MB range.
        val sizeMb = ((manifest.fileSizeBytes + 500_000L) / 1_000_000L).toInt()

        val input = CustomOfflineModelInput(
            displayName = manifest.name.trim(),
            modelUrl = manifest.modelUrl.trim(),
            sha256 = manifest.sha256.trim(),
            downloadSizeMb = sizeMb,
            licenseUrl = manifest.licenseUrl.trim(),
            sourceUrl = manifest.sourceUrl.trim(),
            // RAM/context are advisory hints; the registry clamps them to safe bounds.
            minimumRamMb = manifest.minimumRamMb,
            contextWindowChars = manifest.contextWindowChars
        )
        // Reuse the single source of truth for URL/checksum/name/size validation.
        CustomOfflineModelValidator.validate(input)
        return ManifestImportPreview(input = input, version = manifest.version.trim())
    }

    /** Convenience: parse raw JSON and validate in one step. */
    fun parseAndValidate(raw: String): ManifestImportPreview = toPreview(parse(raw))
}
