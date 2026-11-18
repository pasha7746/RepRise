package com.reprise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B0F14)
private val Panel = Color(0xFF151C24)
private val Panel2 = Color(0xFF1D2732)
private val Accent = Color(0xFF78E0B5)
private val Blue = Color(0xFF7CB7FF)
private val Muted = Color(0xFF9AA8B6)
private val White = Color(0xFFF2F6FA)

data class Exercise(val name: String, val target: Int, val actual: Int, val sets: List<Int>, val seconds: Int)
data class Session(val date: String, val push: Exercise, val crunch: Exercise)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("reprisedata", MODE_PRIVATE)
        setContent {
            var sessions by remember { mutableStateOf(loadSessions(prefs.getString("sessions", "[]") ?: "[]")) }
            var pushTarget by remember { mutableIntStateOf(prefs.getInt("pushTarget", -1)) }
            var crunchTarget by remember { mutableIntStateOf(prefs.getInt("crunchTarget", -1)) }
            var tab by remember { mutableIntStateOf(0) }
            var setup by remember { mutableStateOf(pushTarget < 0 || crunchTarget < 0) }
            fun persist() {
                prefs.edit().putString("sessions", saveSessions(sessions))
                    .putInt("pushTarget", pushTarget).putInt("crunchTarget", crunchTarget).apply()
            }
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg, surface = Panel, primary = Accent, secondary = Blue,
                onBackground = White, onSurface = White
            )) {
                if (setup) {
                    SetupScreen(
                        onSave = { p, c -> pushTarget = p; crunchTarget = c; setup = false; persist() }
                    )
                } else {
                    Scaffold(
                        containerColor = Bg,
                        bottomBar = {
                            NavigationBar(containerColor = Panel) {
                                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Today") })
                                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                                    icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Progress") })
                                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                                    icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                            }
                        }
                    ) { pad ->
                        when (tab) {
                            0 -> TodayScreen(Modifier.padding(pad), pushTarget, crunchTarget, sessions) { p, c ->
                                val today = dateKey()
                                val newSession = Session(today, p, c)
                                sessions = sessions.filterNot { it.date == today } + newSession
                                val previous = sessions.filter { it.date != today }.maxByOrNull { it.date }
                                pushTarget = nextTarget(pushTarget, p, previous?.push)
                                crunchTarget = nextTarget(crunchTarget, c, previous?.crunch)
                                persist()
                            }
                            1 -> ProgressScreen(Modifier.padding(pad), sessions, pushTarget, crunchTarget)
                            else -> HistoryScreen(Modifier.padding(pad), sessions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onSave: (Int, Int) -> Unit) {
    var p by remember { mutableStateOf("10") }
    var c by remember { mutableStateOf("15") }
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("RepRise", color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Build strength, one session at a time.", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        Text("Set your starting targets", color = White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        NumberField("Starting push-ups", p) { p = it }
        NumberField("Starting crunches", c) { c = it }
        Button(
            onClick = { onSave((p.toIntOrNull() ?: 10).coerceAtLeast(1), (c.toIntOrNull() ?: 15).coerceAtLeast(1)) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        ) { Text("Start training", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
}

@Composable
fun TodayScreen(modifier: Modifier, pushTarget: Int, crunchTarget: Int, sessions: List<Session>,
                onSave: (Exercise, Exercise) -> Unit) {
    val today = sessions.firstOrNull { it.date == dateKey() }
    var pSets by remember { mutableStateOf(listOf("0")) }
    var cSets by remember { mutableStateOf(listOf("0")) }
    var pTime by remember { mutableStateOf("0") }
    var cTime by remember { mutableStateOf("0") }
    var saved by remember { mutableStateOf(today != null) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp)) {
        item {
            Text("REP RISE", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Today's challenge", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()), color = Muted, modifier = Modifier.padding(bottom = 20.dp))
        }
        item {
            ExerciseEntry("Push-ups", pushTarget, pSets, { pSets = it }, pTime, { pTime = it }, Blue)
            Spacer(Modifier.height(14.dp))
            ExerciseEntry("Crunches", crunchTarget, cSets, { cSets = it }, cTime, { cTime = it }, Accent)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val p = pSets.map { it.toIntOrNull() ?: 0 }
                    val c = cSets.map { it.toIntOrNull() ?: 0 }
                    onSave(
                        Exercise("Push-ups", pushTarget, p.sum(), p, (pTime.toIntOrNull() ?: 0) * 60),
                        Exercise("Crunches", crunchTarget, c.sum(), c, (cTime.toIntOrNull() ?: 0) * 60)
                    )
                    saved = true
                },
                enabled = !saved,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(if (saved) "Workout saved" else "Save today's workout", fontWeight = FontWeight.Bold) }
            if (saved) Text("Today's session is recorded. Targets update for your next workout.", color = Accent, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ExerciseEntry(title: String, target: Int, sets: List<String>, onSets: (List<String>) -> Unit,
                  minutes: String, onMinutes: (String) -> Unit, tint: Color) {
    val total = sets.sumOf { it.toIntOrNull() ?: 0 }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = tint)
                Spacer(Modifier.width(9.dp))
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Target $target", color = tint, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$total", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(" / $target reps", color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text("${(total * 100 / target).coerceAtMost(999)}%", color = tint, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { (total.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp), color = tint, trackColor = Panel2)
            Text("Sets", color = Muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            sets.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${index + 1}", color = Muted, modifier = Modifier.width(58.dp))
                    OutlinedTextField(value = value, onValueChange = { v ->
                        onSets(sets.toMutableList().also { it[index] = v.filter(Char::isDigit).take(4) })
                    }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (sets.size > 1) onSets(sets.toMutableList().also { it.removeAt(index) }) }) {
                        Icon(Icons.Default.Remove, null, tint = Muted)
                    }
                }
            }
            TextButton(onClick = { onSets(sets + "0") }) { Icon(Icons.Default.Add, null); Text("Add set") }
            OutlinedTextField(value = minutes, onValueChange = { onMinutes(it.filter(Char::isDigit).take(3)) },
                label = { Text("Time taken (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ProgressScreen(modifier: Modifier, sessions: List<Session>, pushTarget: Int, crunchTarget: Int) {
    val sorted = sessions.sortedBy { it.date }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text("Your progress", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Targets adapt as you complete workouts.", color = Muted, modifier = Modifier.padding(bottom = 18.dp))
        Text("Push-ups · completed reps", color = Blue, fontWeight = FontWeight.SemiBold)
        SimpleChart(sorted.map { it.push.actual }, Blue)
        Spacer(Modifier.height(18.dp))
        Text("Crunches · completed reps", color = Accent, fontWeight = FontWeight.SemiBold)
        SimpleChart(sorted.map { it.crunch.actual }, Accent)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Next push-up target", pushTarget.toString(), Modifier.weight(1f))
            StatCard("Next crunch target", crunchTarget.toString(), Modifier.weight(1f))
        }
        Text("${sessions.size} recorded workout days", color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun SimpleChart(values: List<Int>, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().height(150.dp)) {
        if (values.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Complete a workout to see your chart", color = Muted) }
        } else {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                val stepX = if (values.size == 1) size.width else size.width / (values.size - 1)
                val pts = values.mapIndexed { i, v ->
                    androidx.compose.ui.geometry.Offset(i * stepX, size.height - (v / max * size.height))
                }
                for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = 5f, cap = StrokeCap.Round)
                pts.forEach { drawCircle(color, radius = 6f, center = it) }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier, sessions: List<Session>) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text("Workout history", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (sessions.isEmpty()) {
            Text("Your completed workouts will appear here.", color = Muted, modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
                items(sessions.sortedByDescending { it.date }) { s ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text(s.date, color = Accent, fontWeight = FontWeight.Bold)
                            Text("Push-ups: ${s.push.actual}/${s.push.target}  ·  ${s.push.sets.size} sets", color = White)
                            Text("Crunches: ${s.crunch.actual}/${s.crunch.target}  ·  ${s.crunch.sets.size} sets", color = White)
                        }
                    }
                }
            }
        }
    }
}

fun dateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

// Completion-based progression: large over-performance earns a larger increase;
// partial completion reduces the next target. Missing days never call this function.
fun nextTarget(current: Int, e: Exercise, previous: Exercise? = null): Int {
    val ratio = if (e.target <= 0) 0.0 else e.actual.toDouble() / e.target
    val setsUsed = e.sets.count { it > 0 }.coerceAtLeast(1)
    val adjustment = when {
        ratio >= 1.20 -> 4
        ratio >= 1.10 -> 3
        ratio >= 1.00 -> 2
        ratio >= 0.90 -> 1
        ratio >= 0.75 -> 0
        ratio >= 0.50 -> -1
        else -> -2
    }
    // Reward efficient performance, but only after the target is met.
    val setBonus = if (ratio >= 1.0 && setsUsed <= 2) 1 else 0
    val fasterThanPrevious = previous != null &&
        e.actual >= previous.actual &&
        e.seconds > 0 && previous.seconds > 0 &&
        e.seconds <= (previous.seconds * 0.90).toInt()
    val timeBonus = if (ratio >= 1.0 && fasterThanPrevious) 1 else 0
    return (current + adjustment + setBonus + timeBonus).coerceAtLeast(1)
}

fun saveSessions(sessions: List<Session>): String {
    val arr = JSONArray()
    sessions.forEach { s ->
        fun ex(e: Exercise) = JSONObject().put("name", e.name).put("target", e.target)
            .put("actual", e.actual).put("sets", JSONArray(e.sets)).put("seconds", e.seconds)
        arr.put(JSONObject().put("date", s.date).put("push", ex(s.push)).put("crunch", ex(s.crunch)))
    }
    return arr.toString()
}
fun loadSessions(raw: String): List<Session> = try {
    val a = JSONArray(raw)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        fun ex(k: String): Exercise {
            val e = o.getJSONObject(k); val sa = e.getJSONArray("sets")
            return Exercise(e.getString("name"), e.getInt("target"), e.getInt("actual"),
                (0 until sa.length()).map { sa.getInt(it) }, e.optInt("seconds"))
        }
        Session(o.getString("date"), ex("push"), ex("crunch"))
    }
} catch (_: Exception) { emptyList() }
