package com.frost.time.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
import com.frost.time.data.AppUsageInfo
import com.frost.time.data.DashboardRange
import com.frost.time.data.RangeType
import com.frost.time.data.UiState
import com.frost.time.ui.components.*
import java.util.Calendar
import java.util.TimeZone

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
    var showDatePicker by remember { mutableStateOf(false) }
    var showActivityDetail by remember { mutableStateOf(false) }
    var selectedAppForDetail by remember { mutableStateOf<AppUsageInfo?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (selectedAppForDetail != null && uiState is UiState.Success) {
        val successData = (uiState as UiState.Success).data
        AppDetailScreen(
            appInfo = selectedAppForDetail!!,
            rangeLabel = selectedRange.label,
            totalScreenTimeMs = successData.totalScreenTimeMs,
            hourlyBuckets = successData.hourlyBuckets,
            onBack = { selectedAppForDetail = null },
            onLaunchApp = { viewModel.launchApp(it) },
            onToggleFreeze = { pkg, freeze ->
                viewModel.toggleAppFreeze(pkg, freeze)
                selectedAppForDetail = selectedAppForDetail?.copy(isSuspended = freeze)
            }
        )
        return
    }

    if (showActivityDetail && uiState is UiState.Success) {
        val successData = (uiState as UiState.Success).data
        ActivityTimelineScreen(
            rangeLabel = selectedRange.label,
            totalScreenTimeMs = successData.totalScreenTimeMs,
            hourlyBuckets = successData.hourlyBuckets,
            onBack = { showActivityDetail = false },
            onLaunchApp = { viewModel.launchApp(it) },
            onToggleFreeze = { pkg, freeze -> viewModel.toggleAppFreeze(pkg, freeze) }
        )
        return
    }

    // Material 3 Date Picker Dialog for picking ANY date
    if (showDatePicker) {
        val initialUtcMillis = remember(selectedRange.startTime) {
            val localCal = Calendar.getInstance().apply { timeInMillis = selectedRange.startTime }
            val y = localCal.get(Calendar.YEAR)
            val m = localCal.get(Calendar.MONTH)
            val d = localCal.get(Calendar.DAY_OF_MONTH)
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(y, m, d, 0, 0, 0)
            }.timeInMillis
        }

        val maxUtcMillis = remember {
            val today = Calendar.getInstance()
            val y = today.get(Calendar.YEAR)
            val m = today.get(Calendar.MONTH)
            val d = today.get(Calendar.DAY_OF_MONTH)
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(y, m, d, 23, 59, 59)
            }.timeInMillis
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= maxUtcMillis
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            viewModel.selectCustomDateFromUtcMillis(utcMillis)
                        }
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

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
                                text = selectedRange.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
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
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = "Select Date",
                                tint = if (selectedRange.type == RangeType.CUSTOM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }

                    IconButton(onClick = { viewModel.loadDataForSelectedRange(showLoadingIndicator = true) }) {
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
                                onClick = { viewModel.loadDataForSelectedRange(showLoadingIndicator = true) }
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
                        // Dedicated Time Range Space (Today, Yesterday, This Week Total, Current Month, and Calendar Icon)
                        item {
                            RangeFilterRow(
                                selectedRange = selectedRange,
                                currentMonthName = viewModel.currentMonthName,
                                onSelectToday = { viewModel.selectToday() },
                                onSelectYesterday = { viewModel.selectYesterday() },
                                onSelectThisWeek = { viewModel.selectThisWeek() },
                                onSelectThisMonth = { viewModel.selectThisMonth() },
                                onOpenCalendar = { showDatePicker = true }
                            )
                        }

                        // 1. Iconic Multi-segment Screen Time Donut Ring
                        item {
                            ScreenTimeRing(
                                totalScreenTimeMs = data.totalScreenTimeMs,
                                topApps = data.appList,
                                rangeTitle = selectedRange.label
                            )
                        }

                        // 2. Bento Companion Metric Tiles
                        item {
                            BentoStatsRow(
                                unlockCount = data.unlockCount,
                                sessionCount = data.sessionCount,
                                isRootActive = true
                            )
                        }

                        // 3. Hourly Activity Timeline
                        if (data.hourlyBuckets.isNotEmpty()) {
                            item {
                                HourlyBarChart(
                                    hourlyBuckets = data.hourlyBuckets,
                                    onClick = { showActivityDetail = true }
                                )
                            }
                        }

                        // 4. App Usage Section Header
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

                        // 5. Color-Matched App Rows
                        itemsIndexed(filteredApps, key = { _, it -> it.packageName }) { index, appItem ->
                            AppUsageRow(
                                item = appItem,
                                index = index,
                                isRootActive = true,
                                onAppClick = { selectedAppForDetail = it },
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
private fun RangeFilterRow(
    selectedRange: DashboardRange,
    currentMonthName: String,
    onSelectToday: () -> Unit,
    onSelectYesterday: () -> Unit,
    onSelectThisWeek: () -> Unit,
    onSelectThisMonth: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Today
        TimeFilterChip(
            selected = selectedRange.type == RangeType.TODAY,
            text = "Today",
            onClick = onSelectToday
        )

        // 2. Yesterday
        TimeFilterChip(
            selected = selectedRange.type == RangeType.YESTERDAY,
            text = "Yesterday",
            onClick = onSelectYesterday
        )

        // 3. This Week Total
        TimeFilterChip(
            selected = selectedRange.type == RangeType.THIS_WEEK,
            text = "This Week",
            onClick = onSelectThisWeek
        )

        // 4. Current Month
        TimeFilterChip(
            selected = selectedRange.type == RangeType.THIS_MONTH,
            text = currentMonthName,
            onClick = onSelectThisMonth
        )

        // 5. Calendar Icon (or Custom Picked Date)
        val isCustom = (selectedRange.type == RangeType.CUSTOM)
        Surface(
            modifier = Modifier
                .height(38.dp)
                .clip(CircleShape)
                .clickable { onOpenCalendar() },
            shape = CircleShape,
            color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = "Pick Date",
                    tint = if (isCustom) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                if (isCustom) {
                    Text(
                        text = selectedRange.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RootRequiredScreen(
    onGrantRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Grant Root Access", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { activity?.finishAffinity() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Exit")
                }
            }
        }
    }
}
