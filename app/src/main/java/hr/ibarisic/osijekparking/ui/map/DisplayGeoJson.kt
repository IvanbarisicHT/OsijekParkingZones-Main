package hr.ibarisic.osijekparking.ui.map

import hr.ibarisic.osijekparking.domain.EffectiveStatus
import hr.ibarisic.osijekparking.domain.Geometry
import hr.ibarisic.osijekparking.domain.LatLon
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.domain.Zone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Colour category used by map layers and the legend. */
enum class MapCategory { ZONE_0, ZONE_1, ZONE_2, FREE, UNKNOWN }

fun categoryOf(segment: ParkingSegment, status: EffectiveStatus): MapCategory = when (status) {
    EffectiveStatus.Free -> MapCategory.FREE
    EffectiveStatus.Unknown -> MapCategory.UNKNOWN
    EffectiveStatus.Paid, is EffectiveStatus.Planned -> when (segment.zone) {
        Zone.ZONE_0 -> MapCategory.ZONE_0
        Zone.ZONE_1 -> MapCategory.ZONE_1
        Zone.ZONE_2 -> MapCategory.ZONE_2
        Zone.OTHER, null -> MapCategory.UNKNOWN
    }
}

sealed interface CameraCommand {
    data class FitGeometry(val geometry: Geometry) : CameraCommand
    data class MoveTo(val position: LatLon, val zoom: Double) : CameraCommand
    data object TrackUser : CameraCommand
}

/**
 * Builds the GeoJSON fed to the map. Each feature keeps the dataset geometry verbatim and gets
 * only the few properties the style needs, so styling never depends on raw dataset fields.
 */
object DisplayGeoJson {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    const val PROP_ID = "id"
    const val PROP_CATEGORY = "category"
    const val PROP_PLANNED = "planned"

    fun build(segments: List<ParkingSegment>, status: (ParkingSegment) -> EffectiveStatus): String {
        val features = segments.mapNotNull { s ->
            val raw = s.rawGeometryJson ?: return@mapNotNull null
            val st = status(s)
            buildJsonObject {
                put("type", "Feature")
                put("geometry", Json.parseToJsonElement(raw))
                put("properties", buildJsonObject {
                    put(PROP_ID, s.id)
                    put(PROP_CATEGORY, categoryOf(s, st).name)
                    put(PROP_PLANNED, st is EffectiveStatus.Planned)
                })
            }
        }
        return JsonObject(mapOf("type" to JsonPrimitive("FeatureCollection"), "features" to JsonArray(features))).toString()
    }

    fun point(p: LatLon?): String = if (p == null) EMPTY else
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]}}]}"""
}
