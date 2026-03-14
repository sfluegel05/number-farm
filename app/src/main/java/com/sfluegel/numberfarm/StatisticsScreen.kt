package com.sfluegel.numberfarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Emoji symbols used throughout statistics
private const val DIAGONAL_EMOJI       = "↗"
private const val MULTIPLICATION_EMOJI = "✕"

/** Compact label for a configuration, e.g. "1..5", "1..5 ↗", "1..5 ✕", "1..5 ↗✕". */
private fun configLabel(n: Int, diagonalMode: Boolean, multiplicationMode: Boolean): String {
    val modes = buildString {
        if (diagonalMode) append(" $DIAGONAL_EMOJI")
        if (multiplicationMode) append(" $MULTIPLICATION_EMOJI")
    }
    return "1..$n$modes"
}

/** Grouping key for solve records. */
private data class SolveConfig(val n: Int, val diagonalMode: Boolean, val multiplicationMode: Boolean)

private val SolveRecord.config get() = SolveConfig(n, diagonalMode, multiplicationMode)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val records = SolveHistory.records

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No solves yet.\nGo plant some numbers!",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            val grouped = records
                .groupBy { it.config }
                .entries
                .sortedWith(compareBy({ it.key.n }, { it.key.diagonalMode }, { it.key.multiplicationMode }))
                .map { it.toPair() }

            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    Text(
                        "By Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(grouped) { (config, solves) ->
                    ConfigCard(config, solves)
                }

                item { Spacer(Modifier.height(8.dp)) }

                item {
                    Text(
                        "Full History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(records.reversed()) { record ->
                    HistoryItem(record)
                    HorizontalDivider()
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTime(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))

private fun formatDateTime(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()).format(Date(ts))

// ── Cards and rows ────────────────────────────────────────────────────────────

@Composable
private fun ConfigCard(config: SolveConfig, solves: List<SolveRecord>) {
    val avgSecs = solves.map { it.elapsedSeconds }.average().toLong()
    val fastest = solves.minBy { it.elapsedSeconds }
    val slowest = solves.maxBy { it.elapsedSeconds }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                configLabel(config.n, config.diagonalMode, config.multiplicationMode),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${solves.size} solve${if (solves.size == 1) "" else "s"}")
                Text("Avg: ${formatTime(avgSecs)}")
            }
            if (solves.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Fastest: ${formatTime(fastest.elapsedSeconds)} (${formatDate(fastest.timestamp)})",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Slowest: ${formatTime(slowest.elapsedSeconds)} (${formatDate(slowest.timestamp)})",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(record: SolveRecord) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                configLabel(record.n, record.diagonalMode, record.multiplicationMode),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                formatDateTime(record.timestamp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(formatTime(record.elapsedSeconds), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
