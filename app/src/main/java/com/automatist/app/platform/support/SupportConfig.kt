package com.automatist.app.platform.support

/**
 * Support and contact URLs for Automatist.
 *
 * All URLs are external — no backend support ticketing system.
 * Support form and feedback are handled via web forms or email.
 */
object SupportConfig {
    private const val BASE_URL = "https://automatist.cloud"

    // ── Contact Support URLs ──
    const val CONTACT_SUPPORT_URL = "$BASE_URL/support"
    const val PRIORITY_SUPPORT_URL = "$BASE_URL/support?priority=true"

    // ── Feedback ──
    const val FEEDBACK_EMAIL = "feedback@automatist.cloud"

    // ── Legal & Info ──
    const val WEBSITE_URL = "https://automatist.cloud"
    const val PRIVACY_POLICY_URL = "$BASE_URL/privacy"
    const val TERMS_OF_USE_URL = "$BASE_URL/terms"

    /**
     * Build a support request pre-filled with diagnostic info.
     * This can be used to create an email or form submission.
     */
    fun buildSupportEmailBody(
        appVersion: String = "",
        deviceInfo: String = "",
        runId: Long? = null,
        errorMessage: String = ""
    ): String {
        return buildString {
            appendLine("Describe your issue here:")
            appendLine()
            appendLine("--- Diagnostic Information (please keep) ---")
            if (appVersion.isNotBlank()) appendLine("App Version: $appVersion")
            if (deviceInfo.isNotBlank()) appendLine("Device: $deviceInfo")
            if (runId != null && runId > 0) appendLine("Run ID: $runId")
            if (errorMessage.isNotBlank()) {
                appendLine("Error: $errorMessage")
            }
        }
    }
}
