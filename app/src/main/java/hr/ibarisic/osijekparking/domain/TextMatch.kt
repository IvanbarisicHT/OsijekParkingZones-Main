package hr.ibarisic.osijekparking.domain

import java.text.Normalizer

/** Diacritic- and case-insensitive matching so "sepera" finds "Šetalište kardinala Franje Šepera". */
object TextMatch {

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val IGNORED_TOKENS = setOf("osijek", "ulica", "ul", "hr", "hrvatska")

    /** Tokens useful for matching dataset names: drops house numbers and generic words. */
    fun nameTokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.length >= 2 && it !in IGNORED_TOKENS && it.none(Char::isDigit) }

    fun matches(query: String, candidate: String): Boolean {
        val tokens = nameTokens(query)
        if (tokens.isEmpty()) return false
        val target = normalize(candidate)
        return tokens.all { it in target }
    }

    /** Whether a geocoded road name refers to the same street as a dataset name. */
    fun sameStreet(road: String, datasetName: String): Boolean {
        val a = normalize(road)
        val b = normalize(datasetName)
        return a == b || b.startsWith("$a ") || a.startsWith("$b ")
    }
}
