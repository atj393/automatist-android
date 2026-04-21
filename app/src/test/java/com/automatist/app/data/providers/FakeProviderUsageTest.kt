package com.automatist.app.data.providers

import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.TransformType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Fake provider's "token" counts are a char-based simulation, not a real
 * tokeniser. It must flag its results as estimated so the UI doesn't claim
 * exact counts. Also verifies that chars are populated so downstream UI can
 * render them in the Usage section alongside local-model output.
 */
class FakeProviderUsageTest {

    @Test
    fun `fake provider marks its usage as estimated`() = runTest {
        val provider = FakeArticleTransformProvider()
        val result = provider.transform(
            ArticleInput(text = "some short article text"),
            TransformType.SUMMARY
        ).getOrThrow()

        assertTrue(
            "fake provider's simulated counts must be flagged isUsageEstimated = true",
            result.isUsageEstimated
        )
    }

    @Test
    fun `fake provider reports inputChars and outputChars`() = runTest {
        val input = "Hello world. ".repeat(20)
        val provider = FakeArticleTransformProvider()
        val result = provider.transform(
            ArticleInput(text = input),
            TransformType.SUMMARY
        ).getOrThrow()

        assertEquals(input.length, result.inputChars)
        assertNotNull("outputChars must not be null", result.outputChars)
        assertTrue("outputChars should match returned output length",
            result.outputChars == result.outputText.length)
    }
}
