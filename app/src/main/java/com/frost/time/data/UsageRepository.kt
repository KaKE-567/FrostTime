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

    suspend fun fetchDashboardData(timeRange: TimeRange): UsageDashboardData = withContext(Dispatchers.IO) {
        val (startTime, endTime) = getTimeBounds(timeRange)

        // Process raw events strictly within [startTime, endTime] with boundary clamping
        val processed = processEventsForRange(startTime, endTime)

        val appList = mutableListOf<AppUsageInfo>()
        for ((pkg, timeMs) in processed.appDurations) {
            if (timeMs < 10_000L) continue // Ignore apps under 10 seconds

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

        val totalTimeMs = processed.totalTimeMs
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
            unlockCount = processed.unlockCount,
            sessionCount = processed.sessionCount,
            mostUsedApp = mostUsed,
            hourlyBuckets = processed.hourlyBuckets,
            appList = sortedApps,
            isRootActive = true
        )
    }

    private data class ProcessedUsage(
        val totalTimeMs: Long,
        val appDurations: Map<String, Long>,
        val hourlyBuckets: List<HourlyUsage>,
        val unlockCount: Int,
        val sessionCount: Int
    )

    /**
     * Accurately calculates exact active screen time using UsageEvents.
     * Prevents Android's INTERVAL_DAILY bucket overlap bug from leaking prior day's usage past midnight.
     */
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
            val activeResumed = mutableMapOf<String, Long>()
            var isScreenOn = true

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val timestamp = event.timeStamp

                // Filter self and system internals
                if (pkg == "android" || pkg == "com.android.systemui" || pkg == context.packageName) {
                    continue
                }

                when (event.eventType) {
                    18 /* KEYGUARD_HIDDEN (Unlock) */ -> {
                        if (timestamp in queryStart..queryEnd) {
                            keyguardUnlocks++
                        }
                    }
                    15 /* SCREEN_INTERACTIVE (Screen On) */ -> {
                        if (timestamp in queryStart..queryEnd) {
                            screenInteractiveCount++
                        }
                        isScreenOn = true
                    }
                    16 /* SCREEN_NON_INTERACTIVE (Screen Off) */ -> {
                        isScreenOn = false
                        // Pause all active foreground sessions
                        for ((p, resumedAt) in activeResumed) {
                            recordSession(p, resumedAt, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
                        }
                        activeResumed.clear()
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (isScreenOn) {
                            val prior = activeResumed[pkg]
                            if (prior != null && timestamp > prior) {
                                recordSession(pkg, prior, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
                            }
                            activeResumed[pkg] = timestamp
                            if (timestamp in queryStart..queryEnd) {
                                sessionCount++
                            }
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val resumedAt = activeResumed.remove(pkg)
                        if (resumedAt != null && timestamp > resumedAt) {
                            recordSession(pkg, resumedAt, timestamp, queryStart, queryEnd, appDurations, hourlyDurations)
                        }
                    }
                }
            }

            // Close any ongoing active session as of queryEnd
            if (isScreenOn) {
                for ((p, resumedAt) in activeResumed) {
                    if (queryEnd > resumedAt) {
                        recordSession(p, resumedAt, queryEnd, queryStart, queryEnd, appDurations, hourlyDurations)
                    }
                }
            }
        }

        var finalUnlocks = if (keyguardUnlocks > 0) keyguardUnlocks else screenInteractiveCount
        if (finalUnlocks == 0) {
            try {
                val out = Shell.cmd("dumpsys power | grep 'mWakefulness=Awake' | wc -l").exec().out
                val count = out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                if (count > 0) finalUnlocks = count
            } catch (_: Exception) {}
        }

        // Cap total screen time to the maximum physically elapsed time in this window
        val maxPossibleDuration = (queryEnd - queryStart).coerceAtLeast(0L)
        val rawTotal = appDurations.values.sum()
        val finalTotalTime = minOf(rawTotal, maxPossibleDuration)

        val maxHourly = hourlyDurations.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val hourlyList = (0..23).map { h ->
            HourlyUsage(
                hour = h,
                durationMs = hourlyDurations[h],
                percentage = (hourlyDurations[h].toFloat() / maxHourly.toFloat()).coerceIn(0f, 1f)
            )
        }

        return ProcessedUsage(
            totalTimeMs = finalTotalTime,
            appDurations = appDurations,
            hourlyBuckets = hourlyList,
            unlockCount = finalUnlocks,
            sessionCount = sessionCount
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

                // Distribute across hourly buckets
                val cal = Calendar.getInstance()
                var current = clampedStart
                while (current < clampedEnd) {
                    cal.timeInMillis = current
                    val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

                    // End of current hour
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
