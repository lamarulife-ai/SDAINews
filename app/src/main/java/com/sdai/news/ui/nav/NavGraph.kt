package com.sdai.news.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sdai.news.SDAINewsApp
import com.sdai.news.ui.screens.BookmarksScreen
import com.sdai.news.ui.screens.ContactScreen
import com.sdai.news.ui.screens.DisclaimerScreen
import com.sdai.news.ui.screens.FeedScreen
import com.sdai.news.ui.screens.SettingsScreen
import com.sdai.news.ui.screens.SplashScreen
import com.sdai.news.util.ArticleViewer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single-graph nav.
 *
 * First-launch gate: Splash → checks `prefs.disclaimerAccepted`. If
 * unset, the user is routed to [DisclaimerScreen] and **no news data
 * is shown anywhere** until they tap "I understand & accept". Only
 * then do we navigate to [FeedScreen].
 *
 * Article reading happens *outside* the nav graph via Chrome Custom
 * Tabs (see [ArticleViewer]).
 */
object Routes {
    const val SPLASH = "splash"
    const val FEED = "feed"
    const val SETTINGS = "settings"
    const val BOOKMARKS = "bookmarks"
    const val CONTACT = "contact"
    const val DISCLAIMER = "disclaimer"
}

@Composable
fun NavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onContinue = {
                scope.launch {
                    val prefs = SDAINewsApp.get().prefs
                    val accepted = prefs.disclaimerAccepted.first()
                    val next = if (accepted) Routes.FEED else Routes.DISCLAIMER
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(
                onAccepted = {
                    // Flag is already persisted by the screen. Replace
                    // the disclaimer in the back stack with FEED so the
                    // user can't navigate back to it inadvertently.
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.DISCLAIMER) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.FEED) {
            FeedScreen(
                onOpenArticle = { article -> ArticleViewer.open(context, article.url) },
                onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onOpenContact = { navController.navigate(Routes.CONTACT) },
                onOpenDisclaimer = { navController.navigate(Routes.DISCLAIMER) },
            )
        }
        composable(Routes.CONTACT) {
            ContactScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url, _ -> ArticleViewer.open(context, url) },
            )
        }
    }
}
