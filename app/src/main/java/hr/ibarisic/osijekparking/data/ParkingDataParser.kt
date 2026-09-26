package hr.ibarisic.osijekparking.data

import hr.ibarisic.osijekparking.domain.ChargingSchedule
import hr.ibarisic.osijekparking.domain.DataSource
import hr.ibarisic.osijekparking.domain.DayRule
import hr.ibarisic.osijekparking.domain.GeoUtils
import hr.ibarisic.osijekparking.domain.Geometry
import hr.ibarisic.osijekparking.domain.LatLon
import hr.ibarisic.osijekparking.domain.LocationType
import hr.ibarisic.osijekparking.domain.MunicipalStatus
import hr.ibarisic.osijekparking.domain.ParkingDataset
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.domain.TimeWindow
import hr.ibarisic.osijekparking.domain.Zone
import hr.ibarisic.osijekparking.domain.ZoneRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Turns the two data files into a [ParkingDataset]:
 *  - a GeoJSON FeatureCollection with one feature per segment (source of truth for segments), and
 *  - an optional metadata file with `metadata`, `zone_rules`, `sources` and optional `holidays`.
 *
 * Missing per-segment values fall back to the segment's zone rule. Values missing from both stay
 * unknown; nothing is defaulted to "free".
 */
class ParkingDataParser(private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }) {

    fun parse(geoJson: String, metadataJson: String?): ParkingDataset {
        val issues = mutableListOf<String>()
        val meta = metadataJson?.let { json.parseToJsonElement(it).jsonObject }
        val metadata = meta?.obj("metadata")
        val zoneRules = meta?.arr("zone_rules")?.mapNotNull { parseZoneRule(it.jsonObject) }.orEmpty()
        val rulesByZone = zoneRules.associateBy { it.zone }

        val root = json.parseToJsonElement(geoJson).jsonObject
        val features = when (root.str("type")) {
            "FeatureCollection" -> root.arr("features").orEmpty()
            "Feature" -> listOf(root)
            else -> error("Unsupported GeoJSON root type: ${root.str("type")}")
        }

        val seen = HashSet<String>()
        val segments = features.mapIndexedNotNull { index, el ->
            val feature = el as? JsonObject ?: return@mapIndexedNotNull null.also { issues += "Feature #$index is not an object" }
            val props = feature.obj("properties") ?: JsonObject(emptyMap())
            val id = props.str("segment_id") ?: feature.str("id") ?: "feature-$index"
            if (!seen.add(id)) {
                issues += "$id: duplicate segment_id, ignored"
                return@mapIndexedNotNull null
            }
            parseSegment(id, props, feature["geometry"], rulesByZone, issues)
        }

        return ParkingDataset(
            verifiedOn = metadata?.str("verified_on")?.let(::parseDate),
            warning = metadata?.str("warning"),
            zoneRules = zoneRules,
            segments = segments,
            sources = meta?.arr("sources")?.mapNotNull { src ->
                val o = src as? JsonObject ?: return@mapNotNull null
                DataSource(
                    publisher = o.str("publisher") ?: return@mapNotNull null,
                    description = o.str("description").orEmpty(),
                    url = o.str("url") ?: return@mapNotNull null,
                    checkedOn = o.str("checked_on"),
                )
            }.orEmpty(),
            extraHolidays = meta?.arr("holidays")?.mapNotNull { (it as? JsonPrimitive)?.content?.let(::parseDate) }?.toSet().orEmpty(),
            importIssues = issues,
        )
    }

    private fun parseZoneRule(o: JsonObject): ZoneRule? {
        val zone = Zone.parse(o.str("zone")) ?: return null
        return ZoneRule(
            zone = zone,
            hourlyPriceEur = o.num("hourly_price_eur", "price_per_hour"),
            maxHoursPerDay = o.num("max_hours_per_day")?.toInt(),
            schedule = ChargingSchedule(
                weekdays = parseDayRule(o.str("weekday_hours")),
                saturday = parseDayRule(o.str("saturday_hours")),
                sundayAndHolidays = parseDayRule(o.str("sunday_holidays")),
            ),
            smsNumber = o.str("sms_number"),
            rolloutStatus = o.str("current_rollout_status"),
            rolloutNote = o.str("rollout_note"),
            sourceUrl = o.str("source_url"),
        )
    }

    private fun parseSegment(
        id: String,
        p: JsonObject,
        geometryEl: JsonElement?,
        rules: Map<Zone, ZoneRule>,
        issues: MutableList<String>,
    ): ParkingSegment {
        val zone = Zone.parse(p.str("zone"))
        val rule = zone?.let(rules::get)

        fun dayRule(key: String, fallback: DayRule?): DayRule {
            val own = parseDayRule(p.str(key))
            return if (own == DayRule.Unknown) fallback ?: DayRule.Unknown else own
        }

        val geometry = geometryEl?.takeUnless { it is JsonNull }?.let { g ->
            runCatching { parseGeometry(g.jsonObject) }
                .onFailure { issues += "$id: invalid geometry (${it.message}); segment not drawn" }
                .getOrNull()
        }

        return ParkingSegment(
            id = id,
            name = p.str("name", "street") ?: id,
            segmentFrom = p.str("segment_from"),
            segmentTo = p.str("segment_to"),
            locationType = LocationType.parse(p.str("location_type")),
            zone = zone,
            status = MunicipalStatus.parse(p.str("municipal_status", "status")),
            hourlyPriceEur = p.num("hourly_price_eur", "price_per_hour") ?: rule?.hourlyPriceEur,
            maxHoursPerDay = p.num("max_hours_per_day")?.toInt() ?: rule?.maxHoursPerDay,
            activeFrom = p.str("active_from")?.let(::parseDate),
            currentlyActive = p.str("currently_active")?.let(::parseYesNo),
            schedule = ChargingSchedule(
                weekdays = dayRule("weekday_hours", rule?.schedule?.weekdays),
                saturday = dayRule("saturday_hours", rule?.schedule?.saturday),
                sundayAndHolidays = dayRule("sunday_holidays", rule?.schedule?.sundayAndHolidays),
            ),
            graceMinutes = p.num("grace_minutes")?.toInt(),
            smsNumber = p.str("sms_number") ?: rule?.smsNumber,
            specialRule = p.str("special_rule"),
            notes = p.str("notes"),
            geometryStatus = p.str("geometry_status"),
            sourceUrl = p.str("source_url"),
            verified = p.str("verified")?.let(::parseYesNo) == true,
            geometry = geometry,
            rawGeometryJson = geometry?.let { geometryEl.toString() },
        )
    }

    // --- Geometry ---------------------------------------------------------------------------

    internal fun parseGeometry(o: JsonObject): Geometry {
        val coords = o["coordinates"] ?: error("missing coordinates")
        val g = when (val type = o.str("type")) {
            "Point" -> Geometry.Point(position(coords))
            "LineString" -> Geometry.Lines(listOf(line(coords, 2)))
            "MultiLineString" -> Geometry.Lines(coords.jsonArray.map { line(it, 2) })
            "Polygon" -> Geometry.Polygons(listOf(polygon(coords)))
            "MultiPolygon" -> Geometry.Polygons(coords.jsonArray.map(::polygon))
            else -> error("unsupported geometry type $type")
        }
        val outside = GeoUtils.allPoints(g).firstOrNull { it !in GeoUtils.OSIJEK_BOUNDS }
        require(outside == null) { "coordinate $outside is outside Osijek (expected [lon, lat] order)" }
        return g
    }

    private fun position(el: JsonElement): LatLon {
        val a = el.jsonArray
        require(a.size >= 2) { "position needs [lon, lat]" }
        val lon = (a[0] as JsonPrimitive).doubleOrNull ?: error("non-numeric longitude")
        val lat = (a[1] as JsonPrimitive).doubleOrNull ?: error("non-numeric latitude")
        return LatLon(lat, lon)
    }

    private fun line(el: JsonElement, min: Int) = el.jsonArray.map(::position).also {
        require(it.size >= min) { "line needs at least $min positions" }
    }

    private fun polygon(el: JsonElement) = el.jsonArray.map { line(it, 4) }.also {
        require(it.isNotEmpty()) { "polygon has no rings" }
    }

    // --- Scalars ----------------------------------------------------------------------------

    internal fun parseDayRule(raw: String?): DayRule {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return DayRule.Unknown
        if (v.equals("FREE", true) || v.equals("BESPLATNO", true)) return DayRule.Free
        val m = Regex("""^(\d{1,2}):(\d{2})\s*[-–—]\s*(\d{1,2}):(\d{2})$""").find(v) ?: return DayRule.Unknown
        val (h1, m1, h2, m2) = m.destructured
        val start = LocalTime.of(h1.toInt(), m1.toInt())
        val end = if (h2 == "24" && m2 == "00") LocalTime.MAX else LocalTime.of(h2.toInt(), m2.toInt())
        return if (end.isAfter(start)) DayRule.Charged(TimeWindow(start, end)) else DayRule.Unknown
    }

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    private fun parseYesNo(s: String): Boolean? = when (s.trim().uppercase()) {
        "YES", "TRUE", "DA", "1" -> true
        "NO", "FALSE", "NE", "0" -> false
        else -> null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray

    /** First non-blank value among [keys], numbers rendered as text. */
    private fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
    }
}
