package hr.ibarisic.osijekparking.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import hr.ibarisic.osijekparking.R
import hr.ibarisic.osijekparking.domain.ChargingNow
import hr.ibarisic.osijekparking.domain.DayRule
import hr.ibarisic.osijekparking.domain.LocationType
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.domain.Zone
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HR = Locale.forLanguageTag("hr-HR")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d. M. yyyy.")

fun formatPrice(eur: Double): String = String.format(HR, "%.2f €", eur)

fun formatTime(t: LocalTime): String = if (t == LocalTime.MAX) "24:00" else t.format(TIME)

fun formatDate(d: LocalDate): String = d.format(DATE)

@Composable
fun zoneLabel(zone: Zone?): String = when (zone) {
    Zone.ZONE_0 -> stringResource(R.string.zone_0)
    Zone.ZONE_1 -> stringResource(R.string.zone_1)
    Zone.ZONE_2 -> stringResource(R.string.zone_2)
    Zone.OTHER, null -> stringResource(R.string.zone_unknown)
}

@Composable
fun pricePerHour(eur: Double): String = stringResource(R.string.price_per_hour, formatPrice(eur))

@Composable
fun dayRuleText(rule: DayRule): String = when (rule) {
    is DayRule.Charged -> "${formatTime(rule.window.start)} – ${formatTime(rule.window.end)}"
    DayRule.Free -> stringResource(R.string.schedule_free)
    DayRule.Unknown -> stringResource(R.string.schedule_unknown)
}

/** Where on the street the segment applies, from segment_from/segment_to. */
@Composable
fun extentText(s: ParkingSegment): String? {
    val from = s.segmentFrom
    val to = s.segmentTo
    return when (s.locationType) {
        LocationType.ADDRESS_RANGE -> when {
            from != null && to != null -> stringResource(R.string.extent_house_numbers, from, to)
            from != null -> stringResource(R.string.extent_house_number, from)
            else -> null
        }
        LocationType.WHOLE_LOCATION -> stringResource(R.string.extent_whole)
        LocationType.PARKING_AREA -> if (from != null && to != null) stringResource(R.string.extent_parking_between, from, to)
        else stringResource(R.string.extent_parking)
        LocationType.STREET_SEGMENT, LocationType.OTHER -> when {
            from != null && to != null -> stringResource(R.string.extent_from_to, from, to)
            else -> null
        }
    }
}

@Composable
fun maxHoursText(hours: Int): String = pluralStringResource(R.plurals.hours, hours, hours)

data class StatusTexts(val headline: String, val detail: String)

@Composable
fun statusTexts(segment: ParkingSegment, now: ChargingNow, at: ZonedDateTime): StatusTexts = when (now) {
    is ChargingNow.Charging -> StatusTexts(
        stringResource(R.string.status_charging),
        listOfNotNull(
            segment.hourlyPriceEur?.let { pricePerHour(it) },
            stringResource(R.string.status_until, formatTime(now.until.toLocalTime())),
        ).joinToString(" · "),
    )
    is ChargingNow.NotCharging -> StatusTexts(
        stringResource(R.string.status_free_now),
        now.nextStart?.let { stringResource(R.string.status_next_start, relativeWhen(at, it)) }
            ?: stringResource(R.string.status_next_start_unknown),
    )
    ChargingNow.Free -> StatusTexts(stringResource(R.string.status_free), stringResource(R.string.status_free_detail))
    is ChargingNow.Planned -> StatusTexts(
        stringResource(R.string.status_planned),
        now.startsOn?.let { stringResource(R.string.status_planned_from, formatDate(it)) }
            ?: stringResource(R.string.status_planned_no_date),
    )
    ChargingNow.Unknown -> StatusTexts(stringResource(R.string.status_unknown), stringResource(R.string.status_unknown_detail))
}

/** "danas u 07:00", "sutra u 07:00", "u ponedjeljak u 07:00", or a full date. */
@Composable
fun relativeWhen(now: ZonedDateTime, target: ZonedDateTime): String {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
    val time = formatTime(target.toLocalTime())
    val weekdays = stringArrayResource(R.array.weekdays_accusative)
    return when {
        days == 0L -> stringResource(R.string.when_today, time)
        days == 1L -> stringResource(R.string.when_tomorrow, time)
        days in 2..6 -> stringResource(R.string.when_weekday, weekdays[target.dayOfWeek.value - 1], time)
        else -> stringResource(R.string.when_date, formatDate(target.toLocalDate()), time)
    }
}
