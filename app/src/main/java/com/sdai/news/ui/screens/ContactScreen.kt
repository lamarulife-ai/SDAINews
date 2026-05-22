package com.sdai.news.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdai.news.R
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.ArticleViewer

/**
 * Dedicated contact screen.
 *
 * Required by Google Play's News & Magazine policy: aggregator apps
 * must surface clearly-labelled contact information *inside* the app,
 * not only on a linked website. This screen is reachable from Settings
 * via a "Contact us" row.
 */
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Sdai.ink)
            }
            Text(
                stringResource(R.string.contact_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))

        PublisherCard()

        Spacer(Modifier.height(12.dp))

        ActionRow(
            icon = Icons.Outlined.Email,
            label = stringResource(R.string.contact_email_label),
            value = stringResource(R.string.contact_email_value),
            hint = stringResource(R.string.contact_email_action),
            onClick = {
                val mail = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse(
                        "mailto:" + ctx.getString(R.string.contact_email_value)
                    )
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        ctx.getString(R.string.contact_email_subject),
                    )
                }
                runCatching { ctx.startActivity(mail) }
            },
        )

        Spacer(Modifier.height(8.dp))

        ActionRow(
            icon = Icons.Outlined.Language,
            label = stringResource(R.string.contact_website_label),
            value = stringResource(R.string.contact_website_value),
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_website_url))
            },
        )

        Spacer(Modifier.height(8.dp))

        ActionRow(
            icon = Icons.Outlined.PrivacyTip,
            label = stringResource(R.string.contact_privacy_label),
            value = "View online",
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_privacy_url))
            },
        )

        Spacer(Modifier.height(8.dp))

        ActionRow(
            icon = Icons.Outlined.Language,
            label = stringResource(R.string.contact_publisher_page_label),
            value = "View online",
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_publisher_page_url))
            },
        )

        Spacer(Modifier.height(20.dp))

        AggregatorNotice()

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.contact_response_time),
            style = MaterialTheme.typography.labelSmall,
            color = Sdai.muted,
        )
    }
}

@Composable
private fun PublisherCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.contact_publisher_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Sdai.muted,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.contact_publisher_value),
            style = MaterialTheme.typography.titleMedium,
            color = Sdai.ink,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.contact_publisher_role),
            style = MaterialTheme.typography.bodyMedium,
            color = Sdai.inkSubtle,
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String,
    hint: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Sdai.primary)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.padding(start = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Sdai.muted,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = Sdai.ink,
            )
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Sdai.muted,
                )
            }
        }
    }
}

@Composable
private fun AggregatorNotice() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            "About this app".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Sdai.muted,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.contact_aggregator_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = Sdai.inkSubtle,
        )
    }
}
