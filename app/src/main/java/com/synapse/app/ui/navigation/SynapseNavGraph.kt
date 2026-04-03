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
import com.synapse.app.feature.workflow.templates.WorkflowTemplatesScreen
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
    const val SAVED_NOTES = "saved_notes"
}

@Composable
fun SynapseNavGraph(
    navController: NavHostController = rememberNavController(),
    initialSharedText: String? = null
) {
    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            val encodedText = URLEncoder.encode(initialSharedText, StandardCharsets.UTF_8.toString())
            navController.navigate("${Routes.ARTICLE_TRANSFORMER}?sharedText=$encodedText")
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
                onNavigateToWorkflowRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
                onNavigateToNotes = { navController.navigate(Routes.SAVED_NOTES) },
                onNavigateToTemplates = { navController.navigate(Routes.WORKFLOW_TEMPLATES) }
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

        composable(Routes.VAULT) {
            VaultScreen(onBack = { navController.popBackStack() })
        }

        // ── Workflow Templates (browse built-in templates) ──

        composable(Routes.WORKFLOW_TEMPLATES) {
            WorkflowTemplatesScreen(
                onBack = { navController.popBackStack() },
                onUseTemplate = { builtInId ->
                    // Navigate to editor with source template ID to create instance
                    navController.navigate("${Routes.WORKFLOW_EDITOR}?sourceTemplateId=$builtInId")
                },
                onNavigateToMyWorkflows = { navController.navigate(Routes.WORKFLOW_LIST) }
            )
        }

        // ── My Workflows (user-owned instances) ──

        composable(Routes.WORKFLOW_LIST) {
            WorkflowListScreen(
                onBack = { navController.popBackStack() },
                onBrowseTemplates = { navController.navigate(Routes.WORKFLOW_TEMPLATES) },
                onEdit = { id -> navController.navigate("${Routes.WORKFLOW_EDITOR}?templateId=$id") },
                onRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") },
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
                    navController.navigate(Routes.WORKFLOW_LIST)
                },
                onTestRun = { id -> navController.navigate("${Routes.WORKFLOW_RUN}/$id") }
            )
        }

        composable("${Routes.WORKFLOW_RUN}/{templateId}", arguments = listOf(navArgument("templateId") { type = NavType.LongType })) {
            WorkflowRunScreen(onBack = { navController.popBackStack() })
        }

        composable("${Routes.WORKFLOW_RUN_DETAIL}/{runId}", arguments = listOf(navArgument("runId") { type = NavType.LongType })) { backStackEntry ->
            val runId = backStackEntry.arguments?.getLong("runId") ?: return@composable
            WorkflowRunDetailScreen(runId = runId, onBack = { navController.popBackStack() })
        }

        // ── Saved Notes ──

        composable(Routes.SAVED_NOTES) {
            NotesScreen(onBack = { navController.popBackStack() })
        }
    }
}
