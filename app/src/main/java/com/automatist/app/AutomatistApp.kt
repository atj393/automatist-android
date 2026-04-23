package com.automatist.app

import android.app.Application
import android.util.Log
import com.automatist.app.data.billing.BillingManager
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.onboarding.FirstRunSeeder
import com.automatist.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AutomatistApp : Application() {

    @Inject lateinit var scheduleManager: ScheduleManager
    @Inject lateinit var workflowRepository: WorkflowRepository
    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var firstRunSeeder: FirstRunSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Reconcile scheduled jobs on every app start.
        // This catches the case where the one-shot worker completed but was killed
        // before rescheduleNext() could enqueue the next occurrence (e.g. OOM, reboot
        // during execution, force-stop). WorkManager persists enqueued work across
        // reboots, but a consumed one-shot that didn't reschedule is lost.
        // Refresh billing entitlement on app start
        billingManager.queryOwnedPurchases()

        appScope.launch {
            // Seed first-run defaults before schedule reconciliation so a freshly-seeded
            // workflow is visible to the reconciler. Seeder is fully idempotent.
            try {
                firstRunSeeder.seedIfNeeded()
            } catch (e: Exception) {
                Log.e("AutomatistApp", "First-run seeding error: ${e.message}", e)
            }

            // Reconcile orphaned RUNNING rows from a prior process death. The
            // VM-driven run path has no worker-level crash recovery, so without
            // this sweep a killed run stays RUNNING forever. The 1-hour grace
            // window ensures we never clobber a fresh run that just happens to
            // coincide with an app restart.
            try {
                val cutoff = System.currentTimeMillis() - STALE_RUNNING_GRACE_MS
                workflowRepository.failAllStaleRunningRecordsOlderThan(cutoff)
            } catch (e: Exception) {
                Log.w("AutomatistApp", "Stale running reconciliation error: ${e.message}")
            }

            try {
                scheduleManager.reconcileSchedules {
                    workflowRepository.getAllTemplates().first()
                }
            } catch (e: Exception) {
                Log.e("AutomatistApp", "Schedule reconciliation error: ${e.message}", e)
            }
        }
    }

    private companion object {
        private const val STALE_RUNNING_GRACE_MS = 60L * 60L * 1000L // 1 hour
    }
}
