package com.sfluegel.numberfarm

import kotlin.random.Random

object PuzzleGenerator {

    /**
     * Generates a puzzle for [n] (numbers 1..n).
     *
     * Algorithm:
     *  1. Randomly fill a gridSize×gridSize grid (each number at most once per row/column).
     *  2. Compute row/column group-sum hints.
     *  3. Run the solver to verify the hints have a unique solution.
     *  4. Retry up to [maxAttempts] times; fall back to a non-unique puzzle if needed.
     */
    fun generateGame(n: Int, maxAttempts: Int = 100, diagonalMode: Boolean = false, multiplicationMode: Boolean = false): GameState {
        val gridSize = gridSizeFor(n)
        val rng = Random.Default
        repeat(maxAttempts) {
            val solution = generateSolution(n, gridSize, rng, diagonalMode)
            val hints    = computeHints(solution, gridSize, multiplicationMode)
            if (PuzzleSolverRowBased.isUnique(n, gridSize, hints, diagonalMode, multiplicationMode)) {
                println("Generated unique puzzle for n=$n in attempt ${it + 1}")
                return GameState(n, gridSize, solution, hints, diagonalMode, multiplicationMode)
            }
            else {
                println("Attempt ${it + 1}: generated puzzle for n=$n but it is not unique; retrying...")
                println("The hints were: rowHints=${hints.rowHints} colHints=${hints.colHints}")
            }
        }
        println("Failed to generate unique puzzle for n=$n after $maxAttempts attempts; returning non-unique puzzle.")
        // Fallback: return a puzzle even if not proven unique.
        val solution = generateSolution(n, gridSize, rng, diagonalMode)
        return GameState(n, gridSize, solution, computeHints(solution, gridSize, multiplicationMode), diagonalMode, multiplicationMode)
    }

    /**
     * Fills a grid randomly.
     * Each number 1..n appears at most once per row and at most once per column.
     * When [diagonalMode] is true, each number also appears at most once per diagonal.
     * Not every cell needs to be filled; density is chosen randomly per row.
     */
    private fun generateSolution(n: Int, gridSize: Int, rng: Random, diagonalMode: Boolean = false): Array<IntArray> {
        val grid         = Array(gridSize) { IntArray(gridSize) { CELL_EMPTY } }
        val colUsed      = Array(gridSize) { BooleanArray(n + 1) }
        val diagUsed     = if (diagonalMode) BooleanArray(n + 1) else null
        val antiDiagUsed = if (diagonalMode) BooleanArray(n + 1) else null

        for (r in 0 until gridSize) {
            val numbers   = (1..n).shuffled(rng)
            val positions = (0 until gridSize).shuffled(rng)
            val rowUsed   = BooleanArray(n + 1)
            // Target: place between n/2 and n-1 numbers in this row.
            val target = n / 2 + rng.nextInt((n + 1) / 2 + 1)
            var placed = 0

            for (num in numbers) {
                if (placed >= target) break
                for (pos in positions) {
                    val onMainDiag = r == pos
                    val onAntiDiag = r + pos == gridSize - 1
                    if (!rowUsed[num] && !colUsed[pos][num] && grid[r][pos] == CELL_EMPTY
                        && !(diagonalMode && onMainDiag && diagUsed!![num])
                        && !(diagonalMode && onAntiDiag && antiDiagUsed!![num])
                    ) {
                        grid[r][pos] = num
                        rowUsed[num]      = true
                        colUsed[pos][num] = true
                        if (diagonalMode && onMainDiag) diagUsed!![num]     = true
                        if (diagonalMode && onAntiDiag) antiDiagUsed!![num] = true
                        placed++
                        break
                    }
                }
            }
        }
        return grid
    }

    /** Computes group-sum (or group-product) hints from a filled solution grid. */
    fun computeHints(grid: Array<IntArray>, gridSize: Int, multiplicationMode: Boolean = false): GameHints {
        val rowHints = List(gridSize) { r -> groupAggregates(List(gridSize) { c -> grid[r][c] }, multiplicationMode) }
        val colHints = List(gridSize) { c -> groupAggregates(List(gridSize) { r -> grid[r][c] }, multiplicationMode) }
        return GameHints(rowHints, colHints)
    }

    private fun groupAggregates(cells: List<Int>, multiplicationMode: Boolean = false): List<Int> {
        val groups      = mutableListOf<Int>()
        var acc         = if (multiplicationMode) 1 else 0
        var groupHasValues = false
        for (v in cells) {
            if (v > 0) {
                acc = if (multiplicationMode) acc * v else acc + v
                groupHasValues = true
            } else {
                if (groupHasValues) { groups.add(acc); acc = if (multiplicationMode) 1 else 0; groupHasValues = false }
            }
        }
        if (groupHasValues) groups.add(acc)
        return groups
    }
}

// main function for testing
fun main() {
    for (i in 1 ..5) {
        val game = PuzzleGenerator.generateGame(9)
        println("Generated game for n=9 with hints: ${game.hints}")
    }

    val res = PuzzleSolverRowBased.isUnique(5, 6, GameHints(
        rowHints = listOf(listOf(1,2), listOf(10), listOf(5,7), listOf(6), listOf(9), listOf(14,1)),
        colHints = listOf(listOf(1,5,2), listOf(3,5), listOf(5,1,4), listOf(1,9), listOf(7,5), listOf(2,4,1))
    ))
    println(res)
}
