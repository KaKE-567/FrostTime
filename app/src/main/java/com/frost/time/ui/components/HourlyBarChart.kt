package com.frost.time.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.data.HourlyUsage
import com.frost.time.ui.theme.FrostCyan
import com.frost.time.ui.theme.FrostedCard
import com.frost.time.ui.theme.TextPrimary
import com.frost.time.ui.theme.TextSecondary
import java.util.concurrent.TimeUnit

@Composable
fun HourlyBarChart(
    hourlyBuckets: List<HourlyUsage>,
    modifier: Modifier = Modifier
) {
    val peak = hourlyBuckets.maxByOrNull { it.durationMs }
    val peakHour = peak?.hour ?: 0
    val peakFormatted = formatHour(peakHour)
    val peakDurationMin = TimeUnit.MILLISECONDS.toMinutes(peak?.durationMs ?: 0L)

    FrostCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        backgroundColor = FrostedCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HOURLY DISTRIBUTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                if (peakDurationMin > 0) {
                    Text(
                        text = "Peak: $peakFormatted (${peakDurationMin}m)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 24 Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                hourlyBuckets.forEach { bucket ->
                    val isPeak = bucket.hour == peakHour && bucket.durationMs > 0
                    val animatedHeight by animateFloatAsState(
                        targetValue = bucket.percentage.coerceIn(0.06f, 1f),
                        animationSpec = tween(durationMillis = 600),
                        label = "barHeight"
                    )

                    val barColor = if (isPeak) {
                        MaterialTheme.colorScheme.primary
                    } else if (bucket.durationMs > 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(animatedHeight)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Axis labels: 12 AM, 6 AM, 12 PM, 6 PM, 11 PM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "12 AM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = "6 AM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = "12 PM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = "6 PM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = "11 PM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

private fun formatHour(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
