package com.sdai.news.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.LocationProvider
import com.sdai.news.data.LocationResult
import com.sdai.news.data.ResolvedLocation
import com.sdai.news.ui.theme.Sdai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocationPickerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = SDAINewsApp.get().prefs

    val currentLabel by prefs.locationLabel.collectAsState(initial = "")
    val currentCity by prefs.locationCity.collectAsState(initial = "")

    var isDetecting by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualCity by remember { mutableStateOf(currentCity) }
    var manualRegion by remember { mutableStateOf("") }
    var manualCountry by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            reDetect(ctx, scope, prefs, onBack, { isDetecting = it }, { statusMsg = it })
        } else {
            statusMsg = "Permission denied"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Sdai.ink)
            }

            Text(
                "Location",
                style = MaterialTheme.typography.headlineMedium,
                color = Sdai.ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))

            if (currentLabel.isNotBlank()) {
                Text(
                    "Current location",
                    style = MaterialTheme.typography.labelMedium,
                    color = Sdai.muted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Sdai.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        currentLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Sdai.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                enabled = !isDetecting,
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sdai.primary,
                    contentColor = Sdai.onPrimary,
                ),
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Sdai.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Re-detect Location", fontWeight = FontWeight.SemiBold)
                }
            }

            if (statusMsg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    statusMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Sdai.inkSubtle,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            if (!showManual) {
                TextButton(
                    onClick = { showManual = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        "Enter location manually",
                        color = Sdai.inkSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (showManual) {
                Text(
                    "Enter manually",
                    style = MaterialTheme.typography.titleMedium,
                    color = Sdai.ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualCity,
                    onValueChange = { manualCity = it },
                    label = { Text("City", color = Sdai.muted) },
                    placeholder = { Text("e.g. Hyderabad", color = Sdai.mutedDeep) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Sdai.ink,
                        unfocusedTextColor = Sdai.ink,
                        cursorColor = Sdai.primary,
                        focusedBorderColor = Sdai.primary,
                        unfocusedBorderColor = Sdai.border,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualRegion,
                    onValueChange = { manualRegion = it },
                    label = { Text("State / Region", color = Sdai.muted) },
                    placeholder = { Text("e.g. Telangana", color = Sdai.mutedDeep) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Sdai.ink,
                        unfocusedTextColor = Sdai.ink,
                        cursorColor = Sdai.primary,
                        focusedBorderColor = Sdai.primary,
                        unfocusedBorderColor = Sdai.border,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualCountry,
                    onValueChange = { manualCountry = it },
                    label = { Text("Country", color = Sdai.muted) },
                    placeholder = { Text("e.g. India", color = Sdai.mutedDeep) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Sdai.ink,
                        unfocusedTextColor = Sdai.ink,
                        cursorColor = Sdai.primary,
                        focusedBorderColor = Sdai.primary,
                        unfocusedBorderColor = Sdai.border,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            prefs.setManualLocation(
                                manualCity.trim(),
                                manualRegion.trim(),
                                manualCountry.trim(),
                            )
                            onBack()
                        }
                    },
                    enabled = manualCity.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sdai.primary,
                        contentColor = Sdai.onPrimary,
                    ),
                ) {
                    Text("Save Location", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun reDetect(
    ctx: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    prefs: com.sdai.news.data.PrefsStore,
    onBack: () -> Unit,
    onDetecting: (Boolean) -> Unit,
    onStatus: (String) -> Unit,
) {
    onDetecting(true)
    onStatus("Detecting…")
    scope.launch {
        val provider = LocationProvider(ctx)
        val result = withContext(Dispatchers.IO) { provider.resolveCurrentLocation() }
        onDetecting(false)
        when (result) {
            is LocationResult.Success -> {
                prefs.setLocation(result.location)
                onStatus("Location updated to ${result.location.label}")
                onBack()
            }
            is LocationResult.NoPermission -> onStatus("Permission not granted")
            is LocationResult.LocationServicesOff -> onStatus("Location services are off")
            is LocationResult.FixUnavailable -> onStatus("Could not get location. Try manual entry.")
        }
    }
}
