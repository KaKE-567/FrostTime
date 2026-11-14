package com.frost.time.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.data.DayItem

@Composable
fun CalendarDateStrip(
    days: List<DayItem>,
    selectedDay: DayItem,
    onDaySelected: (DayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the end (Today) on initial composition
    LaunchedEffect(days.size) {
        if (days.isNotEmpty()) {
            val selectedIndex = days.indexOfFirst { it.timeInMillis == selectedDay.timeInMillis }
            val targetIndex = if (selectedIndex >= 0) selectedIndex else days.size - 1
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(days, key = { it.timeInMillis }) { dayItem ->
            val isSelected = (dayItem.timeInMillis == selectedDay.timeInMillis)

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer,
                label = "calendarCardBg"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                label = "calendarTextCol"
            )

            val subColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "calendarSubCol"
            )

            Card(
                modifier = Modifier
                    .width(58.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onDaySelected(dayItem) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Day of Week (e.g., SUN, MON)
                    Text(
                        text = if (dayItem.isToday) "TODAY" else dayItem.dayOfWeek.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = if (dayItem.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (dayItem.isToday && !isSelected) MaterialTheme.colorScheme.primary else subColor,
                        maxLines = 1
                    )

                    // Day Number (e.g., 15)
                    Text(
                        text = "${dayItem.dayOfMonth}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        lineHeight = 24.sp
                    )

                    // Month Name (e.g., Nov) or subtle indicator
                    if (dayItem.isToday) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Text(
                            text = dayItem.monthName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = subColor
                        )
                    }
                }
            }
        }
    }
}
