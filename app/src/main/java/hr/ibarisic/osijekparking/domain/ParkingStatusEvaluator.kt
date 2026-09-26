package hr.ibarisic.osijekparking.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

val OSIJEK_ZONE: ZoneId = ZoneId.of("Europe/Zagreb")

/** What the segment is, independent of the time of day. Drives map colour. */
sealed interface EffectiveStatus {
    /** Paid parking in force (active_from reached). */
    data object Paid : EffectiveStatus
    /** Confirmed free by the dataset. */
    data object Free : EffectiveStatus
    /** Paid parking announced but not yet in force. [startsOn] is null when no date is known. */
    data class Planned(val startsOn: LocalDate?) : EffectiveStatus
    /** Dataset cannot establish the status. */
    data object Unknown : EffectiveStatus
}

/** What is happening right now. */
sealed interface ChargingNow {
    data class Charging(val until: ZonedDateTime) : ChargingNow
    /** Not charging now. [nextStart] is null when the next start can't be determined reliably. */
    data class NotCharging(val nextStart: ZonedDateTime?) : ChargingNow
    data object Free : ChargingNow
    data class Planned(val startsOn: LocalDate?) : ChargingNow
    data object Unknown : ChargingNow
}

class ParkingStatusEvaluator(private val extraHolidays: Set<LocalDate> = emptySet()) {

    fun effectiveStatus(segment: ParkingSegment, today: LocalDate): EffectiveStatus = when (segment.status) {
        MunicipalStatus.FREE -> EffectiveStatus.Free
        MunicipalStatus.NOT_CONFIRMED -> EffectiveStatus.Unknown
        MunicipalStatus.PLANNED -> EffectiveStatus.Planned(segment.activeFrom)
        MunicipalStatus.PAID -> {
            val from = segment.activeFrom
            when {
                from != null && today.isBefore(from) -> EffectiveStatus.Planned(from)
                // Explicit "not active" contradicts PAID; don't guess which one is right.
                segment.currentlyActive == false -> EffectiveStatus.Unknown
                from != null -> EffectiveStatus.Paid
                segment.currentlyActive == true -> EffectiveStatus.Paid
                else -> EffectiveStatus.Unknown
            }
        }
    }

    fun chargingNow(segment: ParkingSegment, now: ZonedDateTime): ChargingNow {
        val local = now.withZoneSameInstant(OSIJEK_ZONE)
        return when (val status = effectiveStatus(segment, local.toLocalDate())) {
            EffectiveStatus.Free -> ChargingNow.Free
            EffectiveStatus.Unknown -> ChargingNow.Unknown
            is EffectiveStatus.Planned -> ChargingNow.Planned(status.startsOn)
            EffectiveStatus.Paid -> when (val rule = ruleFor(segment.schedule, local.toLocalDate())) {
                DayRule.Unknown -> ChargingNow.Unknown
                is DayRule.Charged, DayRule.Free -> {
                    if (rule is DayRule.Charged && local.toLocalTime() in rule.window) {
                        ChargingNow.Charging(local.with(rule.window.end))
                    } else {
                        ChargingNow.NotCharging(nextChargeStart(segment.schedule, local))
                    }
                }
            }
        }
    }

    fun ruleFor(schedule: ChargingSchedule, date: LocalDate): DayRule = when {
        CroatianHolidays.isHoliday(date, extraHolidays) -> schedule.sundayAndHolidays
        date.dayOfWeek == DayOfWeek.SUNDAY -> schedule.sundayAndHolidays
        date.dayOfWeek == DayOfWeek.SATURDAY -> schedule.saturday
        else -> schedule.weekdays
    }

    /**
     * First charging start strictly after [from]. Returns null if an unknown day rule is hit
     * first, because then we can't say when charging resumes.
     */
    private fun nextChargeStart(schedule: ChargingSchedule, from: ZonedDateTime): ZonedDateTime? {
        for (offset in 0..14L) {
            val date = from.toLocalDate().plusDays(offset)
            when (val rule = ruleFor(schedule, date)) {
                DayRule.Unknown -> return null
                DayRule.Free -> Unit
                is DayRule.Charged -> {
                    val start = ZonedDateTime.of(date, rule.window.start, OSIJEK_ZONE)
                    if (start.isAfter(from)) return start
                }
            }
        }
        return null
    }
}
