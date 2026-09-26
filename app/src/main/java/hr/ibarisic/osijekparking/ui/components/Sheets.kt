package hr.ibarisic.osijekparking.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.ibarisic.osijekparking.R
import hr.ibarisic.osijekparking.domain.ChargingNow
import hr.ibarisic.osijekparking.domain.EffectiveStatus
import hr.ibarisic.osijekparking.domain.ParkingDataset
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.domain.Zone
import hr.ibarisic.osijekparking.ui.NEARBY_RADIUS_M
import hr.ibarisic.osijekparking.ui.Sheet
import hr.ibarisic.osijekparking.ui.map.MapCategory
import hr.ibarisic.osijekparking.ui.map.categoryOf
import hr.ibarisic.osijekparking.ui.theme.ParkingColors
import java.time.ZonedDateTime
import kotlin.math.roundToInt

// --- Segment detail -------------------------------------------------------------------------

@Composable
fun SegmentDetail(
    segment: ParkingSegment,
    status: EffectiveStatus,
    chargingNow: ChargingNow,
    now: ZonedDateTime,
    dataset: ParkingDataset,
) {
    val context = LocalContext.current
    val category = categoryOf(segment, status)
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(segment.name, style = MaterialTheme.typography.headlineSmall)
            extentText(segment)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (segment.zone != null || segment.hourlyPriceEur != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (segment.zone != null) {
                    ZoneBadge(segment.zone, category)
                    Spacer(Modifier.width(12.dp))
                }
                segment.hourlyPriceEur?.let {
                    Text(pricePerHour(it), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        StatusCard(statusTexts(segment, chargingNow, now), chargingNow)

        segment.specialRule?.let {
            Callout(Icons.Filled.Info, stringResource(R.string.detail_special_rule), it, MaterialTheme.colorScheme.primaryContainer)
        }

        if (status !is EffectiveStatus.Free && status !is EffectiveStatus.Unknown) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.detail_schedule), style = MaterialTheme.typography.titleSmall)
                ScheduleRow(stringResource(R.string.schedule_weekdays), dayRuleText(segment.schedule.weekdays))
                ScheduleRow(stringResource(R.string.schedule_saturday), dayRuleText(segment.schedule.saturday))
                ScheduleRow(stringResource(R.string.schedule_sunday_holidays), dayRuleText(segment.schedule.sundayAndHolidays))
            }
        }

        val facts = buildList {
            segment.maxHoursPerDay?.let { add(stringResource(R.string.detail_max_stay) to stringResource(R.string.detail_per_day, maxHoursText(it))) }
            segment.graceMinutes?.let { add(stringResource(R.string.detail_grace) to stringResource(R.string.minutes, it)) }
        }
        if (facts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { (k, v) -> ScheduleRow(k, v) }
            }
        }

        segment.notes?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DataQualityNotes(segment)

        val sms = segment.smsNumber
        if (sms != null && (status == EffectiveStatus.Paid || status is EffectiveStatus.Planned)) {
            FilledTonalButton(onClick = { openSms(context, sms) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_pay_sms, sms))
            }
        }

        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            segment.sourceUrl?.let { url ->
                val publisher = dataset.sources.firstOrNull { it.url == url }?.publisher ?: Uri.parse(url).host.orEmpty()
                TextButton(onClick = { openUrl(context, url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.detail_source, publisher))
                }
            }
            Text(
                listOfNotNull(
                    dataset.verifiedOn?.let { stringResource(R.string.data_verified_on, formatDate(it)) },
                    stringResource(R.string.disclaimer_signs),
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataQualityNotes(segment: ParkingSegment) {
    val notes = buildList {
        if (segment.geometry == null) add(stringResource(R.string.quality_not_mapped))
        else if (!segment.geometryStatus.equals("VERIFIED", ignoreCase = true)) add(stringResource(R.string.quality_geometry_unverified))
        if (!segment.verified) add(stringResource(R.string.quality_unverified))
    }
    if (notes.isNotEmpty()) {
        Callout(Icons.Filled.Warning, null, notes.joinToString("\n"), MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

@Composable
private fun ZoneBadge(zone: Zone, category: MapCategory) {
    Box(
        Modifier
            .background(ParkingColors.of(category), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(zoneLabel(zone), style = MaterialTheme.typography.labelLarge, color = ParkingColors.onColor(category))
    }
}

@Composable
private fun StatusCard(texts: StatusTexts, now: ChargingNow) {
    val (container, accent) = when (now) {
        is ChargingNow.Charging -> Color(0x1FE53935) to Color(0xFFC62828)
        is ChargingNow.NotCharging, ChargingNow.Free -> Color(0x1F1E9E57) to Color(0xFF14804A)
        is ChargingNow.Planned -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        ChargingNow.Unknown -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(texts.headline, style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Text(texts.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Callout(icon: ImageVector, title: String?, body: String, container: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ScheduleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// --- Rows & lists ---------------------------------------------------------------------------

@Composable
fun shortStatus(segment: ParkingSegment, status: EffectiveStatus): String = when (status) {
    EffectiveStatus.Paid -> listOfNotNull(zoneLabel(segment.zone), segment.hourlyPriceEur?.let { pricePerHour(it) }).joinToString(" · ")
    is EffectiveStatus.Planned -> stringResource(R.string.short_planned, zoneLabel(segment.zone))
    EffectiveStatus.Free -> stringResource(R.string.legend_free)
    EffectiveStatus.Unknown -> stringResource(R.string.legend_unknown)
}

@Composable
fun SegmentRow(
    segment: ParkingSegment,
    status: EffectiveStatus,
    onClick: () -> Unit,
    colors: ListItemColors = ListItemDefaults.colors(),
    distanceMeters: Double? = null,
) {
    val category = categoryOf(segment, status)
    ListItem(
        headlineContent = { Text(segment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(shortStatus(segment, status), extentText(segment)).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            val color = ParkingColors.of(category)
            Box(
                Modifier
                    .size(14.dp)
                    .then(
                        if (status is EffectiveStatus.Planned) Modifier.border(3.dp, color, CircleShape)
                        else Modifier.background(color, CircleShape),
                    ),
            )
        },
        trailingContent = when {
            distanceMeters != null -> { { Text(stringResource(R.string.distance_m, distanceMeters.roundToInt()), style = MaterialTheme.typography.labelMedium) } }
            segment.geometry == null -> { { Text(stringResource(R.string.badge_not_mapped), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            else -> null
        },
        colors = colors,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun NearbyContent(
    sheet: Sheet.Nearby,
    statusOf: (ParkingSegment) -> EffectiveStatus,
    onSegment: (ParkingSegment) -> Unit,
) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                Text(sheet.address.title, style = MaterialTheme.typography.headlineSmall)
                if (sheet.address.subtitle.isNotBlank()) {
                    Text(sheet.address.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (sheet.sameStreet.isNotEmpty()) {
            item { ListHeader(stringResource(R.string.nearby_same_street)) }
            items(sheet.sameStreet, key = { "st-" + it.id }) { s -> SegmentRow(s, statusOf(s), { onSegment(s) }, colors) }
        }
        item { ListHeader(stringResource(R.string.nearby_title, NEARBY_RADIUS_M.roundToInt())) }
        if (sheet.nearby.isEmpty()) {
            item { ListNote(stringResource(R.string.nearby_none)) }
        } else {
            items(sheet.nearby, key = { "nb-" + it.first.id }) { (s, d) -> SegmentRow(s, statusOf(s), { onSegment(s) }, colors, d) }
        }
        item {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Callout(Icons.Filled.Info, null, stringResource(R.string.unknown_not_free), MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
    }
}

@Composable
fun AllSegmentsContent(
    dataset: ParkingDataset,
    statusOf: (ParkingSegment) -> EffectiveStatus,
    onSegment: (ParkingSegment) -> Unit,
) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val groups = dataset.segments.groupBy { it.zone }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                Text(stringResource(R.string.all_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.all_subtitle, dataset.segments.size, dataset.mappedSegments.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { (zone, list) ->
            item(key = "h-${zone?.name}") { ListHeader(zoneLabel(zone)) }
            items(list, key = { it.id }) { s -> SegmentRow(s, statusOf(s), { onSegment(s) }, colors) }
        }
    }
}

@Composable
fun AboutContent(dataset: ParkingDataset?) {
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)

        if (dataset != null && dataset.zoneRules.isNotEmpty()) {
            Text(stringResource(R.string.about_zones), style = MaterialTheme.typography.titleSmall)
            dataset.zoneRules.forEach { r ->
                val cat = when (r.zone) { Zone.ZONE_0 -> MapCategory.ZONE_0; Zone.ZONE_1 -> MapCategory.ZONE_1; Zone.ZONE_2 -> MapCategory.ZONE_2; Zone.OTHER -> MapCategory.UNKNOWN }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(ParkingColors.of(cat), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(zoneLabel(r.zone), r.hourlyPriceEur?.let { pricePerHour(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(rolloutLabel(r.rolloutStatus), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (dataset != null) {
            Text(stringResource(R.string.about_sources), style = MaterialTheme.typography.titleSmall)
            dataset.sources.forEach { src ->
                Column(Modifier.fillMaxWidth().clickable { openUrl(context, src.url) }.padding(vertical = 4.dp)) {
                    Text(src.publisher, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(Uri.parse(src.url).host.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            dataset.verifiedOn?.let {
                Text(stringResource(R.string.data_verified_on, formatDate(it)), style = MaterialTheme.typography.bodySmall)
            }
        }
        Callout(Icons.Filled.Warning, null, stringResource(R.string.unknown_not_free) + "\n" + stringResource(R.string.disclaimer_signs), MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.about_map_attribution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rolloutLabel(raw: String?): String = stringResource(
    when (raw?.uppercase()) {
        "ACTIVE" -> R.string.rollout_active
        "PARTIALLY_ACTIVE" -> R.string.rollout_partial
        "PLANNED" -> R.string.rollout_planned
        else -> R.string.rollout_unknown
    },
)

@Composable
private fun ListHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
)

@Composable
private fun ListNote(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
)

private fun openUrl(context: Context, url: String) = startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun openSms(context: Context, number: String) = startSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app to handle it; nothing sensible to fall back to.
    }
}
