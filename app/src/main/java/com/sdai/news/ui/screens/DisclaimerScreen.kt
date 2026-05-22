package com.sdai.news.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.ui.theme.Sdai
import kotlinx.coroutines.launch

/**
 * Full-screen disclaimer. Mode flips on the persisted acceptance flag:
 *
 *  - **First launch** (not yet accepted): button reads "I understand &
 *    accept", system back is consumed (no escape), [onAccepted] only
 *    fires after the flag is persisted.
 *  - **Revisit** from Settings: shows a "Close" button instead, and
 *    system back works normally.
 *
 * Required by Play News policy + the user's editorial transparency
 * goal — the disclaimer cannot be bypassed without explicit consent.
 */
@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit,
    onClose: () -> Unit,
) {
    val prefs = SDAINewsApp.get().prefs
    val accepted by prefs.disclaimerAccepted.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    // Block the back gesture on first-time view — the user must tap
    // the accept button to exit this screen.
    BackHandler(enabled = !accepted) { /* swallow */ }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.displayLarge,
                color = Sdai.ink,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.disclaimer_intro),
                style = MaterialTheme.typography.titleMedium,
                color = Sdai.inkSubtle,
            )

            Spacer(Modifier.height(20.dp))

            // Scrollable body so the legal text is fully readable even
            // on small devices, without truncation.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Sdai.cardInner)
                    .border(1.dp, Sdai.border, RoundedCornerShape(16.dp))
                    .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp))
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.disclaimer_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Sdai.inkSubtle,
                )
                if (accepted) {
                    Text(
                        stringResource(R.string.disclaimer_accepted_already),
                        style = MaterialTheme.typography.labelSmall,
                        color = Sdai.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (accepted) {
                        onClose()
                    } else {
                        scope.launch {
                            prefs.setDisclaimerAccepted(true)
                            onAccepted()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sdai.primary,
                    contentColor = Sdai.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (accepted) R.string.disclaimer_close
                        else R.string.disclaimer_accept
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
