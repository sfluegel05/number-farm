package com.sfluegel.numberfarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfluegel.numberfarm.ui.theme.NumberFarmTheme

@Composable
fun HomeScreen(
    resumableGame: SavedGame?,
    onResume: () -> Unit,
    onNewGame: (Int) -> Unit,
    onStats: () -> Unit,
    diagonalMode: Boolean,
    onDiagonalModeChange: (Boolean) -> Unit,
    multiplicationMode: Boolean,
    onMultiplicationModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = "Number Farm",
                fontSize   = 48.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text      = "Harvest the right numbers!",
                fontSize  = 16.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Resume button (only shown when there's an in-progress game) ──────
            if (resumableGame != null) {
                val mins = resumableGame.elapsedSeconds / 60
                val secs = resumableGame.elapsedSeconds % 60
                val modeLabel = buildString {
                    if (resumableGame.gameState.diagonalMode) append("  ·  diagonal")
                    if (resumableGame.gameState.multiplicationMode) append("  ·  ×")
                }
                Button(
                    onClick  = onResume,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = "Resume 1..${resumableGame.n}$modeLabel  ·  (%d:%02d)".format(mins, secs),
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text       = "Choose a field size:",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                for (size in listOf(3, 4, 5, 6)) {
                    SizeButton(size = size, onClick = { onNewGame(size) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                for (size in listOf(7, 8, 9)) {
                    SizeButton(size = size, onClick = { onNewGame(size) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Diagonal mode toggle ─────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Diagonal mode",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text     = "Each number once per diagonal",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = diagonalMode,
                    onCheckedChange = onDiagonalModeChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Multiplication mode toggle ────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Multiplication mode",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text     = "Hints show products instead of sums",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = multiplicationMode,
                    onCheckedChange = onMultiplicationModeChange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onStats) {
                Text("View Statistics")
            }

            TextButton(onClick = {
                uriHandler.openUri("https://github.com/sfluegel05/number-farm/issues")
            }) {
                Text("Complaints? Suggestions?")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text      = "v${BuildConfig.VERSION_NAME}",
                fontSize  = 12.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SizeButton(size: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick
        ) {
        Text(text = "1..${size}", fontSize = 16.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    NumberFarmTheme {
        HomeScreen(resumableGame = null, onResume = {}, onNewGame = {}, onStats = {}, diagonalMode = false, onDiagonalModeChange = {}, multiplicationMode = false, onMultiplicationModeChange = {})
    }
}
