package com.automatist.app.domain.models

/**
 * A provider catalog entry represents a named provider option visible in the profile editor.
 * It maps a user-friendly provider name to the actual runtime [ProviderType] and optional
 * preset configuration (base URL, suggested models).
 *
 * - Native entries use a true dedicated adapter (OpenAI, Anthropic, Gemini, Fake).
 * - Preset entries use the OPENAI_COMPATIBLE runtime adapter with a preset base URL.
 * - The Custom entry uses OPENAI_COMPATIBLE with fully user-configured fields.
 */
data class CatalogEntry(
    val id: String,                              // stable identifier, stored in profile
    val displayName: String,                     // user-facing name
    val description: String = "",                // short context line
    val runtimeType: ProviderType,               // which adapter runs it
    val presetBaseUrl: String = "",              // pre-filled base URL (empty for native/custom)
    val suggestedModels: List<Pair<String, String>> = emptyList(), // id to display name
    val defaultModel: String = "",               // pre-selected model
    val category: CatalogCategory = CatalogCategory.PRESET,
    val usesPerProfileKey: Boolean = false        // true if key is stored per-profile (all non-native)
)

enum class CatalogCategory {
    NATIVE,    // true built-in adapter (cloud API)
    PRESET,    // known provider using OPENAI_COMPATIBLE adapter
    OFFLINE,   // on-device model, no internet or API key required
    CUSTOM     // fully user-configured
}

object ProviderCatalog {

    // ── Native providers (dedicated adapters) ──

    private val OPENAI_ENTRY = CatalogEntry(
        id = "openai", displayName = "OpenAI", description = "GPT-4o, GPT-4.1, o3 and more",
        runtimeType = ProviderType.OPENAI, category = CatalogCategory.NATIVE,
        suggestedModels = ProviderModels.OPENAI, defaultModel = ProviderModels.defaultModelFor(ProviderType.OPENAI)
    )

    private val ANTHROPIC_ENTRY = CatalogEntry(
        id = "anthropic", displayName = "Anthropic", description = "Claude Opus 4, Sonnet, Haiku",
        runtimeType = ProviderType.ANTHROPIC, category = CatalogCategory.NATIVE,
        suggestedModels = ProviderModels.ANTHROPIC, defaultModel = ProviderModels.defaultModelFor(ProviderType.ANTHROPIC)
    )

    private val GEMINI_ENTRY = CatalogEntry(
        id = "gemini", displayName = "Google Gemini", description = "Gemini 2.5, 2.0, 1.5 models",
        runtimeType = ProviderType.GEMINI, category = CatalogCategory.NATIVE,
        suggestedModels = ProviderModels.GEMINI, defaultModel = ProviderModels.defaultModelFor(ProviderType.GEMINI)
    )

    private val FAKE_ENTRY = CatalogEntry(
        id = "fake", displayName = "Local Demo", description = "Sample output, no API key needed",
        runtimeType = ProviderType.FAKE, category = CatalogCategory.NATIVE,
        suggestedModels = ProviderModels.FAKE, defaultModel = "fake-demo"
    )

    // ── Popular preset providers (OPENAI_COMPATIBLE runtime) ──

    private val OPENROUTER_ENTRY = CatalogEntry(
        id = "openrouter", displayName = "OpenRouter", description = "Access hundreds of models via one API",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://openrouter.ai/api",
        suggestedModels = listOf(
            "openai/gpt-4o" to "GPT-4o (via OpenRouter)",
            "anthropic/claude-sonnet-4" to "Claude Sonnet 4 (via OpenRouter)",
            "meta-llama/llama-4-maverick" to "Llama 4 Maverick",
            "google/gemini-2.5-flash-preview" to "Gemini 2.5 Flash"
        ),
        defaultModel = "openai/gpt-4o", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val GROQ_ENTRY = CatalogEntry(
        id = "groq", displayName = "Groq", description = "Ultra-fast inference for open models",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.groq.com/openai",
        suggestedModels = listOf(
            "llama-3.3-70b-versatile" to "Llama 3.3 70B",
            "llama-3.1-8b-instant" to "Llama 3.1 8B Instant",
            "mixtral-8x7b-32768" to "Mixtral 8x7B",
            "gemma2-9b-it" to "Gemma 2 9B"
        ),
        defaultModel = "llama-3.3-70b-versatile", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val DEEPSEEK_ENTRY = CatalogEntry(
        id = "deepseek", displayName = "DeepSeek", description = "High-performance reasoning models",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.deepseek.com",
        suggestedModels = listOf(
            "deepseek-chat" to "DeepSeek Chat (V3)",
            "deepseek-reasoner" to "DeepSeek Reasoner (R1)"
        ),
        defaultModel = "deepseek-chat", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val TOGETHER_ENTRY = CatalogEntry(
        id = "together", displayName = "Together AI", description = "Open-source models at scale",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.together.xyz",
        suggestedModels = listOf(
            "meta-llama/Llama-3.3-70B-Instruct-Turbo" to "Llama 3.3 70B Turbo",
            "mistralai/Mixtral-8x7B-Instruct-v0.1" to "Mixtral 8x7B",
            "Qwen/Qwen2.5-72B-Instruct-Turbo" to "Qwen 2.5 72B Turbo"
        ),
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val FIREWORKS_ENTRY = CatalogEntry(
        id = "fireworks", displayName = "Fireworks AI", description = "Fast inference, open models",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.fireworks.ai/inference",
        suggestedModels = listOf(
            "accounts/fireworks/models/llama-v3p3-70b-instruct" to "Llama 3.3 70B",
            "accounts/fireworks/models/mixtral-8x7b-instruct" to "Mixtral 8x7B"
        ),
        defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val PERPLEXITY_ENTRY = CatalogEntry(
        id = "perplexity", displayName = "Perplexity", description = "Search-augmented AI models",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.perplexity.ai",
        suggestedModels = listOf(
            "sonar-pro" to "Sonar Pro",
            "sonar" to "Sonar",
            "sonar-reasoning-pro" to "Sonar Reasoning Pro"
        ),
        defaultModel = "sonar", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val MISTRAL_ENTRY = CatalogEntry(
        id = "mistral", displayName = "Mistral AI", description = "Mistral, Mixtral, Codestral models",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.mistral.ai",
        suggestedModels = listOf(
            "mistral-large-latest" to "Mistral Large",
            "mistral-small-latest" to "Mistral Small",
            "open-mixtral-8x22b" to "Mixtral 8x22B"
        ),
        defaultModel = "mistral-small-latest", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val OLLAMA_ENTRY = CatalogEntry(
        id = "ollama", displayName = "Ollama (Local)", description = "Run models locally on your network",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "http://localhost:11434",
        suggestedModels = listOf(
            "llama3.2" to "Llama 3.2",
            "mistral" to "Mistral 7B",
            "gemma2" to "Gemma 2"
        ),
        defaultModel = "llama3.2", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    private val LM_STUDIO_ENTRY = CatalogEntry(
        id = "lm_studio", displayName = "LM Studio (Local)", description = "Local inference server",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "http://localhost:1234",
        suggestedModels = emptyList(), defaultModel = "",
        usesPerProfileKey = true, category = CatalogCategory.PRESET
    )

    private val ANYSCALE_ENTRY = CatalogEntry(
        id = "anyscale", displayName = "Anyscale", description = "Scalable open model endpoints",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, presetBaseUrl = "https://api.endpoints.anyscale.com",
        suggestedModels = listOf(
            "meta-llama/Llama-3-70b-chat-hf" to "Llama 3 70B Chat"
        ),
        defaultModel = "meta-llama/Llama-3-70b-chat-hf", usesPerProfileKey = true,
        category = CatalogCategory.PRESET
    )

    // ── On-device / Offline provider ──

    /**
     * On-device AI catalog entry. Uses the LOCAL_AI runtime adapter.
     * No API key or internet connection required.
     * Availability must be confirmed via Settings → On-device AI before use.
     * Add future offline models to ProviderModels.LOCAL_AI and OfflineModelCatalog.ALL_MODELS.
     */
    val LOCAL_AI_ENTRY = CatalogEntry(
        id = "local_ai",
        displayName = "On-device AI",
        description = "Run AI fully offline — no internet or API key required",
        runtimeType = ProviderType.LOCAL_AI,
        category = CatalogCategory.OFFLINE,
        suggestedModels = ProviderModels.LOCAL_AI,
        defaultModel = ProviderModels.defaultModelFor(ProviderType.LOCAL_AI),
        usesPerProfileKey = false
    )

    // ── Custom provider (fully user-configured) ──

    val CUSTOM_ENTRY = CatalogEntry(
        id = "custom", displayName = "Custom Provider", description = "Enter your own endpoint URL, model, and key",
        runtimeType = ProviderType.OPENAI_COMPATIBLE, category = CatalogCategory.CUSTOM,
        usesPerProfileKey = true
    )

    // ── Full catalog ──

    val ALL_ENTRIES: List<CatalogEntry> = listOf(
        // Native
        OPENAI_ENTRY, ANTHROPIC_ENTRY, GEMINI_ENTRY,
        // Popular presets
        OPENROUTER_ENTRY, GROQ_ENTRY, DEEPSEEK_ENTRY, TOGETHER_ENTRY,
        FIREWORKS_ENTRY, PERPLEXITY_ENTRY, MISTRAL_ENTRY,
        // Local network
        OLLAMA_ENTRY, LM_STUDIO_ENTRY,
        // Other
        ANYSCALE_ENTRY,
        // Demo
        FAKE_ENTRY,
        // On-device (offline)
        LOCAL_AI_ENTRY,
        // Custom
        CUSTOM_ENTRY
    )

    fun findById(id: String): CatalogEntry? = ALL_ENTRIES.find { it.id == id }

    /**
     * Resolve which catalog entry best matches an existing profile.
     * Used when opening an existing profile for editing.
     */
    fun resolveForProfile(profile: ProviderProfile): CatalogEntry {
        // If profile has a preset ID stored, use it
        if (profile.providerPresetId.isNotBlank()) {
            findById(profile.providerPresetId)?.let { return it }
        }

        // Match native and offline providers by runtime type
        if (profile.providerType != ProviderType.OPENAI_COMPATIBLE) {
            return when (profile.providerType) {
                ProviderType.OPENAI -> OPENAI_ENTRY
                ProviderType.ANTHROPIC -> ANTHROPIC_ENTRY
                ProviderType.GEMINI -> GEMINI_ENTRY
                ProviderType.FAKE -> FAKE_ENTRY
                ProviderType.LOCAL_AI -> LOCAL_AI_ENTRY
                else -> CUSTOM_ENTRY
            }
        }

        // For OPENAI_COMPATIBLE, try matching by base URL to a preset
        if (profile.customBaseUrl.isNotBlank()) {
            val normalizedUrl = profile.customBaseUrl.trimEnd('/')
            ALL_ENTRIES.filter { it.category == CatalogCategory.PRESET }.forEach { entry ->
                if (entry.presetBaseUrl.isNotBlank() && normalizedUrl.startsWith(entry.presetBaseUrl.trimEnd('/'))) {
                    return entry
                }
            }
        }

        return CUSTOM_ENTRY
    }
}
