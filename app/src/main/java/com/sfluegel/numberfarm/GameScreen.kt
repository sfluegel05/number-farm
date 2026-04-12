package com.sfluegel.numberfarm

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfluegel.puzzleutils.PencilMarksGrid
import com.sfluegel.puzzleutils.PuzzleBoard as UtilsPuzzleBoard
import com.sfluegel.puzzleutils.PuzzleGridCell
import com.sfluegel.puzzleutils.PuzzleTopAppBar
import com.sfluegel.puzzleutils.WavyLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val EMPTY_CELL_SYMBOL = "/"

/**
 * Returns the set of hint indices that are confirmed fulfilled, scanning from both ends.
 * A group is confirmed when it's fully bounded by CELL_EMPTY or grid edges (no CELL_UNSET boundary).
 * When [multiplicationMode] is true, groups are matched by product instead of sum.
 */
private fun fulfilledHintIndices(rowCells: List<Int>, hints: List<Int>, multiplicationMode: Boolean = false): Set<Int> {
    if (hints.isEmpty()) return emptySet()
    val result = mutableSetOf<Int>()

    fun aggregate(a: Int, b: Int) = if (multiplicationMode) a * b else a + b
    val identity = if (multiplicationMode) 1 else 0

    // Scan from left
    var hi = 0; var i = 0
    outer@ while (i < rowCells.size && hi < hints.size) {
        when {
            rowCells[i] > 0 -> {
                var acc = identity; var j = i
                while (j < rowCells.size && rowCells[j] > 0) { acc = aggregate(acc, rowCells[j]); j++ }
                if (acc == hints[hi]) { result.add(hi); hi++; i = j } else break@outer
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
                var acc = identity; var j = k
                while (j >= 0 && rowCells[j] > 0) { acc = aggregate(acc, rowCells[j]); j-- }
                if (acc == hints[hi2]) { result.add(hi2); hi2--; k = j } else break@outer
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

// ── Screen entry point ─────────────────────────────────────────────────────────

@Composable
fun GameScreen(n: Int, diagonalMode: Boolean = false, multiplicationMode: Boolean = false, onBack: () -> Unit) {
    var gameState      by remember { mutableStateOf<GameState?>(null) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var timerActive    by remember { mutableStateOf(false) }
    val resetFnHolder  = remember { arrayOf({}) }
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
            val state = withContext(Dispatchers.Default) { PuzzleGenerator.generateGame(n, diagonalMode = diagonalMode, multiplicationMode = multiplicationMode) }
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
                helpText          = run {
                    val diagLine = if (diagonalMode)
                        "\n\nIn diagonal mode, each number also appears at most once in each of the two main diagonals (highlighted in amber)."
                    else ""
                    val hintWord = if (multiplicationMode) "products" else "sums"
                    val hintExample = if (multiplicationMode)
                        "For example, \"6,4\" on the left means a group of adjacent cells with product 6, then a gap, then another group with product 4."
                    else
                        "For example, \"5,2\" on the left means a group of adjacent cells summing to 5, then a gap, then another group summing to 2."
                    "Fill the $gridSize×$gridSize grid with numbers 1–$n.\n\n" +
                    "Each number may appear at most once per row and column.$diagLine " +
                    "Not every cell needs a number — use $EMPTY_CELL_SYMBOL to mark a cell as intentionally empty.\n\n" +
                    "The hints show the $hintWord of consecutive filled cells in each row and column. " +
                    "$hintExample\n\n" +
                    "Tap a cell to select it, then pick a value below. " +
                    "Switch to pencil mode to note down candidates. " +
                    "Use \"Not empty\" to shade a cell green as a reminder that it must contain a number."
                }
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
    val context         = LocalContext.current
    val n               = gameState.n
    val gridSize        = gameState.gridSize
    val prefilledCells  = gameState.prefilledCells

    val allCandidates   = remember(n) { listOf(CELL_EMPTY) + (1..n).toList() }
    val candidateLabels = remember(n) { listOf(EMPTY_CELL_SYMBOL) + (1..n).map { it.toString() } }
    val markCols        = remember(n) { if (n + 1 <= 4) 2 else 3 }
    val flatSolution    = remember(gameState) { gameState.solution.flatMap { it.toList() } }

    var showSolvedDialog by remember { mutableStateOf(false) }

    if (showSolvedDialog) {
        AlertDialog(
            onDismissRequest = { showSolvedDialog = false },
            title            = { Text("Harvest complete!") },
            text             = { Text("You've filled the field correctly. Well planted!") },
            confirmButton    = { TextButton(onClick = onBack) { Text("Back to Menu") } },
            dismissButton    = { TextButton(onClick = { showSolvedDialog = false }) { Text("Keep looking") } }
        )
    }

    UtilsPuzzleBoard(
        size                 = gridSize,
        solution             = flatSolution,
        candidates           = allCandidates,
        unsetValue           = CELL_UNSET,
        initialCells         = if (progress.cells.size == gridSize * gridSize) progress.cells
                               else List(gridSize * gridSize) { idx -> prefilledCells[idx] ?: CELL_UNSET },
        initialPencilMarks   = if (progress.pencilMarks.size == gridSize * gridSize) progress.pencilMarks
                               else List(gridSize * gridSize) { emptySet() },
        initialNotEmptyMarks = if (progress.notEmptyMarks.size == gridSize * gridSize) progress.notEmptyMarks
                               else List(gridSize * gridSize) { false },
        initialHistory       = progress.history,
        modifier             = modifier,
        prefilledCells       = prefilledCells,
        showNotEmptyButton   = true,
        notEmptyActiveColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
        isSolvedCheck        = { cells ->
            cells.indices.all { idx ->
                val sol  = gameState.solution[idx / gridSize][idx % gridSize]
                val cell = cells[idx]
                if (sol == CELL_EMPTY) cell == CELL_EMPTY || cell == CELL_UNSET else cell == sol
            }
        },
        solvedFillValue = CELL_EMPTY,
        onSolved        = {
            GameSave.clear(n)
            SolveHistory.add(
                SolveRecord(
                    timestamp          = System.currentTimeMillis(),
                    n                  = n,
                    elapsedSeconds     = elapsedSeconds,
                    diagonalMode       = gameState.diagonalMode,
                    multiplicationMode = gameState.multiplicationMode
                ), context
            )
            onSolved()
            showSolvedDialog = true
        },
        onProgressUpdate = { cells, pencilMarks, notEmptyMarks, history, isSolved ->
            progress.cells         = cells
            progress.pencilMarks   = pencilMarks
            progress.notEmptyMarks = notEmptyMarks
            progress.history       = history
            progress.isSolved      = isSolved
        },
        onRegisterReset = onRegisterReset,
        candidateLabel  = { candidate ->
            if (candidate == CELL_EMPTY) EMPTY_CELL_SYMBOL else candidate.toString()
        },
        gridContent = { availableWidth, availableHeight, cells, pencilMarks, notEmptyMarks, selectedCells, onSelectionChange ->
            val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

            // CHAR_RATIO:     width per character in a comma-joined row hint as a fraction of cellSize.
            // COL_HINT_LINE_H: height per stacked hint line as a fraction of cellSize.
            val CHAR_RATIO      = 0.25f
            val COL_HINT_LINE_H = 0.50f

            val maxColHints = gameState.hints.colHints.maxOfOrNull { it.size } ?: 1
            val maxRowHintChars = gameState.hints.rowHints.maxOfOrNull { h ->
                h.joinToString(",").length
            }?.coerceAtLeast(1) ?: 1

            // In diagonal mode a strip is added below the grid for the anti-diagonal hint.
            val antiDiagHintRows = if (gameState.diagonalMode) gameState.hints.antiDiagHints.size.coerceAtLeast(1) else 0
            // Solve for cellSize so that hint strips + grid cells fit exactly in available space.
            val cellSizeByWidth  = availableWidth  / (gridSize.toFloat() + maxRowHintChars * CHAR_RATIO)
            val cellSizeByHeight = availableHeight / (gridSize.toFloat() + (maxColHints + antiDiagHintRows) * COL_HINT_LINE_H)
            val cellSize = minOf(cellSizeByWidth, cellSizeByHeight)

            val colHintLineHeight  = cellSize * COL_HINT_LINE_H
            val colHintStripHeight = colHintLineHeight * maxColHints
            val rowHintStripWidth  = cellSize * (maxRowHintChars * CHAR_RATIO)
            // Font size slightly smaller than the line slot; lineHeight removes Compose's 1.4× expansion.
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
                        val rawIdx = if (startRow in 0 until gridSize && startCol in 0 until gridSize)
                            startRow * gridSize + startCol else null
                        val startIdx = if (rawIdx != null && rawIdx !in prefilledCells) rawIdx else null
                        var selection: Set<Int> = if (startIdx != null) setOf(startIdx) else emptySet()
                        currentOnSelectionChange(selection)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val col = ((change.position.x - rowHintPx) / cellPx).toInt()
                            val row = ((change.position.y - colHintPx) / cellPx).toInt()
                            if (row in 0 until gridSize && col in 0 until gridSize) {
                                val idx = row * gridSize + col
                                if (idx !in selection && idx !in prefilledCells) {
                                    selection = selection + idx
                                    currentOnSelectionChange(selection)
                                }
                            }
                        }
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Column hints row — each hint stacked vertically ───────────
                Row {
                    // Top-left corner: main diagonal (↘) hint, or blank spacer.
                    if (gameState.diagonalMode && gameState.hints.diagHints.isNotEmpty()) {
                        val diagCells = (0 until gridSize).map { i -> cells[i * gridSize + i] }
                        val fulfilledDiag = fulfilledHintIndices(diagCells, gameState.hints.diagHints, gameState.multiplicationMode)
                        Column(
                            modifier            = Modifier.width(rowHintStripWidth).height(colHintStripHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            gameState.hints.diagHints.forEachIndexed { hi, hint ->
                                Box(
                                    modifier         = Modifier.width(rowHintStripWidth).height(colHintLineHeight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = hint.toString(),
                                        fontSize   = hintFontSize,
                                        lineHeight = hintLineHeight,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (hi in fulfilledDiag)
                                                         MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                     else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.width(rowHintStripWidth).height(colHintStripHeight))
                    }
                    for (c in 0 until gridSize) {
                        val ch = gameState.hints.colHints[c]
                        val colCells = (0 until gridSize).map { r -> cells[r * gridSize + c] }
                        val fulfilledCol = fulfilledHintIndices(colCells, ch, gameState.multiplicationMode)
                        // Align hints to the bottom so the last hint sits just above the grid row.
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
                        val fulfilledRow = fulfilledHintIndices(rowCells, rh, gameState.multiplicationMode)
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
                            val idx        = r * gridSize + c
                            val value      = cells[idx]
                            val marks      = pencilMarks[idx]
                            val isDiag     = gameState.diagonalMode && (r == c || r + c == gridSize - 1)
                            val isPrefilled = idx in prefilledCells
                            val baseBg = when {
                                isPrefilled         -> MaterialTheme.colorScheme.secondaryContainer
                                value == CELL_EMPTY -> MaterialTheme.colorScheme.surfaceVariant
                                value != CELL_UNSET -> MaterialTheme.colorScheme.primaryContainer
                                notEmptyMarks[idx]  -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                else                -> MaterialTheme.colorScheme.surface
                            }
                            val bgColor = if (isDiag) lerp(baseBg, MaterialTheme.colorScheme.tertiary, 0.12f) else baseBg
                            PuzzleGridCell(
                                cellSize        = cellSize,
                                isSelected      = idx in selectedCells,
                                backgroundColor = bgColor,
                                onClick         = {},
                                cellPadding     = 0.dp
                            ) {
                                when {
                                    isPrefilled && value == CELL_EMPTY -> Text(
                                        text       = EMPTY_CELL_SYMBOL,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = (cellSize.value * 0.50f).sp,
                                        color      = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    isPrefilled -> Text(
                                        text       = value.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = (cellSize.value * 0.45f).sp,
                                        color      = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
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
                // ── Bottom-left: anti-diagonal (↗) hint — same layout as row hints ──
                if (gameState.diagonalMode) {
                    val antiDiagStripHeight = colHintLineHeight * antiDiagHintRows
                    val antiDiagCells = (0 until gridSize).map { i -> cells[i * gridSize + (gridSize - 1 - i)] }
                    val fulfilledAntiDiag = fulfilledHintIndices(antiDiagCells, gameState.hints.antiDiagHints, gameState.multiplicationMode)
                    Row {
                        Box(
                            Modifier.width(rowHintStripWidth).height(antiDiagStripHeight),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (gameState.hints.antiDiagHints.isNotEmpty()) {
                                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                                val grayColor     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                Text(
                                    text = buildAnnotatedString {
                                        gameState.hints.antiDiagHints.forEachIndexed { hi, hint ->
                                            if (hi > 0) append(",")
                                            withStyle(SpanStyle(color = if (hi in fulfilledAntiDiag) grayColor else tertiaryColor)) {
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
                        Spacer(Modifier.width(cellSize * gridSize).height(antiDiagStripHeight))
                    }
                }
            }
        }
    )
}
