package com.frost.time.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
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

    /**
     * Queries usage data for any specific date range (slideable calendar day or custom interval).
     */
    suspend fun fetchDashboardDataForRange(
        startTime: Long,
        endTime: Long,
        isToday: Boolean
    ): UsageDashboardData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val effectiveEnd = if (isToday) minOf(now, endTime) else endTime

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMidnight = todayCal.timeInMillis
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

        val systemAppDurations = mutableMapOf<String, Long>()
        val calStat = Calendar.getInstance()

        if (!isMultiDay) {
            // Single day calculation (Today, Yesterday, or single Custom Date)
            val calTarget = Calendar.getInstance().apply { timeInMillis = startTime }
            val targetDay = calTarget.get(Calendar.DAY_OF_YEAR)
            val targetYear = calTarget.get(Calendar.YEAR)

            for (stat in rawStatsList) {
                val pkg = stat.packageName ?: continue
                if (pkg == "android" || pkg == "com.android.systemui" || pkg == context.packageName) continue

                val duration = stat.totalTimeInForeground
                if (duration <= 0) continue

                calStat.timeInMillis = stat.firstTimeStamp
                val sDay = calStat.get(Calendar.DAY_OF_YEAR)
                val sYear = calStat.get(Calendar.YEAR)

                if (isToday) {
                    if (stat.lastTimeStamp >= startTime && stat.firstTimeStamp >= (startTime - 4 * 3600_000L)) {
                        val maxElapsed = (now - startTime).coerceAtLeast(0L)
                        val safeDuration = minOf(duration, maxElapsed)
                        systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, safeDuration)
                    }
                } else {
                    if (sDay == targetDay && sYear == targetYear) {
                        systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, duration)
                    } else if (stat.lastTimeStamp in startTime..(effectiveEnd + 4 * 3600_000L) && stat.firstTimeStamp < startTime + 14 * 3600_000L) {
                        systemAppDurations[pkg] = maxOf(systemAppDurations[pkg] ?: 0L, duration)
                    }
                }
            }
        } else {
            // Multi-day calculation (This Week Total, Current Month Total)
            val dayBucketsByDate = mutableMapOf<Pair<Int, Int>, MutableMap<String, Long>>()

            for (stat in rawStatsList) {
                val pkg = stat.packageName ?: continue
                if (pkg == "android" || pkg == "com.android.systemui" || pkg == context.packageName) continue
                val duration = stat.totalTimeInForeground
                if (duration <= 0) continue

                calStat.timeInMillis = stat.firstTimeStamp
                val sDay = calStat.get(Calendar.DAY_OF_YEAR)
                val sYear = calStat.get(Calendar.YEAR)

                if (calStat.timeInMillis >= todayMidnight || stat.lastTimeStamp >= todayMidnight) {
                    val maxElapsedToday = (now - todayMidnight).coerceAtLeast(0L)
                    val safeDuration = minOf(duration, maxElapsedToday)
                    val key = Pair(sYear, sDay)
                    val map = dayBucketsByDate.getOrPut(key) { mutableMapOf() }
                    map[pkg] = maxOf(map[pkg] ?: 0L, safeDuration)
                } else if (stat.firstTimeStamp >= (startTime - 6 * 3600_000L) && stat.lastTimeStamp <= (effectiveEnd + 6 * 3600_000L)) {
                    val key = Pair(sYear, sDay)
                    val map = dayBucketsByDate.getOrPut(key) { mutableMapOf() }
                    map[pkg] = maxOf(map[pkg] ?: 0L, minOf(duration, 86_400_000L))
                }
            }

            val dayIter = Calendar.getInstance().apply { timeInMillis = startTime }
            while (dayIter.timeInMillis <= effectiveEnd) {
                val dYear = dayIter.get(Calendar.YEAR)
                val dDay = dayIter.get(Calendar.DAY_OF_YEAR)
                val dayMap = dayBucketsByDate[Pair(dYear, dDay)]
                if (dayMap != null) {
                    for ((pkg, dur) in dayMap) {
                        systemAppDurations[pkg] = (systemAppDurations[pkg] ?: 0L) + dur
                    }
                }
                dayIter.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 2. Process granular UsageEvents for timeline, unlocks & session transitions
        val eventProcessed = processEventsForRange(startTime, effectiveEnd)

        // 3. Merge system totals with event totals
        val mergedDurations = mutableMapOf<String, Long>()
        val allPackages = systemAppDurations.keys + eventProcessed.appDurations.keys

        val maxAllowedTotal = if (isToday) {
            (now - startTime).coerceAtLeast(0L)
        } else if (!isMultiDay) {
            86_400_000L
        } else {
            val elapsedDays = ((effectiveEnd - startTime) / 86_400_000L).coerceAtLeast(1L)
            elapsedDays * 86_400_000L + (now - todayMidnight).coerceAtLeast(0L)
        }

        for (pkg in allPackages) {
            val systemTime = systemAppDurations[pkg] ?: 0L
            val eventTime = eventProcessed.appDurations[pkg] ?: 0L

            val bestTime = if (isToday) {
                val maxElapsed = (now - startTime).coerceAtLeast(0L)
                minOf(maxOf(systemTime, eventTime), maxElapsed)
            } else {
                minOf(maxOf(systemTime, eventTime), maxAllowedTotal)
            }

            if (bestTime >= 10_000L) { // Filter apps under 10 seconds
                mergedDurations[pkg] = bestTime
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

        val rawTotal = mergedDurations.values.sum()
        val totalTimeMs = minOf(rawTotal, maxAllowedTotal)

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
            unlockCount = eventProcessed.unlockCount,
            sessionCount = eventProcessed.sessionCount,
            mostUsedApp = mostUsed,
            hourlyBuckets = eventProcessed.hourlyBuckets,
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
                                recordSession(activePkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
                            }
                        }
                        currentForegroundPkg = null
                        continue
                    }
                }

                // 2. Filter system UI / internals and self
                if (pkg.isEmpty() || pkg == "android" || pkg == "com.android.systemui" || pkg == context.packageName) {
                    continue
                }

                // 3. Process App Transitions
                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        currentForegroundPkg?.let { activePkg ->
                            if (timestamp > currentForegroundStart) {
                                recordSession(activePkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
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
                                recordSession(pkg, currentForegroundStart, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
                            }
                            currentForegroundPkg = null
                        }
                    }
                }
            }

            // Close session active at queryEnd
            currentForegroundPkg?.let { activePkg ->
                if (queryEnd > currentForegroundStart) {
                    recordSession(activePkg, currentForegroundStart, queryEnd, queryStart, queryEnd, appDurations, hourlyDurations)
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
            HourlyUsage(
                hour = h,
                durationMs = hourlyDurations[h],
                percentage = (hourlyDurations[h].toFloat() / maxHourly.toFloat()).coerceIn(0f, 1f)
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
        hourlyDurations: LongArray
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
