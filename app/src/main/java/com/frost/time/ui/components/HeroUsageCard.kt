package com.frost.time.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.ui.theme.*
import java.util.concurrent.TimeUnit

@Composable
fun HeroUsageCard(
    totalScreenTimeMs: Long,
    unlockCount: Int,
    sessionCount: Int,
    mostUsedApp: String,
    isRootActive: Boolean,
    modifier: Modifier = Modifier
) {
    val hours = TimeUnit.MILLISECONDS.toHours(totalScreenTimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalScreenTimeMs) % 60

    FrostCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        backgroundColor = FrostedCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header: Zero-Drain Root Engine Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCREEN ON TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = if (isRootActive) FrostEmerald.copy(alpha = 0.15f) else FrostAmber.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isRootActive) FrostEmerald else FrostAmber)
                        )
                        Text(
                            text = if (isRootActive) "0% Battery Drain" else "Standard Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRootActive) FrostEmerald else FrostAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Screen Time Display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$hours",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    lineHeight = 54.sp
                )
                Text(
                    text = "h",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$minutes",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    lineHeight = 54.sp
                )
                Text(
                    text = "m",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sub-metrics Row (Unlocks, Sessions, Most Used)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SubStatPill(
                    icon = Icons.Rounded.LockOpen,
                    label = "Unlocks",
                    value = "$unlockCount",
                    accentColor = FrostCyan
                )
                SubStatPill(
                    icon = Icons.Rounded.TouchApp,
                    label = "Pickups",
                    value = "$sessionCount",
                    accentColor = FrostBlue
                )
                SubStatPill(
                    icon = Icons.Rounded.Bolt,
                    label = "Top App",
                    value = mostUsedApp.take(10),
                    accentColor = FrostPurple
                )
            }
        }
    }
}

@Composable
private fun SubStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
