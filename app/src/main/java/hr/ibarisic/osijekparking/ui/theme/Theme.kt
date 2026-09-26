package hr.ibarisic.osijekparking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import hr.ibarisic.osijekparking.ui.map.MapCategory

/** Parking status colours. Shared by map layers (as ARGB ints) and Compose UI. */
object ParkingColors {
    val Zone0 = Color(0xFFE53935)
    val Zone1 = Color(0xFFFB8C00)
    val Zone2 = Color(0xFFF2C200)
    val Free = Color(0xFF1E9E57)
    val Unknown = Color(0xFF9AA0A6)

    fun of(category: MapCategory): Color = when (category) {
        MapCategory.ZONE_0 -> Zone0
        MapCategory.ZONE_1 -> Zone1
        MapCategory.ZONE_2 -> Zone2
        MapCategory.FREE -> Free
        MapCategory.UNKNOWN -> Unknown
    }

    /** Readable foreground on a solid [of] background. */
    fun onColor(category: MapCategory): Color =
        if (category == MapCategory.ZONE_2) Color(0xFF2B2300) else Color.White
}

private val Light = lightColorScheme(
    primary = Color(0xFF1F4E9D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondaryContainer = Color(0xFFE1E3EC),
    surface = Color(0xFFFCFCFF),
    surfaceContainer = Color(0xFFF0F1F6),
    surfaceContainerHigh = Color(0xFFEAEBF0),
    surfaceContainerLow = Color(0xFFF6F7FB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00429B),
    onPrimaryContainer = Color(0xFFD9E2FF),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun OsijekParkingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
