package hr.ibarisic.osijekparking.data

import hr.ibarisic.osijekparking.domain.DayRule
import hr.ibarisic.osijekparking.domain.Geometry
import hr.ibarisic.osijekparking.domain.MunicipalStatus
import hr.ibarisic.osijekparking.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ParkingDataParserTest {

    private val parser = ParkingDataParser()

    private fun asset(name: String) = File("src/main/assets/parking/$name").readText()

    @Test fun `bundled dataset parses`() {
        val ds = parser.parse(asset("osijek_parking_segments.geojson"), asset("osijek_parking_data.json"))
        assertEquals(20, ds.segments.size)
        assertEquals(3, ds.zoneRules.size)
        assertEquals(LocalDate.of(2026, 9, 26), ds.verifiedOn)
        // The bundled GeoJSON has no geometry yet: nothing may be drawn.
        assertTrue(ds.mappedSegments.isEmpty())
        assertTrue(ds.importIssues.isEmpty())

        val gaj = ds.segments.first { it.id == "OS-Z0-011" }
        assertEquals(Zone.ZONE_0, gaj.zone)
        assertEquals(1.5, gaj.hourlyPriceEur!!, 0.0)
        assertEquals(3, gaj.maxHoursPerDay)
        assertEquals(DayRule.Free, gaj.schedule.sundayAndHolidays)
        assertNotNull(gaj.specialRule)

        val z1 = ds.segments.first { it.id == "OS-Z1-001" }
        assertNull(z1.maxHoursPerDay) // empty in data: not shown rather than guessed
    }

    @Test fun `accepts field aliases and LineString geometry`() {
        val geo = """
            {"type":"Feature","properties":{"segment_id":"X","street":"Županijska ulica","zone":"0",
             "price_per_hour":1.50,"status":"PAID","active_from":"2026-09-21"},
             "geometry":{"type":"LineString","coordinates":[[18.6935,45.5590],[18.6950,45.5600]]}}
        """.trimIndent()
        val s = parser.parse(geo, null).segments.single()
        assertEquals("Županijska ulica", s.name)
        assertEquals(MunicipalStatus.PAID, s.status)
        assertEquals(1.5, s.hourlyPriceEur!!, 0.0)
        assertTrue(s.geometry is Geometry.Lines)
        assertNotNull(s.rawGeometryJson)
        // No hours anywhere: unknown, not free.
        assertEquals(DayRule.Unknown, s.schedule.weekdays)
    }

    @Test fun `rejects swapped coordinates`() {
        val geo = """
            {"type":"Feature","properties":{"segment_id":"X","name":"A","status":"PAID"},
             "geometry":{"type":"LineString","coordinates":[[45.5590,18.6935],[45.5600,18.6950]]}}
        """.trimIndent()
        val ds = parser.parse(geo, null)
        assertNull(ds.segments.single().geometry)
        assertEquals(1, ds.importIssues.size)
    }

    @Test fun `unknown status maps to not confirmed`() {
        val geo = """{"type":"Feature","properties":{"segment_id":"X","name":"A"},"geometry":null}"""
        assertEquals(MunicipalStatus.NOT_CONFIRMED, parser.parse(geo, null).segments.single().status)
    }

    @Test fun `day rule parsing`() {
        assertTrue(parser.parseDayRule("07:00-21:00") is DayRule.Charged)
        assertTrue(parser.parseDayRule("7:00 – 15:00") is DayRule.Charged)
        assertEquals(DayRule.Free, parser.parseDayRule("FREE"))
        assertEquals(DayRule.Unknown, parser.parseDayRule(""))
        assertEquals(DayRule.Unknown, parser.parseDayRule("po dogovoru"))
    }
}
