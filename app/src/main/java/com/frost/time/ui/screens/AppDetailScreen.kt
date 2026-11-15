package com.frost.time.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.frost.time.data.AppUsageInfo
import com.frost.time.data.HourlyUsage
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appInfo: AppUsageInfo,
    rangeLabel: String,
    totalScreenTimeMs: Long,
    hourlyBuckets: List<HourlyUsage>,
    onBack: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onToggleFreeze: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // Extract this app's exact usage for each of the 24 hours
    val appHourlyUsage = remember(appInfo.packageName, hourlyBuckets) {
        (0..23).map { hour ->
            val bucket = hourlyBuckets.find { it.hour == hour }
            val dur = bucket?.topApps?.find { it.packageName == appInfo.packageName }?.durationMs ?: 0L
            dur
        }
    }

    val maxAppHourly = remember(appHourlyUsage) {
        appHourlyUsage.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    }

    val peakHour = remember(appHourlyUsage) {
        val maxVal = appHourlyUsage.maxOrNull() ?: 0L
        if (maxVal > 0) appHourlyUsage.indexOf(maxVal) else -1
    }

    val initialHour = if (peakHour >= 0) peakHour else 12
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    val selectedHourDuration = appHourlyUsage.getOrElse(selectedHour) { 0L }

    val activeHoursCount = remember(appHourlyUsage) {
        appHourlyUsage.count { it > 0 }
    }
    val avgPerActiveHour = remember(appInfo.durationMs, activeHoursCount) {
        if (activeHoursCount > 0) appInfo.durationMs / activeHoursCount else 0L
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = rangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onLaunchApp(appInfo.packageName) }) {
                        Icon(
                            Icons.Rounded.Launch,
                            contentDescription = "Open App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { openSystemSettings(context, appInfo.packageName) }) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "System Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. App Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // App Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                appInfo.icon?.let {
                                    Image(
                                        bitmap = it.toBitmap(192, 192).asImageBitmap(),
                                        contentDescription = appInfo.appName,
                                        modifier = Modifier.size(52.dp)
                                    )
                                } ?: Text(
                                    text = appInfo.appName.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appInfo.appName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = appInfo.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (appInfo.isSuspended) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "FROZEN",
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = if (appInfo.isSystemApp) "System App" else "Installed App",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            thickness = 1.dp
                        )

                        // Duration & Percentage
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = formatDurationFull(appInfo.durationMs),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                val percent = (appInfo.percentage * 100).toInt()
                                Text(
                                    text = "$percent% of screen time",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = appInfo.percentage.coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }

            // 2. Bento Stats Row (Times Opened, Peak Hour, Average)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Launches / Times Opened
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Opened",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${appInfo.launchCount} times",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Peak Usage Hour
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Peak Hour",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (peakHour >= 0) formatHourLabel(peakHour) else "None",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Average Active Hour
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Avg/Active Hr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatMinutes(avgPerActiveHour),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Touchable 24-Hour Interactive Timeline Graph
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
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
                                text = "24-Hour Graph (Tap Any Bar)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Selected: ${formatHourLabel(selectedHour)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Touchable 24 Bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            (0..23).forEach { hour ->
                                val dur = appHourlyUsage[hour]
                                val isSelected = (hour == selectedHour)
                                val hasUsage = dur > 0

                                val animatedHeight by animateFloatAsState(
                                    targetValue = (dur.toFloat() / maxAppHourly.toFloat()).coerceIn(0.08f, 1f),
                                    animationSpec = tween(durationMillis = 350),
                                    label = "appBarHeight"
                                )

                                val barColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasUsage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { selectedHour = hour }
                                        .padding(horizontal = 1.dp)
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(if (isSelected) 0.85f else 0.55f)
                                            .fillMaxHeight(animatedHeight)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Axis Labels (12 AM, 1 AM, 3 AM, 5 AM, 7 AM, 9 AM, 11 AM, 1 PM, 3 PM, 5 PM, 7 PM, 9 PM, 11 PM)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val labels = listOf("12a", "1a", "3a", "5a", "7a", "9a", "11a", "1p", "3p", "5p", "7p", "9p", "11p")
                            labels.forEach { lbl ->
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Horizontal Hour Chips
                        val chipScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(chipScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (0..23).forEach { h ->
                                val isSelected = (h == selectedHour)
                                val hasUsage = (appHourlyUsage[h] > 0)
                                Surface(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { selectedHour = h },
                                    shape = CircleShape,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        hasUsage -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Text(
                                        text = formatHourLabel(h),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Selected Hour Details Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatHourRange(selectedHour),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (selectedHourDuration > 0) {
                                val hourShare = (selectedHourDuration * 100 / 3_600_000L).coerceAtMost(100)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "$hourShare% of this hour",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (selectedHourDuration > 0) {
                            Text(
                                text = "App was active for ${formatExactTime(selectedHourDuration)} during this hour.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "App was not used between ${formatHourRange(selectedHour)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. App Controls & Actions Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Launch App Button
                            Button(
                                onClick = { onLaunchApp(appInfo.packageName) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Launch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open App", fontWeight = FontWeight.Bold)
                            }

                            // Freeze / Unfreeze Button
                            OutlinedButton(
                                onClick = { onToggleFreeze(appInfo.packageName, !appInfo.isSuspended) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (appInfo.isSuspended) Icons.Rounded.WbSunny else Icons.Rounded.AcUnit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (appInfo.isSuspended) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (appInfo.isSuspended) "Unfreeze" else "Freeze",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // System App Settings Button
                        OutlinedButton(
                            onClick = { openSystemSettings(context, appInfo.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("System App Settings", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun openSystemSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun formatHourLabel(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

private fun formatHourRange(hour: Int): String {
    val next = (hour + 1) % 24
    return "${formatHourLabel(hour)} – ${formatHourLabel(next)}"
}

private fun formatMinutes(durationMs: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    return if (mins > 0) "${mins}m" else "< 1m"
}

private fun formatExactTime(durationMs: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val secs = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return when {
        mins > 0 -> "${mins}m ${secs}s"
        secs > 0 -> "${secs}s"
        else -> "0s"
    }
}

private fun formatDurationFull(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val secs = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        secs > 0 -> "${secs}s"
        else -> "0s"
    }
}