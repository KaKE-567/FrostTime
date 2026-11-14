package com.frost.time.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.frost.time.data.AppUsageInfo
import com.frost.time.ui.theme.*
import java.util.concurrent.TimeUnit

@Composable
fun AppUsageRow(
    item: AppUsageInfo,
    isRootActive: Boolean,
    onLaunchApp: (String) -> Unit,
    onToggleFreeze: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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

    FrostCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = FrostedCard,
        onClick = { menuExpanded = true }
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
                // App Icon in Squircle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
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
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // App Name and Duration
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.isSuspended) {
                            Surface(
                                color = FrostCyan.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "FROZEN",
                                    color = FrostCyan,
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
                        color = TextSecondary,
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$percentageInt%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                // Dropdown Menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(FrostedCardElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open App", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Launch, contentDescription = null, tint = FrostCyan)
                            },
                            onClick = {
                                menuExpanded = false
                                onLaunchApp(item.packageName)
                            }
                        )

                        if (isRootActive) {
                            if (item.isSuspended) {
                                DropdownMenuItem(
                                    text = { Text("Unfreeze App (Root)", color = FrostEmerald) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = FrostEmerald)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFreeze(item.packageName, false)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Freeze App (Root)", color = FrostCyan) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = FrostCyan)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFreeze(item.packageName, true)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (item.isSuspended) FrostCyan else MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}
