package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fence for the "disable = delete" bug in the AI Profiles section.
 *
 * Before the fix, [com.automatist.app.data.local.WorkflowDao.getAllProfiles]
 * was scoped `WHERE isEnabled = 1`. Toggling a profile off in Settings
 * invoked `repository.saveProfile(p.copy(isEnabled = false))`, which left
 * the row in Room but the next emission from `getAllProfiles()` filtered
 * it out — so the card disappeared from the list and the user had no way
 * to toggle it back on short of recreating the profile.
 *
 * The fix removes the filter from the DAO; consumers that need "only
 * usable profiles" (the editor's ProfilePicker, the default/fallback
 * selector) filter client-side on `isEnabled`. These tests lock in the
 * repository-layer contract the UI now depends on.
 */
class ProfileToggleTest {

    private fun cloudProfile(
        id: String = "p1",
        name: String = "My OpenAI",
        isDefault: Boolean = false,
        isFallback: Boolean = false,
        isEnabled: Boolean = true
    ) = ProviderProfile(
        id = id,
        name = name,
        providerType = ProviderType.OPENAI,
        modelId = "gpt-4o-mini",
        isDefault = isDefault,
        isFallback = isFallback,
        isEnabled = isEnabled
    )

    // ── Toggle off: the row survives, just flips isEnabled ──

    @Test
    fun `disabling a profile keeps the row stored and visible to the management list`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile())

        // Simulate the VM's toggle path.
        repo.saveProfile(cloudProfile(isEnabled = false))

        val all = repo.getAllProfiles().first()
        assertEquals(1, all.size)
        val stored = all.single()
        assertEquals("p1", stored.id)
        assertFalse("toggle off should flip isEnabled, not remove the row", stored.isEnabled)
        assertEquals("original provider/model must survive the toggle", ProviderType.OPENAI, stored.providerType)
        assertEquals("gpt-4o-mini", stored.modelId)

        // And the row is still fetchable by id — no silent deletion.
        assertNotNull(repo.getProfileById("p1"))
    }

    @Test
    fun `re-enabling a previously disabled profile restores it without losing fields`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile())

        repo.saveProfile(cloudProfile(isEnabled = false))
        repo.saveProfile(cloudProfile(isEnabled = true))

        val stored = repo.getProfileById("p1")!!
        assertTrue("re-enable should make the profile usable again", stored.isEnabled)
        assertEquals(ProviderType.OPENAI, stored.providerType)
        assertEquals("gpt-4o-mini", stored.modelId)
    }

    // ── Delete remains the only destructive action ──

    @Test
    fun `deleting a profile actually removes it from the management list`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile())

        repo.deleteProfile("p1")

        val all = repo.getAllProfiles().first()
        assertTrue("delete must remove the row — this is the only destructive action", all.isEmpty())
        assertNull(repo.getProfileById("p1"))
    }

    @Test
    fun `disabling then deleting both work in sequence without fighting each other`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile())

        repo.saveProfile(cloudProfile(isEnabled = false))
        assertNotNull("disabled profile still stored before delete", repo.getProfileById("p1"))

        repo.deleteProfile("p1")
        assertNull("delete after disable still removes the row", repo.getProfileById("p1"))
    }

    // ── UI-layer contract: disabled rows reach the list, picker-side filter excludes them ──

    @Test
    fun `management list includes both enabled and disabled profiles`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(id = "enabled", name = "Enabled"))
        repo.seedProfile(cloudProfile(id = "disabled", name = "Disabled", isEnabled = false))

        val all = repo.getAllProfiles().first()
        val ids = all.map { it.id }.toSet()
        assertEquals(
            "both enabled and disabled profiles must show up in the Settings list — " +
                "that's what makes a disabled profile recoverable without recreation",
            setOf("enabled", "disabled"),
            ids
        )
    }

    @Test
    fun `active-use filter (isEnabled) correctly excludes disabled profiles from the picker subset`() = runBlocking {
        // The UI pattern is: Settings shows all from the repo, but the
        // ProfilePicker / default-selector filters with `it.isEnabled`.
        // This test documents that invariant at the domain layer so a
        // future UI refactor that forgets to filter at the call site
        // can be spotted via this contract.
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(id = "a", name = "A", isEnabled = true))
        repo.seedProfile(cloudProfile(id = "b", name = "B", isEnabled = false))
        repo.seedProfile(cloudProfile(id = "c", name = "C", isEnabled = true))

        val all = repo.getAllProfiles().first()
        val usable = all.filter { it.isEnabled }.map { it.id }.toSet()
        assertEquals(setOf("a", "c"), usable)
    }

    // ── Default / fallback safety when their profile is disabled ──

    @Test
    fun `disabling the current default keeps the isDefault flag but surfaces as unusable`() = runBlocking {
        // Product decision: keep the stored assignment (so re-enabling
        // immediately restores it) but rely on readiness/checks to block
        // execution. This matches the existing readiness behaviour that
        // says "App default profile is disabled. Enable it or choose a
        // different default."
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(isDefault = true))

        repo.saveProfile(cloudProfile(isDefault = true, isEnabled = false))

        val stored = repo.getProfileById("p1")!!
        assertTrue(
            "isDefault assignment stays so re-enabling restores it without requiring the user to re-pick the default",
            stored.isDefault
        )
        assertFalse(stored.isEnabled)
        // And the repo's getDefaultProfile() returns the disabled default
        // rather than null — consumers (ReadinessEvaluator) handle the
        // "disabled default" case explicitly.
        val default = repo.getDefaultProfile()
        assertNotNull(default)
        assertFalse("the retrieved default is not actively usable while disabled", default!!.isEnabled)
    }

    @Test
    fun `disabling the current fallback keeps the isFallback flag`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(isFallback = true))

        repo.saveProfile(cloudProfile(isFallback = true, isEnabled = false))

        val stored = repo.getProfileById("p1")!!
        assertTrue(stored.isFallback)
        assertFalse(stored.isEnabled)
    }

    @Test
    fun `disabling a profile does not promote another profile to default`() = runBlocking {
        // Regression guard: the toggle path must not trigger any cascading
        // reassignment. If a user disables a default profile they should
        // see it surfaced as disabled (and re-pickable), not silently
        // replaced by some heuristic choice.
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(id = "a", isDefault = true))
        repo.seedProfile(cloudProfile(id = "b", isDefault = false))

        repo.saveProfile(cloudProfile(id = "a", isDefault = true, isEnabled = false))

        val all = repo.getAllProfiles().first()
        assertEquals(
            "only the toggled profile changes",
            mapOf("a" to true, "b" to false),
            all.associate { it.id to it.isDefault }
        )
    }

    // ── Many profiles: disabling one doesn't touch the others ──

    @Test
    fun `toggling one profile off does not affect siblings`() = runBlocking {
        val repo = FakeWorkflowRepository()
        repo.seedProfile(cloudProfile(id = "a", name = "Alpha"))
        repo.seedProfile(cloudProfile(id = "b", name = "Beta"))
        repo.seedProfile(cloudProfile(id = "c", name = "Gamma"))

        repo.saveProfile(cloudProfile(id = "b", name = "Beta", isEnabled = false))

        val all = repo.getAllProfiles().first()
        val enabledById = all.associate { it.id to it.isEnabled }
        assertEquals(
            mapOf("a" to true, "b" to false, "c" to true),
            enabledById
        )
    }
}
