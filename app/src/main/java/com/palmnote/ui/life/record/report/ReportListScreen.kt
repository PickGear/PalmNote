package com.palmnote.ui.life.record.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.ui.life.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListScreen(onBack: () -> Unit, onItemClick: (Long) -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val rColor = Color(0xFF42A5F5)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.life_report_title), fontWeight = FontWeight.Bold, color = rColor) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (state.isLoading) { Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = rColor) }; return@Scaffold }
        if (state.reports.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.life_report_empty),
                subtitle = stringResource(R.string.life_report_empty_subtitle)
            )
        } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.reports.sortedByDescending { it.periodStart }, key = { it.id }) { report ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (report.type == "WEEKLY") Icons.Default.DateRange else Icons.Default.CalendarMonth, null, tint = rColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (report.type == "WEEKLY") stringResource(R.string.life_report_tab_weekly) else stringResource(R.string.life_report_tab_monthly), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        }
    }
}

