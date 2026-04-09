package com.synapse.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.synapse.app.feature.article.ArticleScreen
import com.synapse.app.feature.dashboard.DashboardScreen
import com.synapse.app.feature.history.HistoryDetailScreen
import com.synapse.app.feature.history.HistoryScreen
import com.synapse.app.feature.vault.VaultScreen
import com.synapse.app.feature.meeting.MeetingScreen
import com.synapse.app.feature.brief.BriefScreen
import com.synapse.app.feature.workflow.list.WorkflowListScreen
import com.synapse.app.feature.workflow.editor.WorkflowEditorScreen
import com.synapse.app.feature.workflow.run.WorkflowRunScreen
import com.synapse.app.feature.workflow.run.WorkflowRunDetailScreen
import com.synapse.app.feature.workflow.details.WorkflowDetailsScreen
import com.synapse.app.feature.workflow.history.WorkflowHistoryScreen
import com.synapse.app.feature.workflow.schedule.ScheduleStatusScreen
import com.synapse.app.feature.workflow.templates.WorkflowTemplatesScreen
import com.synapse.app.feature.vault.SettingsSection
import com.synapse.app.feature.notes.NotesScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val DASHBOARD = "dashboard"
    const val ARTICLE_TRANSFORMER = "article_transformer"
    const val MEETING_STRATEGIST = "meeting_strategist"
    const val MORNING_BRIEF = "morning_brief"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail"
    const val VAULT = "vault"
    const val WORKFLOW_TEMPLATES = "workflow_templates"
    const val WORKFLOW_LIST = "workflow_list"
    const val WORKFLOW_EDITOR = "workflow_editor"
    const val WORKFLOW_RUN = "workflow_run"
    const val WORKFLOW_RUN_DETAIL = "workflow_run_detail"
    const val WORKFLOW_DETAILS = "workflow_details"
    const val WORKFLOW_HISTORY = "workflow_history"
    const val SAVED_NOTES = "saved_notes"
    const val SCHEDULE_STATUS = "schedule_status"
}

@Composable
fun SynapseNavGraph(
    navController: NavHostController = rememberNavController(),
    initialSharedText: String? = null,
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            val encodedText = URLEncoder.encode(initialSharedText, StandardCharsets.UTF_8.toString())
            navController.navigate("${Routes.ARTICLE_TRANSFORMER}?sharedText=$encodedText")
        }
    }

    // Handle notification deep links: "workflow_run_detail/123" etc.
    // pendingDeepLink changes both on initial launch AND on onNewIntent (singleTop),
    // so this LaunchedEffect re-fires whenever a new notification tap arrives.
    LaunchedEffect(pendingDeepLink) {
        if (!pendingDeepLink.isNullOrBlank()) {
            navController.navigate(pendingDeepLink) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToArticle = { navController.navigate(Routes.ARTICLE_TRANSFORMER) },
                onNavigateToMeeting = { navController.navigate(Routes.MEETING_STRATEGIST) },
                onNavigateToBrief = { navController.navigate(Routes.MORNING_BRIEF) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToHistoryDetail = { id -> navController.navigate("${Routes.HISTORY_DETAIL}/$id") },
                onNavigateToVault = { navController.navigate(Routes.VAULT) },
                onNavigateToWorkflowList = { navController.navigate(Routes.WORKFLOW_LIST) },
                onNavigateToWorkflowDetails = { id -> navController.navigate("${Routes.WORKFLOW_DETAILS}/$id") },
                onNavigateToWorkflowRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onNavigateToNotes = { navController.navigate(Routes.SAVED_NOTES) },
                onNavigateToTemplates = { navController.navigate(Routes.WORKFLOW_TEMPLATES) },
                onCreateBlankWorkflow = { navController.navigate(Routes.WORKFLOW_EDITOR) }
            )
        }

        composable("${Routes.ARTICLE_TRANSFORMER}?sharedText={sharedText}") { backStackEntry ->
            val text = backStackEntry.arguments?.getString("sharedText") ?: ""
            ArticleScreen(sharedText = text, onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() }, onItemClick = { id -> navController.navigate("${Routes.HISTORY_DETAIL}/$id") })
        }

        composable("${Routes.HISTORY_DETAIL}/{itemId}", arguments = listOf(navArgument("itemId") { type = NavType.LongType })) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            HistoryDetailScreen(itemId = id, onBack = { navController.popBackStack() })
        }

        composable(Routes.MEETING_STRATEGIST) {
            MeetingScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MORNING_BRIEF) {
            BriefScreen(onBack = { navController.popBackStack() })
        }

        composable(
            "${Routes.VAULT}?section={section}",
            arguments = listOf(navArgument("section") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString("section") ?: ""
            VaultScreen(onBack = { navController.popBackStack() }, initialSection = section)
        }

        // ── Workflow Templates (browse built-in templates) ──

        composable(Routes.WORKFLOW_TEMPLATES) {
            WorkflowTemplatesScreen(
                onBack = { navController.popBackStack() },
                onUseTemplate = { builtInId ->
                    navController.navigate("${Routes.WORKFLOW_EDITOR}?sourceTemplateId=$builtInId")
                },
                onNavigateToMyWorkflows = { navController.navigate(Routes.WORKFLOW_LIST) },
                onCreateBlank = { navController.navigate(Routes.WORKFLOW_EDITOR) },
                onNavigateToSettings = { navController.navigate("${Routes.VAULT}?section=${SettingsSection.SERVICE_KEYS.key}") }
            )
        }

        // ── My Workflows (user-owned instances) ──

        composable(Routes.WORKFLOW_LIST) {
            WorkflowListScreen(
                onBack = { navController.popBackStack() },
                onBrowseTemplates = { navController.navigate(Routes.WORKFLOW_TEMPLATES) },
                onCreateBlank = { navController.navigate(Routes.WORKFLOW_EDITOR) },
                onEdit = { id -> navController.navigate("${Routes.WORKFLOW_EDITOR}?templateId=$id") },
                onRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onViewDetails = { id -> navController.navigate("${Routes.WORKFLOW_DETAILS}/$id") },
                onViewRunDetail = { runId -> navController.navigate("${Routes.WORKFLOW_RUN_DETAIL}/$runId") },
                onViewHistory = { id -> navController.navigate("${Routes.WORKFLOW_HISTORY}/$id") },
                onViewSchedules = { navController.navigate(Routes.SCHEDULE_STATUS) }
            )
        }

        // ── Workflow Details (read-only overview) ──

        composable(
            "${Routes.WORKFLOW_DETAILS}/{templateId}",
            arguments = listOf(navArgument("templateId") { type = NavType.LongType })
        ) {
            WorkflowDetailsScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("${Routes.WORKFLOW_EDITOR}?templateId=$id") },
                onRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onViewHistory = { id -> navController.navigate("${Routes.WORKFLOW_HISTORY}/$id") },
                onViewRunDetail = { runId -> navController.navigate("${Routes.WORKFLOW_RUN_DETAIL}/$runId") }
            )
        }

        // ── Workflow History (per-workflow run list) ──

        composable(
            "${Routes.WORKFLOW_HISTORY}/{templateId}",
            arguments = listOf(navArgument("templateId") { type = NavType.LongType })
        ) {
            WorkflowHistoryScreen(
                onBack = { navController.popBackStack() },
                onViewRunDetail = { runId -> navController.navigate("${Routes.WORKFLOW_RUN_DETAIL}/$runId") }
            )
        }

        // ── Workflow Editor (create from template or edit existing) ──

        composable(
            "${Routes.WORKFLOW_EDITOR}?templateId={templateId}&sourceTemplateId={sourceTemplateId}",
            arguments = listOf(
                navArgument("templateId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("sourceTemplateId") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            WorkflowEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("${Routes.WORKFLOW_DETAILS}/$id")
                },
                onTestRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onNavigateToSettings = { navController.navigate("${Routes.VAULT}?section=${SettingsSection.SERVICE_KEYS.key}") }
            )
        }

        composable("${Routes.WORKFLOW_RUN}/{templateId}", arguments = listOf(navArgument("templateId") { type = NavType.LongType })) {
            WorkflowRunScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate("${Routes.VAULT}?section=${SettingsSection.SERVICE_KEYS.key}") }
            )
        }

        composable("${Routes.WORKFLOW_RUN_DETAIL}/{runId}", arguments = listOf(navArgument("runId") { type = NavType.LongType })) { backStackEntry ->
            val runId = backStackEntry.arguments?.getLong("runId") ?: return@composable
            WorkflowRunDetailScreen(runId = runId, onBack = { navController.popBackStack() })
        }

        // ── Schedule Status (inspect scheduled workflows) ──

        composable(Routes.SCHEDULE_STATUS) {
            ScheduleStatusScreen(
                onBack = { navController.popBackStack() },
                onEditWorkflow = { id -> navController.navigate("${Routes.WORKFLOW_EDITOR}?templateId=$id") },
                onViewRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onCreateWorkflow = { navController.navigate(Routes.WORKFLOW_EDITOR) }
            )
        }

        // ── Saved Notes ──

        composable(Routes.SAVED_NOTES) {
            NotesScreen(onBack = { navController.popBackStack() })
        }
    }
}
