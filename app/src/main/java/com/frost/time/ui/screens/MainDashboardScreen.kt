package com.frost.time.ui.screens

import android.app.Activity
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.time.data.UiState
import com.frost.time.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
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
                        Column {
                            Text(
                                text = "FrostTime",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedDay.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

                    IconButton(onClick = { viewModel.loadDataForSelectedDay(showLoadingIndicator = true) }) {
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

                is UiState.RootRequired -> {
                    RootRequiredScreen(
                        onGrantRoot = { viewModel.requestRootAccess() }
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
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.loadDataForSelectedDay(showLoadingIndicator = true) }
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
                        // 1. Slideable 30-Day Calendar Strip
                        item {
                            CalendarDateStrip(
                                days = viewModel.pastDays,
                                selectedDay = selectedDay,
                                onDaySelected = { viewModel.onDaySelected(it) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // 2. Iconic Multi-segment Screen Time Donut Ring
                        item {
                            ScreenTimeRing(
                                totalScreenTimeMs = data.totalScreenTimeMs,
                                topApps = data.appList,
                                rangeTitle = selectedDay.label
                            )
                        }

                        // 3. Bento Companion Metric Tiles
                        item {
                            BentoStatsRow(
                                unlockCount = data.unlockCount,
                                sessionCount = data.sessionCount,
                                isRootActive = true
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
                                isRootActive = true,
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
private fun RootRequiredScreen(
    onGrantRoot: () -> Unit
) {
    val context = LocalContext.current

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
                    text = "Root Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "FrostTime is built strictly for rooted devices (Magisk, KernelSU, APatch) to eliminate background polling services and ensure 0.0% battery drain.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onGrantRoot,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text(
                        "Grant Superuser Access",
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = {
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Exit App")
                }
            }
        }
    }
}
