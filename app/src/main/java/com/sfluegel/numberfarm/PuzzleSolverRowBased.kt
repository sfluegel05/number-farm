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
     * When [multiplicationMode] is true, groups are compared by product instead of sum.
     */
    fun hintsFulfilled(hints: List<Int>, row: IntArray, multiplicationMode: Boolean = false): Boolean {
        var hintIdx = 0
        var acc = if (multiplicationMode) 1 else 0
        var groupHasValues = false
        var groupHasUnset = false
        var seenIncompleteGroup = false

        for (v in row) {
            when {
                v == CELL_UNSET -> groupHasUnset = true
                v == CELL_EMPTY -> {
                    if (groupHasValues || groupHasUnset) {
                        when {
                            groupHasUnset -> seenIncompleteGroup = true
                            !seenIncompleteGroup -> {
                                if (hintIdx >= hints.size || hints[hintIdx] != acc) return false
                                hintIdx++
                            }
                        }
                    }
                    acc = if (multiplicationMode) 1 else 0
                    groupHasValues = false
                    groupHasUnset = false
                }
                else -> {
                    acc = if (multiplicationMode) acc * v else acc + v
                    groupHasValues = true
                }
            }
        }
        if (groupHasValues && !groupHasUnset && !seenIncompleteGroup) {
            if (hintIdx >= hints.size || hints[hintIdx] != acc) return false
        }
        return true
    }

    /**
     * Returns true if the fully-determined [row] (no CELL_UNSET) uses exactly [hints].
     * Returns true early if any CELL_UNSET is present (row not yet complete).
     * When [multiplicationMode] is true, groups are compared by product instead of sum.
     */
    fun hintsUsed(hints: List<Int>, row: IntArray, multiplicationMode: Boolean = false): Boolean {
        var hintIdx = 0
        var targetHint = if (hints.isNotEmpty()) hints[0] else (if (multiplicationMode) 1 else 0)
        var acc = if (multiplicationMode) 1 else 0
        var groupHasValues = false
        for (v in row) {
            when {
                v == CELL_UNSET -> return true
                v == CELL_EMPTY -> {
                    if (groupHasValues) {
                        if (acc != targetHint) return false
                        hintIdx++
                        targetHint = if (hintIdx < hints.size) hints[hintIdx] else (if (multiplicationMode) 1 else 0)
                    }
                    acc = if (multiplicationMode) 1 else 0
                    groupHasValues = false
                }
                else -> {
                    acc = if (multiplicationMode) acc * v else acc + v
                    groupHasValues = true
                }
            }
        }
        if (groupHasValues) {
            if (acc != targetHint) return false
            hintIdx++
        }
        return hintIdx == hints.size
    }

    /**
     * Returns true if the aggregate of placed numbers does not exceed the total hint aggregate.
     * Provides early pruning during row enumeration (CELL_UNSET and CELL_EMPTY are ignored).
     * When [multiplicationMode] is true, compares products instead of sums.
     */
    fun aggregateValid(hints: List<Int>, row: IntArray, multiplicationMode: Boolean = false): Boolean {
        return if (multiplicationMode) {
            val rowProduct   = row.filter { it > 0 }.fold(1) { a, v -> a * v }
            val hintProduct  = hints.fold(1) { a, h -> a * h }
            rowProduct <= hintProduct
        } else {
            row.filter { it > 0 }.sum() <= hints.sum()
        }
    }

    /**
     * Returns true if a partial column (rows 0..r, each cell is CELL_EMPTY or 1..n,
     * no CELL_UNSET) is consistent with [hints]:
     *  - All completed groups (terminated by CELL_EMPTY) match hints in order.
     *  - The ongoing running group aggregate does not already exceed the next expected hint.
     * When [multiplicationMode] is true, groups are compared by product instead of sum.
     */
    fun isColPartialConsistent(hints: List<Int>, partial: IntArray, multiplicationMode: Boolean = false): Boolean {
        var hintIdx = 0
        var acc = if (multiplicationMode) 1 else 0
        var groupHasValues = false
        for (v in partial) {
            if (v == CELL_EMPTY) {
                if (groupHasValues) {
                    if (hintIdx >= hints.size || hints[hintIdx] != acc) return false
                    hintIdx++
                }
                acc = if (multiplicationMode) 1 else 0
                groupHasValues = false
            } else {
                acc = if (multiplicationMode) acc * v else acc + v
                groupHasValues = true
                if (hintIdx >= hints.size || acc > hints[hintIdx]) return false
            }
        }
        return true
    }

    /**
     * Enumerates all valid row arrangements of length [gridSize] using numbers from 1..[n].
     * Each arrangement satisfies [rowHints] and has no repeated numbers.
     *
     * @param allowed Optional per-position value filters. When provided, only values present in
     *                [allowed][pos] are tried at each position. Pass null to allow all values.
     */
    fun getValidRows(n: Int, gridSize: Int, hints: List<Int>, allowed: Array<Set<Int>>? = null, multiplicationMode: Boolean = false): List<IntArray> {
        val results = mutableListOf<IntArray>()
        val current = IntArray(gridSize) { CELL_UNSET }

        fun isPartialOk(row: IntArray): Boolean =
            hintsFulfilled(hints, row, multiplicationMode) && hintsUsed(hints, row, multiplicationMode) && aggregateValid(hints, row, multiplicationMode)

        fun backtrack(pos: Int, availableNumbers: List<Int>) {
            if (pos == gridSize) {
                if (isPartialOk(current)) results.add(current.copyOf())
                return
            }
            val candidates = listOf(CELL_EMPTY) + availableNumbers
            for (num in if (allowed != null) candidates.filter { it in allowed[pos] } else candidates) {
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
    fun isUnique(n: Int, gridSize: Int, hints: GameHints, diagonalMode: Boolean = false, multiplicationMode: Boolean = false): Boolean {
        val sols = countSolutions(n, gridSize, hints, maxCount = 2, useAC3 = false, diagonalMode = diagonalMode, multiplicationMode = multiplicationMode)
        if (sols == 0) println("No solutions found for hints: $hints")
        else if (sols > 1) println("Multiple solutions found for hints: $hints")
        return sols == 1
    }

    /** Counts the number of distinct solutions, up to [maxCount]. */
    fun countSolutions(
        n: Int,
        gridSize: Int,
        hints: GameHints,
        maxCount: Int = Int.MAX_VALUE,
        useAC3: Boolean = true,
        diagonalMode: Boolean = false,
        multiplicationMode: Boolean = false
    ): Int {
        // possible[r][c] = values that can appear at cell (r,c) given configs generated so far.
        // Starts as the full domain {CELL_EMPTY, 1..n}; narrowed after each row/column is generated.
        val possible = Array(gridSize) { Array(gridSize) { (0..n).toMutableSet() } }

        val rowConfigs = MutableList<List<IntArray>>(gridSize) { emptyList() }
        val colConfigs = MutableList<List<IntArray>>(gridSize) { emptyList() }

        // Start with row 0, then col 0, then row 1, then col 1, etc., always picking the next item with the smallest aggregate hint.
        fun List<Int>.totalAggregate() = if (multiplicationMode) fold(1) { a, h -> a * h } else sum()
        val items = ((0 until gridSize).map { true to it } + (0 until gridSize).map { false to it })
            .sortedBy { (isRow, idx) -> if (isRow) hints.rowHints[idx].totalAggregate() else hints.colHints[idx].totalAggregate() }

        for ((isRow, idx) in items) {
            if (isRow) {
                val allowed = Array(gridSize) { c -> possible[idx][c].toSet() }
                rowConfigs[idx] = getValidRows(n, gridSize, hints.rowHints[idx], allowed, multiplicationMode)
                // Narrow possible[idx][c] to only values that actually appear in any config.
                for (c in 0 until gridSize) {
                    possible[idx][c].retainAll(rowConfigs[idx].mapTo(mutableSetOf()) { it[c] })
                }
            } else {
                val allowed = Array(gridSize) { r -> possible[r][idx].toSet() }
                colConfigs[idx] = getValidRows(n, gridSize, hints.colHints[idx], allowed, multiplicationMode)
                // Narrow possible[r][idx] to only values that actually appear in any config.
                for (r in 0 until gridSize) {
                    possible[r][idx].retainAll(colConfigs[idx].mapTo(mutableSetOf()) { it[r] })
                }
            }
        }
        println("Row/col configs after interleaved generation: rows=${rowConfigs.sumOf { it.size }} cols=${colConfigs.sumOf { it.size }}")

        fun arcReduce(left: Int, right: Int): Boolean {
            var changed = false
            var remainingConfigLeft = rowConfigs[left].toMutableList()
            for (configLeft in rowConfigs[left]) {
                var matchesAny = false
                for (configRight in rowConfigs[right]) {
                    var matchesAllColumns = true
                    for (c in 0 until gridSize) {
                        var matchesThisColumn = false
                        for (colConfig in colConfigs[c]) {
                            if (configLeft[c] == colConfig[left] && configRight[c] == colConfig[right]) {
                                matchesThisColumn = true
                                break
                            }
                        }
                        if (!matchesThisColumn) {
                            matchesAllColumns = false
                            break
                        }
                    }
                    if (matchesAllColumns) {
                        matchesAny = true
                        break
                    }
                }
                if (!matchesAny) {
                    remainingConfigLeft.remove(configLeft)
                    changed = true
                }
            }
            rowConfigs[left] = remainingConfigLeft
            return changed
        }
        /**
         * For each cell (r, c), the value it holds must be reachable from both its row
         * configuration (rowConfigs[r][*][c]) and its column configuration (colConfigs[c][*][r]).
         * The intersection of those two value-sets is the only set a cell can ever take.
         * Prune any row/column config that assigns a value outside that intersection.
         * Repeat until no more configs are removed (fixed point).
         */
        fun cellPrune() {
            var changed = true
            while (changed) {
                changed = false
                // possible[r][c] = values that appear in at least one rowConfig for row r at
                // position c, AND in at least one colConfig for column c at position r.
                val possible = Array(gridSize) { r ->
                    Array(gridSize) { c ->
                        val fromRow = rowConfigs[r].mapTo(mutableSetOf()) { it[c] }
                        val fromCol = colConfigs[c].mapTo(mutableSetOf()) { it[r] }
                        fromRow.retainAll(fromCol)
                        fromRow  // now the intersection
                    }
                }
                for (r in 0 until gridSize) {
                    val pruned = rowConfigs[r].filter { config ->
                        (0 until gridSize).all { c -> config[c] in possible[r][c] }
                    }
                    if (pruned.size < rowConfigs[r].size) { rowConfigs[r] = pruned; changed = true }
                }
                for (c in 0 until gridSize) {
                    val pruned = colConfigs[c].filter { config ->
                        (0 until gridSize).all { r -> config[r] in possible[r][c] }
                    }
                    if (pruned.size < colConfigs[c].size) { colConfigs[c] = pruned; changed = true }
                }
            }
        }
        cellPrune()



        fun ac3Prune() {
            // start with all row-row pairs
            var agenda = ArrayDeque<Pair<Int, Int>>()
            for (row in 0 until gridSize) {
                for (row2 in 0 until gridSize) {
                    if (row != row2) {
                        agenda.add(row to row2)
                        agenda.add(row2 to row)
                    }
                }
            }
            while (agenda.isNotEmpty()) {
                val (left, right) = agenda.removeFirst()
                if (arcReduce(left, right)) {
                    // if left was reduced, add all pairs (other, left) back to the agenda
                    for (other in 0 until gridSize) {
                        if (other != left && other != right) {
                            agenda.add(other to left)
                        }
                    }
                }
            }

        }
        if (useAC3) {
            ac3Prune()
            println("Number of row configs after AC-3: ${rowConfigs.sumOf { it.size }}")
        }

        val grid = Array(gridSize) { IntArray(gridSize) { CELL_EMPTY } }
        // colUsed[c][v] = true if value v has been placed in column c already.
        val colUsed      = Array(gridSize) { BooleanArray(n + 1) }
        // diagUsed[v] / antiDiagUsed[v] = true if value v has been placed on that diagonal.
        val diagUsed     = if (diagonalMode) BooleanArray(n + 1) else null
        val antiDiagUsed = if (diagonalMode) BooleanArray(n + 1) else null

        var count = 0

        // Returns true  → keep searching (count < maxCount).
        // Returns false → stop searching (count reached maxCount).
        fun backtrack(row: Int): Boolean {
            if (row == gridSize) {
                // All rows placed — verify full column hints.
                for (c in 0 until gridSize) {
                    val col = IntArray(gridSize) { grid[it][c] }
                    if (!hintsUsed(hints.colHints[c], col, multiplicationMode)) return true
                }
                count++
                return count < maxCount
            }

            outer@ for (config in rowConfigs[row]) {
                // Reject configurations that reuse a value already present in some column.
                for (c in 0 until gridSize) {
                    if (config[c] != CELL_EMPTY && colUsed[c][config[c]]) continue@outer
                }

                // Reject configurations that violate diagonal uniqueness.
                if (diagonalMode) {
                    val mainVal = config[row]
                    if (mainVal != CELL_EMPTY && diagUsed!![mainVal]) continue@outer
                    val antiCol = gridSize - 1 - row
                    val antiVal = config[antiCol]
                    if (antiVal != CELL_EMPTY && antiDiagUsed!![antiVal]) continue@outer
                }

                // Place the row.
                for (c in 0 until gridSize) {
                    grid[row][c] = config[c]
                    if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = true
                }
                if (diagonalMode) {
                    val mainVal = config[row]
                    if (mainVal != CELL_EMPTY) diagUsed!![mainVal] = true
                    val antiCol = gridSize - 1 - row
                    val antiVal = config[antiCol]
                    if (antiVal != CELL_EMPTY) antiDiagUsed!![antiVal] = true
                }

                // Prune: each partial column must still be hint-consistent.
                var colsOk = true
                for (c in 0 until gridSize) {
                    val partial = IntArray(row + 1) { grid[it][c] }
                    if (!isColPartialConsistent(hints.colHints[c], partial, multiplicationMode)) {
                        colsOk = false
                        break
                    }
                }

                if (colsOk && !backtrack(row + 1)) {
                    for (c in 0 until gridSize) {
                        if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = false
                        grid[row][c] = CELL_EMPTY
                    }
                    if (diagonalMode) {
                        val mainVal = config[row]
                        if (mainVal != CELL_EMPTY) diagUsed!![mainVal] = false
                        val antiCol = gridSize - 1 - row
                        val antiVal = config[antiCol]
                        if (antiVal != CELL_EMPTY) antiDiagUsed!![antiVal] = false
                    }
                    return false
                }

                // Unplace the row.
                for (c in 0 until gridSize) {
                    if (config[c] != CELL_EMPTY) colUsed[c][config[c]] = false
                    grid[row][c] = CELL_EMPTY
                }
                if (diagonalMode) {
                    val mainVal = config[row]
                    if (mainVal != CELL_EMPTY) diagUsed!![mainVal] = false
                    val antiCol = gridSize - 1 - row
                    val antiVal = config[antiCol]
                    if (antiVal != CELL_EMPTY) antiDiagUsed!![antiVal] = false
                }
            }
            return true
        }

        backtrack(0)
        return count
    }
}
