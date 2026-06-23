package com.automatist.app.data.offline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.domain.offline.CustomOfflineModelInput
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelEntry
import com.automatist.app.domain.offline.OfflineModelResolver
import com.automatist.app.domain.offline.OfflineModelUrlSafety
import com.automatist.app.domain.offline.OfflineRuntimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customOfflineModelDataStore by preferencesDataStore(name = "custom_offline_models")
private val customOfflineModelsKey = stringPreferencesKey("models")

/**
 * Validates the narrow set of model sources Automatist can safely download.
 *
 * A custom model is *data* for the already bundled MediaPipe runtime. It must never
 * be an APK, native library, script, or another executable plugin — only a MediaPipe
 * LLM `.task` package, fetched over plain HTTPS, with a mandatory integrity checksum
 * and a public license link.
 */
object CustomOfflineModelValidator {
    private const val MIN_DOWNLOAD_SIZE_MB = 1
    private const val MAX_DOWNLOAD_SIZE_MB = 3_072

    fun validate(input: CustomOfflineModelInput) {
        require(input.displayName.trim().length in 1..80) {
            "Use a model name between 1 and 80 characters."
        }
        val modelUrl = requireHttpsUrl(input.modelUrl, "Model URL")
        require(modelUrl.encodedPath.endsWith(".task", ignoreCase = true)) {
            "Only MediaPipe .task model files are supported."
        }
        require(input.sha256.trim().matches(Regex("^[A-Fa-f0-9]{64}$"))) {
            "SHA-256 must be exactly 64 hexadecimal characters."
        }
        require(input.downloadSizeMb in MIN_DOWNLOAD_SIZE_MB..MAX_DOWNLOAD_SIZE_MB) {
            "Download size must be between $MIN_DOWNLOAD_SIZE_MB MB and $MAX_DOWNLOAD_SIZE_MB MB."
        }
        requireHttpsUrl(input.licenseUrl, "License URL")
        // sourceUrl is optional (manifest import provides it); validate only when present.
        input.sourceUrl?.takeIf { it.isNotBlank() }?.let { requireHttpsUrl(it, "Source URL") }
    }

    /**
     * Parse [value] as a safe HTTPS URL (port 443, no embedded credentials), or throw
     * an [IllegalArgumentException] labelled with [label]. Shared with the download-time
     * check via [OfflineModelUrlSafety] so the accepted-source rule and the on-the-wire
     * rule can never drift apart.
     */
    fun requireHttpsUrl(value: String, label: String): HttpUrl =
        OfflineModelUrlSafety.parseSafe(value)
            ?: throw IllegalArgumentException(
                "$label must be a valid HTTPS URL on the standard secure port, with no embedded credentials."
            )
}

/**
 * Persisted custom model source. The companion [CustomOfflineModelStore] holds the pure
 * (I/O-free) logic for reading, writing, and mutating the stored set, so the
 * security-relevant behaviour — validation, normalisation, safe ID/file-name generation,
 * deduplication — is unit-testable without an Android context.
 */
@Serializable
internal data class StoredCustomOfflineModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val sha256: String,
    val downloadSizeMb: Int,
    val licenseUrl: String,
    // Fields below were added with the manifest-import path. Defaults keep older
    // stored JSON (written before these existed) decodable without migration.
    val sourceUrl: String? = null,
    val minimumRamMb: Int = CustomOfflineModelStore.DEFAULT_MINIMUM_RAM_MB,
    val contextWindowChars: Int = CustomOfflineModelStore.DEFAULT_CONTEXT_WINDOW_CHARS
) {
    fun toEntry(): OfflineModelEntry = OfflineModelEntry(
        id = id,
        displayName = displayName,
        description = "Custom MediaPipe model. It is downloaded only when you choose Download and runs locally after setup.",
        sizeLabel = "~$downloadSizeMb MB download",
        tags = listOf("Custom", "MediaPipe", "Offline"),
        isSystemManaged = false,
        runtimeType = OfflineRuntimeType.DOWNLOADABLE,
        downloadUrl = downloadUrl,
        downloadSizeBytes = downloadSizeMb.toLong() * 1_000_000L,
        fileSha256 = sha256,
        // ID is an app-generated UUID (hex + hyphens only), so the derived file name is
        // always a safe single path segment — no user-controlled characters reach the FS.
        modelFileName = "$id.task",
        contextWindowChars = contextWindowChars,
        minimumRamMb = minimumRamMb,
        isUserAdded = true
    )
}

internal object CustomOfflineModelStore {
    private val json = Json { ignoreUnknownKeys = true }

    // Safe bounds for hint values that may come from an external manifest. RAM/context
    // are treated as advisory only — a manifest can never push them outside this range.
    const val DEFAULT_CONTEXT_WINDOW_CHARS = 2_500
    const val MIN_CONTEXT_WINDOW_CHARS = 500
    const val MAX_CONTEXT_WINDOW_CHARS = 4_000
    const val DEFAULT_MINIMUM_RAM_MB = 3_000
    const val MAX_MINIMUM_RAM_MB = 16_384

    /** Clamp a (possibly null/external) context hint into the supported range. */
    fun boundedContextWindowChars(value: Int?): Int =
        (value ?: DEFAULT_CONTEXT_WINDOW_CHARS).coerceIn(MIN_CONTEXT_WINDOW_CHARS, MAX_CONTEXT_WINDOW_CHARS)

    /** Clamp a (possibly null/external) RAM hint into the supported range. */
    fun boundedMinimumRamMb(value: Int?): Int =
        (value ?: DEFAULT_MINIMUM_RAM_MB).coerceIn(0, MAX_MINIMUM_RAM_MB)

    /** Decode the stored list, tolerating null/garbage by returning an empty list. */
    fun parse(raw: String?): List<StoredCustomOfflineModel> =
        raw?.let { runCatching { json.decodeFromString<List<StoredCustomOfflineModel>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    fun serialize(models: List<StoredCustomOfflineModel>): String = json.encodeToString(models)

    /**
     * Validate and normalise [input] into a new stored source. Generates a fresh
     * `custom-<uuid>` ID. Throws [IllegalArgumentException] if the input is invalid.
     */
    fun create(input: CustomOfflineModelInput): StoredCustomOfflineModel {
        CustomOfflineModelValidator.validate(input)
        val normalizedUrl = CustomOfflineModelValidator.requireHttpsUrl(input.modelUrl, "Model URL").toString()
        val normalizedLicenseUrl = CustomOfflineModelValidator.requireHttpsUrl(input.licenseUrl, "License URL").toString()
        val normalizedSourceUrl = input.sourceUrl?.takeIf { it.isNotBlank() }
            ?.let { CustomOfflineModelValidator.requireHttpsUrl(it, "Source URL").toString() }
        return StoredCustomOfflineModel(
            id = "custom-${UUID.randomUUID()}",
            displayName = input.displayName.trim(),
            downloadUrl = normalizedUrl,
            sha256 = input.sha256.trim().lowercase(),
            downloadSizeMb = input.downloadSizeMb,
            licenseUrl = normalizedLicenseUrl,
            sourceUrl = normalizedSourceUrl,
            // RAM/context arrive as untrusted hints (manual import leaves them null);
            // always clamp to the supported range here so neither path can exceed it.
            minimumRamMb = boundedMinimumRamMb(input.minimumRamMb),
            contextWindowChars = boundedContextWindowChars(input.contextWindowChars)
        )
    }

    /** Append [created] to [existing], rejecting a source whose normalised URL is already present. */
    fun add(
        existing: List<StoredCustomOfflineModel>,
        created: StoredCustomOfflineModel
    ): List<StoredCustomOfflineModel> {
        require(existing.none { it.downloadUrl == created.downloadUrl }) {
            "This model URL has already been added."
        }
        return existing + created
    }

    /** Remove the source with [modelId]. Returns the list unchanged if absent. */
    fun remove(
        existing: List<StoredCustomOfflineModel>,
        modelId: String
    ): List<StoredCustomOfflineModel> = existing.filterNot { it.id == modelId }
}

/**
 * Persistent registry for user-added MediaPipe model sources.
 *
 * Built-in entries remain code-defined in [OfflineModelCatalog]; this registry merges
 * them with any custom sources. Custom entries are stored as *metadata only* — the model
 * files themselves live in app-private storage and are managed by [ModelDownloadManager].
 */
@Singleton
class OfflineModelRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) : OfflineModelResolver {

    override val models: Flow<List<OfflineModelEntry>> =
        context.customOfflineModelDataStore.data.map { preferences ->
            OfflineModelCatalog.ALL_MODELS +
                CustomOfflineModelStore.parse(preferences[customOfflineModelsKey]).map { it.toEntry() }
        }

    override suspend fun findById(modelId: String): OfflineModelEntry? =
        models.first().firstOrNull { it.id == modelId }

    suspend fun addCustomModel(input: CustomOfflineModelInput): OfflineModelEntry {
        // Validate + generate the ID outside the DataStore edit; the duplicate check
        // runs inside the edit so concurrent adds resolve against the latest state.
        val created = CustomOfflineModelStore.create(input)
        context.customOfflineModelDataStore.edit { preferences ->
            val existing = CustomOfflineModelStore.parse(preferences[customOfflineModelsKey])
            preferences[customOfflineModelsKey] =
                CustomOfflineModelStore.serialize(CustomOfflineModelStore.add(existing, created))
        }
        return created.toEntry()
    }

    /**
     * Forget a user-added model source.
     *
     * Removes only the source metadata. It deliberately does NOT delete any AI profile
     * that references this model — such a profile keeps its exact model ID and simply
     * surfaces an honest setup-required state. The caller is responsible for removing any
     * downloaded file first (see [OfflineModelRepository.removeModel]). No-op for built-in
     * or unknown IDs.
     */
    suspend fun removeCustomModel(modelId: String) {
        context.customOfflineModelDataStore.edit { preferences ->
            val existing = CustomOfflineModelStore.parse(preferences[customOfflineModelsKey])
            preferences[customOfflineModelsKey] =
                CustomOfflineModelStore.serialize(CustomOfflineModelStore.remove(existing, modelId))
        }
    }
}
