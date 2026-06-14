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
import com.sdai.news.ui.screens.LocationPickerScreen
import com.sdai.news.ui.screens.MainScreen
import com.sdai.news.ui.screens.SettingsScreen
import com.sdai.news.ui.screens.SetupScreen
import com.sdai.news.ui.screens.SplashScreen
import com.sdai.news.util.ArticleViewer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val DISCLAIMER = "disclaimer"
    const val SETUP = "setup"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val BOOKMARKS = "bookmarks"
    const val CONTACT = "contact"
    const val LOCATION_PICKER = "location_picker"
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
                    val setupDone = prefs.setupCompleted.first()
                    val next = when {
                        !accepted -> Routes.DISCLAIMER
                        !setupDone -> Routes.SETUP
                        else -> Routes.MAIN
                    }
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(
                onAccepted = {
                    scope.launch {
                        val setupDone = SDAINewsApp.get().prefs.setupCompleted.first()
                        val next = if (setupDone) Routes.MAIN else Routes.SETUP
                        navController.navigate(next) {
                            popUpTo(Routes.DISCLAIMER) { inclusive = true }
                        }
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
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
                onOpenLocationPicker = { navController.navigate(Routes.LOCATION_PICKER) },
            )
        }
        composable(Routes.LOCATION_PICKER) {
            LocationPickerScreen(onBack = { navController.popBackStack() })
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
