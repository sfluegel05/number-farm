package com.sfluegel.numberfarm

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import com.sfluegel.numberfarm.ui.theme.Green40
import com.sfluegel.numberfarm.ui.theme.GreenGrey40
import com.sfluegel.puzzleutils.PencilMarksGrid
import com.sfluegel.puzzleutils.PuzzleGridCell
import com.sfluegel.puzzleutils.PuzzleLayout
import com.sfluegel.puzzleutils.PuzzleTopAppBar
import com.sfluegel.puzzleutils.WavyLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val EMPTY_CELL_SYMBOL = "/"

/**
 * Returns the set of hint indices that are confirmed fulfilled, scanning from both ends.
 * A group is confirmed when it's fully bounded by CELL_EMPTY or grid edges (no CELL_UNSET boundary).
 */
private fun fulfilledHintIndices(rowCells: List<Int>, hints: List<Int>): Set<Int> {
    if (hints.isEmpty()) return emptySet()
    val result = mutableSetOf<Int>()

    // Scan from left
    var hi = 0; var i = 0
    outer@ while (i < rowCells.size && hi < hints.size) {
        when {
            rowCells[i] > 0 -> {
                var sum = 0; var j = i
                while (j < rowCells.size && rowCells[j] > 0) { sum += rowCells[j]; j++ }
                if (sum == hints[hi]) { result.add(hi); hi++; i = j } else break@outer
            }
            rowCells[i] == CELL_EMPTY -> i++
            else -> break@outer
        }
    }

    // Scan from right
    var hi2 = hints.size - 1; var k = rowCells.size - 1
    outer@ while (k >= 0 && hi2 >= 0) {
        when {
            rowCells[k] > 0 -> {
                var sum = 0; var j = k
                while (j >= 0 && rowCells[j] > 0) { sum += rowCells[j]; j-- }
                if (sum == hints[hi2]) { result.add(hi2); hi2--; k = j } else break@outer
            }
            rowCells[k] == CELL_EMPTY -> k--
            else -> break@outer
        }
    }

    return result
}

// ── Mutable bridge written by PuzzleBoard, read by GameScreen on back ──────────

private class GameProgress(
    var cells: List<Int>                                               = emptyList(),
    var pencilMarks: List<Set<Int>>                                    = emptyList(),
    var notEmptyMarks: List<Boolean>                                   = emptyList(),
    var history: List<Triple<List<Int>, List<Set<Int>>, List<Boolean>>> = emptyList(),
    var isSolved: Boolean                                              = false
)

/** Background tint applied when the player marks a cell as "definitely not empty". */
private val NotEmptyMarkColor = GreenGrey40.copy(alpha = 0.45f)

// ── Screen entry point ─────────────────────────────────────────────────────────

@Composable
fun GameScreen(n: Int, onBack: () -> Unit) {
    var gameState      by remember { mutableStateOf<GameState?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var timerActive    by remember { mutableStateOf(false) }
    val resetFnHolder  = remember { arrayOf<() -> Unit>({}) }
    val progress       = remember { GameProgress() }

    // Auto-save when this composable leaves composition (back nav or rotation).
    DisposableEffect(Unit) {
        onDispose {
            val gs = gameState
            if (gs != null && !progress.isSolved) {
                GameSave.put(SavedGame(
                    n              = n,
                    gameState      = gs,
                    cells          = progress.cells,
                    pencilMarks    = progress.pencilMarks,
                    notEmptyMarks  = progress.notEmptyMarks,
                    elapsedSeconds = elapsedSeconds,
                    history        = progress.history
                ))
            }
        }
    }

    // Restore a saved game or generate a fresh one.
    LaunchedEffect(n) {
        val saved = GameSave.get(n)
        if (saved != null) {
            progress.cells         = saved.cells
            progress.pencilMarks   = saved.pencilMarks
            progress.notEmptyMarks = saved.notEmptyMarks
            progress.history       = saved.history
            elapsedSeconds         = saved.elapsedSeconds
            gameState              = saved.gameState
        } else {
            gameState      = null
            timerActive    = false
            elapsedSeconds = 0L
            val state = withContext(Dispatchers.Default) { PuzzleGenerator.generateGame(n) }
            gameState = state
        }
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
                onBack            = {
                    val gs = gameState
                    if (gs != null && !progress.isSolved) {
                        GameSave.put(SavedGame(
                            n              = n,
                            gameState      = gs,
                            cells          = progress.cells,
                            pencilMarks    = progress.pencilMarks,
                            notEmptyMarks  = progress.notEmptyMarks,
                            elapsedSeconds = elapsedSeconds,
                            history        = progress.history
                        ))
                    }
                    onBack()
                },
                elapsedSeconds    = elapsedSeconds,
                onReset           = if (gameState != null) { { resetFnHolder[0]() } } else null,
                resetConfirmTitle = "Clear field?",
                resetConfirmText  = "This will erase all your entries.",
                helpTitle         = "How to play",
                helpText          =
                    "Fill the $gridSize×$gridSize grid with numbers 1–$n.\n\n" +
                    "Each number may appear at most once per row and column. " +
                    "Not every cell needs a number — use $EMPTY_CELL_SYMBOL to mark a cell as intentionally empty.\n\n" +
                    "The hints show the sums of consecutive filled cells in each row and column. " +
                    "For example, \"5,2\" on the left means a group of adjacent cells summing to 5, " +
                    "then a gap, then another group summing to 2.\n\n" +
                    "Tap a cell to select it, then pick a value below. " +
                    "Switch to pencil mode to note down candidates. " +
                    "Use \"Not empty\" to shade a cell green as a reminder that it must contain a number."
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
                progress         = progress,
                elapsedSeconds   = elapsedSeconds,
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
    progress: GameProgress,
    elapsedSeconds: Long,
    onBack: () -> Unit,
    onSolved: () -> Unit,
    onRegisterReset: (() -> Unit) -> Unit
) {
    val context  = LocalContext.current
    val n        = gameState.n
    val gridSize = gameState.gridSize

    // Candidate values and their display labels — index i corresponds to allCandidates[i].
    // Index 0 = CELL_EMPTY (·), index 1..n = numbers 1..n.
    val allCandidates  = remember(n) { listOf(CELL_EMPTY) + (1..n).toList() }
    val candidateLabels = remember(n) { listOf(EMPTY_CELL_SYMBOL) + (1..n).map { it.toString() } }
    val markCols       = remember(n) { if (n + 1 <= 4) 2 else 3 }

    val cells = remember(gameState) {
        val initial = if (progress.cells.size == gridSize * gridSize) progress.cells
                      else List(gridSize * gridSize) { CELL_UNSET }
        mutableStateListOf(*initial.toTypedArray())
    }
    val pencilMarks = remember(gameState) {
        val initial = if (progress.pencilMarks.size == gridSize * gridSize) progress.pencilMarks
                      else List<Set<Int>>(gridSize * gridSize) { emptySet() }
        mutableStateListOf(*initial.toTypedArray())
    }
    val notEmptyMarks = remember(gameState) {
        val initial = if (progress.notEmptyMarks.size == gridSize * gridSize) progress.notEmptyMarks
                      else List(gridSize * gridSize) { false }
        mutableStateListOf(*initial.toTypedArray())
    }
    var selectedCells by remember { mutableStateOf(emptySet<Int>()) }
    var pencilMode    by remember { mutableStateOf(false) }
    val history = remember(gameState) {
        val deque = ArrayDeque<Triple<List<Int>, List<Set<Int>>, List<Boolean>>>()
        progress.history.forEach { deque.addLast(it) }
        deque
    }

    fun saveSnapshot() {
        history.addLast(Triple(cells.toList(), pencilMarks.toList(), notEmptyMarks.toList()))
    }
    fun undo() {
        val (snapCells, snapMarks, snapNotEmpty) = history.removeLastOrNull() ?: return
        snapCells.forEachIndexed    { i, v -> cells[i]         = v }
        snapMarks.forEachIndexed    { i, v -> pencilMarks[i]   = v }
        snapNotEmpty.forEachIndexed { i, v -> notEmptyMarks[i] = v }
        selectedCells = emptySet()
    }

    fun candidateIndex(value: Int) = if (value == CELL_EMPTY) 0 else value

    fun commitValue(candidate: Int) {
        if (selectedCells.isEmpty()) return
        saveSnapshot()
        if (pencilMode) {
            val ci = candidateIndex(candidate)
            val allHaveMark = selectedCells.all { ci in pencilMarks[it] }
            selectedCells.forEach { idx ->
                pencilMarks[idx] = if (allHaveMark) pencilMarks[idx] - ci else pencilMarks[idx] + ci
            }
        } else {
            val allHaveValue = selectedCells.all { cells[it] == candidate }
            selectedCells.forEach { idx ->
                cells[idx] = if (allHaveValue) CELL_UNSET else candidate
            }
        }
    }

    val isSolved by remember {
        derivedStateOf {
            cells.indices.all { idx ->
                val sol = gameState.solution[idx / gridSize][idx % gridSize]
                val cell = cells[idx]
                if (sol == CELL_EMPTY) cell == CELL_EMPTY || cell == CELL_UNSET
                else cell == sol
            }
        }
    }

    var showSolvedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isSolved) {
        if (isSolved) {
            GameSave.clear(n)
            SolveHistory.add(
                SolveRecord(
                    timestamp      = System.currentTimeMillis(),
                    n              = n,
                    elapsedSeconds = elapsedSeconds
                ), context
            )
            // Fill any cells the player left unset (solution has CELL_EMPTY there).
            cells.indices.forEach { idx -> if (cells[idx] == CELL_UNSET) cells[idx] = CELL_EMPTY }
            onSolved()
            showSolvedDialog = true
        }
    }

    // Keep the progress bridge in sync so GameScreen can persist it on back/rotation.
    SideEffect {
        progress.cells         = cells.toList()
        progress.pencilMarks   = pencilMarks.toList()
        progress.notEmptyMarks = notEmptyMarks.toList()
        progress.history       = history.toList()
        progress.isSolved      = isSolved
        onRegisterReset {
            cells.indices.forEach         { i -> cells[i]         = CELL_UNSET }
            pencilMarks.indices.forEach   { i -> pencilMarks[i]   = emptySet() }
            notEmptyMarks.indices.forEach { i -> notEmptyMarks[i] = false }
            history.clear()
            selectedCells = emptySet()
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
            // Measure how much space column and row hint strips need relative to cellSize.
            // COL_HINT_RATIO: height per stacked hint line as a fraction of cellSize.
            // CHAR_RATIO:     width per character in a comma-joined row hint as a fraction of cellSize.
            val CHAR_RATIO        = 0.25f
            // Each stacked hint line gets this fraction of cellSize in height.
            // 0.50f leaves room for font ascender/descender; lineHeight is set to match exactly.
            val COL_HINT_LINE_H   = 0.50f

            val maxColHints = gameState.hints.colHints.maxOfOrNull { it.size } ?: 1
            val maxRowHintChars = gameState.hints.rowHints.maxOfOrNull { h ->
                h.joinToString(",").length
            }?.coerceAtLeast(1) ?: 1

            // Solve for cellSize so that hint strips + grid cells fit exactly in available space.
            val cellSizeByWidth  = availableWidth  / (gridSize.toFloat() + maxRowHintChars * CHAR_RATIO)
            val cellSizeByHeight = availableHeight / (gridSize.toFloat() + maxColHints * COL_HINT_LINE_H)
            val cellSize = minOf(cellSizeByWidth, cellSizeByHeight)

            // colHintLineHeight: the exact dp height allocated to one hint line.
            val colHintLineHeight  = cellSize * COL_HINT_LINE_H
            val colHintStripHeight = colHintLineHeight * maxColHints
            val rowHintStripWidth  = cellSize * (maxRowHintChars * CHAR_RATIO)
            // Font size slightly smaller than the line slot; lineHeight = colHintLineHeight removes
            // Compose's default 1.4× line-height expansion so hints don't overflow.
            val hintFontSize   = (cellSize.value * 0.36f).sp
            val hintLineHeight = (cellSize.value * COL_HINT_LINE_H).sp

            Column(
                modifier = Modifier.pointerInput(gridSize, cellSize, rowHintStripWidth, colHintStripHeight) {
                    val cellPx    = cellSize.toPx()
                    val rowHintPx = rowHintStripWidth.toPx()
                    val colHintPx = colHintStripHeight.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startCol = ((down.position.x - rowHintPx) / cellPx).toInt()
                        val startRow = ((down.position.y - colHintPx) / cellPx).toInt()
                        val startIdx = if (startRow in 0 until gridSize && startCol in 0 until gridSize)
                            startRow * gridSize + startCol else null
                        var selection: Set<Int> = if (startIdx != null) setOf(startIdx) else emptySet()
                        selectedCells = selection
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val col = ((change.position.x - rowHintPx) / cellPx).toInt()
                            val row = ((change.position.y - colHintPx) / cellPx).toInt()
                            if (row in 0 until gridSize && col in 0 until gridSize) {
                                val idx = row * gridSize + col
                                if (idx !in selection) {
                                    selection = selection + idx
                                    selectedCells = selection
                                }
                            }
                        }
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Column hints row — each hint stacked vertically ───────────
                Row {
                    Spacer(Modifier.width(rowHintStripWidth).height(colHintStripHeight))
                    for (c in 0 until gridSize) {
                        val ch = gameState.hints.colHints[c]
                        val colCells = (0 until gridSize).map { r -> cells[r * gridSize + c] }
                        val fulfilledCol = fulfilledHintIndices(colCells, ch)
                        // Align hints to the bottom so the last hint sits just above the grid row.
                        // Each hint is in its own fixed-height Box so the layout is predictable.
                        Column(
                            modifier             = Modifier.width(cellSize).height(colHintStripHeight),
                            horizontalAlignment  = Alignment.CenterHorizontally,
                            verticalArrangement  = Arrangement.Bottom
                        ) {
                            ch.forEachIndexed { hi, hint ->
                                Box(
                                    modifier         = Modifier.width(cellSize).height(colHintLineHeight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = hint.toString(),
                                        fontSize   = hintFontSize,
                                        lineHeight = hintLineHeight,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (hi in fulfilledCol)
                                                         MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                     else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                // ── Grid rows with row hints — comma-joined on one line ───────
                for (r in 0 until gridSize) {
                    Row {
                        val rh = gameState.hints.rowHints[r]
                        val rowCells = (0 until gridSize).map { c -> cells[r * gridSize + c] }
                        val fulfilledRow = fulfilledHintIndices(rowCells, rh)
                        Box(
                            Modifier.width(rowHintStripWidth).height(cellSize),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (rh.isNotEmpty()) {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val grayColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                Text(
                                    text = buildAnnotatedString {
                                        rh.forEachIndexed { hi, hint ->
                                            if (hi > 0) append(",")
                                            withStyle(SpanStyle(color = if (hi in fulfilledRow) grayColor else primaryColor)) {
                                                append(hint.toString())
                                            }
                                        }
                                    },
                                    fontSize   = hintFontSize,
                                    lineHeight = hintLineHeight,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.End
                                )
                            }
                        }
                        for (c in 0 until gridSize) {
                            val idx   = r * gridSize + c
                            val value = cells[idx]
                            val marks = pencilMarks[idx]
                            val bgColor = when {
                                value == CELL_EMPTY  -> MaterialTheme.colorScheme.surfaceVariant
                                value != CELL_UNSET  -> MaterialTheme.colorScheme.primaryContainer
                                notEmptyMarks[idx]   -> NotEmptyMarkColor
                                else                 -> MaterialTheme.colorScheme.surface
                            }
                            PuzzleGridCell(
                                cellSize        = cellSize,
                                isSelected      = idx in selectedCells,
                                backgroundColor = bgColor,
                                onClick         = {},
                                cellPadding     = 0.dp
                            ) {
                                when {
                                    value == CELL_UNSET && marks.isNotEmpty() ->
                                        PencilMarksGrid(
                                            labels   = candidateLabels,
                                            marked   = marks,
                                            cols     = markCols,
                                            cellSize = cellSize
                                        )
                                    value == CELL_UNSET -> { /* blank or just the green shading */ }
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
                            val isSelected = selectedCells.isNotEmpty() && if (pencilMode) {
                                val ci = candidateIndex(candidate)
                                selectedCells.all { ci in pencilMarks[it] }
                            } else {
                                selectedCells.all { cells[it] == candidate }
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick  = { commitValue(candidate) },
                                label    = {
                                    Text(
                                        text     = if (candidate == CELL_EMPTY) EMPTY_CELL_SYMBOL else candidate.toString(),
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                enabled  = selectedCells.isNotEmpty()
                            )
                        }
                    }
                }
                // ── Undo · Clear · Pencil toggle · Not-empty mark (pencil only) ───
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { undo() },
                        enabled = history.isNotEmpty(),
                    ) { Text("Undo", fontSize = 20.sp) }

                    val clearEnabled = selectedCells.isNotEmpty() && selectedCells.any { idx ->
                        cells[idx] != CELL_UNSET || pencilMarks[idx].isNotEmpty() || notEmptyMarks[idx]
                    }
                    TextButton(
                        onClick = {
                            if (selectedCells.isEmpty()) return@TextButton
                            saveSnapshot()
                            selectedCells.forEach { idx ->
                                when {
                                    cells[idx] != CELL_UNSET       -> cells[idx] = CELL_UNSET
                                    pencilMarks[idx].isNotEmpty()  -> pencilMarks[idx] = emptySet()
                                    else                           -> notEmptyMarks[idx] = false
                                }
                            }
                        },
                        enabled = clearEnabled
                    ) { Text("Clear", fontSize = 20.sp) }

                    val allNotEmpty = selectedCells.isNotEmpty() && selectedCells.all { notEmptyMarks[it] }
                    FilterChip(
                        selected = allNotEmpty,
                        onClick  = {
                            if (selectedCells.isEmpty()) return@FilterChip
                            saveSnapshot()
                            val newValue = !allNotEmpty
                            selectedCells.forEach { idx -> notEmptyMarks[idx] = newValue }
                        },
                        label   = { Text("Any", fontSize = 20.sp) },
                        enabled = selectedCells.isNotEmpty(),
                        colors = if (allNotEmpty) FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NotEmptyMarkColor,
                            selectedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant
                        ) else FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Green40.copy(alpha = 0.5f),
                            selectedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )

                    FilterChip(
                        selected = pencilMode,
                        onClick  = { pencilMode = !pencilMode },
                        label    = { Text(if (pencilMode) "Pencil: ON" else "Pencil: OFF", fontSize = 20.sp) }
                    )
                }
            }
        }
    )
}
