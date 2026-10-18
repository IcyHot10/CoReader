package com.indeavour.coreader.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LineAxis
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indeavour.coreader.ui.theme.Teal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInsightsScreen(
    onBack: () -> Unit,
    routeToGroupProgress: () -> Unit,
    routeToGroupReadingList: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = MaterialTheme.colorScheme.secondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InsightCard(
                title = "View Group Book Progress",
                description = "Track reading progress across group members",
                icon = Icons.Default.LineAxis,
                onClick = routeToGroupProgress
            )

            InsightCard(
                title = "Group Next Book Selection",
                description = "Suggest and decide on the next book for your group",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = routeToGroupReadingList
            )
        }
    }
}
