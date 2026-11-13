package app.fasila.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A resolved place, ready for `default_config_json`. */
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val country: String,
)

/** Entry of the shared city list (`data/cities.json`). */
data class City(
    val name: String,
    val nameAr: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    fun displayName(locale: Locale): String = if (locale.language == "ar" && nameAr.isNotEmpty()) nameAr else name
    fun toPlace(locale: Locale) = Place(displayName(locale), latitude, longitude, timezone, country)
}

class LocationService(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Current device place: fused provider first (Play services), platform LocationManager
     * otherwise; name and country from Geocoder, falling back to the nearest bundled city.
     * Time zone: the device's, since the user is physically there.
     */
    suspend fun currentPlace(cities: List<City>): Place? {
        if (!hasPermission()) return null
        val loc = fusedLocation() ?: platformLocation() ?: return null
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val address = geocode(loc.latitude, loc.longitude, locale)
        val nearest = nearestCity(cities, loc.latitude, loc.longitude)
        val name = address?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.featureName }
            ?: nearest?.displayName(locale)
            ?: String.format(Locale.ROOT, "%.3f, %.3f", loc.latitude, loc.longitude)
        val country = address?.countryCode ?: nearest?.country ?: ""
        return Place(name, loc.latitude, loc.longitude, TimeZone.getDefault().id, country)
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(): Location? = try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        withTimeoutOrNull(15_000) {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
        } ?: client.lastLocation.await()
    } catch (e: Exception) {
        // No Google Play services (or disabled): use the platform provider.
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull()
        }
        val fresh = provider?.let { p ->
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        lm, p, signal, ContextCompat.getMainExecutor(context),
                    ) { cont.resume(it) }
                }
            }
        }
        return fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private suspend fun geocode(lat: Double, lon: Double, locale: Locale): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) { cont.resume(addresses.firstOrNull()) }
                            override fun onError(errorMessage: String?) { cont.resume(null) }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Great-circle distance in km (only used to pick a label from the city list). */
        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }

        fun nearestCity(cities: List<City>, lat: Double, lon: Double, maxKm: Double = 75.0): City? =
            cities.minByOrNull { distanceKm(lat, lon, it.latitude, it.longitude) }
                ?.takeIf { distanceKm(lat, lon, it.latitude, it.longitude) <= maxKm }
    }
}
