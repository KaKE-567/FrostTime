package com.frost.time.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.frost.time.data.TimeRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        val ranges = TimeRange.entries
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = range.title,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
