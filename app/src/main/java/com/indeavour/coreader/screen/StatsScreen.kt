package com.indeavour.coreader.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.model.firebase.UserStatsDocument
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.repository.StatsRepository
import com.indeavour.coreader.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppRoomDatabase.getDatabase(context) }
    val statsRepository = remember { StatsRepository() }
    val userRepository = remember { UserRepository() }
    
    var totalBooks by remember { mutableIntStateOf(0) }
    var completedBooks by remember { mutableIntStateOf(0) }
    var averageProgression by remember { mutableFloatStateOf(0f) }
    
    var currentStreak by remember { mutableIntStateOf(0) }
    var maxStreak by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    var statsDocument by remember { mutableStateOf<UserStatsDocument?>(null) }
    var availableYears by remember { mutableStateOf(listOf<String>()) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }

    LaunchedEffect(Unit) {
        isLoading = true
        val allBooks = database.bookDao().getAll().firstOrNull() ?: emptyList()
        totalBooks = allBooks.size
        
        var totalProgressionValue = 0f
        var completedTotal = 0
        
        allBooks.forEach { book ->
            val progress = try {
                if (book.progression.isNullOrBlank()) 0f
                else {
                    val json = JSONObject(book.progression)
                    val locations = json.optJSONObject("locations")
                    locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                }
            } catch (_: Exception) { 0f }
            
            totalProgressionValue += progress
            if (progress >= 0.995f) completedTotal++
        }
        
        completedBooks = completedTotal
        averageProgression = if (allBooks.isNotEmpty()) (totalProgressionValue / allBooks.size) * 100 else 0f

        // Yearly and Streak Stats from the cloud (aggregated source of truth)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val userModel = userRepository.user(uid)
            val stats = statsRepository.safeStats(uid)

            if (userModel != null) {
                // Lifetime overview is derived from the active cloud library so it agrees with
                // what the library screen shows.
                val activeCloudBooks = userModel.books.values.filter { !it.isDeleted }
                totalBooks = activeCloudBooks.size

                var cloudTotalProgressionValue = 0f
                var cloudCompletedCount = 0
                activeCloudBooks.forEach { b ->
                    val p = try {
                        if (b.progress.isBlank()) 0f
                        else JSONObject(b.progress).optJSONObject("locations")
                            ?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                    } catch (_: Exception) { 0f }

                    cloudTotalProgressionValue += p
                    if (p >= 0.995f) cloudCompletedCount++
                }

                completedBooks = cloudCompletedCount
                averageProgression = if (activeCloudBooks.isNotEmpty()) {
                    (cloudTotalProgressionValue / activeCloudBooks.size) * 100
                } else {
                    0f
                }
            }

            if (stats != null) {
                statsDocument = stats
                val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
                availableYears = (stats.yearlyStats.keys + currentYear).toList().sortedDescending()

                val yearly = stats.yearlyStats[currentYear]

                // Lifetime carries the streak; yearly is the fallback for older documents.
                val cloudCurrentStreak = if (stats.lifetimeStats.currentStreak == 0 && yearly != null) {
                    yearly.currentStreak
                } else {
                    stats.lifetimeStats.currentStreak
                }
                val cloudLastTimestamp = if (stats.lifetimeStats.lastReadingTimestamp == 0L && yearly != null) {
                    yearly.lastReadingTimestamp
                } else {
                    stats.lifetimeStats.lastReadingTimestamp
                }

                currentStreak = cloudCurrentStreak
                maxStreak = maxOf(maxStreak, stats.lifetimeStats.maxStreak)

                statsRepository.reconcileLifetime(
                    uid = uid,
                    stats = stats,
                    currentYear = currentYear,
                    booksCompleted = completedBooks,
                    currentStreak = cloudCurrentStreak,
                    lastReadingTimestamp = cloudLastTimestamp,
                    maxStreak = maxStreak
                )
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StreakCard("Current Streak", currentStreak, Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                        StreakCard("Best Streak", maxStreak, Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                    }
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
                        
                        val yearlyData = statsDocument?.yearlyStats?.get(selectedYear)
                        StatsCard(
                            title = "Yearly Activity ($selectedYear)",
                            stats = listOf(
                                "Books Read" to (yearlyData?.booksCompleted ?: 0).toString(),
                                "Time Spent" to "${(yearlyData?.secondsRead ?: 0L) / 60} mins"
                            ),
                            icon = Icons.Default.CalendarToday
                        )
                    }
                }
                
                item {
                    StatsCard(
                        title = "Lifetime Overview",
                        stats = listOf(
                            "Total Books" to totalBooks.toString(),
                            "Books Completed" to completedBooks.toString(),
                            "Avg. Progress" to "${averageProgression.toInt()}%"
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
