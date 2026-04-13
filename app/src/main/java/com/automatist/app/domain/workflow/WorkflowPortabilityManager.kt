package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.*
import com.automatist.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ── Result types ──

data class DuplicateResult(
    val newWorkflowId: Long,
    val newName: String
)

data class ExportResult(
    val json: String,
    val suggestedFilename: String,
    val warnings: List<String>
)

data class ImportResult(
    val workflowId: Long,
    val workflowName: String,
    val warnings: List<String>
)

@Singleton
class WorkflowPortabilityManager @Inject constructor(
    private val repository: WorkflowRepository
) {

    private val exportJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val parseJson = Json {
        ignoreUnknownKeys = true
    }

    // ═══════════════════════════════════════════════════════════════
    //  DUPLICATE
    // ═══════════════════════════════════════════════════════════════

    suspend fun duplicateWorkflow(sourceId: Long): DuplicateResult {
        val source = repository.getTemplateById(sourceId)
            ?: throw IllegalArgumentException("Workflow not found")

        val uniqueName = generateUniqueName(source.name)
        val now = System.currentTimeMillis()

        val duplicate = WorkflowTemplate(
            id = 0,
            name = uniqueName,
            description = source.description,
            isEnabled = false,
            trigger = source.trigger,
            actions = source.actions.map { it.copy(id = UUID.randomUUID().toString()) },
            globalInstruction = source.globalInstruction,
            outputConfig = source.outputConfig,
            notifyOnCompletion = source.notifyOnCompletion,
            notifyOnStart = source.notifyOnStart,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastRunAtMillis = null,
            lastRunStatus = null,
            sourceTemplateId = source.sourceTemplateId,
            category = source.category,
            customization = source.customization,
            defaultProfileId = source.defaultProfileId
        )

        val newId = repository.saveTemplate(duplicate)
        return DuplicateResult(newId, uniqueName)
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXPORT
    // ═══════════════════════════════════════════════════════════════

    suspend fun exportWorkflow(templateId: Long): ExportResult {
        val template = repository.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Workflow not found")

        val warnings = mutableListOf<String>()

        // ── Collect profile references (safe metadata only) ──
        val profileIds = collectProfileIds(template)
        val profileRefs = profileIds.mapNotNull { pid ->
            repository.getProfileById(pid)?.let { p ->
                SafeProfileRefDto(
                    profileId = p.id,
                    name = p.name,
                    providerType = p.providerType.name,
                    modelId = p.modelId
                )
            }
        }

        // ── Collect saved note snapshots ──
        val noteSnapshots = collectNoteSnapshots(template)

        // ── Build export actions with sanitization ──
        var hadSensitiveFields = false
        val exportActions = template.actions.map { action ->
            val (config, wasSanitized) = sanitizeAndStructureConfig(action)
            if (wasSanitized) hadSensitiveFields = true
            ActionExportDto(
                type = action.type,
                label = action.label,
                sourceData = action.sourceData,
                instruction = action.instruction,
                order = action.order,
                isEnabled = action.isEnabled,
                extraConfig = config,
                profileId = action.profileId
            )
        }

        if (hadSensitiveFields) {
            warnings.add("Sensitive fields (API keys, tokens, auth headers) were redacted from action configs.")
        }

        // ── Check sourceData URLs for embedded secrets ──
        val suspiciousActions = findActionsWithSuspiciousUrls(template.actions)
        if (suspiciousActions.isNotEmpty()) {
            val labels = suspiciousActions.joinToString(", ") { "\"${it}\"" }
            warnings.add("Action $labels may contain sensitive values in URLs. Review the exported file before sharing.")
        }

        val envelope = WorkflowExportEnvelope(
            exportedAt = java.time.Instant.now().toString(),
            workflow = WorkflowExportDto(
                name = template.name,
                description = template.description,
                trigger = template.trigger,
                actions = exportActions,
                globalInstruction = template.globalInstruction,
                outputConfig = template.outputConfig,
                notifyOnCompletion = template.notifyOnCompletion,
                notifyOnStart = template.notifyOnStart,
                sourceTemplateId = template.sourceTemplateId,
                category = template.category,
                customization = template.customization,
                defaultProfileId = template.defaultProfileId
            ),
            references = ExportReferences(
                profiles = profileRefs,
                savedNotes = noteSnapshots.distinctBy { it.originalNoteId }
            )
        )

        val jsonString = exportJson.encodeToString(WorkflowExportEnvelope.serializer(), envelope)

        val safeName = template.name
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim()
            .replace(" ", "_")
            .ifBlank { "workflow" }
        val filename = "$safeName.json"

        return ExportResult(jsonString, filename, warnings)
    }

    // ═══════════════════════════════════════════════════════════════
    //  IMPORT
    // ═══════════════════════════════════════════════════════════════

    suspend fun importWorkflow(jsonString: String): ImportResult {
        val warnings = mutableListOf<String>()

        // ── Parse ──
        val envelope = try {
            parseJson.decodeFromString<WorkflowExportEnvelope>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid workflow file: ${e.message?.take(200)}")
        }

        // ── Validate ──
        if (envelope.type != "automatist-workflow") {
            throw IllegalArgumentException("Not an Automatist workflow file (type: \"${envelope.type}\")")
        }
        if (envelope.schemaVersion > 1) {
            throw IllegalArgumentException(
                "Unsupported schema version ${envelope.schemaVersion}. Please update the app to import this workflow."
            )
        }

        val wf = envelope.workflow

        // ── Resolve profiles ──
        val profileMap = buildProfileResolutionMap(envelope.references.profiles)
        var unresolvedProfiles = 0

        fun resolveProfileId(originalId: String): String {
            if (originalId.isBlank()) return ""
            return profileMap[originalId] ?: run { unresolvedProfiles++; "" }
        }

        val resolvedDefaultProfileId = resolveProfileId(wf.defaultProfileId)
        val resolvedOutputProfileId = resolveProfileId(wf.outputConfig.outputProfileId)

        // ── Resolve saved notes ──
        val noteIdMap = resolveSavedNotes(envelope.references.savedNotes)

        // ── Check for previous-output actions (non-portable references) ──
        val hasPreviousOutputActions = wf.actions.any {
            it.type == WorkflowActionType.USE_PREVIOUS_OUTPUT
        }
        if (hasPreviousOutputActions) {
            warnings.add("\"Previous Output\" actions reference workflows from the source device and need reconfiguring.")
        }

        // ── Build imported actions ──
        val importedActions = wf.actions.map { actionDto ->
            val extraConfigStr = resolveActionConfig(actionDto, noteIdMap, profileMap)
            WorkflowAction(
                id = UUID.randomUUID().toString(),
                type = actionDto.type,
                label = actionDto.label,
                sourceData = actionDto.sourceData,
                instruction = actionDto.instruction,
                order = actionDto.order,
                isEnabled = actionDto.isEnabled,
                extraConfig = extraConfigStr,
                profileId = resolveProfileId(actionDto.profileId)
            )
        }

        // ── Create workflow ──
        val uniqueName = generateUniqueName(wf.name)
        val now = System.currentTimeMillis()

        val template = WorkflowTemplate(
            id = 0,
            name = uniqueName,
            description = wf.description,
            isEnabled = false,
            trigger = wf.trigger,
            actions = importedActions,
            globalInstruction = wf.globalInstruction,
            outputConfig = wf.outputConfig.copy(outputProfileId = resolvedOutputProfileId),
            notifyOnCompletion = wf.notifyOnCompletion,
            notifyOnStart = wf.notifyOnStart,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastRunAtMillis = null,
            lastRunStatus = null,
            sourceTemplateId = wf.sourceTemplateId,
            category = wf.category,
            customization = wf.customization,
            defaultProfileId = resolvedDefaultProfileId
        )

        val newId = repository.saveTemplate(template)

        if (unresolvedProfiles > 0) {
            warnings.add("$unresolvedProfiles AI profile(s) not found locally. Set them up in Settings > AI Profiles.")
        }
        if (uniqueName != wf.name) {
            warnings.add("Renamed to \"$uniqueName\" to avoid a duplicate name.")
        }

        // ── Inform about recreated saved notes ──
        val recreatedNotes = noteIdMap.count { (originalId, newId) -> originalId != newId }
        if (recreatedNotes > 0) {
            warnings.add("$recreatedNotes saved note(s) were recreated from the exported snapshot.")
        }

        return ImportResult(newId, uniqueName, warnings)
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    internal suspend fun generateUniqueName(baseName: String): String {
        val existingNames = repository.getAllTemplates().first().map { it.name }.toSet()

        if (baseName !in existingNames) return baseName

        val copyName = "$baseName (Copy)"
        if (copyName !in existingNames) return copyName

        var counter = 2
        while (counter <= 100) {
            val candidate = "$baseName (Copy $counter)"
            if (candidate !in existingNames) return candidate
            counter++
        }
        return "$baseName (${UUID.randomUUID().toString().take(6)})"
    }

    // ── Profile ID collection for export ──

    private fun collectProfileIds(template: WorkflowTemplate): Set<String> {
        val ids = mutableSetOf<String>()
        if (template.defaultProfileId.isNotBlank()) ids.add(template.defaultProfileId)
        if (template.outputConfig.outputProfileId.isNotBlank()) ids.add(template.outputConfig.outputProfileId)
        template.actions.forEach { action ->
            if (action.profileId.isNotBlank()) ids.add(action.profileId)
            ids.addAll(extractProfileIdsFromConfig(action))
        }
        return ids
    }

    private fun extractProfileIdsFromConfig(action: WorkflowAction): List<String> {
        if (action.extraConfig.isBlank()) return emptyList()
        return try {
            val element = parseJson.parseToJsonElement(action.extraConfig)
            if (element is JsonObject) {
                val pid = element["profileId"]?.let { (it as? JsonPrimitive)?.content }
                if (!pid.isNullOrBlank()) listOf(pid) else emptyList()
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ── Saved note snapshot collection for export ──

    private suspend fun collectNoteSnapshots(template: WorkflowTemplate): List<SavedNoteSnapshotDto> {
        val snapshots = mutableListOf<SavedNoteSnapshotDto>()
        for (action in template.actions) {
            if (action.type == WorkflowActionType.USE_SAVED_NOTE && action.extraConfig.isNotBlank()) {
                try {
                    val ref = parseJson.decodeFromString<SavedNoteReference>(action.extraConfig)
                    if (ref.noteId > 0) {
                        repository.getNoteById(ref.noteId)?.let { note ->
                            snapshots.add(SavedNoteSnapshotDto(note.id, note.title, note.content))
                        }
                    }
                } catch (_: Exception) { /* skip unparseable config */ }
            }
        }
        return snapshots
    }

    // ── Export: sourceData URL secret detection ──

    internal fun findActionsWithSuspiciousUrls(actions: List<WorkflowAction>): List<String> {
        val result = mutableListOf<String>()
        for (action in actions) {
            val data = action.sourceData
            if (data.isBlank()) continue
            // Only inspect values that look like URLs with query strings
            if (!data.contains("?") || !data.contains("=")) continue
            val queryPart = data.substringAfter("?", "")
            if (queryPart.isBlank()) continue
            val paramNames = queryPart.split("&").mapNotNull { param ->
                param.substringBefore("=", "").takeIf { it.isNotBlank() }
            }
            if (paramNames.any { isSensitiveKey(it) }) {
                result.add(action.label.ifBlank { action.type.displayName })
            }
        }
        return result
    }

    // ── Export sanitization ──

    internal fun sanitizeAndStructureConfig(action: WorkflowAction): Pair<JsonElement?, Boolean> {
        if (action.extraConfig.isBlank()) return null to false

        val element = try {
            parseJson.parseToJsonElement(action.extraConfig)
        } catch (_: Exception) {
            return JsonPrimitive(action.extraConfig) to false
        }

        if (action.type == WorkflowActionType.FETCH_API_GET && element is JsonObject) {
            return sanitizeApiGetConfig(element)
        }

        return element to false
    }

    private fun sanitizeApiGetConfig(obj: JsonObject): Pair<JsonElement, Boolean> {
        var sanitized = false
        val mutable = obj.toMutableMap()

        // Sanitize headers
        val headers = obj["headers"]
        if (headers is JsonObject && headers.isNotEmpty()) {
            val cleaned = headers.toMutableMap()
            for ((key, _) in headers) {
                if (isSensitiveKey(key)) {
                    cleaned[key] = JsonPrimitive("[REDACTED]")
                    sanitized = true
                }
            }
            mutable["headers"] = JsonObject(cleaned)
        }

        // Sanitize query params
        val queryParams = obj["queryParams"]
        if (queryParams is JsonObject && queryParams.isNotEmpty()) {
            val cleaned = queryParams.toMutableMap()
            for ((key, _) in queryParams) {
                if (isSensitiveKey(key)) {
                    cleaned[key] = JsonPrimitive("[REDACTED]")
                    sanitized = true
                }
            }
            mutable["queryParams"] = JsonObject(cleaned)
        }

        return JsonObject(mutable) to sanitized
    }

    internal fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower == "key" ||
            lower.contains("auth") ||
            lower.contains("token") ||
            lower.contains("secret") ||
            lower.contains("password") ||
            lower.contains("bearer") ||
            lower.contains("api_key") ||
            lower.contains("apikey") ||
            lower.contains("api-key") ||
            lower.contains("private_key") ||
            lower.contains("private-key")
    }

    // ── Import: profile resolution ──

    private suspend fun buildProfileResolutionMap(
        profileRefs: List<SafeProfileRefDto>
    ): Map<String, String> {
        if (profileRefs.isEmpty()) return emptyMap()

        val localProfiles = repository.getAllProfiles().first()
        val map = mutableMapOf<String, String>()

        for (ref in profileRefs) {
            // 1. Exact ID match
            val exact = localProfiles.find { it.id == ref.profileId }
            if (exact != null) { map[ref.profileId] = exact.id; continue }

            // 2. Match by name + provider + model
            val nameMatch = localProfiles.find {
                it.name == ref.name &&
                    it.providerType.name == ref.providerType &&
                    it.modelId == ref.modelId
            }
            if (nameMatch != null) { map[ref.profileId] = nameMatch.id; continue }

            // 3. Match by provider + model only
            val providerMatch = localProfiles.find {
                it.providerType.name == ref.providerType && it.modelId == ref.modelId
            }
            if (providerMatch != null) { map[ref.profileId] = providerMatch.id; continue }

            // 4. Unresolvable — will be cleared to "" during import
        }
        return map
    }

    // ── Import: saved note resolution ──

    private suspend fun resolveSavedNotes(
        snapshots: List<SavedNoteSnapshotDto>
    ): Map<Long, Long> {
        if (snapshots.isEmpty()) return emptyMap()

        val map = mutableMapOf<Long, Long>()
        for (snapshot in snapshots) {
            val existing = repository.getNoteById(snapshot.originalNoteId)
            if (existing != null && existing.title == snapshot.title) {
                // Exact ID + title match — reuse
                map[snapshot.originalNoteId] = existing.id
            } else {
                // Create imported copy
                val newId = repository.saveNote(
                    SavedNote(title = snapshot.title, content = snapshot.content)
                )
                map[snapshot.originalNoteId] = newId
            }
        }
        return map
    }

    // ── Import: action config resolution ──

    private fun resolveActionConfig(
        actionDto: ActionExportDto,
        noteIdMap: Map<Long, Long>,
        profileMap: Map<String, String>
    ): String {
        val configElement = actionDto.extraConfig ?: return ""

        return when {
            configElement is JsonNull -> ""
            configElement is JsonPrimitive -> configElement.content

            actionDto.type == WorkflowActionType.USE_SAVED_NOTE && configElement is JsonObject -> {
                val originalId = configElement["noteId"]?.jsonPrimitive?.longOrNull ?: 0L
                val newId = noteIdMap[originalId] ?: originalId
                val updated = configElement.toMutableMap()
                updated["noteId"] = JsonPrimitive(newId)
                JsonObject(updated).toString()
            }

            actionDto.type == WorkflowActionType.USE_PREVIOUS_OUTPUT && configElement is JsonObject -> {
                // Clear non-portable local references
                val updated = configElement.toMutableMap()
                updated["sourceWorkflowId"] = JsonPrimitive(0)
                updated.remove("specificRunId")
                JsonObject(updated).toString()
            }

            configElement is JsonObject -> {
                // Resolve profileId in any config that has one (e.g. AiPromptConfig)
                val pid = configElement["profileId"]?.jsonPrimitive?.contentOrNull
                if (!pid.isNullOrBlank()) {
                    val resolved = profileMap[pid] ?: ""
                    val updated = configElement.toMutableMap()
                    updated["profileId"] = JsonPrimitive(resolved)
                    JsonObject(updated).toString()
                } else {
                    configElement.toString()
                }
            }

            else -> configElement.toString()
        }
    }
}
