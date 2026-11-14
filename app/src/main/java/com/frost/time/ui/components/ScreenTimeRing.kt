package com.frost.time.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.data.AppUsageInfo
import com.frost.time.ui.theme.RingPalette
import java.util.concurrent.TimeUnit

@Composable
fun ScreenTimeRing(
    totalScreenTimeMs: Long,
    topApps: List<AppUsageInfo>,
    rangeTitle: String = "Today",
    modifier: Modifier = Modifier
) {
    val hours = TimeUnit.MILLISECONDS.toHours(totalScreenTimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalScreenTimeMs) % 60

    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(totalScreenTimeMs) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val appsToDraw = topApps.take(6)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Multi-segment Donut Ring Canvas
        Canvas(
            modifier = Modifier.size(240.dp)
        ) {
            val strokeWidth = 22.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // 1. Background Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Colored Slices for Top Apps
            if (totalScreenTimeMs > 0) {
                var currentAngle = -90f
                val totalSweepAllowed = 360f * animationProgress.value

                appsToDraw.forEachIndexed { index, app ->
                    val color = RingPalette[index % RingPalette.size]
                    val sweep = (app.percentage * 360f) * animationProgress.value

                    // Draw arc slice with subtle padding gap
                    if (sweep > 3f) {
                        drawArc(
                            color = color,
                            startAngle = currentAngle + 2f,
                            sweepAngle = (sweep - 4f).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    currentAngle += sweep
                }
            }
        }

        // Centered Bold Screen Time
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Screen Time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$hours",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 44.sp
                )
                Text(
                    text = "h",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$minutes",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 44.sp
                )
                Text(
                    text = "m",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = rangeTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
