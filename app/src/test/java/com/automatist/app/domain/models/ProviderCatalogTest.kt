package com.automatist.app.domain.models

import org.junit.Assert.*
import org.junit.Test

class ProviderCatalogTest {

    @Test
    fun `catalog has at least 15 entries`() {
        assertTrue("Catalog should have at least 15 entries, has ${ProviderCatalog.ALL_ENTRIES.size}",
            ProviderCatalog.ALL_ENTRIES.size >= 15)
    }

    @Test
    fun `native providers are present`() {
        assertNotNull(ProviderCatalog.findById("openai"))
        assertNotNull(ProviderCatalog.findById("anthropic"))
        assertNotNull(ProviderCatalog.findById("gemini"))
        assertNotNull(ProviderCatalog.findById("fake"))
    }

    @Test
    fun `popular presets are present`() {
        assertNotNull(ProviderCatalog.findById("openrouter"))
        assertNotNull(ProviderCatalog.findById("groq"))
        assertNotNull(ProviderCatalog.findById("deepseek"))
        assertNotNull(ProviderCatalog.findById("together"))
        assertNotNull(ProviderCatalog.findById("fireworks"))
        assertNotNull(ProviderCatalog.findById("perplexity"))
        assertNotNull(ProviderCatalog.findById("mistral"))
        assertNotNull(ProviderCatalog.findById("ollama"))
    }

    @Test
    fun `custom entry exists`() {
        assertNotNull(ProviderCatalog.findById("custom"))
        assertEquals(CatalogCategory.CUSTOM, ProviderCatalog.CUSTOM_ENTRY.category)
    }

    @Test
    fun `native entries have NATIVE category`() {
        val openai = ProviderCatalog.findById("openai")!!
        assertEquals(CatalogCategory.NATIVE, openai.category)
        assertEquals(ProviderType.OPENAI, openai.runtimeType)
    }

    @Test
    fun `preset entries use OPENAI_COMPATIBLE runtime`() {
        val groq = ProviderCatalog.findById("groq")!!
        assertEquals(CatalogCategory.PRESET, groq.category)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, groq.runtimeType)
        assertTrue(groq.presetBaseUrl.isNotBlank())
        assertTrue(groq.usesPerProfileKey)
    }

    @Test
    fun `resolveForProfile matches native OpenAI`() {
        val profile = ProviderProfile(id = "1", name = "Test", providerType = ProviderType.OPENAI, modelId = "gpt-4o")
        val entry = ProviderCatalog.resolveForProfile(profile)
        assertEquals("openai", entry.id)
    }

    @Test
    fun `resolveForProfile matches by presetId`() {
        val profile = ProviderProfile(id = "1", name = "Test", providerType = ProviderType.OPENAI_COMPATIBLE,
            modelId = "llama-3.3-70b-versatile", customBaseUrl = "https://api.groq.com/openai",
            providerPresetId = "groq")
        val entry = ProviderCatalog.resolveForProfile(profile)
        assertEquals("groq", entry.id)
    }

    @Test
    fun `resolveForProfile matches by base URL when no preset ID`() {
        val profile = ProviderProfile(id = "1", name = "Test", providerType = ProviderType.OPENAI_COMPATIBLE,
            modelId = "sonar", customBaseUrl = "https://api.perplexity.ai")
        val entry = ProviderCatalog.resolveForProfile(profile)
        assertEquals("perplexity", entry.id)
    }

    @Test
    fun `resolveForProfile falls back to custom for unknown URL`() {
        val profile = ProviderProfile(id = "1", name = "Test", providerType = ProviderType.OPENAI_COMPATIBLE,
            modelId = "some-model", customBaseUrl = "https://my-custom-server.example.com")
        val entry = ProviderCatalog.resolveForProfile(profile)
        assertEquals("custom", entry.id)
    }

    @Test
    fun `all entries have unique IDs`() {
        val ids = ProviderCatalog.ALL_ENTRIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `preset entries have suggested models`() {
        val groq = ProviderCatalog.findById("groq")!!
        assertTrue(groq.suggestedModels.isNotEmpty())
        assertTrue(groq.defaultModel.isNotBlank())
    }

    @Test
    fun `no entry is labeled OpenAI-Compatible`() {
        ProviderCatalog.ALL_ENTRIES.forEach { entry ->
            assertFalse("Entry '${entry.displayName}' should not be labeled 'OpenAI-Compatible'",
                entry.displayName == "OpenAI-Compatible")
        }
    }
}
