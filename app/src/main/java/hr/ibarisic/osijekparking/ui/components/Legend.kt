package hr.ibarisic.osijekparking.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hr.ibarisic.osijekparking.R
import hr.ibarisic.osijekparking.ui.map.MapCategory
import hr.ibarisic.osijekparking.ui.theme.ParkingColors

private val LEGEND_ORDER = listOf(MapCategory.FREE, MapCategory.ZONE_0, MapCategory.ZONE_1, MapCategory.ZONE_2, MapCategory.UNKNOWN)

@Composable
fun categoryLabel(c: MapCategory): String = stringResource(
    when (c) {
        MapCategory.FREE -> R.string.legend_free
        MapCategory.ZONE_0 -> R.string.zone_0
        MapCategory.ZONE_1 -> R.string.zone_1
        MapCategory.ZONE_2 -> R.string.zone_2
        MapCategory.UNKNOWN -> R.string.legend_unknown
    },
)

/** Compact map legend; tap to collapse into a row of colour dots. */
@Composable
fun Legend(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
    ) {
        AnimatedContent(expanded, label = "legend") { open ->
            if (open) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LEGEND_ORDER.forEach { c -> LegendRow(ParkingColors.of(c), dashed = false, categoryLabel(c)) }
                    LegendRow(MaterialTheme.colorScheme.onSurfaceVariant, dashed = true, stringResource(R.string.legend_planned))
                }
            } else {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LEGEND_ORDER.forEach { c ->
                        Box(Modifier.padding(end = 4.dp).size(10.dp).background(ParkingColors.of(c), CircleShape))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.legend_title), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 8.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 5.dp.toPx(),
                cap = if (dashed) StrokeCap.Butt else StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
