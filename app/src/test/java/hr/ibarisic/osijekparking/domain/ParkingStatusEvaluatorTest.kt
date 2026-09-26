package hr.ibarisic.osijekparking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class ParkingStatusEvaluatorTest {

    private val evaluator = ParkingStatusEvaluator()

    private val standard = ChargingSchedule(
        weekdays = DayRule.Charged(TimeWindow(LocalTime.of(7, 0), LocalTime.of(21, 0))),
        saturday = DayRule.Charged(TimeWindow(LocalTime.of(7, 0), LocalTime.of(15, 0))),
        sundayAndHolidays = DayRule.Free,
    )

    private fun segment(
        status: MunicipalStatus = MunicipalStatus.PAID,
        activeFrom: LocalDate? = LocalDate.of(2026, 9, 21),
        currentlyActive: Boolean? = true,
        schedule: ChargingSchedule = standard,
    ) = ParkingSegment(
        id = "T", name = "Test", segmentFrom = null, segmentTo = null, locationType = LocationType.STREET_SEGMENT,
        zone = Zone.ZONE_0, status = status, hourlyPriceEur = 1.5, maxHoursPerDay = 3, activeFrom = activeFrom,
        currentlyActive = currentlyActive, schedule = schedule, graceMinutes = 15, smsNumber = null, specialRule = null,
        notes = null, geometryStatus = null, sourceUrl = null, verified = true, geometry = null, rawGeometryJson = null,
    )

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, OSIJEK_ZONE)

    @Test fun `weekday within hours is charging until end`() {
        val r = evaluator.chargingNow(segment(), at(2026, 9, 28, 10)) // Monday
        assertTrue(r is ChargingNow.Charging)
        assertEquals(LocalTime.of(21, 0), (r as ChargingNow.Charging).until.toLocalTime())
    }

    @Test fun `end time is exclusive`() {
        val r = evaluator.chargingNow(segment(), at(2026, 9, 28, 21))
        assertTrue(r is ChargingNow.NotCharging)
        assertEquals(at(2026, 9, 29, 7), (r as ChargingNow.NotCharging).nextStart)
    }

    @Test fun `saturday afternoon is free until monday`() {
        val r = evaluator.chargingNow(segment(), at(2026, 9, 26, 16)) // Saturday
        assertEquals(at(2026, 9, 28, 7), (r as ChargingNow.NotCharging).nextStart)
    }

    @Test fun `sunday is free`() {
        val r = evaluator.chargingNow(segment(), at(2026, 9, 27, 12))
        assertTrue(r is ChargingNow.NotCharging)
    }

    @Test fun `public holiday on weekday follows sunday rule`() {
        // Thursday 5 Aug 2027 is Victory Day.
        val r = evaluator.chargingNow(segment(), at(2027, 8, 5, 10))
        assertEquals(at(2027, 8, 6, 7), (r as ChargingNow.NotCharging).nextStart)
    }

    @Test fun `before active_from is planned, not free`() {
        val r = evaluator.chargingNow(segment(), at(2026, 9, 20, 10))
        assertEquals(ChargingNow.Planned(LocalDate.of(2026, 9, 21)), r)
    }

    @Test fun `not confirmed is unknown`() {
        assertEquals(ChargingNow.Unknown, evaluator.chargingNow(segment(status = MunicipalStatus.NOT_CONFIRMED), at(2026, 9, 28, 10)))
    }

    @Test fun `paid but flagged inactive is unknown`() {
        assertEquals(ChargingNow.Unknown, evaluator.chargingNow(segment(currentlyActive = false), at(2026, 9, 28, 10)))
    }

    @Test fun `missing hours for today is unknown, never free`() {
        val s = segment(schedule = standard.copy(weekdays = DayRule.Unknown))
        assertEquals(ChargingNow.Unknown, evaluator.chargingNow(s, at(2026, 9, 28, 10)))
    }

    @Test fun `next start unknown when an unknown day comes first`() {
        val s = segment(schedule = standard.copy(sundayAndHolidays = DayRule.Unknown))
        val r = evaluator.chargingNow(s, at(2026, 9, 26, 16)) // Saturday after hours; Sunday unknown
        assertNull((r as ChargingNow.NotCharging).nextStart)
    }

    @Test fun `free status is free`() {
        assertEquals(ChargingNow.Free, evaluator.chargingNow(segment(status = MunicipalStatus.FREE), at(2026, 9, 28, 10)))
    }
}
