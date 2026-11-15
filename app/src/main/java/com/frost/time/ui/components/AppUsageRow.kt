package com.frost.time.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.frost.time.data.AppUsageInfo
import com.frost.time.ui.theme.RingPalette
import java.util.concurrent.TimeUnit

private fun openSystemSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

@Composable
fun AppUsageRow(
    item: AppUsageInfo,
    index: Int,
    isRootActive: Boolean,
    onAppClick: (AppUsageInfo) -> Unit = {},
    onLaunchApp: (String) -> Unit,
    onToggleFreeze: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = item.percentage,
        label = "appProgress"
    )

    val minutes = TimeUnit.MILLISECONDS.toMinutes(item.durationMs)
    val formattedDuration = if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        "${h}h ${m}m"
    } else {
        "${minutes}m"
    }
    val percentageInt = (item.percentage * 100).toInt()

    // Assign color consistent with the donut ring
    val appAccentColor = if (item.isSuspended) {
        MaterialTheme.colorScheme.tertiary
    } else {
        RingPalette[index % RingPalette.size]
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = { onAppClick(item) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon inside squircle container
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    item.icon?.let {
                        Image(
                            bitmap = it.toBitmap(128, 128).asImageBitmap(),
                            contentDescription = item.appName,
                            modifier = Modifier.size(36.dp)
                        )
                    } ?: Text(
                        text = item.appName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // App Name and Package
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.isSuspended) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "FROZEN",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Time and Percentage Pill
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formattedDuration,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = appAccentColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$percentageInt%",
                            style = MaterialTheme.typography.labelSmall,
                            color = appAccentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                // Options Menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        DropdownMenuItem(
                            text = { Text("App Details & Graph") },
                            leadingIcon = {
                                Icon(Icons.Rounded.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onAppClick(item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Open App") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            onClick = {
                                menuExpanded = false
                                onLaunchApp(item.packageName)
                            }
                        )

                        if (isRootActive) {
                            if (item.isSuspended) {
                                DropdownMenuItem(
                                    text = { Text("Unfreeze App (Root)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFreeze(item.packageName, false)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Freeze App (Root)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFreeze(item.packageName, true)
                                    }
                                )
                            }
                        }

                        DropdownMenuItem(
                            text = { Text("System Settings") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = {
                                menuExpanded = false
                                openSystemSettings(context, item.packageName)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rounded Progress Indicator matching Ring Color
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = appAccentColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}
