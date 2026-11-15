package com.frost.time.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class UsageRepository(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    suspend fun checkAndElevateRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext false

            Shell.cmd(
                "pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS",
                "appops set ${context.packageName} GET_USAGE_STATS allow",
                "pm grant ${context.packageName} android.permission.DUMP"
            ).exec()

            isPermissionGranted()
        } catch (e: Exception) {
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private data class DayProcessedStats(
        val durations: Map<String, Long>,
        val unlockCount: Int,
        val sessionCount: Int,
        val hourlyBuckets: List<HourlyUsage>
    )

    private fun computeSingleDayStats(
        dayStart: Long,
        dayEnd: Long,
        isToday: Boolean,
        rawStatsList: List<UsageStats>
    ): DayProcessedStats {
        val now = System.currentTimeMillis()
        val effectiveEnd = if (isToday) now else dayEnd
        val calTarget = Calendar.getInstance().apply { timeInMillis = dayStart }
        val targetDay = calTarget.get(Calendar.DAY_OF_YEAR)
        val targetYear = calTarget.get(Calendar.YEAR)

        val calStat = Calendar.getInstance()
        val systemAppDurations = mutableMapOf<String, Long>()

        for (stat in rawStatsList) {
            val pkg = stat.packageName ?: continue
            if (pkg == "android" || pkg == "com.android.systemui") continue

            val duration = stat.totalTimeInForeground
            if (duration <= 0) continue

            calStat.timeInMillis = stat.firstTimeStamp
            val sDay = calStat.get(Calendar.DAY_OF_YEAR)
            val sYear = calStat.get(Calendar.YEAR)

            if (isToday) {
                if (stat.lastTimeStamp >= dayStart && stat.firstTimeStamp >= (dayStart - 4 * 3600_000L)) {
                    val maxElapsed = (now - dayStart).coerceAtLeast(0L)
                    val safeDuration = minOf(duration, maxElapsed)
                    systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, safeDuration)
                }
            } else {
                if (sDay == targetDay && sYear == targetYear) {
                    systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, duration)
                } else if (stat.lastTimeStamp in dayStart..(effectiveEnd + 4 * 3600_000L) && stat.firstTimeStamp < dayStart + 14 * 3600_000L) {
                    systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, duration)
                }
            }
        }

        val eventProcessed = processEventsForRange(dayStart, effectiveEnd)
        val dayDurations = mutableMapOf<String, Long>()
        val allPackages = systemAppDurations.keys + eventProcessed.appDurations.keys

        for (pkg in allPackages) {
            val systemTime = systemAppDurations[pkg] ?: 0L
            val eventTime = eventProcessed.appDurations[pkg] ?: 0L

            val bestTime = if (isToday) {
                val maxElapsed = (now - dayStart).coerceAtLeast(0L)
                minOf(maxOf(systemTime, eventTime), maxElapsed)
            } else {
                minOf(maxOf(systemTime, eventTime), 86_400_000L)
            }

            if (bestTime >= 10_000L) {
                dayDurations[pkg] = bestTime
            }
        }

        return DayProcessedStats(
            durations = dayDurations,
            unlockCount = eventProcessed.unlockCount,
            sessionCount = eventProcessed.sessionCount,
            hourlyBuckets = eventProcessed.hourlyBuckets
        )
    }

    /**
     * Queries usage data for any specific date range (slideable calendar day or custom interval).
     */
    suspend fun fetchDashboardDataForRange(
        startTime: Long,
        endTime: Long,
        isToday: Boolean
    ): UsageDashboardData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val effectiveEnd = if (isToday) now else endTime

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val isMultiDay = (effectiveEnd - startTime) > (86_400_000L + 60_000L)

        // 1. Query System Aggregated UsageStats
        val rawStatsList = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime - 12 * 3600_000L,
                effectiveEnd + 12 * 3600_000L
            ) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val mergedDurations = mutableMapOf<String, Long>()
        var totalUnlocks = 0
        var totalSessions = 0
        val finalHourlyBuckets: List<HourlyUsage>

        if (!isMultiDay) {
            val dayStats = computeSingleDayStats(startTime, effectiveEnd, isToday, rawStatsList)
            mergedDurations.putAll(dayStats.durations)
            totalUnlocks = dayStats.unlockCount
            totalSessions = dayStats.sessionCount
            finalHourlyBuckets = dayStats.hourlyBuckets
        } else {
            val hourlyAgg = LongArray(24) { 0L }
            val hourlyUnlocksAgg = IntArray(24) { 0 }
            val hourlyAppsAgg = Array(24) { mutableMapOf<String, Long>() }
            val dayIter = Calendar.getInstance().apply { timeInMillis = startTime }

            while (dayIter.timeInMillis <= effectiveEnd) {
                val dStart = dayIter.timeInMillis
                val isDayToday = (dayIter.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) &&
                                  dayIter.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR))
                val dEnd = if (isDayToday) now else (dStart + 86_400_000L - 1L)

                val dayStats = computeSingleDayStats(dStart, dEnd, isDayToday, rawStatsList)
                for ((pkg, timeMs) in dayStats.durations) {
                    mergedDurations[pkg] = (mergedDurations[pkg] ?: 0L) + timeMs
                }
                totalUnlocks += dayStats.unlockCount
                totalSessions += dayStats.sessionCount
                for (bucket in dayStats.hourlyBuckets) {
                    if (bucket.hour in 0..23) {
                        hourlyAgg[bucket.hour] += bucket.durationMs
                        hourlyUnlocksAgg[bucket.hour] += bucket.unlockCount
                        for (app in bucket.topApps) {
                            val map = hourlyAppsAgg[bucket.hour]
                            map[app.packageName] = (map[app.packageName] ?: 0L) + app.durationMs
                        }
                    }
                }

                dayIter.add(Calendar.DAY_OF_YEAR, 1)
            }

            val maxHour = hourlyAgg.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            finalHourlyBuckets = (0..23).map { hour ->
                val hourApps = hourlyAppsAgg[hour].entries
                    .sortedByDescending { it.value }
                    .mapNotNull { (pkg, dur) ->
                        if (dur < 2_000L) null
                        else {
                            var appName = pkg
                            var isSystem = false
                            val icon = try {
                                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                                appName = packageManager.getApplicationLabel(appInfo).toString()
                                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                packageManager.getApplicationIcon(appInfo)
                            } catch (e: Exception) {
                                null
                            }
                            AppUsageInfo(
                                packageName = pkg,
                                appName = appName,
                                icon = icon,
                                durationMs = dur,
                                percentage = if (hourlyAgg[hour] > 0) (dur.toFloat() / hourlyAgg[hour].toFloat()) else 0f,
                                isSystemApp = isSystem
                            )
                        }
                    }

                HourlyUsage(
                    hour = hour,
                    durationMs = hourlyAgg[hour],
                    percentage = (hourlyAgg[hour].toFloat() / maxHour.toFloat()).coerceIn(0f, 1f),
                    unlockCount = hourlyUnlocksAgg[hour],
                    topApps = hourApps
                )
            }
        }

        val appList = mutableListOf<AppUsageInfo>()
        for ((pkg, timeMs) in mergedDurations) {
            var appName = pkg
            var isSystem = false
            val icon = try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                appName = packageManager.getApplicationLabel(appInfo).toString()
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                packageManager.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }

            val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    packageManager.isPackageSuspended(pkg)
                } catch (e: Exception) {
                    false
                }
            } else false

            appList.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    durationMs = timeMs,
                    percentage = 0f,
                    isSystemApp = isSystem,
                    isSuspended = isSuspended
                )
            )
        }

        val totalTimeMs = mergedDurations.values.sum()
        val sortedApps = appList
            .sortedByDescending { it.durationMs }
            .map {
                it.copy(
                    percentage = if (totalTimeMs > 0) (it.durationMs.toFloat() / totalTimeMs.toFloat()) else 0f
                )
            }

        val mostUsed = sortedApps.firstOrNull()?.appName ?: "None"

        UsageDashboardData(
            totalScreenTimeMs = totalTimeMs,
            unlockCount = totalUnlocks,
            sessionCount = totalSessions,
            mostUsedApp = mostUsed,
            hourlyBuckets = finalHourlyBuckets,
            appList = sortedApps,
            isRootActive = true
        )
    }

    suspend fun fetchDashboardData(timeRange: TimeRange): UsageDashboardData = withContext(Dispatchers.IO) {
        val (startTime, endTime) = getTimeBounds(timeRange)
        val isToday = (timeRange == TimeRange.TODAY)
        fetchDashboardDataForRange(startTime, endTime, isToday)
    }

    private data class ProcessedUsage(
        val totalTimeMs: Long,
        val appDurations: Map<String, Long>,
        val hourlyBuckets: List<HourlyUsage>,
        val unlockCount: Int,
        val sessionCount: Int
    )

    private fun processEventsForRange(queryStart: Long, queryEnd: Long): ProcessedUsage {
        val appDurations = mutableMapOf<String, Long>()
        val hourlyDurations = LongArray(24) { 0L }
        val hourlyAppDurations = Array(24) { mutableMapOf<String, Long>() }
        val hourlyUnlocks = IntArray(24) { 0 }
        var keyguardUnlocks = 0
        var screenInteractiveCount = 0
        var sessionCount = 0

        // Look back up to 24 hours to catch sessions started before queryStart
        val lookbackStart = queryStart - 24 * 60 * 60 * 1000L
        val events = try {
            usageStatsManager.queryEvents(lookbackStart, queryEnd)
        } catch (e: Exception) {
            null
        }

        if (events != null) {
            val event = UsageEvents.Event()
            var currentForegroundPkg: String? = null
            var currentForegroundStart: Long = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: ""
                val timestamp = event.timeStamp
                val eventType = event.eventType

                // 1. Process System Unlock & Screen Wake Events
                when (eventType) {
                    18 /* KEYGUARD_HIDDEN (Unlock) */ -> {
                        if (timestamp in queryStart..queryEnd) {
                            keyguardUnlocks++
                            val calU = Calendar.getInstance().apply { timeInMillis = timestamp }
                            val h = calU.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                            hourlyUnlocks[h]++
                        }
                        continue
                    }
                    15 /* SCREEN_INTERACTIVE (Screen Woke Up) */ -> {
                        if (timestamp in queryStart..queryEnd) {
                            screenInteractiveCount++
                        }
                        continue
                    }
                    16 /* SCREEN_NON_INTERACTIVE (Screen Turned Off) */ -> {
                        currentForegroundPkg?.let { activePkg ->
                            if (timestamp > currentForegroundStart) {
                                recordSession(activePkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations, hourlyAppDurations)
                            }
                        }
                        currentForegroundPkg = null
                        continue
                    }
                }

                // 2. Filter system UI / internals (only framework and systemui)
                if (pkg.isEmpty() || pkg == "android" || pkg == "com.android.systemui") {
                    continue
                }

                // 3. Process App Transitions
                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        currentForegroundPkg?.let { activePkg ->
                            if (timestamp > currentForegroundStart) {
                                recordSession(activePkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations, hourlyAppDurations)
                            }
                        }

                        currentForegroundPkg = pkg
                        currentForegroundStart = timestamp

                        if (timestamp in queryStart..queryEnd) {
                            sessionCount++
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (currentForegroundPkg == pkg) {
                            if (timestamp > currentForegroundStart) {
                                recordSession(pkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations, hourlyAppDurations)
                            }
                            currentForegroundPkg = null
                        }
                    }
                }
            }

            // Close session active at queryEnd
            currentForegroundPkg?.let { activePkg ->
                if (queryEnd > currentForegroundStart) {
                    recordSession(activePkg, currentForegroundStart, queryEnd, queryStart, queryEnd, appDurations, hourlyDurations, hourlyAppDurations)
                }
            }
        }

        val finalUnlocks = when {
            keyguardUnlocks > 0 -> keyguardUnlocks
            screenInteractiveCount > 0 -> screenInteractiveCount
            else -> 0
        }

        val finalSessions = maxOf(sessionCount, screenInteractiveCount, finalUnlocks)
        val rawTotal = appDurations.values.sum()
        val maxDuration = (queryEnd - queryStart).coerceAtLeast(0L)
        val finalTotal = minOf(rawTotal, maxDuration)

        val maxHourly = hourlyDurations.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val hourlyList = (0..23).map { h ->
            val hourApps = hourlyAppDurations[h].entries
                .sortedByDescending { it.value }
                .mapNotNull { (pkg, dur) ->
                    if (dur < 2_000L) null
                    else {
                        var appName = pkg
                        var isSystem = false
                        val icon = try {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            appName = packageManager.getApplicationLabel(appInfo).toString()
                            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }
                        AppUsageInfo(
                            packageName = pkg,
                            appName = appName,
                            icon = icon,
                            durationMs = dur,
                            percentage = if (hourlyDurations[h] > 0) (dur.toFloat() / hourlyDurations[h].toFloat()) else 0f,
                            isSystemApp = isSystem
                        )
                    }
                }

            HourlyUsage(
                hour = h,
                durationMs = hourlyDurations[h],
                percentage = (hourlyDurations[h].toFloat() / maxHourly.toFloat()).coerceIn(0f, 1f),
                unlockCount = hourlyUnlocks[h],
                topApps = hourApps
            )
        }

        return ProcessedUsage(
            totalTimeMs = finalTotal,
            appDurations = appDurations,
            hourlyBuckets = hourlyList,
            unlockCount = finalUnlocks,
            sessionCount = finalSessions
        )
    }

    private fun recordSession(
        packageName: String,
        sessionStart: Long,
        sessionEnd: Long,
        queryStart: Long,
        queryEnd: Long,
        appDurations: MutableMap<String, Long>,
        hourlyDurations: LongArray,
        hourlyAppDurations: Array<MutableMap<String, Long>>
    ) {
        val clampedStart = maxOf(sessionStart, queryStart)
        val clampedEnd = minOf(sessionEnd, queryEnd)

        if (clampedEnd > clampedStart) {
            val duration = clampedEnd - clampedStart
            if (duration in 1..86_400_000L) {
                appDurations[packageName] = (appDurations[packageName] ?: 0L) + duration

                val cal = Calendar.getInstance()
                var current = clampedStart
                while (current < clampedEnd) {
                    cal.timeInMillis = current
                    val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val hourEnd = cal.timeInMillis + 1L

                    val chunkEnd = minOf(clampedEnd, hourEnd)
                    val chunk = chunkEnd - current
                    if (chunk > 0) {
                        hourlyDurations[hour] += chunk
                        val map = hourlyAppDurations[hour]
                        map[packageName] = (map[packageName] ?: 0L) + chunk
                    }
                    current = chunkEnd
                }
            }
        }
    }

    private fun getTimeBounds(timeRange: TimeRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        return when (timeRange) {
            TimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            TimeRange.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            TimeRange.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
        }
    }

    suspend fun freezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = Shell.cmd(
            "pm suspend $packageName",
            "am force-stop $packageName"
        ).exec()
        cmd.isSuccess
    }

    suspend fun unfreezeApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = Shell.cmd("pm unsuspend $packageName").exec()
        cmd.isSuccess
    }

    fun openApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
