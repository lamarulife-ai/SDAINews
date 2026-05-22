package com.sdai.news.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens an article URL in Chrome Custom Tabs.
 *
 * Why Custom Tabs over WebView:
 *  - Reuses the user's installed browser engine — their cookies,
 *    extensions, ad-blockers, autofill, and reader-mode all apply.
 *  - Pre-warmed Chrome makes opens feel instant (~100ms).
 *  - The toolbar is themed to match SD AI News (AMOLED black scheme).
 *  - No WebView CVE surface to maintain.
 *
 * Falls back to a plain `ACTION_VIEW` intent if no Custom-Tabs
 * provider is installed; ultimately ignores opens we can't satisfy.
 */
object ArticleViewer {

    fun open(context: Context, url: String) {
        if (url.isBlank()) return
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return

        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.parseColor("#0A0A0F"))
            .setNavigationBarColor(Color.parseColor("#000000"))
            .build()

        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        runCatching { intent.launchUrl(context, uri) }
            .recoverCatching { fallbackView(context, uri) }
    }

    private fun fallbackView(context: Context, uri: Uri) {
        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
            // Last resort: no browser installed. Nothing more to do.
            .recoverCatching { e ->
                if (e is ActivityNotFoundException) Unit else throw e
            }
    }
}
