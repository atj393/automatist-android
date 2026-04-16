package com.automatist.app.domain.models

import org.junit.Assert.*
import org.junit.Test

class ProviderModelsTest {

    @Test
    fun `all built-in providers have at least one model`() {
        listOf(ProviderType.OPENAI, ProviderType.ANTHROPIC, ProviderType.GEMINI, ProviderType.FAKE).forEach { provider ->
            val models = ProviderModels.modelsFor(provider)
            assertTrue("$provider should have at least one model", models.isNotEmpty())
        }
    }

    @Test
    fun `OPENAI_COMPATIBLE has empty model list`() {
        assertTrue(ProviderModels.modelsFor(ProviderType.OPENAI_COMPATIBLE).isEmpty())
    }

    @Test
    fun `default model is in the model list for built-in providers`() {
        listOf(ProviderType.OPENAI, ProviderType.ANTHROPIC, ProviderType.GEMINI, ProviderType.FAKE).forEach { provider ->
            val defaultModel = ProviderModels.defaultModelFor(provider)
            val models = ProviderModels.modelsFor(provider)
            assertTrue("$provider default model '$defaultModel' should be in its model list",
                models.any { it.first == defaultModel })
        }
    }

    @Test
    fun `OPENAI_COMPATIBLE default model is empty`() {
        assertEquals("", ProviderModels.defaultModelFor(ProviderType.OPENAI_COMPATIBLE))
    }

    @Test
    fun `isBuiltIn returns true for known models`() {
        assertTrue(ProviderModels.isBuiltIn(ProviderType.OPENAI, "gpt-4o"))
        assertTrue(ProviderModels.isBuiltIn(ProviderType.ANTHROPIC, "claude-sonnet-4-20250514"))
        assertTrue(ProviderModels.isBuiltIn(ProviderType.GEMINI, "gemini-2.0-flash"))
        assertTrue(ProviderModels.isBuiltIn(ProviderType.FAKE, "fake-demo"))
    }

    @Test
    fun `isBuiltIn returns false for custom models`() {
        assertFalse(ProviderModels.isBuiltIn(ProviderType.OPENAI, "gpt-4-turbo-custom"))
        assertFalse(ProviderModels.isBuiltIn(ProviderType.ANTHROPIC, "claude-unknown"))
        assertFalse(ProviderModels.isBuiltIn(ProviderType.OPENAI_COMPATIBLE, "anything"))
    }

    @Test
    fun `BUILT_IN_PROVIDERS contains the right set`() {
        assertEquals(3, ProviderModels.BUILT_IN_PROVIDERS.size)
        assertTrue(ProviderModels.BUILT_IN_PROVIDERS.contains(ProviderType.OPENAI))
        assertTrue(ProviderModels.BUILT_IN_PROVIDERS.contains(ProviderType.ANTHROPIC))
        assertTrue(ProviderModels.BUILT_IN_PROVIDERS.contains(ProviderType.GEMINI))
        assertFalse(ProviderModels.BUILT_IN_PROVIDERS.contains(ProviderType.FAKE))
        assertFalse(ProviderModels.BUILT_IN_PROVIDERS.contains(ProviderType.OPENAI_COMPATIBLE))
    }
}
