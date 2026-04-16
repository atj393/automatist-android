package com.automatist.app.domain.models

import org.junit.Assert.*
import org.junit.Test

class ProviderProfileTest {

    private fun profile(
        providerType: ProviderType = ProviderType.OPENAI,
        modelId: String = "gpt-4o",
        isDefault: Boolean = false,
        isFallback: Boolean = false,
        customBaseUrl: String = "",
        customApiKeyId: String = "",
        providerPresetId: String = ""
    ) = ProviderProfile(
        id = "test-id", name = "Test Profile", providerType = providerType,
        modelId = modelId, isDefault = isDefault, isFallback = isFallback,
        customBaseUrl = customBaseUrl, customApiKeyId = customApiKeyId,
        providerPresetId = providerPresetId
    )

    @Test
    fun `isCustomModel false for built-in models`() {
        assertFalse(profile(ProviderType.OPENAI, "gpt-4o").isCustomModel)
        assertFalse(profile(ProviderType.FAKE, "fake-demo").isCustomModel)
    }

    @Test
    fun `isCustomModel true for non-built-in`() {
        assertTrue(profile(ProviderType.OPENAI, "custom-model-123").isCustomModel)
    }

    @Test
    fun `OPENAI_COMPATIBLE always custom model`() {
        assertTrue(profile(ProviderType.OPENAI_COMPATIBLE, "llama-3-70b").isCustomModel)
    }

    @Test
    fun `usesPerProfileKey true when ID set`() {
        assertTrue(profile(customApiKeyId = "profile_abc").usesPerProfileKey)
        assertFalse(profile(customApiKeyId = "").usesPerProfileKey)
    }

    @Test
    fun `displayLabel uses catalog name when preset ID is set`() {
        val p = profile(ProviderType.OPENAI_COMPATIBLE, providerPresetId = "groq")
        assertTrue(p.displayLabel.contains("Groq"))
    }

    @Test
    fun `displayLabel falls back to ProviderType when no preset ID`() {
        val p = profile(ProviderType.OPENAI)
        assertTrue(p.displayLabel.contains("OpenAI"))
    }

    @Test
    fun `copy preserves all custom fields`() {
        val original = profile(ProviderType.OPENAI_COMPATIBLE, "model",
            customBaseUrl = "https://example.com", customApiKeyId = "key_123", providerPresetId = "groq")
        val copied = original.copy(name = "Updated")
        assertEquals("https://example.com", copied.customBaseUrl)
        assertEquals("key_123", copied.customApiKeyId)
        assertEquals("groq", copied.providerPresetId)
    }

    @Test
    fun `old OPENAI_COMPATIBLE profile without preset ID resolves via catalog`() {
        // Simulates an existing profile from the previous pass
        val oldProfile = profile(ProviderType.OPENAI_COMPATIBLE, "llama-3-70b",
            customBaseUrl = "https://api.groq.com/openai")
        val entry = ProviderCatalog.resolveForProfile(oldProfile)
        assertEquals("groq", entry.id)
    }
}
