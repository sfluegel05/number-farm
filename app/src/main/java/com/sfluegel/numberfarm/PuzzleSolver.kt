package com.sfluegel.numberfarm

/**
 * Solver for Number Farm puzzles.
 *
 * Approach: row-by-row backtracking.
 *  1. Pre-enumerate all valid row arrangements that satisfy the row hints.
 *  2. Apply them one by one, pruning via column constraints at each step.
 *  3. After all rows are placed, verify column hints are fully satisfied.
 */
object PuzzleSolver {

    fun hintsFulfilled(hints: List<Int>, row: IntArray): Boolean {
        var sum = 0 // get sum of group
        var groupIdx = 0 // get index of group in hints
        for (v in row) {
            if (v == CELL_EMPTY) {
                // group finished
                if (sum > 0) {
                    // if any of hints[groupIdx:] matches the group sum, hints are fulfilled
                    if (!(groupIdx < hints.size && hints.subList(groupIdx, hints.size)
                            .contains(sum))
                    ) {
                        return false
                    }
                    groupIdx += 1 + hints.subList(groupIdx + 1, hints.size)
                        .indexOf(sum) // move to the next hint that matches the group sum
                }
                sum = 0
            } else if (v == CELL_UNSET) {
                sum = -1
            } else {
                if (sum >= 0) sum += v
            }
        }
        if (sum > 0) {
            // if any of hints[groupIdx+1:] matches the group sum, hints are fulfilled
            if (!(groupIdx < hints.size && hints.subList(groupIdx, hints.size)
                    .contains(sum))
            ) {
                return false
            }
        }
        return true
    }

    fun hintsUsed(hints: List<Int>, row: IntArray): Boolean {
        var hintIdx = 0
        var targetSum = 0
        if (hintIdx < hints.size) {
            targetSum = hints[hintIdx]
        }
        var sum = 0
        for (v in row) {
            if (v == CELL_UNSET) {
                return true
            }
            else if (v == CELL_EMPTY) {
                if (sum > 0) {
                    if (sum != targetSum) {
                        return false
                    }
                    hintIdx++
                    if (hintIdx < hints.size) {
                        targetSum = hints[hintIdx]
                    } else {
                        targetSum = 0
                    }
                }
                sum = 0
            }
            else {
                sum += v
            }
        }
        if (sum > 0) {
            if (sum != targetSum) {
                return false
            }
            hintIdx++
        }
        return hintIdx == hints.size
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



        fun canPlace(row: Int, col: Int, value: Int): Boolean {
            // numbers must be unique in their row and column
            if (value != CELL_EMPTY) {
                for (r in 0 until gridSize) if (grid[r][col] == value) return false
                for (c in 0 until gridSize) if (grid[row][c] == value) return false
            }
            // check if value complies with hints
            // check if all hints are used (for fully filled rows/columns)
            for (r in 0 until gridSize) {
                if (!hintsFulfilled(hints.rowHints[r], grid[r])) return false
                if (!hintsUsed(hints.rowHints[r], grid[r])) return false
            }
            for (c in 0 until gridSize) {
                val colValues = IntArray(gridSize) { grid[it][c] }
                if (!hintsFulfilled(hints.colHints[c], colValues)) return false
                if (!hintsUsed(hints.colHints[c], colValues)) return false
            }

            return true
        }

        var remainingValues = Array(gridSize * gridSize) { (0..n).toMutableSet() }
        // remove values that cannot be placed in a cell due to hints
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val cellValues = remainingValues[r * gridSize + c]
                for (v in cellValues.toList()) {
                    if (!canPlace(r, c, v)) cellValues.remove(v)
                }
            }
        }

        fun nextCell(): Pair<Int, Int> {
            // Select the next cell to fill using MRV heuristic: the one with the fewest remaining options.
            var minOptions = Int.MAX_VALUE
            var next: Pair<Int, Int> = Pair(0, 0)
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
            // After placing a value, remove it from the remaining options of cells in the same row and column.
            for (c in 0 until gridSize) {
                for (v in remainingValues[row * gridSize + c].toList()) {
                    if (!canPlace(row, c, v)) remainingValues[row * gridSize + c].remove(v)
                }
            }
            for (r in 0 until gridSize) {
                for (v in remainingValues[r * gridSize + col].toList()) {
                    if (!canPlace(r, col, v)) remainingValues[r * gridSize + col].remove(v)
                }
            }
        }

        var count = 0
        fun backtrack(pos: Pair<Int, Int>, nFilled: Int): Boolean {
            val row = pos.first
            val column = pos.second
            if (nFilled == gridSize * gridSize) {
                println("Found solution ${count + 1}")
                println(grid.joinToString("\n") { it.joinToString(" ") })
                count++
                return count < maxCount
            }
            for (value in remainingValues[row * gridSize + column].toList()) {
                grid[row][column] = value
                val remainingValuesCopy = remainingValues.map { it.toMutableSet() }
                    .toTypedArray() // deep copy for backtracking
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

        val first = nextCell()
        backtrack(first, 0)
        return count
    }
}