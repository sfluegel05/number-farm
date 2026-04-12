package com.sfluegel.numberfarm

import com.sfluegel.puzzleutils.PersistentHistory
import com.sfluegel.puzzleutils.SaveStore

/** A cell deliberately left blank (valid puzzle move). */
const val CELL_EMPTY = 0

/** Sentinel for cells the player has not yet touched. */
const val CELL_UNSET = -1

/** Grid side length for a given n. */
fun gridSizeFor(n: Int) = if (n <= 5) n + 1 else n + 2

/**
 * Hints for one puzzle.
 *
 * rowHints[r]  = consecutive group aggregates in row r, left-to-right.
 * colHints[c]  = consecutive group aggregates in column c, top-to-bottom.
 * diagHints    = group aggregates along the main diagonal (↘), top-to-bottom.
 *                Empty when not in diagonal mode.
 * antiDiagHints = group aggregates along the anti-diagonal (↗), top-to-bottom.
 *                Empty when not in diagonal mode.
 *
 * A "group" is a maximal run of adjacent non-empty cells.
 * E.g. row [1, 4, EMPTY, 2] → hints = [5, 2] (sum) or [4, 2] (product).
 */
data class GameHints(
    val rowHints: List<List<Int>>,
    val colHints: List<List<Int>>,
    val diagHints: List<Int> = emptyList(),
    val antiDiagHints: List<Int> = emptyList()
)

/**
 * Full state of one puzzle.
 *
 * @param n                  Max number: cells hold values 1..n or CELL_EMPTY.
 * @param gridSize           Side length of the grid (n+1 for n≤5, n+2 for n>5).
 * @param solution           solution[r][c] = CELL_EMPTY or 1..n.
 * @param hints              Row/column group hints shown to the player.
 * @param diagonalMode       Whether the diagonal uniqueness rule is active.
 * @param multiplicationMode Whether hints are products instead of sums.
 * @param prefilledCells     Cells revealed to the player to guarantee a unique solution.
 *                           Maps cell index (r*gridSize+c) to value (CELL_EMPTY or 1..n).
 *                           Pre-filled cells are shown as non-editable from the start.
 */
class GameState(
    val n: Int,
    val gridSize: Int,
    val solution: Array<IntArray>,
    val hints: GameHints,
    val diagonalMode: Boolean = false,
    val multiplicationMode: Boolean = false,
    val prefilledCells: Map<Int, Int> = emptyMap()
)

/** Snapshot of an in-progress game that can be restored later. */
data class SavedGame(
    val n: Int,
    val gameState: GameState,
    val cells: List<Int>,
    val pencilMarks: List<Set<Int>>,
    val notEmptyMarks: List<Boolean>,
    val elapsedSeconds: Long,
    val history: List<Triple<List<Int>, List<Set<Int>>, List<Boolean>>>
)

/** In-memory save slots, one per n value. Tracks the most-recently saved slot. */
object GameSave : SaveStore<Int, SavedGame>({ it.n })

/** One completed solve record. */
data class SolveRecord(
    val timestamp: Long,
    val n: Int,
    val elapsedSeconds: Long,
    val diagonalMode: Boolean = false,
    val multiplicationMode: Boolean = false
)

/** Persistent solve history, backed by SharedPreferences. */
object SolveHistory : PersistentHistory<SolveRecord>("solve_history") {
    override fun serialize(record: SolveRecord) =
        "${record.timestamp},${record.n},${record.elapsedSeconds},${record.diagonalMode},${record.multiplicationMode}"

    override fun deserialize(s: String): SolveRecord? = try {
        val p = s.split(",")
        SolveRecord(
            timestamp          = p[0].toLong(),
            n                  = p[1].toInt(),
            elapsedSeconds     = p[2].toLong(),
            diagonalMode       = p.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            multiplicationMode = p.getOrNull(4)?.toBooleanStrictOrNull() ?: false
        )
    } catch (_: Exception) { null }

    override fun timestampOf(record: SolveRecord) = record.timestamp
}
