package com.sdai.news.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Launches the system share sheet — WhatsApp, Gmail, Telegram, Slack,
 * etc., and now with a generated card image so the share looks like a
 * post instead of a flat link.
 *
 * When [imageUrl] is provided, we generate a 1080×1350 PNG (hero +
 * headline + branded footer) and attach it as `image/png`. Most chat
 * apps render the image inline. If image generation fails we silently
 * fall back to text-only — the share still works.
 */
object Share {

    fun article(
        context: Context,
        title: String,
        url: String,
        source: String = "",
        imageUrl: String? = null,
    ) {
        // Fire-and-forget the text-only share immediately if we have no
        // image; otherwise render the card on a coroutine and dispatch
        // when it's ready.
        if (imageUrl == null) {
            launchTextShare(context, title, url)
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val cardUri = ShareCardRenderer.render(context, title, source, imageUrl)
            if (cardUri != null) launchImageShare(context, title, url, cardUri)
            else launchTextShare(context, title, url)
        }
    }

    private fun launchTextShare(context: Context, title: String, url: String) {
        val body = "$title\n\n$url\n\nShared via SD AI News"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(send, "Share article via"))
    }

    private fun launchImageShare(context: Context, title: String, url: String, cardUri: Uri) {
        val body = "$title\n\n$url\n\nShared via SD AI News"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, cardUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share article via"))
    }
}
