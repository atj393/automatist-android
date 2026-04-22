package com.automatist.app.platform.onboarding

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultProfilePromoterTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var state: FakeSeedingStateStore
    private lateinit var promoter: DefaultProfilePromoter

    private fun fakeProfile(id: String, isDefault: Boolean = false) = ProviderProfile(
        id = id,
        name = "Local Fake Demo",
        providerType = ProviderType.FAKE,
        modelId = "fake-demo",
        isDefault = isDefault
    )

    private fun realProfile(
        id: String,
        providerType: ProviderType = ProviderType.OPENAI,
        enabled: Boolean = true
    ) = ProviderProfile(
        id = id,
        name = "$providerType profile",
        providerType = providerType,
        modelId = "gpt-4",
        isEnabled = enabled
    )

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        state = FakeSeedingStateStore()
        promoter = DefaultProfilePromoter(repo, state)
    }

    @Test
    fun `promotes new OpenAI profile when current default is the seeded fake`() = runTest {
        val fake = fakeProfile("fake-1", isDefault = true)
        repo.seedProfile(fake)
        state.fakeProfileId = "fake-1"

        val newProfile = realProfile("real-1", ProviderType.OPENAI)
        repo.seedProfile(newProfile)

        val promoted = promoter.maybePromoteOnCreate(newProfile, wasNewProfile = true)

        assertTrue("should promote", promoted)
        val default = repo.getDefaultProfile()
        assertEquals("real-1", default?.id)
        assertNull("seeded-fake marker should be cleared after promotion", state.fakeProfileId)
    }

    @Test
    fun `promotes LOCAL_AI profile same as cloud profile`() = runTest {
        val fake = fakeProfile("fake-1", isDefault = true)
        repo.seedProfile(fake)
        state.fakeProfileId = "fake-1"

        val local = realProfile("local-1", ProviderType.LOCAL_AI)
        repo.seedProfile(local)

        val promoted = promoter.maybePromoteOnCreate(local, wasNewProfile = true)

        assertTrue(promoted)
        assertEquals("local-1", repo.getDefaultProfile()?.id)
    }

    @Test
    fun `does not promote when user has already chosen a non-fake default`() = runTest {
        // User's explicit choice — must not be overridden.
        val userChoice = realProfile("user-choice", ProviderType.ANTHROPIC).copy(isDefault = true)
        repo.seedProfile(userChoice)
        // Seeded fake marker still points at a fake that's no longer the default.
        val oldFake = fakeProfile("fake-1")
        repo.seedProfile(oldFake)
        state.fakeProfileId = "fake-1"

        val newCloud = realProfile("openai-1", ProviderType.OPENAI)
        repo.seedProfile(newCloud)

        val promoted = promoter.maybePromoteOnCreate(newCloud, wasNewProfile = true)

        assertFalse("must not overwrite an explicit user choice", promoted)
        assertEquals("user-choice", repo.getDefaultProfile()?.id)
        assertEquals("seeded marker unchanged when we don't promote", "fake-1", state.fakeProfileId)
    }

    @Test
    fun `does not promote a new FAKE profile`() = runTest {
        val fake = fakeProfile("fake-1", isDefault = true)
        repo.seedProfile(fake)
        state.fakeProfileId = "fake-1"

        val anotherFake = fakeProfile("fake-2")
        repo.seedProfile(anotherFake)

        val promoted = promoter.maybePromoteOnCreate(anotherFake, wasNewProfile = true)

        assertFalse("never promote another FAKE as default", promoted)
        assertEquals("fake-1", repo.getDefaultProfile()?.id)
    }

    @Test
    fun `does not promote on profile edit`() = runTest {
        val fake = fakeProfile("fake-1", isDefault = true)
        repo.seedProfile(fake)
        state.fakeProfileId = "fake-1"

        val edited = realProfile("real-1", ProviderType.OPENAI)
        repo.seedProfile(edited)

        val promoted = promoter.maybePromoteOnCreate(edited, wasNewProfile = false)

        assertFalse("edits must not trigger auto-promote", promoted)
        assertEquals("fake-1", repo.getDefaultProfile()?.id)
    }

    @Test
    fun `does not promote when no seeded-fake marker is recorded`() = runTest {
        // Upgraded install: user has no seeded fake at all.
        val existingDefault = realProfile("existing", ProviderType.OPENAI).copy(isDefault = true)
        repo.seedProfile(existingDefault)
        state.fakeProfileId = null

        val newProfile = realProfile("gemini-1", ProviderType.GEMINI)
        repo.seedProfile(newProfile)

        val promoted = promoter.maybePromoteOnCreate(newProfile, wasNewProfile = true)

        assertFalse(promoted)
        assertEquals("existing", repo.getDefaultProfile()?.id)
    }

    @Test
    fun `does not promote a disabled profile`() = runTest {
        val fake = fakeProfile("fake-1", isDefault = true)
        repo.seedProfile(fake)
        state.fakeProfileId = "fake-1"

        val disabled = realProfile("real-1", ProviderType.OPENAI, enabled = false)
        repo.seedProfile(disabled)

        val promoted = promoter.maybePromoteOnCreate(disabled, wasNewProfile = true)

        assertFalse("disabled profiles are not usable — can't promote", promoted)
        assertEquals("fake-1", repo.getDefaultProfile()?.id)
    }
}
