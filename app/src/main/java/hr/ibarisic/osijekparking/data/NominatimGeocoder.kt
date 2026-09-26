package hr.ibarisic.osijekparking.data

import hr.ibarisic.osijekparking.domain.GeoUtils
import hr.ibarisic.osijekparking.domain.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AddressResult(
    val title: String,
    val subtitle: String,
    val position: LatLon,
    /** Street name from the geocoder, used to match the address against dataset street names. */
    val road: String?,
)

interface Geocoder {
    suspend fun search(query: String): List<AddressResult>
}

/**
 * OpenStreetMap Nominatim, restricted to the Osijek area.
 * The public instance allows ~1 request/second and forbids autocomplete, so the UI only calls this
 * when the user submits a query. For heavier traffic, point [baseUrl] at a self-hosted instance.
 */
class NominatimGeocoder(
    private val userAgent: String,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
) : Geocoder {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<AddressResult> = withContext(Dispatchers.IO) {
        val b = GeoUtils.OSIJEK_BOUNDS
        val url = URL(
            "$baseUrl/search?format=jsonv2&addressdetails=1&limit=8&countrycodes=hr&accept-language=hr" +
                "&bounded=1&viewbox=${b.west},${b.north},${b.east},${b.south}" +
                "&q=" + URLEncoder.encode(query, "UTF-8"),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "hr")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val lat = o.s("lat")?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = o.s("lon")?.toDoubleOrNull() ?: return@mapNotNull null
                val address = o["address"] as? JsonObject
                val road = address?.s("road") ?: address?.s("pedestrian") ?: address?.s("square")
                val house = address?.s("house_number")
                val display = o.s("display_name").orEmpty()
                val title = when {
                    road != null && house != null -> "$road $house"
                    o.s("name") != null -> o.s("name")!!
                    road != null -> road
                    else -> display.substringBefore(',')
                }
                val subtitle = listOfNotNull(
                    address?.s("suburb") ?: address?.s("neighbourhood"),
                    address?.s("city") ?: address?.s("town") ?: address?.s("village"),
                ).distinct().joinToString(", ")
                AddressResult(title, subtitle, LatLon(lat, lon), road)
            }.distinctBy { it.title to it.subtitle }
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
