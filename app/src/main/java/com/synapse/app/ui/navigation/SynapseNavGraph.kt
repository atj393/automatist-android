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
                onNavigateToVault = { navController.navigate(Routes.VAULT) }
            )
        }
        
        composable("${Routes.ARTICLE_TRANSFORMER}?sharedText={sharedText}") { backStackEntry ->
            val text = backStackEntry.arguments?.getString("sharedText") ?: ""
            ArticleScreen(
                sharedText = text,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate("${Routes.HISTORY_DETAIL}/$id") }
            )
        }

        composable(
            "${Routes.HISTORY_DETAIL}/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            HistoryDetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MEETING_STRATEGIST) {
            MeetingScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MORNING_BRIEF) {
            BriefScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VAULT) {
            VaultScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
