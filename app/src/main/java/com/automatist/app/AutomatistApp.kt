package com.automatist.app

import android.app.Application
import android.util.Log
import com.automatist.app.data.billing.BillingManager
import com.automatist.app.domain.repositories.WorkflowRepository
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
            try {
                scheduleManager.reconcileSchedules {
                    workflowRepository.getAllTemplates().first()
                }
            } catch (e: Exception) {
                Log.e("AutomatistApp", "Schedule reconciliation error: ${e.message}", e)
            }
        }
    }
}
