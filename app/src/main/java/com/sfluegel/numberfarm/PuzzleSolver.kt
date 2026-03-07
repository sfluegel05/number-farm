package com.sfluegel.numberfarm

/**
 * Solver for Number Farm puzzles.
 *
 * Rules:
 *  - Each number 1..n appears at most once per row and at most once per column.
 *  - Row/column hints list the sums of consecutive blank-separated blocks, in order.
 *
 * Approach: backtracking with forward checking.
 *  1. Pick the cell with the fewest remaining options (MRV).
 *  2. Try each candidate value; prune via canPlace after each placement.
 *  3. After all cells are filled, the grid is a valid solution.
 */
object PuzzleSolver {

    /**
     * Returns true if every *completed* group in [row] matches [hints] in order.
     *
     * A group is "complete" if it contains no CELL_UNSET cells.
     * Once an incomplete group is encountered, no further groups are validated
     * (we can't determine their hint index without resolving the unknowns).
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
                        // A group ended here.
                        when {
                            groupHasUnset -> seenIncompleteGroup = true
                            !seenIncompleteGroup -> {
                                // Complete group before any incomplete group — validate in order.
                                if (hintIdx >= hints.size || hints[hintIdx] != sum) return false
                                hintIdx++
                            }
                            // else: complete group after an incomplete group — can't validate position.
                        }
                    }
                    sum = 0
                    groupHasUnset = false
                }
                else -> sum += v
            }
        }

        // Validate the last group only if it is complete and no incomplete group preceded it.
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

    fun sumValid(hints: List<Int>, row: IntArray): Boolean {
        // check if sum of hints is realistic for the row
        val hintSum = hints.sum()
        val rowSum = row.filter { it > 0 }.sum()
        return rowSum <= hintSum
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
        val grid = Array(gridSize) { IntArray(gridSize) { CELL_UNSET } }

        /**
         * Returns true if placing [value] at [row],[col] is consistent with:
         *  1. Uniqueness: [value] does not already appear in the same row or column.
         *  2. Row hint: the affected row is hint-consistent after the placement.
         *  3. Col hint: the affected column is hint-consistent after the placement.
         */
        fun canPlace(row: Int, col: Int, value: Int): Boolean {
            if (value != CELL_EMPTY) {
                for (r in 0 until gridSize) if (grid[r][col] == value) return false
                for (c in 0 until gridSize) if (grid[row][c] == value) return false
            }
            // Temporarily place the value to check hint consistency.
            val old = grid[row][col]
            grid[row][col] = value
            val rowOk = hintsFulfilled(hints.rowHints[row], grid[row]) &&
                        hintsUsed(hints.rowHints[row], grid[row]) && sumValid(hints.rowHints[row], grid[row])
            val colValues = IntArray(gridSize) { grid[it][col] }
            val colOk = hintsFulfilled(hints.colHints[col], colValues) &&
                        hintsUsed(hints.colHints[col], colValues) && sumValid(hints.colHints[col], colValues)
            grid[row][col] = old
            return rowOk && colOk
        }

        var remainingValues = Array(gridSize * gridSize) { (0..n).toMutableSet() }

        // Initial pruning pass.
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val cellValues = remainingValues[r * gridSize + c]
                for (v in cellValues.toList()) {
                    if (!canPlace(r, c, v)) cellValues.remove(v)
                }
            }
        }

        fun nextCell(): Pair<Int, Int> {
            // MRV heuristic: pick the unset cell with the fewest remaining options.
            var minOptions = Int.MAX_VALUE
            var next = Pair(0, 0)
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (grid[r][c] == CELL_UNSET) {
                        val options = remainingValues[r * gridSize + c].size
                        if (options < minOptions) {
                            minOptions = options
                            next = Pair(r, c)
                        }
                    }
                }
            }
            return next
        }

        fun forwardCheck(row: Int, col: Int) {
            // Re-prune candidates in the same row and column after placing a value.
            for (c in 0 until gridSize) {
                if (grid[row][c] == CELL_UNSET) {
                    for (v in remainingValues[row * gridSize + c].toList()) {
                        if (!canPlace(row, c, v)) remainingValues[row * gridSize + c].remove(v)
                    }
                }
            }
            for (r in 0 until gridSize) {
                if (grid[r][col] == CELL_UNSET) {
                    for (v in remainingValues[r * gridSize + col].toList()) {
                        if (!canPlace(r, col, v)) remainingValues[r * gridSize + col].remove(v)
                    }
                }
            }
        }

        var count = 0

        // Returns true  → keep searching (haven't hit maxCount yet).
        // Returns false → stop searching (hit maxCount, or caller should stop).
        fun backtrack(pos: Pair<Int, Int>, nFilled: Int): Boolean {
            if (nFilled == gridSize * gridSize) {
                count++
                return count < maxCount
            }
            val (row, column) = pos
            for (value in remainingValues[row * gridSize + column].toList()) {
                grid[row][column] = value
                val remainingValuesCopy = remainingValues.map { it.toMutableSet() }.toTypedArray()
                forwardCheck(row, column)
                val next = nextCell()
                if (!backtrack(next, nFilled + 1)) {
                    grid[row][column] = CELL_UNSET
                    remainingValues = remainingValuesCopy
                    return false
                }
                grid[row][column] = CELL_UNSET
                remainingValues = remainingValuesCopy
            }
            return true
        }

        backtrack(nextCell(), 0)
        return count
    }
}
