package com.indeavour.coreader.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.repository.StatsRepository
import com.indeavour.coreader.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val COMPLETED_AT = 0.995f

private fun progressOf(locatorJson: String?): Float = try {
    if (locatorJson.isNullOrBlank()) 0f
    else JSONObject(locatorJson).optJSONObject("locations")
        ?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
} catch (_: Exception) {
    0f
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        seconds <= 0 -> "0 min"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "$minutes min"
        else -> "<1 min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppRoomDatabase.getDatabase(context) }
    val statsRepository = remember { StatsRepository() }
    val userRepository = remember { UserRepository() }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val currentYear = today.year.toString()

    var totalBooks by remember { mutableIntStateOf(0) }
    var completedBooks by remember { mutableIntStateOf(0) }
    var averageProgression by remember { mutableFloatStateOf(0f) }

    var currentStreak by remember { mutableIntStateOf(0) }
    var maxStreak by remember { mutableIntStateOf(0) }
    var readToday by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var statsDocument by remember { mutableStateOf<UserStatsDocument?>(null) }
    var availableYears by remember { mutableStateOf(listOf(currentYear)) }
    var selectedYear by remember { mutableStateOf(currentYear) }

    LaunchedEffect(Unit) {
        isLoading = true

        // The local library is the baseline, so the screen still has something to show offline.
        val allBooks = database.bookDao().getAll().firstOrNull() ?: emptyList()
        val localProgress = allBooks.map { progressOf(it.progression) }
        totalBooks = allBooks.size
        completedBooks = localProgress.count { it >= COMPLETED_AT }
        averageProgression = if (localProgress.isNotEmpty()) localProgress.average().toFloat() * 100 else 0f

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            try {
                val userModel = userRepository.user(uid)
                val stats = statsRepository.safeStats(uid)

                if (userModel != null) {
                    // Lifetime overview is derived from the active cloud library so it agrees with
                    // what the library screen shows.
                    val cloudProgress = userModel.books.values
                        .filter { !it.isDeleted }
                        .map { progressOf(it.progress) }
                    totalBooks = cloudProgress.size
                    completedBooks = cloudProgress.count { it >= COMPLETED_AT }
                    averageProgression = if (cloudProgress.isNotEmpty()) {
                        cloudProgress.average().toFloat() * 100
                    } else {
                        0f
                    }
                }

                if (stats != null) {
                    statsDocument = stats
                    availableYears = (stats.yearlyStats.keys + currentYear).distinct().sortedDescending()

                    // The stored streak only changes when something is read, so work out what
                    // it is worth today instead of showing it as-is.
                    currentStreak = StatsRepository.liveStreak(stats, today, zone)
                    readToday = StatsRepository.readOn(stats, today, zone)
                    maxStreak = maxOf(
                        currentStreak,
                        stats.lifetimeStats.maxStreak,
                        stats.yearlyStats.values.maxOfOrNull { it.maxStreak } ?: 0
                    )

                    // Lifetime carries the streak; yearly is the fallback for older documents.
                    val yearly = stats.yearlyStats[currentYear]
                    val storedStreak = if (stats.lifetimeStats.currentStreak == 0 && yearly != null) {
                        yearly.currentStreak
                    } else {
                        stats.lifetimeStats.currentStreak
                    }
                    val storedLastTimestamp = if (stats.lifetimeStats.lastReadingTimestamp == 0L && yearly != null) {
                        yearly.lastReadingTimestamp
                    } else {
                        stats.lifetimeStats.lastReadingTimestamp
                    }

                    if (userModel != null) {
                        statsRepository.reconcileLifetime(
                            uid = uid,
                            stats = stats,
                            currentYear = currentYear,
                            booksCompleted = completedBooks,
                            currentStreak = storedStreak,
                            lastReadingTimestamp = storedLastTimestamp,
                            maxStreak = maxStreak
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Most likely offline: keep whatever was loaded rather than crashing the screen.
                Log.e("StatsScreen", "Failed to load cloud stats", e)
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Statistics", color = MaterialTheme.colorScheme.secondary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        } else {
            val stats = statsDocument ?: UserStatsDocument()

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StreakCard("Current Streak", currentStreak, Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                            StreakCard("Best Streak", maxStreak, Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                        }
                        if (currentStreak > 0 && !readToday) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Read today to keep your streak going",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                item {
                    val week = (6 downTo 0).map { daysAgo ->
                        val day = today.minusDays(daysAgo.toLong())
                        day to StatsRepository.secondsOn(stats, day)
                    }
                    WeekCard(week)
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (availableYears.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = availableYears.indexOf(selectedYear).coerceAtLeast(0),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Teal,
                                edgePadding = 0.dp,
                                divider = {}
                            ) {
                                availableYears.forEach { year ->
                                    Tab(
                                        selected = selectedYear == year,
                                        onClick = { selectedYear = year },
                                        text = { Text(year) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        val yearly = stats.yearlyStats[selectedYear] ?: UserStats()
                        val activeDays = yearly.dailySeconds.filterValues { it > 0 }
                        val bestDay = activeDays.maxByOrNull { it.value }
                        val bestDayLabel = bestDay?.let { (key, seconds) ->
                            val date = try {
                                LocalDate.parse("$selectedYear-$key").format(DateTimeFormatter.ofPattern("d MMM"))
                            } catch (_: Exception) {
                                key
                            }
                            "${formatDuration(seconds)} ($date)"
                        }

                        StatsCard(
                            title = "Yearly Activity ($selectedYear)",
                            stats = listOfNotNull(
                                "Books Read" to yearly.booksCompleted.toString(),
                                "Time Spent" to formatDuration(yearly.secondsRead),
                                "Days Read" to yearly.daysRead.toString(),
                                if (activeDays.isNotEmpty()) {
                                    "Avg. per Reading Day" to formatDuration(activeDays.values.sum() / activeDays.size)
                                } else null,
                                bestDayLabel?.let { "Best Day" to it },
                                "Reading Sessions" to yearly.sessions.toString(),
                                "Longest Session" to formatDuration(yearly.longestSessionSeconds),
                                "Highlights Made" to yearly.highlightsMade.toString(),
                                "Notes Written" to yearly.notesWritten.toString()
                            ),
                            icon = Icons.Default.CalendarToday
                        )
                    }
                }

                item {
                    val lifetime = stats.lifetimeStats
                    // Older documents only tracked time per year, so lifetime can lag behind.
                    val lifetimeSeconds = maxOf(lifetime.secondsRead, stats.yearlyStats.values.sumOf { it.secondsRead })
                    StatsCard(
                        title = "Lifetime Overview",
                        stats = listOf(
                            "Time Spent" to formatDuration(lifetimeSeconds),
                            "Total Books" to totalBooks.toString(),
                            "Books Completed" to completedBooks.toString(),
                            "Avg. Progress" to "${averageProgression.toInt()}%",
                            "Days Read" to lifetime.daysRead.toString(),
                            "Reading Sessions" to lifetime.sessions.toString(),
                            "Longest Session" to formatDuration(lifetime.longestSessionSeconds),
                            "Highlights Made" to lifetime.highlightsMade.toString(),
                            "Notes Written" to lifetime.notesWritten.toString()
                        ),
                        icon = Icons.Default.Schedule
                    )
                }

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            "Overall Completion Rate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { averageProgression / 100f },
                                modifier = Modifier.size(140.dp),
                                color = Teal,
                                strokeWidth = 12.dp,
                                trackColor = Teal.copy(alpha = 0.2f)
                            )
                            Text(
                                "${averageProgression.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Time read on each of the last seven days, oldest first. */
@Composable
private fun WeekCard(days: List<Pair<LocalDate, Long>>) {
    val total = days.sumOf { it.second }
    val peak = days.maxOf { it.second }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Last 7 Days", style = MaterialTheme.typography.titleLarge, color = Teal, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Teal.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.forEach { (day, seconds) ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (seconds >= 60) "${seconds / 60}m" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val fraction = if (seconds > 0) (seconds.toFloat() / peak).coerceIn(0.04f, 1f) else 0f
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(Teal)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(Teal.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(formatDuration(total), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily Average", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(formatDuration(total / days.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StreakCard(label: String, value: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = if (value > 0) Teal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Teal)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun StatsCard(title: String, stats: List<Pair<String, String>>, icon: ImageVector? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = Teal, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Teal.copy(alpha = 0.3f))
            stats.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
