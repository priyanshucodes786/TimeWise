package com.kashyap.timewise

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeWiseAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val appPrefs by lazy {
        getSharedPreferences("timewise_local", MODE_PRIVATE)
    }
    private val monitorPrefs by lazy {
        getSharedPreferences("timewise_monitor", MODE_PRIVATE)
    }

    private var foregroundPackage: String? = null
    private var checkpointElapsed = 0L
    private var dayKey = todayKey()

    private val ticker = object : Runnable {
        override fun run() {
            ensureDay()
            checkpointUsage()
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureDay()
        checkpointElapsed = SystemClock.elapsedRealtime()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        ensureDay()
        checkpointUsage()

        if (packageName == packageNameOfSelf()) {
            checkpointElapsed = SystemClock.elapsedRealtime()
            return
        }

        if (foregroundPackage != packageName) {
            if (!isTransientSystemPackage(packageName)) {
                foregroundPackage = packageName
                checkAndNotifyLimitExhausted(packageName)
            }
            checkpointElapsed = SystemClock.elapsedRealtime()
        }
    }

    override fun onInterrupt() {
        // Android may interrupt accessibility events temporarily.
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        checkpointUsage()
        super.onDestroy()
    }

    private fun packageNameOfSelf(): String = applicationContext.packageName

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDay() {
        val today = todayKey()
        if (today == dayKey && monitorPrefs.getString("day", "") == today) return

        dayKey = today
        val editor = monitorPrefs.edit()
        if (monitorPrefs.getString("day", "") != today) {
            editor.clear()
            editor.putString("day", today)
        }
        editor.apply()
        foregroundPackage = null
        checkpointElapsed = SystemClock.elapsedRealtime()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun checkpointUsage() {
        val pkg = foregroundPackage ?: run {
            checkpointElapsed = SystemClock.elapsedRealtime()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (checkpointElapsed == 0L) {
            checkpointElapsed = now
            return
        }
        val seconds = ((now - checkpointElapsed) / 1000L).coerceAtLeast(0L)
        if (seconds > 0L) {
            val key = "used_$pkg"
            monitorPrefs.edit().putLong(key, monitorPrefs.getLong(key, 0L) + seconds).apply()
            checkpointElapsed = now
        }
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        if (pkg == packageNameOfSelf()) return true
        if (pkg == "android" || pkg == "com.android.systemui") return true
        val lower = pkg.lowercase(Locale.US)
        return lower.contains("inputmethod") || lower.contains("keyboard") ||
                lower.contains("honeyboard") || lower.contains("swiftkey")
    }

    private fun selectedPackages(): Set<String> {
        val arr = runCatching {
            JSONArray(appPrefs.getString("selected", "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return buildSet {
            for (i in 0 until arr.length()) add(arr.optString(i))
        }
    }

    private fun limitSeconds(pkg: String): Long {
        val limits = runCatching {
            JSONObject(appPrefs.getString("limits", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        return limits.optInt(pkg, 0).coerceAtLeast(0) * 60L
    }

    private fun pendingGoalTitles(): List<String> {
        val goals = runCatching {
            JSONArray(appPrefs.getString("goals", "[]") ?: "[]")
        }.getOrDefault(JSONArray())

        val pending = mutableListOf<String>()
        for (i in 0 until goals.length()) {
            val goal = goals.optJSONObject(i) ?: continue
            if (!goal.optBoolean("done")) {
                val title = goal.optString("title").trim()
                if (title.isNotEmpty()) pending += title
            }
        }
        return pending
    }

    private fun getUsedSeconds(pkg: String): Long {
        val localUsed = monitorPrefs.getLong("used_$pkg", 0L)
        if (!hasUsageAccess()) return localUsed

        val systemUsed = runCatching {
            val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val start = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val statsMap = manager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
            (statsMap[pkg]?.totalTimeInForeground ?: 0L) / 1000L
        }.getOrDefault(0L)

        return maxOf(localUsed, systemUsed)
    }

    private fun checkAndNotifyLimitExhausted(pkg: String) {
        if (pkg !in selectedPackages()) return
        val limit = limitSeconds(pkg)
        if (limit <= 0L) return

        val used = getUsedSeconds(pkg)
        if (used < limit) return

        sendLimitExhaustedNotification(pkg)
    }

    private fun getAppName(pkg: String): String {
        return runCatching {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault("")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Time Wise Reminders"
            val descriptionText = "Notifications for app time limit reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendLimitExhaustedNotification(pkg: String) {
        createNotificationChannel()

        val pending = pendingGoalTitles()
        val baseMessage = "You have passed your time limit. Be Mindful."
        val fullMessage = if (pending.isNotEmpty()) {
            "$baseMessage Alert: Your daily Goals are still left!"
        } else {
            baseMessage
        }

        val appLabel = getAppName(pkg)
        val title = if (appLabel.isNotEmpty()) "Time Wise - $appLabel" else "Time Wise"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(fullMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        runCatching {
            notificationManager.notify(pkg.hashCode(), builder.build())
        }
    }

    companion object {
        private const val CHANNEL_ID = "timewise_reminders"
    }
}
