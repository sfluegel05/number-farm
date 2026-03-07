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
    fun generateGame(n: Int, maxAttempts: Int = 10): GameState {
        val gridSize = gridSizeFor(n)
        val rng = Random.Default
        repeat(maxAttempts) {
            val solution = generateSolution(n, gridSize, rng)
            val hints    = computeHints(solution, gridSize)
            if (PuzzleSolver.isUnique(n, gridSize, hints)) {
                println("Generated unique puzzle for n=$n in attempt ${it + 1}")
                return GameState(n, gridSize, solution, hints)
            }
            else {
                println("Attempt ${it + 1}: generated puzzle for n=$n but it is not unique; retrying...")
                println("The hints were: rowHints=${hints.rowHints} colHints=${hints.colHints}")
            }
        }
        println("Failed to generate unique puzzle for n=$n after $maxAttempts attempts; returning non-unique puzzle.")
        // Fallback: return a puzzle even if not proven unique.
        val solution = generateSolution(n, gridSize, rng)
        return GameState(n, gridSize, solution, computeHints(solution, gridSize))
    }

    /**
     * Fills a grid randomly.
     * Each number 1..n appears at most once per row and at most once per column.
     * Not every cell needs to be filled; density is chosen randomly per row.
     */
    private fun generateSolution(n: Int, gridSize: Int, rng: Random): Array<IntArray> {
        val grid    = Array(gridSize) { IntArray(gridSize) { CELL_EMPTY } }
        val colUsed = Array(gridSize) { BooleanArray(n + 1) }

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
                    if (!rowUsed[num] && !colUsed[pos][num] && grid[r][pos] == CELL_EMPTY) {
                        grid[r][pos] = num
                        rowUsed[num]     = true
                        colUsed[pos][num] = true
                        placed++
                        break
                    }
                }
            }
        }
        return grid
    }

    /** Computes group-sum hints from a filled solution grid. */
    fun computeHints(grid: Array<IntArray>, gridSize: Int): GameHints {
        val rowHints = List(gridSize) { r -> groupSums(List(gridSize) { c -> grid[r][c] }) }
        val colHints = List(gridSize) { c -> groupSums(List(gridSize) { r -> grid[r][c] }) }
        return GameHints(rowHints, colHints)
    }

    private fun groupSums(cells: List<Int>): List<Int> {
        val groups = mutableListOf<Int>()
        var sum    = 0
        for (v in cells) {
            if (v > 0) {
                sum += v
            } else {
                if (sum > 0) { groups.add(sum); sum = 0 }
            }
        }
        if (sum > 0) groups.add(sum)
        return groups
    }
}

// main function for testing
fun main() {
    val hints = listOf<Int>()
    val row: IntArray = intArrayOf(1, 0, 0)
    val r = PuzzleSolver.hintsFulfilled(hints, row)
    print("Fulfilled: $r")
    for (n in 1..3) {
        val game = PuzzleGenerator.generateGame(n)
        println("Generated puzzle for n=$n with gridSize=${game.gridSize}")
        println("Hints: rowHints=${game.hints.rowHints} colHints=${game.hints.colHints}")
        println("Solution:\n${game.solution.joinToString("\n") { it.joinToString(" ") }}\n")
    }
}