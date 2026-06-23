package com.automatist.app.domain.offline

/**
 * Lifecycle state of a single offline AI model on this device.
 *
 * State transitions differ by [OfflineRuntimeType]:
 *
 * **AICORE (system-managed)**:
 *   NOT_INSTALLED → DOWNLOADING (checking AICore availability) → INSTALLED or UNSUPPORTED
 *                ↘ FAILED  (if AICore check throws)
 *
 * **DOWNLOADABLE (app-managed)**:
 *   NOT_INSTALLED → DOWNLOADING (HTTP download in progress) → INSTALLED or FAILED
 *   INSTALLED → NOT_INSTALLED (on user-triggered removal, file deleted)
 *   Any state → UNSUPPORTED (if device lacks minimum requirements)
 */
enum class OfflineModelStatus {
    /** Model has not been checked/downloaded or was removed. Default for new installs. */
    NOT_INSTALLED,
    /** Availability check or download in progress. */
    DOWNLOADING,
    /** Model is available and ready to use. */
    INSTALLED,
    /** Availability check or download failed due to an error. */
    FAILED,
    /** This device does not meet the minimum requirements for this model. */
    UNSUPPORTED
}

/**
 * Identifies the inference runtime used by an offline model.
 *
 * Each runtime has distinct availability-checking, download, and inference mechanics.
 * The [LocalAIArticleTransformProvider][com.automatist.app.data.providers.LocalAIArticleTransformProvider]
 * dispatches to the correct inference path based on this type.
 */
enum class OfflineRuntimeType {
    /** Android AICore system service (Gemini Nano). System-managed, no app download. */
    AICORE,
    /** App-managed downloadable model using MediaPipe LLM Inference. */
    DOWNLOADABLE
}

/**
 * Metadata for a single offline AI model option.
 *
 * Add new catalog entries to [OfflineModelCatalog.ALL_MODELS] to support
 * additional models in future releases — no core routing or profile logic changes required.
 *
 * @param isSystemManaged True if the model is managed by the Android system (e.g. AICore / Gemini Nano)
 *   rather than downloaded directly by this app. System-managed models cannot be manually removed.
 * @param runtimeType The inference runtime used by this model.
 * @param downloadUrl URL for downloading app-managed models. Null for system-managed models.
 * @param downloadSizeBytes Approximate download size in bytes. 0 for system-managed models.
 * @param fileSha256 SHA-256 hex digest of the downloaded model file for integrity verification.
 *   Null for system-managed models.
 * @param modelFileName File name used when storing the model in app-internal storage.
 *   Null for system-managed models.
 * @param contextWindowChars Maximum input characters this model can handle in the prompt.
 *   Used by the prompt builder to cap user content.
 */
data class OfflineModelEntry(
    /** Stable identifier used for storage keys and profile model IDs. */
    val id: String,
    /** User-facing model name. */
    val displayName: String,
    /** Short description shown in the On-device AI settings section. */
    val description: String,
    /** Human-readable size/availability hint (e.g. "~1.2 GB" or "System-managed"). */
    val sizeLabel: String,
    /** True if this model supports text-only tasks. */
    val isTextOnly: Boolean = true,
    /** Minimum Android API level required to run this model. */
    val minimumAndroidApiLevel: Int = 26,
    /** Optional tags displayed as chips (e.g. "Google", "On-device", "Downloadable"). */
    val tags: List<String> = emptyList(),
    /**
     * True if the model is managed by Android system services (AICore).
     * System-managed models are checked for availability rather than downloaded.
     * The model binary is owned and updated by the OS, not by this app.
     */
    val isSystemManaged: Boolean = false,
    /** The inference runtime used by this model. */
    val runtimeType: OfflineRuntimeType = OfflineRuntimeType.AICORE,
    /** URL for downloading app-managed models. Null for system-managed models. */
    val downloadUrl: String? = null,
    /** Approximate download size in bytes. 0 for system-managed models. */
    val downloadSizeBytes: Long = 0L,
    /** SHA-256 hex digest for integrity verification. Null for system-managed models. */
    val fileSha256: String? = null,
    /** File name used in app-internal storage. Null for system-managed models. */
    val modelFileName: String? = null,
    /** Maximum input characters for prompt building. Defaults to Gemini Nano's limit. */
    val contextWindowChars: Int = 3_000,
    /** Minimum device RAM in MB required to run this model. 0 means no check. */
    val minimumRamMb: Int = 0,
    /**
     * True if this entry was added by the user from an external source rather than
     * being one of the code-defined built-in catalog entries. User-added entries can
     * have their source metadata removed; built-in entries cannot. Always false for
     * [OfflineModelCatalog.ALL_MODELS].
     */
    val isUserAdded: Boolean = false
)

/**
 * User-provided metadata for a MediaPipe-compatible downloadable model.
 *
 * The model file itself remains private to this app and is executed only by the
 * MediaPipe runtime already shipped with Automatist. The checksum and license URL
 * are required so a source cannot silently change after the user has reviewed it.
 */
data class CustomOfflineModelInput(
    val displayName: String,
    val modelUrl: String,
    val sha256: String,
    val downloadSizeMb: Int,
    val licenseUrl: String
)

/**
 * Static catalog of offline AI models available in this release.
 *
 * Contains two models:
 * - **Gemini Nano**: system-managed via Android AICore (Pixel 8+, Android 14+)
 * - **Gemma 3 1B (int4)**: app-managed downloadable via MediaPipe LLM Inference (broader device support)
 *
 * Extensibility: to add a future model, insert a new [OfflineModelEntry] into [ALL_MODELS]
 * and add the model ID to [ProviderModels.LOCAL_AI][com.automatist.app.domain.models.ProviderModels].
 * No changes to routing, profile, or readiness infrastructure are required.
 */
object OfflineModelCatalog {

    /** Stable ID for the Gemini Nano on-device model (available via Android AICore). */
    const val GEMINI_NANO_ID = "gemini-nano"

    /**
     * Stable ID for the downloadable offline model. Historical value kept for
     * compatibility with existing user profiles and storage keys; the model it
     * refers to is actually Gemma 3 1B int4, not Gemma 3n E2B. See [DOWNLOADABLE_MODEL_ID_ALIAS].
     */
    const val GEMMA_3N_E2B_ID = "gemma-3n-e2b"

    /** Preferred alias for the same downloadable entry — use in new code. */
    const val DOWNLOADABLE_MODEL_ID_ALIAS = GEMMA_3N_E2B_ID

    /**
     * All offline models available in this release.
     * New entries can be added here for future releases without touching core logic.
     */
    val ALL_MODELS: List<OfflineModelEntry> = listOf(
        OfflineModelEntry(
            id = GEMINI_NANO_ID,
            displayName = "Gemini Nano",
            description = "Google's on-device AI model, available on select Pixel and Galaxy devices " +
                "running Android 14+. Managed by Android system services via AICore — " +
                "no manual download required. Runs fully offline without an internet connection or API key.",
            sizeLabel = "System-managed",
            isTextOnly = true,
            minimumAndroidApiLevel = 34,
            tags = listOf("Google", "On-device", "Android 14+"),
            isSystemManaged = true,
            runtimeType = OfflineRuntimeType.AICORE,
            contextWindowChars = 3_000
        ),
        OfflineModelEntry(
            id = GEMMA_3N_E2B_ID,
            // NAME HONESTY: the bundled file is gemma3-1b-it-int4.task (Gemma 3 1B int4),
            // not the larger Gemma 3n E2B. Display name now matches the file.
            displayName = "Gemma 3 1B (int4)",
            description = "Compact 1B-parameter offline AI model (int4-quantized). Download once, " +
                "then run fully offline — no internet or API key required. Best for quick " +
                "summaries and bullet points on most modern Android devices.",
            sizeLabel = "~529 MB download",
            isTextOnly = true,
            minimumAndroidApiLevel = 26,
            tags = listOf("Google", "Downloadable", "Offline"),
            isSystemManaged = false,
            runtimeType = OfflineRuntimeType.DOWNLOADABLE,
            downloadUrl = "https://github.com/atj393/automatist-models/releases/download/offline-models-v1/gemma3-1b-it-int4.task",
            downloadSizeBytes = 554_661_243L, // ~529 MB
            fileSha256 = "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee",
            modelFileName = "gemma3-1b-it-int4.task",
            // Reduced from 4000 → 2500 chars to keep prefill fast on a 1B model running
            // on mobile CPU. Generation is the main cost; a smaller context reduces
            // prefill latency and leaves more of the 120s budget for generation.
            contextWindowChars = 2_500,
            minimumRamMb = 3_000 // 3 GB minimum RAM
        )
    )

    /** Look up a model entry by its stable ID. Returns null if not found. */
    fun findById(id: String): OfflineModelEntry? = ALL_MODELS.find { it.id == id }
}
