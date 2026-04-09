package com.synapse.app.domain.engine

/**
 * Exception that carries both a human-readable message and raw technical detail.
 * Used by providers and actions to preserve the original error for debugging,
 * while still providing a friendly message for the UI.
 */
class DiagnosticException(
    message: String,
    val rawDetail: String = "",
    val httpStatus: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Redacts sensitive values from error strings before display or persistence.
 * Removes API keys, bearer tokens, and auth headers while keeping the error useful.
 */
object ErrorRedactor {

    /** Max characters to persist in the errorDetail DB column. */
    const val MAX_PERSISTED_LENGTH = 4000

    private val PATTERNS = listOf(
        // OpenAI keys: sk-proj-..., sk-...
        Regex("""(sk-[a-zA-Z0-9_-]{2})[a-zA-Z0-9_-]{10,}""") to "$1****",
        // Generic long hex/alphanum tokens (32+ chars, likely keys)
        Regex("""(["']?)([a-zA-Z0-9]{32,})\1""") to "$1****REDACTED****$1",
        // Bearer tokens in headers
        Regex("""(Bearer\s+)\S+""", RegexOption.IGNORE_CASE) to "$1****",
        // Authorization header values
        Regex("""(Authorization:\s*)\S+""", RegexOption.IGNORE_CASE) to "$1****",
        // x-api-key header values
        Regex("""(x-api-key:\s*)\S+""", RegexOption.IGNORE_CASE) to "$1****",
        // appid= query parameter (OpenWeatherMap)
        Regex("""(appid=)[^&\s"']+""") to "$1****",
        // api_key= query parameter (ORS)
        Regex("""(api_key=)[^&\s"']+""") to "$1****",
        // key= query parameter (Gemini)
        Regex("""(\bkey=)[^&\s"']+""") to "$1****"
    )

    fun redact(input: String): String {
        var result = input
        for ((pattern, replacement) in PATTERNS) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    /**
     * Redact and truncate for safe DB persistence.
     * Returns null if input is blank.
     */
    fun redactForStorage(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val redacted = redact(input)
        return if (redacted.length <= MAX_PERSISTED_LENGTH) {
            redacted
        } else {
            redacted.take(MAX_PERSISTED_LENGTH - 30) + "\n\n[truncated — ${redacted.length} chars total]"
        }
    }
}
