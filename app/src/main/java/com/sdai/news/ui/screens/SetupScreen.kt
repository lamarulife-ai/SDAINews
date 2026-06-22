package com.sdai.news.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.LocationProvider
import com.sdai.news.data.LocationResult
import com.sdai.news.data.ResolvedLocation
import com.sdai.news.ui.theme.Sdai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep {
    WELCOME,
    LOCATING,
    LOCATION_FOUND,
    MANUAL_ENTRY,
    PERMISSION_DENIED,
    LOCATION_OFF,
    INTERESTS,
}

// Interest → affinity category code, used to seed the personalization engine.
private val INTEREST_OPTIONS = listOf(
    "❤️ Health & Nutrition" to "health",
    "🛡️ Product Safety" to "safety",
    "🍼 Kids Health" to "kids",
    "🔬 Research & Science" to "science",
    "🌿 Healthy Living" to "health",
    "🌍 Environment" to "climate",
    "💻 Technology" to "tech",
    "💼 Business" to "business",
    "🏏 Sports" to "sports",
    "🏛️ Politics" to "politics",
    "📍 Local News" to "local",
    "🌸 Anime" to "anime",
    "🎮 Gaming" to "gaming",
    "✨ Inspiration" to "inspiration",
)

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = SDAINewsApp.get().prefs

    var step by remember { mutableStateOf(SetupStep.WELCOME) }
    var isLocating by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf<ResolvedLocation?>(null) }
    var errorMsg by remember { mutableStateOf("") }

    var manualCity by remember { mutableStateOf("") }
    var manualRegion by remember { mutableStateOf("") }
    var manualCountry by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val anyGranted = result.values.any { it }
        if (anyGranted) {
            detectLocation(ctx, scope, { loc -> location = loc; step = SetupStep.LOCATION_FOUND }, { errorMsg = it; step = SetupStep.MANUAL_ENTRY }, { isLocating = it })
        } else {
            step = SetupStep.PERMISSION_DENIED
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                SetupStep.WELCOME -> WelcomeContent(
                    onAllowLocation = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    onEnterManually = { step = SetupStep.MANUAL_ENTRY },
                )

                SetupStep.LOCATING -> LocatingContent()

                SetupStep.LOCATION_FOUND -> location?.let { loc ->
                    LocationFoundContent(
                        location = loc,
                        onConfirm = {
                            scope.launch {
                                prefs.saveLocationWithLanguage(loc)
                                prefs.setSetupCompleted(true)
                                step = SetupStep.INTERESTS
                            }
                        },
                        onRetry = {
                            detectLocation(ctx, scope, { l -> location = l; step = SetupStep.LOCATION_FOUND }, { errorMsg = it; step = SetupStep.MANUAL_ENTRY }, { isLocating = it })
                        },
                        onEnterManually = { step = SetupStep.MANUAL_ENTRY },
                    )
                }

                SetupStep.MANUAL_ENTRY -> ManualEntryContent(
                    city = manualCity,
                    region = manualRegion,
                    country = manualCountry,
                    onCityChange = { manualCity = it },
                    onRegionChange = { manualRegion = it },
                    onCountryChange = { manualCountry = it },
                    error = errorMsg,
                    onSave = {
                        if (manualCity.isBlank()) {
                            errorMsg = "Please enter a city name"
                            return@ManualEntryContent
                        }
                        scope.launch {
                            val loc = ResolvedLocation(
                                latitude = 0.0,
                                longitude = 0.0,
                                city = manualCity.trim(),
                                region = manualRegion.trim(),
                                country = manualCountry.trim(),
                                label = listOfNotNull(
                                    manualCity.trim().takeIf { it.isNotBlank() },
                                    manualRegion.trim().takeIf { it.isNotBlank() },
                                ).joinToString(", "),
                            )
                            prefs.saveLocationWithLanguage(loc)
                            prefs.setSetupCompleted(true)
                            step = SetupStep.INTERESTS
                        }
                    },
                )

                SetupStep.INTERESTS -> InterestsContent(
                    onContinue = { picked ->
                        scope.launch {
                            // Persist the picks as Preferred Topics (shown in
                            // Settings + boosted by the ranker) AND seed the
                            // affinity engine so the feed leans toward them from
                            // day one.
                            if (picked.isNotEmpty()) {
                                prefs.setPreferredTopics(picked.toSet())
                                prefs.addAffinity(picked.map { "cat:$it" }, 30f)
                            }
                            onComplete()
                        }
                    },
                    onSkip = onComplete,
                )

                SetupStep.PERMISSION_DENIED -> PermissionDeniedContent(
                    onRetry = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    onEnterManually = { step = SetupStep.MANUAL_ENTRY },
                )

                SetupStep.LOCATION_OFF -> LocationOffContent(
                    onOpenSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onEnterManually = { step = SetupStep.MANUAL_ENTRY },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InterestsContent(
    onContinue: (List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val minRequired = 3

    Text(
        "Choose Your Interests",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Scan. Learn. Decide. · Aware of What You Consume",
        style = MaterialTheme.typography.labelLarge,
        color = Sdai.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Choose $minRequired or more topics you like — your feed will be tuned just for you.",
        style = MaterialTheme.typography.bodyMedium,
        color = Sdai.muted,
        modifier = Modifier.padding(horizontal = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "${selected.size} of $minRequired selected",
        style = MaterialTheme.typography.labelMedium,
        color = if (selected.size >= minRequired) Sdai.success else Sdai.muted,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(14.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        INTEREST_OPTIONS.forEach { (label, code) ->
            val isOn = code in selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isOn) Sdai.primary else Sdai.cardInner)
                    .border(1.dp, if (isOn) Sdai.primary else Sdai.border, RoundedCornerShape(50))
                    .clickable { if (isOn) selected.remove(code) else selected.add(code) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    label,
                    color = if (isOn) Sdai.onPrimary else Sdai.ink,
                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = { onContinue(selected.toList()) },
        enabled = selected.size >= minRequired,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
            disabledContainerColor = Sdai.mutedDeep,
            disabledContentColor = Sdai.muted,
        ),
    ) {
        Text(
            if (selected.size >= minRequired) "Start Reading" else "Pick ${minRequired - selected.size} more",
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("Skip for now", color = Sdai.muted)
    }
}

@Composable
private fun WelcomeContent(
    onAllowLocation: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Text(
        "Welcome to Awarely",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Get global, national, and local news tailored to you.",
        style = MaterialTheme.typography.bodyLarge,
        color = Sdai.inkSubtle,
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onAllowLocation,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
        ),
    ) {
        Text("Allow Location Access", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onEnterManually) {
        Text(
            "Enter city manually",
            color = Sdai.inkSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocatingContent() {
    CircularProgressIndicator(
        modifier = Modifier.size(40.dp),
        color = Sdai.primary,
        strokeWidth = 3.dp,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Detecting your location…",
        style = MaterialTheme.typography.bodyLarge,
        color = Sdai.inkSubtle,
    )
    Text(
        "This may take a few seconds",
        style = MaterialTheme.typography.bodySmall,
        color = Sdai.muted,
    )
}

@Composable
private fun LocationFoundContent(
    location: ResolvedLocation,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Text(
        "Location found",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LocationRow("City", location.city.ifEmpty { "—" })
        LocationRow("Region", location.region.ifEmpty { "—" })
        LocationRow("Country", location.country.ifEmpty { "—" })
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
        ),
    ) {
        Text("Use This Location", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Retry Detection")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onEnterManually) {
        Text(
            "Enter city manually",
            color = Sdai.inkSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ManualEntryContent(
    city: String,
    region: String,
    country: String,
    error: String,
    onCityChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        "Enter your location",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Tell us where you are to get local news.",
        style = MaterialTheme.typography.bodyLarge,
        color = Sdai.inkSubtle,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = city,
        onValueChange = { onCityChange(it) },
        label = { Text("City *", color = Sdai.muted) },
        placeholder = { Text("e.g. Hyderabad", color = Sdai.mutedDeep) },
        singleLine = true,
        isError = error.isNotEmpty(),
        supportingText = if (error.isNotEmpty()) {{ Text(error, color = Sdai.danger) }} else null,
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = region,
        onValueChange = { onRegionChange(it) },
        label = { Text("State / Region", color = Sdai.muted) },
        placeholder = { Text("e.g. Telangana", color = Sdai.mutedDeep) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = country,
        onValueChange = { onCountryChange(it) },
        label = { Text("Country", color = Sdai.muted) },
        placeholder = { Text("e.g. India", color = Sdai.mutedDeep) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        colors = textFieldColors(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onSave,
        enabled = city.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
        ),
    ) {
        Text("Start Reading", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionDeniedContent(
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Text(
        "Location access needed",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "We use your location to show relevant local news. " +
            "You can also enter your city manually.",
        style = MaterialTheme.typography.bodyLarge,
        color = Sdai.inkSubtle,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
        ),
    ) {
        Text("Grant Permission", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onEnterManually) {
        Text(
            "Enter city manually",
            color = Sdai.inkSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocationOffContent(
    onOpenSettings: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Text(
        "Location is turned off",
        style = MaterialTheme.typography.headlineMedium,
        color = Sdai.ink,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Please turn on location services to auto-detect your city, " +
            "or enter it manually.",
        style = MaterialTheme.typography.bodyLarge,
        color = Sdai.inkSubtle,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Sdai.primary,
            contentColor = Sdai.onPrimary,
        ),
    ) {
        Text("Open Location Settings", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onEnterManually) {
        Text(
            "Enter city manually",
            color = Sdai.inkSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocationRow(label: String, value: String) {
    Text(
        "$label:  $value",
        style = MaterialTheme.typography.bodyMedium,
        color = Sdai.ink,
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Sdai.ink,
    unfocusedTextColor = Sdai.ink,
    cursorColor = Sdai.primary,
    focusedBorderColor = Sdai.primary,
    unfocusedBorderColor = Sdai.border,
)

private fun detectLocation(
    ctx: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (ResolvedLocation) -> Unit,
    onError: (String) -> Unit,
    onLocating: (Boolean) -> Unit,
) {
    onLocating(true)
    scope.launch {
        val provider = LocationProvider(ctx)
        val result = withContext(Dispatchers.IO) { provider.resolveCurrentLocation() }
        onLocating(false)
        when (result) {
            is LocationResult.Success -> onSuccess(result.location)
            is LocationResult.NoPermission -> onError("Location permission not granted")
            is LocationResult.LocationServicesOff -> onError("Location services are off")
            is LocationResult.FixUnavailable -> onError("Could not get location. Try manual entry.")
        }
    }
}
