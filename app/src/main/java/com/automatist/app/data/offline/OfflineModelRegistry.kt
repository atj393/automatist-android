package com.automatist.app.data.offline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.domain.offline.CustomOfflineModelInput
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelEntry
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customOfflineModelDataStore by preferencesDataStore(name = "custom_offline_models")
private val customOfflineModelsKey = stringPreferencesKey("models")

/**
 * Validates the narrow set of model sources Automatist can safely download.
 *
 * A custom model is data for the already bundled MediaPipe runtime. It must never
 * be an APK, native library, script, or another executable plugin.
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
    }

    fun requireHttpsUrl(value: String, label: String): HttpUrl {
        val url = value.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("$label must be a valid HTTPS URL.")
        require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()) {
            "$label must use HTTPS on the standard secure port."
        }
        return url
    }
}

/**
 * Persistent registry for user-added MediaPipe model sources.
 *
 * Built-in entries remain code-defined in [OfflineModelCatalog]. Custom entries
 * are stored as metadata only; model files themselves stay in app-private storage
 * and are managed by [ModelDownloadManager].
 */
@Singleton
class OfflineModelRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    val models: Flow<List<OfflineModelEntry>> =
        context.customOfflineModelDataStore.data.map { preferences ->
            OfflineModelCatalog.ALL_MODELS + readStoredModels(preferences[customOfflineModelsKey])
                .map { it.toEntry() }
        }

    suspend fun findById(modelId: String): OfflineModelEntry? =
        models.first().firstOrNull { it.id == modelId }

    suspend fun addCustomModel(input: CustomOfflineModelInput): OfflineModelEntry {
        CustomOfflineModelValidator.validate(input)
        val normalizedUrl = CustomOfflineModelValidator.requireHttpsUrl(input.modelUrl, "Model URL").toString()
        val normalizedLicenseUrl = CustomOfflineModelValidator.requireHttpsUrl(input.licenseUrl, "License URL").toString()
        val stored = StoredCustomOfflineModel(
            id = "custom-${UUID.randomUUID()}",
            displayName = input.displayName.trim(),
            downloadUrl = normalizedUrl,
            sha256 = input.sha256.trim().lowercase(),
            downloadSizeMb = input.downloadSizeMb,
            licenseUrl = normalizedLicenseUrl
        )

        context.customOfflineModelDataStore.edit { preferences ->
            val existing = readStoredModels(preferences[customOfflineModelsKey])
            require(existing.none { it.downloadUrl == stored.downloadUrl }) {
                "This model URL has already been added."
            }
            preferences[customOfflineModelsKey] = json.encodeToString(existing + stored)
        }
        return stored.toEntry()
    }

    private fun readStoredModels(raw: String?): List<StoredCustomOfflineModel> =
        raw?.let { runCatching { json.decodeFromString<List<StoredCustomOfflineModel>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    @Serializable
    private data class StoredCustomOfflineModel(
        val id: String,
        val displayName: String,
        val downloadUrl: String,
        val sha256: String,
        val downloadSizeMb: Int,
        val licenseUrl: String
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
            modelFileName = "$id.task",
            contextWindowChars = 2_500,
            minimumRamMb = 3_000
        )
    }
}
