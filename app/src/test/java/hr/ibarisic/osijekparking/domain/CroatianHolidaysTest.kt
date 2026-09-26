package hr.ibarisic.osijekparking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CroatianHolidaysTest {

    @Test fun `easter dates`() {
        assertEquals(LocalDate.of(2025, 4, 20), CroatianHolidays.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), CroatianHolidays.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), CroatianHolidays.easterSunday(2027))
    }

    @Test fun `movable and fixed holidays`() {
        assertTrue(CroatianHolidays.isHoliday(LocalDate.of(2026, 4, 6)))   // Easter Monday
        assertTrue(CroatianHolidays.isHoliday(LocalDate.of(2026, 6, 4)))   // Corpus Christi
        assertTrue(CroatianHolidays.isHoliday(LocalDate.of(2026, 11, 18)))
        assertFalse(CroatianHolidays.isHoliday(LocalDate.of(2026, 9, 28)))
    }

    @Test fun `extra holidays from data are honoured`() {
        val d = LocalDate.of(2026, 12, 7)
        assertTrue(CroatianHolidays.isHoliday(d, setOf(d)))
    }
}
