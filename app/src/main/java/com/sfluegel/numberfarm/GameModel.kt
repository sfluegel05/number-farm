package com.sfluegel.numberfarm

/** A cell deliberately left blank (valid puzzle move). */
const val CELL_EMPTY = 0

/** Sentinel for cells the player has not yet touched. */
const val CELL_UNSET = -1

/** Grid side length for a given n. */
fun gridSizeFor(n: Int) = if (n <= 5) n + 1 else n + 2

/**
 * Hints for one puzzle.
 *
 * rowHints[r] = consecutive group sums in row r, left-to-right.
 * colHints[c] = consecutive group sums in column c, top-to-bottom.
 *
 * A "group" is a maximal run of adjacent non-empty cells.
 * E.g. row [1, 4, EMPTY, 2] → rowHints = [5, 2].
 */
data class GameHints(
    val rowHints: List<List<Int>>,
    val colHints: List<List<Int>>
)

/**
 * Full state of one puzzle.
 *
 * @param n        Max number: cells hold values 1..n or CELL_EMPTY.
 * @param gridSize Side length of the grid (n+1 for n≤5, n+2 for n>5).
 * @param solution solution[r][c] = CELL_EMPTY or 1..n.
 * @param hints    Row/column group-sum hints shown to the player.
 */
class GameState(
    val n: Int,
    val gridSize: Int,
    val solution: Array<IntArray>,
    val hints: GameHints
)
