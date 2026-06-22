package com.sdai.news.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sdai.news.data.ProductTextHeuristicParser
import com.sdai.news.data.ScanResult
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.db.ScanHistoryEntity
import com.sdai.news.data.remote.ScanResolveResult
import com.sdai.news.data.remote.ScanSearchClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data object PromptManualEntry : ScanUiState
    data class Resolving(val code: String) : ScanUiState
    data class Found(val result: ScanResult) : ScanUiState
    data class NotFound(val code: String) : ScanUiState
    data class ScanError(val message: String) : ScanUiState
    data object PermissionDenied : ScanUiState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val historyDao = SDAIDatabase.get(app).scanHistoryDao()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val _showTimeoutHint = MutableStateFlow(false)
    val showTimeoutHint: StateFlow<Boolean> = _showTimeoutHint.asStateFlow()

    private var timeoutJob: Job? = null
    private var lastDecodedCode: String? = null
    private var scanAttempts = 0

    fun onPermissionGranted() {
        if (_uiState.value is ScanUiState.Idle || _uiState.value is ScanUiState.PermissionDenied) {
            startScanning()
        }
    }

    fun onPermissionDenied() {
        _uiState.value = ScanUiState.PermissionDenied
    }

    fun startScanning() {
        _uiState.value = ScanUiState.Scanning
        scheduleTimeoutHint()
    }

    // Capture button pressed — OCR reads label text, heuristic extracts product name,
    // then queries Open Food Facts by name. All on-device until the final API call.
    fun onImageCaptured(bitmap: Bitmap, rotationDegrees: Int = 0) {
        if (_uiState.value !is ScanUiState.Scanning) return
        cancelTimeoutHint()
        triggerFeedback()
        _uiState.value = ScanUiState.Resolving("label")
        viewModelScope.launch {
            try {
                val mlText = performOcr(bitmap, rotationDegrees)
                val name = ProductTextHeuristicParser.extractProductName(mlText, bitmap.height)
                if (name.isNullOrBlank()) {
                    _uiState.value = ScanUiState.PromptManualEntry
                    return@launch
                }
                when (val result = ScanSearchClient.resolveByName(name)) {
                    is ScanResolveResult.Found -> { saveToHistory(result.result); _uiState.value = ScanUiState.Found(result.result) }
                    is ScanResolveResult.NotFound,
                    is ScanResolveResult.Error -> _uiState.value = ScanUiState.PromptManualEntry
                }
            } catch (_: Exception) {
                _uiState.value = ScanUiState.PromptManualEntry
            }
        }
    }

    fun onCaptureError(message: String) {
        _uiState.value = ScanUiState.ScanError(message)
    }

    private suspend fun performOcr(bitmap: Bitmap, rotationDegrees: Int): Text = suspendCoroutine { cont ->
        textRecognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    // Barcode auto-detected by camera. Stops and shows NotFound after MAX_SCAN_ATTEMPTS failures.
    fun onBarcodeDetected(barcode: String) {
        if (_uiState.value !is ScanUiState.Scanning) return
        if (barcode == lastDecodedCode) return
        lastDecodedCode = barcode
        scanAttempts++
        triggerFeedback()
        _uiState.value = ScanUiState.Resolving(barcode)
        viewModelScope.launch {
            when (val result = ScanSearchClient.resolveBarcode(barcode)) {
                is ScanResolveResult.Found -> { saveToHistory(result.result); _uiState.value = ScanUiState.Found(result.result) }
                is ScanResolveResult.NotFound -> {
                    lastDecodedCode = null
                    if (scanAttempts >= MAX_SCAN_ATTEMPTS) _uiState.value = ScanUiState.NotFound(barcode)
                    else startScanning()
                }
                is ScanResolveResult.Error -> {
                    lastDecodedCode = null
                    if (scanAttempts >= MAX_SCAN_ATTEMPTS) _uiState.value = ScanUiState.PromptManualEntry
                    else startScanning()
                }
            }
        }
    }

    // Manually entered product name — searches Open Food Facts by name.
    fun onInputReceived(name: String) {
        val validStates = _uiState.value is ScanUiState.Scanning || _uiState.value is ScanUiState.PromptManualEntry
        if (!validStates) return
        cancelTimeoutHint()
        triggerFeedback()
        _uiState.value = ScanUiState.Resolving(name)
        viewModelScope.launch {
            when (val result = ScanSearchClient.resolveByName(name)) {
                is ScanResolveResult.Found -> { saveToHistory(result.result); _uiState.value = ScanUiState.Found(result.result) }
                is ScanResolveResult.NotFound -> _uiState.value = ScanUiState.NotFound(name)
                is ScanResolveResult.Error -> _uiState.value = ScanUiState.ScanError(result.message)
            }
        }
    }

    fun resumeScanning() {
        lastDecodedCode = null
        scanAttempts = 0
        startScanning()
    }

    fun toggleTorch() {
        _torchOn.value = !_torchOn.value
    }

    private suspend fun saveToHistory(result: ScanResult) {
        historyDao.upsert(
            ScanHistoryEntity(
                barcode = result.barcode,
                name = result.name,
                brand = result.brand,
                overallRating = result.overallRating,
                safetyLabel = result.safetyLabel,
                category = result.category,
                scannedAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun scheduleTimeoutHint() {
        timeoutJob?.cancel()
        _showTimeoutHint.value = false
        timeoutJob = viewModelScope.launch {
            delay(TIMEOUT_MS)
            if (_uiState.value is ScanUiState.Scanning) {
                _showTimeoutHint.value = true
                delay(5_000)
                _showTimeoutHint.value = false
                if (_uiState.value is ScanUiState.Scanning) scheduleTimeoutHint()
            }
        }
    }

    private fun cancelTimeoutHint() {
        timeoutJob?.cancel()
        _showTimeoutHint.value = false
    }

    private fun triggerFeedback() {
        val ctx: Context = getApplication()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                mgr.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 80, 50, 80), -1)
                }
            }
        } catch (_: Exception) {}
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel()
        textRecognizer.close()
    }

    companion object {
        private const val TIMEOUT_MS = 6_000L
        private const val MAX_SCAN_ATTEMPTS = 3
    }
}
