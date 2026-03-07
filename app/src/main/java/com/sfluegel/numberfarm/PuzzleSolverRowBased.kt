package com.sfluegel.numberfarm

/**
 * Solver for Number Farm puzzles.
 *
 * Rules:
 *  - Each number 1..n appears at most once per row and at most once per column.
 *  - Row/column hints list the sums of consecutive blank-separated blocks, in order.
 *
 * Approach: row-by-row backtracking.
 *  1. Pre-enumerate all valid row arrangements per row via [getValidRows].
 *  2. Assign rows one by one, pruning with column-uniqueness and partial-column-hint checks.
 *  3. After all rows are placed, verify full column hints.
 */
object PuzzleSolverRowBased {

    /**
     * Returns true if every *completed* group in a partial [row] (may contain CELL_UNSET)
     * matches [hints] in order.
     * Groups containing CELL_UNSET are skipped; validation stops after the first such group.
     * Used for pruning during row enumeration.
     */
    fun hintsFulfilled(hints: List<Int>, row: IntArray): Boolean {
        var hintIdx = 0
        var sum = 0
        var groupHasUnset = false
        var seenIncompleteGroup = false

        for (v in row) {
            when {
                v == CELL_UNSET -> groupHasUnset = true
                v == CELL_EMPTY -> {
                    if (sum > 0 || groupHasUnset) {
                        when {
                            groupHasUnset -> seenIncompleteGroup = true
                            !seenIncompleteGroup -> {
                                if (hintIdx >= hints.size || hints[hintIdx] != sum) return false
                                hintIdx++
                            }
                        }
                    }
                    sum = 0
                    groupHasUnset = false
                }
                else -> sum += v
            }
        }
        if (sum > 0 && !groupHasUnset && !seenIncompleteGroup) {
            if (hintIdx >= hints.size || hints[hintIdx] != sum) return false
        }
        return true
    }

    /**
     * Returns true if the fully-determined [row] (no CELL_UNSET) uses exactly [hints].
     * Returns true early if any CELL_UNSET is present (row not yet complete).
     */
    fun hintsUsed(hints: List<Int>, row: IntArray): Boolean {
        var hintIdx = 0
        var targetSum = if (hints.isNotEmpty()) hints[0] else 0
        var sum = 0
        for (v in row) {
            when {
                v == CELL_UNSET -> return true
                v == CELL_EMPTY -> {
                    if (sum > 0) {
                        if (sum != targetSum) return false
                        hintIdx++
                        targetSum = if (hintIdx < hints.size) hints[hintIdx] else 0
                    }
                    sum = 0
                }
                else -> sum += v
            }
        }
        if (sum > 0) {
            if (sum != targetSum) return false
            hintIdx++
        }
        return hintIdx == hints.size
    }

    /**
     * Returns true if the sum of placed numbers does not exceed the total hint sum.
     * Provides early pruning during row enumeration (CELL_UNSET is ignored).
     */
    fun sumValid(hints: List<Int>, row: IntArray): Boolean {
        val rowSum = row.filter { it > 0 }.sum()
        return rowSum <= hints.sum()
    }

    /**
     * Returns true if a partial column (rows 0..r, each cell is CELL_EMPTY or 1..n,
     * no CELL_UNSET) is consistent with [hints]:
     *  - All completed groups (terminated by CELL_EMPTY) match hints in order.
     *  - The ongoing running group sum does not already exceed the next expected hint.
     */
    fun isColPartialConsistent(hints: List<Int>, partial: IntArray): Boolean {
        var hintIdx = 0
        var sum = 0
        for (v in partial) {
            if (v == CELL_EMPTY) {
                if (sum > 0) {
                    if (hintIdx >= hints.size || hints[hintIdx] != sum) return false
                    hintIdx++
                }
                sum = 0
            } else {
                sum += v
                if (hintIdx >= hints.size || sum > hints[hintIdx]) return false
            }
        }
        return true
    }

    /**
     * Enumerates all valid row arrangements of length [gridSize] using numbers from 1..[n].
     * Each arrangement satisfies [rowHints] and has no repeated numbers.
     */
    fun getValidRows(n: Int, gridSize: Int, hints: List<Int>): List<IntArray> {
        val results = mutableListOf<IntArray>()
        val current = IntArray(gridSize) { CELL_UNSET }

        fun isPartialOk(row: IntArray): Boolean =
            hintsFulfilled(hints, row) && hintsUsed(hints, row) && sumValid(hints, row)

        fun backtrack(pos: Int, availableNumbers: List<Int>) {
            if (pos == gridSize) {
                if (isPartialOk(current)) results.add(current.copyOf())
                return
            }
            for (num in listOf(CELL_EMPTY) + availableNumbers) {
                current[pos] = num
                if (isPartialOk(current)) {
                    backtrack(pos + 1, if (num == CELL_EMPTY) availableNumbers else availableNumbers - num)
                }
            }
            current[pos] = CELL_UNSET
        }

        backtrack(0, (1..n).toList())
        return results
    }

    /** True iff [hints] have exactly one solution (stops after finding 2). */
    fun isUnique(n: Int, gridSize: Int, hints: GameHints): Boolean {
        val sols = countSolutions(n, gridSize, hints, maxCount = 2)
        if (sols == 0) println("No solutions found for hints: $hints")
        else if (sols > 1) println("Multiple solutions found for hints: $hints")
        return sols == 1
    }

    /** Counts the number of distinct solutions, up to [maxCount]. */
    fun countSolutions(
        n: Int,
        gridSize: Int,
        hints: GameHints,
        maxCount: Int = Int.MAX_VALUE
    ): Int {
        // Pre-enumerate valid row configurations for each row.
        val rowConfigs = List(gridSize) { r -> getValidRows(n, gridSize, hints.rowHints[r]) }

        val grid = Array(gridSize) { IntArray(gridSize) { CELL_EMPTY } }
        // colUsed[c][v] = true if value v has been placed in column c already.
        val colUsed = Array(gridSize) { BooleanArray(n + 1) }

        var count = 0

        // Returns true  → keep searching (count < maxCount).
        // Returns false → stop searching (count reached maxCount).
        fun backtrack(row: Int): Boolean {
            if (row == gridSize) {
                // All rows placed — verify full column hints.
                for (c in 0 until gridSize) {
                    val col = IntArray(gridSize) { grid[it][c] }
                    if (!hintsUsed(hints.colHints[c], col)) return true
                }
                count++
                return count < maxCount
            }

            outer@ for (config in rowConfigs[row]) {
                // Reject configurations that reuse a value already present in some column.
                for (c in 0 until gridSize) {
                    if (config[c] != CELL_EMPTY && colUsed[c][config[c]]) continue@outer
                }

                // Place the row.
                for (c in 0 until gridSize) {
                    grid[row][c] = config[c]
                    if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = true
                }

                // Prune: each partial column must still be hint-consistent.
                var colsOk = true
                for (c in 0 until gridSize) {
                    val partial = IntArray(row + 1) { grid[it][c] }
                    if (!isColPartialConsistent(hints.colHints[c], partial)) {
                        colsOk = false
                        break
                    }
                }

                if (colsOk && !backtrack(row + 1)) {
                    for (c in 0 until gridSize) {
                        if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = false
                        grid[row][c] = CELL_EMPTY
                    }
                    return false
                }

                // Unplace the row.
                for (c in 0 until gridSize) {
                    if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = false
                    grid[row][c] = CELL_EMPTY
                }
            }
            return true
        }

        backtrack(0)
        return count
    }
}
