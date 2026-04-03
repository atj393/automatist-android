package com.synapse.app.data.repositories

import com.synapse.app.data.local.WorkflowDao
import com.synapse.app.data.local.toDomain
import com.synapse.app.data.local.toEntity
import com.synapse.app.domain.models.WorkflowRun
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomWorkflowRepository @Inject constructor(
    private val dao: WorkflowDao
) : WorkflowRepository {

    // ── Templates ──

    override fun getAllTemplates(): Flow<List<WorkflowTemplate>> =
        dao.getAllTemplates().map { list -> list.map { it.toDomain() } }

    override suspend fun getTemplateById(id: Long): WorkflowTemplate? =
        dao.getTemplateById(id)?.toDomain()

    override suspend fun saveTemplate(template: WorkflowTemplate): Long =
        dao.insertTemplate(template.toEntity())

    override suspend fun updateTemplate(template: WorkflowTemplate) =
        dao.updateTemplate(template.toEntity())

    override suspend fun deleteTemplate(id: Long) =
        dao.deleteTemplate(id)

    // ── Runs ──

    override fun getRunsForTemplate(templateId: Long): Flow<List<WorkflowRun>> =
        dao.getRunsForTemplate(templateId).map { list -> list.map { it.toDomain() } }

    override fun getRecentRuns(limit: Int): Flow<List<WorkflowRun>> =
        dao.getRecentRuns(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getRunById(id: Long): WorkflowRun? =
        dao.getRunById(id)?.toDomain()

    override suspend fun insertRun(run: WorkflowRun): Long =
        dao.insertRun(run.toEntity())

    override suspend fun updateRun(run: WorkflowRun) =
        dao.updateRun(run.toEntity())
}
