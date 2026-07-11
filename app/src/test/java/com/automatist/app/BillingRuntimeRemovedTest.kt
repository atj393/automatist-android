package com.automatist.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Architectural guards confirming Google Play Billing and the product-access
 * entitlement layer are fully removed from the build (Phase 3). These use
 * classpath/reflection checks rather than brittle source-text scans; the release
 * dependency inspection and merged manifest cover the packaged artifact.
 */
class BillingRuntimeRemovedTest {

    private fun classExists(fqName: String): Boolean =
        try {
            Class.forName(fqName, false, this::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        }

    @Test
    fun `google play billing classes are absent from the classpath`() {
        assertFalse(classExists("com.android.billingclient.api.BillingClient"))
        assertFalse(classExists("com.automatist.app.data.billing.BillingManager"))
    }

    @Test
    fun `product-access entitlement classes are absent`() {
        assertFalse(classExists("com.automatist.app.data.access.BillingProductAccessRepository"))
        assertFalse(classExists("com.automatist.app.data.access.LocalProductAccessRepository"))
        assertFalse(classExists("com.automatist.app.domain.access.ProductAccessRepository"))
        assertFalse(classExists("com.automatist.app.domain.access.PlanState"))
        assertFalse(classExists("com.automatist.app.di.AccessModule"))
    }

    @Test
    fun `app no longer injects a BillingManager at startup`() {
        val fields = AutomatistApp::class.java.declaredFields.map { it.name }
        assertFalse("AutomatistApp must not inject billingManager", fields.contains("billingManager"))
        // Sanity anchor: the app still injects its real dependencies.
        assertTrue(fields.contains("scheduleManager"))
        assertTrue(fields.contains("firstRunSeeder"))
    }
}
