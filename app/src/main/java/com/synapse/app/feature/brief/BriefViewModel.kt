package com.synapse.app.feature.brief

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.synapse.app.data.local.SettingsRepository
import com.synapse.app.domain.models.*
import com.synapse.app.domain.repositories.HistoryRepository
import com.synapse.app.platform.automation.SynthesizerWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BriefViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val config = settingsRepository.briefConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, BriefConfig())
    
    val recentRuns = historyRepository.getHistoryByType(WorkflowType.MORNING_BRIEF)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    fun updateConfig(newConfig: BriefConfig) {
        viewModelScope.launch {
            _isSaving.value = true
            settingsRepository.setBriefConfig(newConfig)
            scheduleWorker(newConfig)
            _isSaving.value = false
        }
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<SynthesizerWorker>().build()
        workManager.enqueueUniqueWork("SynthesizerWorker_OneTime", ExistingWorkPolicy.REPLACE, request)
    }
    
    private fun scheduleWorker(config: BriefConfig) {
        if (config.scheduleType == ScheduleType.EVERY_N_HOURS && config.intervalHours != null) {
            val request = PeriodicWorkRequestBuilder<SynthesizerWorker>(
                config.intervalHours.toLong(), TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork("SynthesizerWorker_Periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            // Simplified Daily scheduler utilizing a raw 24 hours Periodic interval 
            val request = PeriodicWorkRequestBuilder<SynthesizerWorker>(
                24, TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork("SynthesizerWorker_Periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
