package com.automatist.app.ui.navigation

import com.automatist.app.feature.dashboard.DashboardViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Architectural guards confirming the paid/Upgrade UI is gone after Phase 2.
 * These use reflection/classpath checks rather than brittle source-text scans.
 */
class PaidUiRemovedTest {

    private fun classExists(fqName: String): Boolean =
        try {
            Class.forName(fqName, false, this::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        }

    @Test
    fun `navigation Routes has no Upgrade route`() {
        val routeFields = Routes::class.java.declaredFields.map { it.name }
        assertFalse("Routes.UPGRADE must not exist", routeFields.contains("UPGRADE"))
        // Sanity anchor: real routes are still present.
        assertTrue(routeFields.contains("DASHBOARD"))
    }

    @Test
    fun `upgrade screen and view model classes are removed from the build`() {
        assertFalse(classExists("com.automatist.app.feature.upgrade.UpgradeViewModel"))
        assertFalse(classExists("com.automatist.app.feature.upgrade.UpgradeScreenKt"))
    }

    @Test
    fun `activation-limit and upgrade prompt dialogs are removed from the build`() {
        // ActivationLimitDialog and UpgradePromptDialog lived in UpgradePrompt.kt.
        assertFalse(classExists("com.automatist.app.feature.upgrade.UpgradePromptKt"))
    }

    @Test
    fun `dashboard no longer exposes a plan-badge state`() {
        val methods = DashboardViewModel::class.java.declaredMethods.map { it.name }
        assertFalse("DashboardViewModel must not expose planState", methods.contains("getPlanState"))
        // Sanity anchor: the ViewModel still exposes its real observational state.
        assertTrue(methods.contains("getRecentRuns"))
    }
}
