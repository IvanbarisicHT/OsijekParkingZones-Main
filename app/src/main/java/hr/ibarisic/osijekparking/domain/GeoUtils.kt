package hr.ibarisic.osijekparking.domain

import kotlin.math.cos
import kotlin.math.hypot

/** Planar approximations; accurate to well under a metre at city scale. */
object GeoUtils {

    /** Generous box around the City of Osijek, used to reject obviously wrong coordinates. */
    val OSIJEK_BOUNDS = Bounds(south = 45.44, west = 18.52, north = 45.64, east = 18.86)
    val OSIJEK_CENTER = LatLon(45.5550, 18.6955)

    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
        operator fun contains(p: LatLon) = p.lat in south..north && p.lon in west..east
    }

    fun allPoints(g: Geometry): List<LatLon> = when (g) {
        is Geometry.Point -> listOf(g.position)
        is Geometry.Lines -> g.lines.flatten()
        is Geometry.Polygons -> g.polygons.flatten().flatten()
    }

    fun bounds(g: Geometry): Bounds {
        val pts = allPoints(g)
        return Bounds(pts.minOf { it.lat }, pts.minOf { it.lon }, pts.maxOf { it.lat }, pts.maxOf { it.lon })
    }

    fun distanceMeters(p: LatLon, g: Geometry): Double = when (g) {
        is Geometry.Point -> pointToPoint(p, g.position)
        is Geometry.Lines -> g.lines.minOf { polylineDistance(p, it) }
        is Geometry.Polygons -> g.polygons.minOf { rings ->
            if (rings.isNotEmpty() && insideRing(p, rings[0]) && rings.drop(1).none { insideRing(p, it) }) 0.0
            else rings.minOf { polylineDistance(p, it) }
        }
    }

    private const val M_PER_DEG_LAT = 111_320.0

    private fun toXY(origin: LatLon, p: LatLon): Pair<Double, Double> {
        val mPerDegLon = M_PER_DEG_LAT * cos(Math.toRadians(origin.lat))
        return (p.lon - origin.lon) * mPerDegLon to (p.lat - origin.lat) * M_PER_DEG_LAT
    }

    fun pointToPoint(a: LatLon, b: LatLon): Double {
        val (x, y) = toXY(a, b)
        return hypot(x, y)
    }

    private fun polylineDistance(p: LatLon, line: List<LatLon>): Double {
        if (line.size == 1) return pointToPoint(p, line[0])
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val (ax, ay) = toXY(p, line[i])
            val (bx, by) = toXY(p, line[i + 1])
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + t * dx, ay + t * dy))
        }
        return best
    }

    private fun insideRing(p: LatLon, ring: List<LatLon>): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
            ) inside = !inside
            j = i
        }
        return inside
    }
}
