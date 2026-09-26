package hr.ibarisic.osijekparking.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.ibarisic.osijekparking.R
import hr.ibarisic.osijekparking.data.AddressResult
import hr.ibarisic.osijekparking.domain.EffectiveStatus
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.ui.SearchState

@Composable
fun SearchBox(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onInfo: () -> Unit,
    onSegment: (ParkingSegment) -> Unit,
    onAddress: (AddressResult) -> Unit,
    statusOf: (ParkingSegment) -> EffectiveStatus,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.active) { if (!state.active) focusManager.clearFocus() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.active) {
                    IconButton(onClick = { onActiveChange(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_close_search))
                    }
                } else {
                    IconButton(onClick = { focusRequester.requestFocus() }) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = {
                            onQueryChange(it)
                            if (!state.active) onActiveChange(true)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused && !state.active) onActiveChange(true) },
                    )
                }
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear)) }
                } else {
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Filled.Info, stringResource(R.string.cd_about), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (state.active) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SearchResults(state, onSubmit, onSegment, onAddress, statusOf)
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    onSubmit: () -> Unit,
    onSegment: (ParkingSegment) -> Unit,
    onAddress: (AddressResult) -> Unit,
    statusOf: (ParkingSegment) -> EffectiveStatus,
) {
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    LazyColumn(Modifier.heightIn(max = 440.dp).padding(vertical = 8.dp)) {
        if (state.query.isBlank()) {
            item { Hint(stringResource(R.string.search_empty_hint)) }
            return@LazyColumn
        }
        if (state.local.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_section_parking)) }
            items(state.local, key = { "s-" + it.id }) { s ->
                SegmentRow(s, statusOf(s), onClick = { onSegment(s) }, colors = itemColors)
            }
        }
        if (!state.submitted && state.query.trim().length >= 3) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.search_address_action, state.query.trim()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Filled.Search, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable(onClick = onSubmit),
                )
            }
        }
        when {
            state.loading -> item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
            state.failed -> item { Hint(stringResource(R.string.search_failed)) }
            state.addresses.isNotEmpty() -> {
                item {
                    if (state.local.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionHeader(stringResource(R.string.search_section_addresses))
                }
                items(state.addresses, key = { "a-${it.title}-${it.position}" }) { a ->
                    ListItem(
                        headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = a.subtitle.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
                        leadingContent = { Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = itemColors,
                        modifier = Modifier.clickable { onAddress(a) },
                    )
                }
            }
            state.submitted && state.local.isEmpty() -> item { Hint(stringResource(R.string.search_no_results)) }
            state.submitted -> item { Hint(stringResource(R.string.search_no_addresses)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
)
