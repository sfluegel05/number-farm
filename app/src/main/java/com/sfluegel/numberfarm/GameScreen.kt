package com.sfluegel.numberfarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfluegel.puzzleutils.PencilMarksGrid
import com.sfluegel.puzzleutils.PuzzleGridCell
import com.sfluegel.puzzleutils.PuzzleHintCell
import com.sfluegel.puzzleutils.PuzzleLayout
import com.sfluegel.puzzleutils.PuzzleTopAppBar
import com.sfluegel.puzzleutils.WavyLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val EMPTY_CELL_SYMBOL = "·"

// ── Screen entry point ─────────────────────────────────────────────────────────

@Composable
fun GameScreen(n: Int, onBack: () -> Unit) {
    var gameState      by remember { mutableStateOf<GameState?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var timerActive    by remember { mutableStateOf(false) }
    val resetFnHolder  = remember { arrayOf<() -> Unit>({}) }

    // Generate puzzle off the main thread.
    LaunchedEffect(n) {
        gameState   = null
        timerActive = false
        elapsedSeconds = 0L
        val state = withContext(Dispatchers.Default) { PuzzleGenerator.generateGame(n) }
        gameState   = state
        timerActive = true
    }

    // Tick once per second while active.
    LaunchedEffect(timerActive) {
        if (timerActive) { while (true) { delay(1000L); elapsedSeconds++ } }
    }

    val gridSize = gridSizeFor(n)

    Scaffold(
        topBar = {
            PuzzleTopAppBar(
                title             = "Number Farm  ·  1..$n",
                onBack            = onBack,
                elapsedSeconds    = elapsedSeconds,
                onReset           = if (gameState != null) { { resetFnHolder[0]() } } else null,
                resetConfirmTitle = "Clear field?",
                resetConfirmText  = "This will erase all your entries.",
                helpTitle         = "How to play",
                helpText          =
                    "Fill the $gridSize×$gridSize grid with numbers 1–$n.\n\n" +
                    "Each number may appear at most once per row and column. " +
                    "Not every cell needs a number — use · to mark a cell as intentionally empty.\n\n" +
                    "The hints show the sums of consecutive filled cells in each row and column. " +
                    "For example, \"5,2\" on the left means a group of adjacent cells summing to 5, " +
                    "then a gap, then another group summing to 2.\n\n" +
                    "Tap a cell to select it, then pick a value below. " +
                    "Switch to pencil mode to note down candidates."
            )
        }
    ) { innerPadding ->
        val state = gameState
        if (state == null) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WavyLoadingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text      = "Planting your puzzle…",
                    fontSize  = 16.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            PuzzleBoard(
                gameState        = state,
                modifier         = Modifier.padding(innerPadding),
                onBack           = onBack,
                onSolved         = { timerActive = false },
                onRegisterReset  = { fn -> resetFnHolder[0] = fn }
            )
        }
    }
}

// ── Puzzle board ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PuzzleBoard(
    gameState: GameState,
    modifier: Modifier,
    onBack: () -> Unit,
    onSolved: () -> Unit,
    onRegisterReset: (() -> Unit) -> Unit
) {
    val n        = gameState.n
    val gridSize = gameState.gridSize

    // Candidate values and their display labels — index i corresponds to allCandidates[i].
    // Index 0 = CELL_EMPTY (·), index 1..n = numbers 1..n.
    val allCandidates  = remember(n) { listOf(CELL_EMPTY) + (1..n).toList() }
    val candidateLabels = remember(n) { listOf(EMPTY_CELL_SYMBOL) + (1..n).map { it.toString() } }
    val markCols       = remember(n) { if (n + 1 <= 4) 2 else 3 }

    val cells       = remember(gameState) { mutableStateListOf(*Array(gridSize * gridSize) { CELL_UNSET }) }
    val pencilMarks = remember(gameState) { mutableStateListOf(*Array<Set<Int>>(gridSize * gridSize) { emptySet() }) }
    var selectedCell by remember { mutableStateOf<Int?>(null) }
    var pencilMode   by remember { mutableStateOf(false) }
    val history      = remember(gameState) { ArrayDeque<Pair<List<Int>, List<Set<Int>>>>() }

    fun saveSnapshot() { history.addLast(cells.toList() to pencilMarks.toList()) }
    fun undo() {
        val (snapCells, snapMarks) = history.removeLastOrNull() ?: return
        snapCells.forEachIndexed { i, v -> cells[i] = v }
        snapMarks.forEachIndexed { i, v -> pencilMarks[i] = v }
        selectedCell = null
    }

    fun candidateIndex(value: Int) = if (value == CELL_EMPTY) 0 else value

    fun tap(r: Int, c: Int) {
        val idx = r * gridSize + c
        selectedCell = if (selectedCell == idx) null else idx
    }

    fun commitValue(candidate: Int) {
        val idx = selectedCell ?: return
        saveSnapshot()
        if (pencilMode) {
            val ci = candidateIndex(candidate)
            pencilMarks[idx] = if (ci in pencilMarks[idx]) pencilMarks[idx] - ci else pencilMarks[idx] + ci
        } else {
            cells[idx] = if (cells[idx] == candidate) CELL_UNSET else candidate
        }
    }

    val isSolved by remember {
        derivedStateOf {
            cells.indices.all { idx ->
                cells[idx] == gameState.solution[idx / gridSize][idx % gridSize]
            }
        }
    }

    var showSolvedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isSolved) { if (isSolved) { onSolved(); showSolvedDialog = true } }

    // Register reset function so the top-bar's ↺ button can clear everything.
    SideEffect {
        onRegisterReset {
            cells.indices.forEach       { i -> cells[i]       = CELL_UNSET }
            pencilMarks.indices.forEach { i -> pencilMarks[i] = emptySet() }
            history.clear()
            selectedCell = null
        }
    }

    if (showSolvedDialog) {
        AlertDialog(
            onDismissRequest = { showSolvedDialog = false },
            title            = { Text("Harvest complete!") },
            text             = { Text("You've filled the field correctly. Well planted!") },
            confirmButton    = {
                TextButton(onClick = onBack) { Text("Back to Menu") }
            },
            dismissButton    = {
                TextButton(onClick = { showSolvedDialog = false }) { Text("Keep looking") }
            }
        )
    }

    PuzzleLayout(
        modifier = modifier,
        grid = { availableWidth, availableHeight ->
            // One extra cell on each axis for the hint strip.
            val cellSize = minOf(
                availableWidth  / (gridSize + 1),
                availableHeight / (gridSize + 1)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ── Column hints row ──────────────────────────────────────────
                Row {
                    Spacer(Modifier.size(cellSize))   // top-left corner
                    for (c in 0 until gridSize) {
                        val ch = gameState.hints.colHints[c]
                        PuzzleHintCell(
                            primaryHint   = ch.firstOrNull()?.toString(),
                            secondaryHint = ch.drop(1).joinToString(",").ifEmpty { null },
                            cellSize      = cellSize
                        )
                    }
                }
                // ── Grid rows with row hints ──────────────────────────────────
                for (r in 0 until gridSize) {
                    Row {
                        val rh = gameState.hints.rowHints[r]
                        PuzzleHintCell(
                            primaryHint   = rh.firstOrNull()?.toString(),
                            secondaryHint = rh.drop(1).joinToString(",").ifEmpty { null },
                            cellSize      = cellSize
                        )
                        for (c in 0 until gridSize) {
                            val idx   = r * gridSize + c
                            val value = cells[idx]
                            val marks = pencilMarks[idx]
                            val bgColor = when (value) {
                                CELL_UNSET -> MaterialTheme.colorScheme.surface
                                CELL_EMPTY -> MaterialTheme.colorScheme.surfaceVariant
                                else       -> MaterialTheme.colorScheme.primaryContainer
                            }
                            PuzzleGridCell(
                                cellSize        = cellSize,
                                isSelected      = selectedCell == idx,
                                backgroundColor = bgColor,
                                onClick         = { tap(r, c) }
                            ) {
                                when {
                                    value == CELL_UNSET && marks.isNotEmpty() ->
                                        PencilMarksGrid(
                                            labels   = candidateLabels,
                                            marked   = marks,
                                            cols     = markCols,
                                            cellSize = cellSize
                                        )
                                    value == CELL_UNSET -> { /* blank */ }
                                    value == CELL_EMPTY -> Text(
                                        text     = EMPTY_CELL_SYMBOL,
                                        fontSize = (cellSize.value * 0.50f).sp,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    else -> Text(
                                        text       = value.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = (cellSize.value * 0.45f).sp,
                                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        controls = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Value picker ──────────────────────────────────────────────
                val half       = (allCandidates.size + 1) / 2
                val pickerRows = if (n >= 6) listOf(allCandidates.take(half), allCandidates.drop(half))
                                 else        listOf(allCandidates)
                pickerRows.forEach { rowCandidates ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        rowCandidates.forEach { candidate ->
                            val sel        = selectedCell
                            val isSelected = sel != null && if (pencilMode) {
                                candidateIndex(candidate) in pencilMarks[sel]
                            } else {
                                cells[sel] == candidate
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick  = { commitValue(candidate) },
                                label    = {
                                    Text(if (candidate == CELL_EMPTY) EMPTY_CELL_SYMBOL else candidate.toString())
                                },
                                enabled  = selectedCell != null
                            )
                        }
                    }
                }
                // ── Undo · Clear · Pencil toggle ──────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { undo() },
                        enabled = history.isNotEmpty()
                    ) { Text("Undo") }

                    val sel          = selectedCell
                    val clearEnabled = sel != null && (cells[sel] != CELL_UNSET || pencilMarks[sel].isNotEmpty())
                    TextButton(
                        onClick = {
                            val idx = sel ?: return@TextButton
                            saveSnapshot()
                            if (cells[idx] != CELL_UNSET) cells[idx] = CELL_UNSET
                            else pencilMarks[idx] = emptySet()
                        },
                        enabled = clearEnabled
                    ) { Text("Clear") }

                    FilterChip(
                        selected = pencilMode,
                        onClick  = { pencilMode = !pencilMode },
                        label    = { Text(if (pencilMode) "Pencil: ON" else "Pencil: OFF") }
                    )
                }
            }
        }
    )
}
