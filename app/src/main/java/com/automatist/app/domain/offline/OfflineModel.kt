package com.automatist.app.domain.offline

/**
 * Lifecycle state of a single offline AI model on this device.
 *
 * For AICore / Gemini Nano, state transitions work as follows:
 *   NOT_INSTALLED → DOWNLOADING (checking AICore availability) → INSTALLED or UNSUPPORTED
 *                ↘ FAILED  (if AICore check throws)
 *   INSTALLED → NOT_INSTALLED (on status reset)
 *   Any state → UNSUPPORTED (if device is below Android 14 or not AICore-capable)
 *
 * Note: For system-managed models (Gemini Nano via AICore), DOWNLOADING represents
 * "checking system availability" rather than a file download operation.
 */
enum class OfflineModelStatus {
    /** Model has not been checked or is not yet available. Default for new installs. */
    NOT_INSTALLED,
    /** Availability check in progress. For AICore: checking system service status. */
    DOWNLOADING,
    /** Model is available and ready to use. For AICore: Gemini Nano confirmed available. */
    INSTALLED,
    /** Availability check failed due to an unexpected error. */
    FAILED,
    /** This device does not meet the requirements for on-device AI (Android 14+ / AICore-capable hardware). */
    UNSUPPORTED
}

/**
 * Metadata for a single offline AI model option.
 *
 * Add new catalog entries to [OfflineModelCatalog.ALL_MODELS] to support
 * additional models in future releases — no core routing or profile logic changes required.
 *
 * @param isSystemManaged True if the model is managed by the Android system (e.g. AICore / Gemini Nano)
 *   rather than downloaded directly by this app. System-managed models cannot be manually removed.
 */
data class OfflineModelEntry(
    /** Stable identifier used for storage keys and profile model IDs. */
    val id: String,
    /** User-facing model name. */
    val displayName: String,
    /** Short description shown in the On-device AI settings section. */
    val description: String,
    /** Human-readable size/availability hint (e.g. "~800 MB" or "System-managed"). */
    val sizeLabel: String,
    /** True if this model supports text-only tasks. */
    val isTextOnly: Boolean = true,
    /** Minimum Android API level required to run this model. */
    val minimumAndroidApiLevel: Int = 26,
    /** Optional tags displayed as chips (e.g. "Google", "Android 14+"). */
    val tags: List<String> = emptyList(),
    /**
     * True if the model is managed by Android system services (AICore).
     * System-managed models are checked for availability rather than downloaded.
     * The model binary is owned and updated by the OS, not by this app.
     */
    val isSystemManaged: Boolean = false
)

/**
 * Static catalog of offline AI models available in this release.
 *
 * Currently contains exactly one model: Gemini Nano, accessed via Android AICore.
 *
 * Extensibility: to add a future model, insert a new [OfflineModelEntry] into [ALL_MODELS].
 * No changes to the routing, profile, or readiness infrastructure are required.
 */
object OfflineModelCatalog {

    /** Stable ID for the Gemini Nano on-device model (available via Android AICore). */
    const val GEMINI_NANO_ID = "gemini-nano"

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
            minimumAndroidApiLevel = 34, // Android 14 required for AICore
            tags = listOf("Google", "On-device", "Android 14+"),
            isSystemManaged = true
        )
    )

    /** Look up a model entry by its stable ID. Returns null if not found. */
    fun findById(id: String): OfflineModelEntry? = ALL_MODELS.find { it.id == id }
}
