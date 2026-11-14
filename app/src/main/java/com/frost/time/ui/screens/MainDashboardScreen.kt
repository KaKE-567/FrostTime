package com.frost.time.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.data.TimeRange
import com.frost.time.data.UiState
import com.frost.time.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search apps...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "FrostTime",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }

                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is UiState.NeedPermission -> {
                    PermissionRequestScreen(
                        isRootAvailable = state.isRootAvailable,
                        onGrantWithRoot = { viewModel.requestRootGrant() },
                        onOpenSettings = { viewModel.openSettings() }
                    )
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(
                                onClick = { viewModel.loadData() }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val data = state.data
                    val filteredApps = remember(data.appList, searchQuery) {
                        if (searchQuery.isBlank()) data.appList
                        else data.appList.filter {
                            it.appName.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Official Material 3 Segmented Toggle
                        item {
                            TimeRangeSelector(
                                selectedRange = selectedRange,
                                onRangeSelected = { viewModel.onRangeSelected(it) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // 2. Iconic Multi-segment Screen Time Donut Ring
                        item {
                            ScreenTimeRing(
                                totalScreenTimeMs = data.totalScreenTimeMs,
                                topApps = data.appList
                            )
                        }

                        // 3. Bento Companion Metric Tiles
                        item {
                            BentoStatsRow(
                                unlockCount = data.unlockCount,
                                sessionCount = data.sessionCount,
                                isRootActive = data.isRootActive
                            )
                        }

                        // 4. Hourly Activity Timeline
                        if (data.hourlyBuckets.isNotEmpty()) {
                            item {
                                HourlyBarChart(hourlyBuckets = data.hourlyBuckets)
                            }
                        }

                        // 5. App Usage Section Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Most Used Apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "${filteredApps.size} apps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 6. Color-Matched App Rows
                        itemsIndexed(filteredApps, key = { _, it -> it.packageName }) { index, appItem ->
                            AppUsageRow(
                                item = appItem,
                                index = index,
                                isRootActive = data.isRootActive,
                                onLaunchApp = { viewModel.launchApp(it) },
                                onToggleFreeze = { pkg, freeze -> viewModel.toggleAppFreeze(pkg, freeze) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    isRootAvailable: Boolean,
    onGrantWithRoot: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Usage Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "FrostTime reads Android's native foreground logs on-demand with 0% idle battery and zero background services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isRootAvailable) {
                    Button(
                        onClick = onGrantWithRoot,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text(
                            "Auto-Grant with Root (Instant)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Open System Settings")
                }
            }
        }
    }
}
