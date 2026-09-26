package hr.ibarisic.osijekparking.domain

import java.time.LocalDate
import java.time.Month

/**
 * Public holidays of the Republic of Croatia (Zakon o blagdanima, spomendanima i neradnim danima,
 * as amended in 2019, in force since 2020). Movable feasts are derived from Western Easter.
 */
object CroatianHolidays {

    fun isHoliday(date: LocalDate, extra: Set<LocalDate> = emptySet()): Boolean =
        date in extra || date in forYear(date.year)

    private val cache = HashMap<Int, Set<LocalDate>>()

    @Synchronized
    fun forYear(year: Int): Set<LocalDate> = cache.getOrPut(year) {
        val easter = easterSunday(year)
        setOf(
            LocalDate.of(year, Month.JANUARY, 1),    // Nova godina
            LocalDate.of(year, Month.JANUARY, 6),    // Bogojavljenje
            easter,                                  // Uskrs
            easter.plusDays(1),                      // Uskrsni ponedjeljak
            LocalDate.of(year, Month.MAY, 1),        // Praznik rada
            LocalDate.of(year, Month.MAY, 30),       // Dan državnosti
            easter.plusDays(60),                     // Tijelovo
            LocalDate.of(year, Month.JUNE, 22),      // Dan antifašističke borbe
            LocalDate.of(year, Month.AUGUST, 5),     // Dan pobjede i domovinske zahvalnosti
            LocalDate.of(year, Month.AUGUST, 15),    // Velika Gospa
            LocalDate.of(year, Month.NOVEMBER, 1),   // Svi sveti
            LocalDate.of(year, Month.NOVEMBER, 18),  // Dan sjećanja na žrtve Domovinskog rata
            LocalDate.of(year, Month.DECEMBER, 25),  // Božić
            LocalDate.of(year, Month.DECEMBER, 26),  // Sveti Stjepan
        )
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    internal fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
