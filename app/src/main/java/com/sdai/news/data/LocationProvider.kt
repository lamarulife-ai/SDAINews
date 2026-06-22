package com.sdai.news.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.Locale

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val region: String,
    val country: String,
    val label: String,
    val countryCode: String = "",
)

sealed interface LocationResult {
    data class Success(val location: ResolvedLocation) : LocationResult
    data object NoPermission : LocationResult
    data object LocationServicesOff : LocationResult
    data object FixUnavailable : LocationResult
}

class LocationProvider(private val context: Context) {

    private val fused by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    suspend fun resolveCurrentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.NoPermission
        if (!isLocationEnabled()) return LocationResult.LocationServicesOff

        val location = withTimeoutOrNull(15_000L) { freshLocation() }
            ?: return LocationResult.FixUnavailable

        val resolved = reverseGeocode(location.latitude, location.longitude)
        return LocationResult.Success(resolved)
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): android.location.Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }

    /** Resolve a typed place name (e.g. "Vizag") to a full location with state +
     *  country code, so manually-entered cities get proper Local sources and a
     *  resolved regional language. Returns null if it can't be resolved. */
    suspend fun forwardGeocode(query: String): ResolvedLocation? {
        if (query.isBlank() || !Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.ENGLISH)
        val addresses: List<Address>? = withTimeoutOrNull(6_000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1) { addr -> cont.resume(addr) }
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, 1) }.getOrDefault(null)
            }
        }
        val a = addresses?.firstOrNull() ?: return null
        val city = listOfNotNull(
            a.locality?.takeIf { it.isNotBlank() },
            a.subAdminArea?.takeIf { it.isNotBlank() },
            a.subLocality?.takeIf { it.isNotBlank() },
        ).firstOrNull().orEmpty().ifBlank { query.trim() }
        val region = a.adminArea?.takeIf { it.isNotBlank() }.orEmpty()
        val country = a.countryName?.takeIf { it.isNotBlank() }.orEmpty()
        val countryCode = a.countryCode?.takeIf { it.isNotBlank() }.orEmpty()
        return ResolvedLocation(
            latitude = a.latitude,
            longitude = a.longitude,
            city = city,
            region = region,
            country = country,
            label = listOfNotNull(city.takeIf { it.isNotBlank() }, region.takeIf { it.isNotBlank() })
                .joinToString(", ").ifBlank { query.trim() },
            countryCode = countryCode,
        )
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): ResolvedLocation {
        val fallback = ResolvedLocation(
            latitude = lat,
            longitude = lon,
            city = "",
            region = "",
            country = "",
            label = "%.3f, %.3f".format(lat, lon),
        )

        if (!Geocoder.isPresent()) return fallback

        val geocoder = Geocoder(context, Locale.ENGLISH)
        val addresses: List<Address>? = withTimeoutOrNull(5_000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { addr ->
                        cont.resume(addr)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1) }
                    .getOrDefault(null)
            }
        }

        val a = addresses?.firstOrNull() ?: return fallback

        val city = listOfNotNull(
            a.subLocality?.takeIf { it.isNotBlank() },
            a.locality?.takeIf { it.isNotBlank() },
            a.subAdminArea?.takeIf { it.isNotBlank() },
        ).firstOrNull().orEmpty()

        val region = a.adminArea?.takeIf { it.isNotBlank() }.orEmpty()
        val country = a.countryName?.takeIf { it.isNotBlank() }.orEmpty()
        val countryCode = a.countryCode?.takeIf { it.isNotBlank() }.orEmpty()

        val labelParts = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            region.takeIf { it.isNotBlank() },
        )
        val label = if (labelParts.isNotEmpty()) labelParts.joinToString(", ")
        else fallback.label

        return ResolvedLocation(
            latitude = lat,
            longitude = lon,
            city = city,
            region = region,
            country = country,
            label = label,
            countryCode = countryCode,
        )
    }
}
