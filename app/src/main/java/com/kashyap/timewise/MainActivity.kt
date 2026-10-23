package com.kashyap.timewise

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.kashyap.timewise.ui.theme.TimeWiseTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private enum class PageScreen {
    HOME, PICKER, TIMER, GOALS
}

private data class DeviceApp(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    val selected: Boolean = false,
    val limitMinutes: Int = 0,
    val usedMinutes: Int = 0
)

private data class Goal(
    val id: String,
    val title: String,
    val priority: String,
    val done: Boolean = false
)

private fun getPriorityColor(priority: String): Color {
    return when (priority.trim().lowercase()) {
        "high" -> Color(0xFFE53935)  // Red
        "low" -> Color(0xFF43A047)   // Green
        else -> Color(0xFFF57C00)    // Mid (Yellow / Orange)
    }
}

class MainActivity : ComponentActivity() {

    private var refreshCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("timewise_local", Context.MODE_PRIVATE)
            }
            var isDarkMode by remember {
                mutableStateOf(prefs.getBoolean("dark_mode", false))
            }

            val view = LocalView.current
            SideEffect {
                val window = (view.context as ComponentActivity).window
                val statusBarColor = if (isDarkMode) Color.Black else Color.White
                window.statusBarColor = statusBarColor.toArgb()
                window.navigationBarColor = statusBarColor.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkMode
                controller.isAppearanceLightNavigationBars = !isDarkMode
            }

            TimeWiseTheme(darkTheme = isDarkMode) {
                TimeWiseApp(
                    isDarkMode = isDarkMode,
                    onToggleTheme = {
                        isDarkMode = !isDarkMode
                        prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
                    },
                    registerRefresh = { refreshCallback = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCallback?.invoke()
    }
}

@Composable
private fun TimeWiseApp(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    registerRefresh: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("timewise_local", Context.MODE_PRIVATE)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var screen by remember { mutableStateOf(PageScreen.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var editingPackage by remember { mutableStateOf<String?>(null) }
    var timerValue by remember { mutableIntStateOf(30) }
    var newGoal by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf("Mid") }

    val pageColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val cardColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF7F7F7)
    val inkColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF000000)
    val mutedColor = if (isDarkMode) Color(0xFF888888) else Color(0xFF666666)
    val primaryColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF000000)
    val onPrimaryColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val secondaryColor = if (isDarkMode) Color(0xFF222222) else Color(0xFFEFEFEF)
    val onSecondaryColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF000000)
    val lineColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE5E5E5)

    val limits = remember {
        mutableStateMapOf<String, Int>().apply {
            val json = runCatching {
                JSONObject(prefs.getString("limits", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            json.keys().forEach { key ->
                put(key, json.optInt(key, 0))
            }
        }
    }

    val selected = remember {
        mutableStateListOf<String>().apply {
            val array = runCatching {
                JSONArray(prefs.getString("selected", "[]") ?: "[]")
            }.getOrDefault(JSONArray())
            for (i in 0 until array.length()) {
                add(array.optString(i))
            }
        }
    }

    val goals = remember {
        mutableStateListOf<Goal>().apply {
            val array = runCatching {
                JSONArray(prefs.getString("goals", "[]") ?: "[]")
            }.getOrDefault(JSONArray())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    Goal(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        priority = item.optString("priority", "Mid"),
                        done = item.optBoolean("done")
                    )
                )
            }
        }
    }

    fun saveAll() {
        val selectedJson = JSONArray()
        selected.forEach { selectedJson.put(it) }

        val limitsJson = JSONObject()
        limits.forEach { (key, value) -> limitsJson.put(key, value) }

        val goalsJson = JSONArray()
        goals.forEach { goal ->
            goalsJson.put(
                JSONObject().apply {
                    put("id", goal.id)
                    put("title", goal.title)
                    put("priority", goal.priority)
                    put("done", goal.done)
                }
            )
        }

        prefs.edit()
            .putString("selected", selectedJson.toString())
            .putString("limits", limitsJson.toString())
            .putString("goals", goalsJson.toString())
            .apply()
    }

    registerRefresh { refresh++ }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            refresh++
        }
    }

    val usageAllowed = remember(refresh) {
        hasUsageAccess(context)
    }

    val deviceApps = remember(refresh) {
        loadLaunchableApps(context, selected, limits, usageAllowed)
    }

    val selectedApps = deviceApps.filter { it.selected }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.weight(1f),
                label = "TimeWiseNavigation"
            ) { destination ->
                when (destination) {
                    PageScreen.HOME -> HomePage(
                        apps = selectedApps,
                        goals = goals,
                        usageAllowed = usageAllowed,
                        isDarkMode = isDarkMode,
                        inkColor = inkColor,
                        mutedColor = mutedColor,
                        lineColor = lineColor,
                        cardColor = cardColor,
                        primaryColor = primaryColor,
                        onPrimaryColor = onPrimaryColor,
                        secondaryColor = secondaryColor,
                        onSecondaryColor = onSecondaryColor,
                        onToggleTheme = onToggleTheme,
                        onAddApps = { search = ""; screen = PageScreen.PICKER },
                        onTimer = { app ->
                            editingPackage = app.packageName
                            timerValue = limits[app.packageName] ?: 30
                            screen = PageScreen.TIMER
                        },
                        onAddGoal = { screen = PageScreen.GOALS },
                        onToggleGoal = { id ->
                            val index = goals.indexOfFirst { it.id == id }
                            if (index >= 0) {
                                goals[index] = goals[index].copy(done = !goals[index].done)
                                saveAll()
                            }
                        },
                        onUsageSettings = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )

                    PageScreen.PICKER -> {
                        val filtered = deviceApps.filter {
                            it.name.contains(search, ignoreCase = true) ||
                                    it.packageName.contains(search, ignoreCase = true)
                        }

                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            Header("Select Apps", inkColor) {
                                screen = PageScreen.HOME
                            }

                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Which apps need a little less time?",
                                color = inkColor,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Select apps installed on this device.",
                                color = mutedColor,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Search installed apps", color = mutedColor) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = inkColor,
                                    unfocusedBorderColor = lineColor,
                                    focusedTextColor = inkColor,
                                    unfocusedTextColor = inkColor
                                )
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${selected.size} selected",
                                color = inkColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(filtered, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selected.contains(app.packageName)) {
                                                    selected.remove(app.packageName)
                                                } else {
                                                    selected.add(app.packageName)
                                                }
                                                saveAll()
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(app.icon, 40)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, color = inkColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                app.packageName,
                                                color = mutedColor,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Checkbox(
                                            checked = selected.contains(app.packageName),
                                            onCheckedChange = {
                                                if (it) selected.add(app.packageName)
                                                else selected.remove(app.packageName)
                                                saveAll()
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = primaryColor,
                                                uncheckedColor = mutedColor,
                                                checkmarkColor = onPrimaryColor
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = lineColor)
                                }
                            }

                            PrimaryButton("Finish · ${selected.size} apps", primaryColor, onPrimaryColor) {
                                screen = PageScreen.HOME
                                refresh++
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    PageScreen.TIMER -> {
                        val app = deviceApps.firstOrNull { it.packageName == editingPackage }

                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            Header("Set Daily Timer", inkColor) {
                                screen = PageScreen.HOME
                            }

                            if (app != null) {
                                Spacer(Modifier.height(24.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(app.icon, 44)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        app.name,
                                        color = inkColor,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "$timerValue minutes",
                                    color = inkColor,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Slider(
                                    value = timerValue.toFloat(),
                                    onValueChange = {
                                        timerValue = ((it / 15).toInt() * 15).coerceIn(15, 90)
                                    },
                                    valueRange = 15f..90f,
                                    steps = 4,
                                    colors = SliderDefaults.colors(
                                        thumbColor = primaryColor,
                                        activeTrackColor = primaryColor,
                                        inactiveTrackColor = lineColor
                                    )
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("15 min", color = mutedColor)
                                    Text("90 min", color = mutedColor)
                                }

                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Today's recorded usage: ${app.usedMinutes} min",
                                    color = mutedColor,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.weight(1f))
                            PrimaryButton("Save Timer", primaryColor, onPrimaryColor) {
                                editingPackage?.let { pkg ->
                                    limits[pkg] = timerValue
                                    if (!selected.contains(pkg)) selected.add(pkg)
                                }
                                saveAll()
                                screen = PageScreen.HOME
                                refresh++
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    PageScreen.GOALS -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            Header("Daily Goals", inkColor) {
                                screen = PageScreen.HOME
                            }

                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Focus & Priorities",
                                color = inkColor,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = newGoal,
                                onValueChange = { newGoal = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Add a daily goal", color = mutedColor) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newGoal.isNotBlank()) {
                                            goals.add(
                                                Goal(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    title = newGoal.trim(),
                                                    priority = newPriority
                                                )
                                            )
                                            newGoal = ""
                                            saveAll()
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = inkColor,
                                    unfocusedBorderColor = lineColor,
                                    focusedTextColor = inkColor,
                                    unfocusedTextColor = inkColor
                                )
                            )

                            Spacer(Modifier.height(12.dp))
                            Text("PRIORITY", color = mutedColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("High", "Mid", "Low").forEach { priority ->
                                    val pColor = getPriorityColor(priority)
                                    FilterChip(
                                        selected = newPriority == priority,
                                        onClick = { newPriority = priority },
                                        label = { Text(priority, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = pColor,
                                            selectedLabelColor = Color.White,
                                            containerColor = pColor.copy(alpha = 0.12f),
                                            labelColor = pColor
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Add Goal", primaryColor, onPrimaryColor) {
                                if (newGoal.isNotBlank()) {
                                    goals.add(
                                        Goal(
                                            id = java.util.UUID.randomUUID().toString(),
                                            title = newGoal.trim(),
                                            priority = newPriority
                                        )
                                    )
                                    newGoal = ""
                                    saveAll()
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            LazyColumn {
                                items(goals, key = { it.id }) { goal ->
                                    val pColor = getPriorityColor(goal.priority)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = goal.done,
                                            onCheckedChange = { checked ->
                                                val i = goals.indexOfFirst { it.id == goal.id }
                                                if (i >= 0) {
                                                    goals[i] = goals[i].copy(done = checked)
                                                    saveAll()
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = primaryColor,
                                                uncheckedColor = mutedColor,
                                                checkmarkColor = onPrimaryColor
                                            )
                                        )

                                        Text(
                                            goal.title,
                                            Modifier.weight(1f),
                                            color = if (goal.done) mutedColor else inkColor,
                                            fontSize = 13.sp,
                                            fontWeight = if (goal.done) FontWeight.Normal else FontWeight.Medium
                                        )

                                        Surface(
                                            color = pColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, pColor.copy(alpha = 0.5f)),
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                goal.priority.uppercase(),
                                                color = pColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            "✕",
                                            color = mutedColor,
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .clickable {
                                                    goals.removeAll { it.id == goal.id }
                                                    saveAll()
                                                }
                                        )
                                    }
                                    HorizontalDivider(color = lineColor)
                                }
                            }
                        }
                    }
                }
            }

            if (screen == PageScreen.HOME || screen == PageScreen.GOALS) {
                NavigationBar(
                    containerColor = pageColor,
                    tonalElevation = 0.dp
                ) {
                    listOf(
                        PageScreen.HOME to "Home",
                        PageScreen.PICKER to "Limits",
                        PageScreen.GOALS to "Tasks"
                    ).forEach { (destination, label) ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { screen = destination },
                            icon = {
                                Text(
                                    when (label) {
                                        "Home" -> "⌂"
                                        "Limits" -> "◷"
                                        else -> "☷"
                                    },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = primaryColor,
                                selectedTextColor = primaryColor,
                                indicatorColor = secondaryColor,
                                unselectedIconColor = mutedColor,
                                unselectedTextColor = mutedColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    apps: List<DeviceApp>,
    goals: List<Goal>,
    usageAllowed: Boolean,
    isDarkMode: Boolean,
    inkColor: Color,
    mutedColor: Color,
    lineColor: Color,
    cardColor: Color,
    primaryColor: Color,
    onPrimaryColor: Color,
    secondaryColor: Color,
    onSecondaryColor: Color,
    onToggleTheme: () -> Unit,
    onAddApps: () -> Unit,
    onTimer: (DeviceApp) -> Unit,
    onAddGoal: () -> Unit,
    onToggleGoal: (String) -> Unit,
    onUsageSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⌛ TIME WISE",
                        color = inkColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (isDarkMode) "☀️" else "🌙",
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { onToggleTheme() }
                    )
                }
            }

            item {
                Text(
                    text = if (apps.isEmpty()) {
                        "Be Mindful of Your\nChoices. Get Started"
                    } else {
                        "Productivity & Efficiency!"
                    },
                    color = inkColor,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Less screen time. More real life.",
                    color = mutedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            item {
                SectionHeader("YOUR APPS", "+ Add Apps", inkColor, primaryColor, onAddApps)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth()) {
                    Text("APPS", Modifier.weight(1.3f), color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("REMAINING", Modifier.weight(1f), color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("LIMIT", Modifier.weight(.8f), color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                HorizontalDivider(color = lineColor)

                if (apps.isEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Choose apps installed on your phone, then set a daily limit for each.",
                        color = mutedColor,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("+ Add Apps", primaryColor, onPrimaryColor, onAddApps)
                } else {
                    apps.forEach { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTimer(app) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1.3f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.icon, 28)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    app.name,
                                    color = inkColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                if (app.limitMinutes > 0) {
                                    "${(app.limitMinutes - app.usedMinutes).coerceAtLeast(0)} min"
                                } else "Set timer",
                                Modifier.weight(1f),
                                color = if (app.limitMinutes > 0) inkColor else mutedColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (app.limitMinutes > 0) "${app.limitMinutes} min" else "—",
                                Modifier.weight(.8f),
                                color = mutedColor,
                                fontSize = 12.sp
                            )
                        }
                        HorizontalDivider(color = lineColor)
                    }
                    Text(
                        "Tap an app row to change its daily timer.",
                        color = mutedColor,
                        fontSize = 10.sp
                    )
                }
            }

            item {
                SectionHeader("DAILY GOALS", "+ Add Goals", inkColor, primaryColor, onAddGoal)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth()) {
                    Text("TASK", Modifier.weight(1f), color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("PRIORITY", Modifier.width(76.dp), color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("✓", Modifier.width(30.dp), color = mutedColor, fontSize = 12.sp)
                }
                HorizontalDivider(color = lineColor)

                if (goals.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Make space for what matters.",
                        color = inkColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Start with one small thing you want to do today.",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("+ Add Goals", secondaryColor, onSecondaryColor, onAddGoal)
                } else {
                    goals.forEachIndexed { index, goal ->
                        val pColor = getPriorityColor(goal.priority)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}. ${goal.title}",
                                Modifier.weight(1f),
                                color = if (goal.done) mutedColor else inkColor,
                                fontSize = 12.sp,
                                fontWeight = if (goal.done) FontWeight.Normal else FontWeight.Medium
                            )
                            Surface(
                                color = pColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, pColor.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    goal.priority.uppercase(),
                                    color = pColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Checkbox(
                                checked = goal.done,
                                onCheckedChange = { onToggleGoal(goal.id) },
                                modifier = Modifier.size(30.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = primaryColor,
                                    uncheckedColor = mutedColor,
                                    checkmarkColor = onPrimaryColor
                                )
                            )
                        }
                        HorizontalDivider(color = lineColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("+ Add Goals", secondaryColor, onSecondaryColor, onAddGoal)
                }
            }

            item {
                if (!usageAllowed) {
                    Surface(
                        color = cardColor,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, lineColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUsageSettings() }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Enable Usage Access",
                                color = inkColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Allow TimeWise in Android settings to read app usage and calculate remaining time.",
                                color = mutedColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val annotatedBottomText = buildAnnotatedString {
                    append("✧ A fresh start every day. Limits reset at midnight.\nCheck complete insights in ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = inkColor)) {
                        append("Settings > Digital Wellbeing")
                    }
                    append(".")
                }

                Text(
                    text = annotatedBottomText,
                    modifier = Modifier.fillMaxWidth(),
                    color = mutedColor,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, inkColor: Color, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            modifier = Modifier
                .clickable { onBack() }
                .padding(end = 12.dp),
            color = inkColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Image(
            painter = painterResource(R.drawable.timewise_logo),
            contentDescription = "TimeWise logo",
            modifier = Modifier.size(28.dp)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = title,
            color = inkColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    inkColor: Color,
    actionColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(
            action,
            modifier = Modifier.clickable { onClick() },
            color = actionColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Text(text, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun AppIcon(icon: Drawable, size: Int) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(icon)
            }
        },
        update = { it.setImageDrawable(icon) },
        modifier = Modifier.size(size.dp)
    )
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun loadLaunchableApps(
    context: Context,
    selected: List<String>,
    limits: Map<String, Int>,
    usageAllowed: Boolean
): List<DeviceApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    val usage = if (usageAllowed) getTodayUsage(context) else emptyMap()

    return resolved
        .mapNotNull { result ->
            val activityInfo = result.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName

            if (packageName == context.packageName) return@mapNotNull null

            val label = runCatching {
                activityInfo.loadLabel(pm).toString()
            }.getOrNull() ?: return@mapNotNull null

            val icon = runCatching {
                activityInfo.loadIcon(pm)
            }.getOrNull() ?: return@mapNotNull null

            DeviceApp(
                packageName = packageName,
                name = label,
                icon = icon,
                selected = selected.contains(packageName),
                limitMinutes = limits[packageName] ?: 0,
                usedMinutes = usage[packageName] ?: 0
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

@RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
private fun getTodayUsage(context: Context): Map<String, Int> {
    val manager = context.getSystemService(
        Context.USAGE_STATS_SERVICE
    ) as UsageStatsManager

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val usageStats = manager.queryAndAggregateUsageStats(
        calendar.timeInMillis,
        System.currentTimeMillis()
    )

    return usageStats.mapValues { (_, stats) ->
        (stats.totalTimeInForeground / 60_000L).toInt()
    }
}
