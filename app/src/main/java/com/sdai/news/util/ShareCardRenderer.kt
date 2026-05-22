package com.sdai.news.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sdai.news.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a branded 1080×1350 PNG card for sharing:
 *   - top: hero image (16:10 crop)
 *   - bottom: headline + source + "SD AI News" footer
 *
 * Returns a content:// URI the caller hands to ACTION_SEND. Falls back
 * to null if the hero image fails to load — in that case the caller
 * shares text-only.
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1350
    private const val HERO_H = 675           // 16:10-ish hero on top half

    suspend fun render(
        context: Context,
        title: String,
        source: String,
        imageUrl: String?,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val hero = imageUrl?.let { loadBitmap(context, it) }
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.sdai_logo)

            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawBackground(canvas)
            drawHero(canvas, hero)
            drawFooterText(canvas, title, source)

            val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
            val file = File(dir, "card_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
            bitmap.recycle()
            hero.recycle()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull()
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = ImageLoader(context).execute(request)
        return (result as? SuccessResult)?.drawable?.let { d ->
            // Convert any drawable to a bitmap.
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: W
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: HERO_H
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bm)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            bm
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint().apply { color = Color.parseColor("#0A0A0F") }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
    }

    private fun drawHero(canvas: Canvas, hero: Bitmap) {
        val dst = Rect(0, 0, W, HERO_H)
        val src = computeCenterCrop(hero.width, hero.height, W, HERO_H)
        canvas.drawBitmap(hero, src, dst, null)

        // Bottom fade so the headline sits on a darker patch.
        val grad = LinearGradient(
            0f, HERO_H * 0.55f, 0f, HERO_H.toFloat(),
            Color.TRANSPARENT, Color.parseColor("#0A0A0F"),
            Shader.TileMode.CLAMP,
        )
        val gp = Paint().apply { shader = grad }
        canvas.drawRect(0f, 0f, W.toFloat(), HERO_H.toFloat(), gp)
    }

    /** Centre-crop source rect for fitting [sw]×[sh] into [dw]×[dh]. */
    private fun computeCenterCrop(sw: Int, sh: Int, dw: Int, dh: Int): Rect {
        val sAspect = sw.toFloat() / sh
        val dAspect = dw.toFloat() / dh
        return if (sAspect > dAspect) {
            // Source is wider — crop horizontally
            val targetW = (sh * dAspect).toInt()
            val left = (sw - targetW) / 2
            Rect(left, 0, left + targetW, sh)
        } else {
            val targetH = (sw / dAspect).toInt()
            val top = (sh - targetH) / 2
            Rect(0, top, sw, top + targetH)
        }
    }

    private fun drawFooterText(canvas: Canvas, title: String, source: String) {
        val padX = 64f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F5FA")
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, (W - 2 * padX).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setMaxLines(4)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        val titleY = HERO_H + 60f
        canvas.save()
        canvas.translate(padX, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // Source row
        val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(source, padX, titleY + titleLayout.height + 50f, srcPaint)

        // Footer: "SD AI News" pinned to the bottom edge
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 30f
        }
        val footerY = H - 70f
        canvas.drawText("SD AI News", padX, footerY, footerPaint)
        canvas.drawText("Smart News. Real Insight.", padX, footerY + 38f, tagPaint)
    }

    // suppress unused — kept in case we want a circle clip on logos
    @Suppress("unused")
    private fun roundedClip(canvas: Canvas, rect: RectF, radius: Float) {
        val clip = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawRoundRect(rect, radius, radius, clip)
    }
}
