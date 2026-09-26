package hr.ibarisic.osijekparking.domain

import java.time.LocalDate
import java.time.LocalTime

/** Municipal status exactly as declared in the dataset. Anything unrecognised maps to [NOT_CONFIRMED]. */
enum class MunicipalStatus {
    PAID, FREE, PLANNED, NOT_CONFIRMED;

    companion object {
        fun parse(raw: String?): MunicipalStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NOT_CONFIRMED
    }
}

/** Zones known to the app. [OTHER] keeps unknown zone codes from being silently dropped. */
enum class Zone(val code: String) {
    ZONE_0("0"), ZONE_1("1"), ZONE_2("2"), OTHER("?");

    companion object {
        fun parse(raw: String?): Zone? {
            val v = raw?.trim()?.uppercase()?.removePrefix("ZONA")?.removePrefix("ZONE")?.trim()
            if (v.isNullOrEmpty()) return null
            return when (v) {
                "0" -> ZONE_0
                "1", "I" -> ZONE_1
                "2", "II" -> ZONE_2
                else -> OTHER
            }
        }
    }
}

enum class LocationType { STREET_SEGMENT, WHOLE_LOCATION, ADDRESS_RANGE, PARKING_AREA, OTHER;

    companion object {
        fun parse(raw: String?): LocationType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

data class TimeWindow(val start: LocalTime, val end: LocalTime) {
    operator fun contains(t: LocalTime) = !t.isBefore(start) && t.isBefore(end)
}

/**
 * Charging rule for one kind of day.
 * [Unknown] is deliberately distinct from [Free]: a missing value must never be read as "free".
 */
sealed interface DayRule {
    data class Charged(val window: TimeWindow) : DayRule
    data object Free : DayRule
    data object Unknown : DayRule
}

data class ChargingSchedule(
    val weekdays: DayRule,
    val saturday: DayRule,
    val sundayAndHolidays: DayRule,
)

sealed interface Geometry {
    data class Point(val position: LatLon) : Geometry
    data class Lines(val lines: List<List<LatLon>>) : Geometry
    data class Polygons(val polygons: List<List<List<LatLon>>>) : Geometry
}

data class LatLon(val lat: Double, val lon: Double)

data class ParkingSegment(
    val id: String,
    val name: String,
    val segmentFrom: String?,
    val segmentTo: String?,
    val locationType: LocationType,
    val zone: Zone?,
    val status: MunicipalStatus,
    val hourlyPriceEur: Double?,
    val maxHoursPerDay: Int?,
    val activeFrom: LocalDate?,
    /** Snapshot flag from the dataset ("YES"/"NO"); null if absent. */
    val currentlyActive: Boolean?,
    val schedule: ChargingSchedule,
    val graceMinutes: Int?,
    val smsNumber: String?,
    val specialRule: String?,
    val notes: String?,
    val geometryStatus: String?,
    val sourceUrl: String?,
    val verified: Boolean,
    /** Validated geometry, or null if the dataset has none (or it failed validation). */
    val geometry: Geometry?,
    /** Original GeoJSON geometry object, kept verbatim for rendering. */
    val rawGeometryJson: String?,
)

data class ZoneRule(
    val zone: Zone,
    val hourlyPriceEur: Double?,
    val maxHoursPerDay: Int?,
    val schedule: ChargingSchedule,
    val smsNumber: String?,
    val rolloutStatus: String?,
    val rolloutNote: String?,
    val sourceUrl: String?,
)

data class DataSource(val publisher: String, val description: String, val url: String, val checkedOn: String?)

data class ParkingDataset(
    val verifiedOn: LocalDate?,
    val warning: String?,
    val zoneRules: List<ZoneRule>,
    val segments: List<ParkingSegment>,
    val sources: List<DataSource>,
    /** Extra holiday dates from the data file (added on top of Croatian public holidays). */
    val extraHolidays: Set<LocalDate>,
    /** Features dropped during import, with a reason; surfaced in logs, never rendered. */
    val importIssues: List<String>,
) {
    val mappedSegments get() = segments.filter { it.geometry != null }
    val unmappedSegments get() = segments.filter { it.geometry == null }
}
