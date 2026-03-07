package com.sfluegel.numberfarm

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PuzzleSolverRowBased.
 *
 * Covers:
 *  - hintsFulfilled  – partial-row/column hint consistency (used during row enumeration)
 *  - hintsUsed       – complete-row/column hint verification
 *  - sumValid        – running-sum upper-bound pruning
 *  - isColPartialConsistent – partial column consistency during row-by-row backtracking
 *  - getValidRows    – row configuration enumeration
 *  - countSolutions / isUnique – end-to-end solver correctness
 */
class PuzzleSolverTest {

    // -------------------------------------------------------------------------
    // hintsFulfilled – partial rows (may contain CELL_UNSET)
    // -------------------------------------------------------------------------

    @Test
    fun `hintsFulfilled - empty row empty hints`() {
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(emptyList(), intArrayOf(0, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - row with number but no hints`() {
        assertFalse(PuzzleSolverRowBased.hintsFulfilled(emptyList(), intArrayOf(1, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - single complete group matches hint`() {
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(listOf(3), intArrayOf(1, 2, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - single complete group does not match hint`() {
        assertFalse(PuzzleSolverRowBased.hintsFulfilled(listOf(3), intArrayOf(1, 0, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - hints checked in order first hint must match first group`() {
        // First group sums to 1 but hints[0] = 2 → invalid.
        assertFalse(PuzzleSolverRowBased.hintsFulfilled(listOf(2, 1), intArrayOf(1, 0, 2, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - two complete groups both match in order`() {
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(listOf(1, 2), intArrayOf(1, 0, 2, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - two complete groups second does not match`() {
        assertFalse(PuzzleSolverRowBased.hintsFulfilled(listOf(1, 2), intArrayOf(1, 0, 3, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - incomplete group with CELL_UNSET is not validated`() {
        // CELL_UNSET in first position → can't evaluate group, must return true.
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(listOf(3), intArrayOf(CELL_UNSET, 0, 0, 0)))
    }

    @Test
    fun `hintsFulfilled - first group correct second incomplete`() {
        // [1,2]=3 matches hints[0]=3; second group has CELL_UNSET → skip.
        assertTrue(
            PuzzleSolverRowBased.hintsFulfilled(
                listOf(3, 1),
                intArrayOf(1, 2, 0, CELL_UNSET, 0)
            )
        )
    }

    @Test
    fun `hintsFulfilled - complete row all groups match`() {
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(listOf(3, 3), intArrayOf(1, 2, 0, 3)))
    }

    @Test
    fun `hintsFulfilled - complete row second group mismatch`() {
        assertFalse(PuzzleSolverRowBased.hintsFulfilled(listOf(3, 3), intArrayOf(1, 2, 0, 2)))
    }

    // -------------------------------------------------------------------------
    // hintsUsed – complete rows (no CELL_UNSET)
    // -------------------------------------------------------------------------

    @Test
    fun `hintsUsed - empty row empty hints`() {
        assertTrue(PuzzleSolverRowBased.hintsUsed(emptyList(), intArrayOf(0, 0, 0)))
    }

    @Test
    fun `hintsUsed - row with CELL_UNSET returns true early`() {
        assertTrue(PuzzleSolverRowBased.hintsUsed(listOf(3), intArrayOf(1, CELL_UNSET, 0)))
    }

    @Test
    fun `hintsUsed - correct single group`() {
        assertTrue(PuzzleSolverRowBased.hintsUsed(listOf(3), intArrayOf(1, 2, 0, 0)))
    }

    @Test
    fun `hintsUsed - correct two groups`() {
        assertTrue(PuzzleSolverRowBased.hintsUsed(listOf(1, 2), intArrayOf(1, 0, 2, 0)))
    }

    @Test
    fun `hintsUsed - too many groups`() {
        assertFalse(PuzzleSolverRowBased.hintsUsed(listOf(1), intArrayOf(1, 0, 2, 0)))
    }

    @Test
    fun `hintsUsed - too few groups`() {
        assertFalse(PuzzleSolverRowBased.hintsUsed(listOf(1, 2), intArrayOf(1, 0, 0, 0)))
    }

    @Test
    fun `hintsUsed - group sum mismatch`() {
        assertFalse(PuzzleSolverRowBased.hintsUsed(listOf(2), intArrayOf(1, 0, 0, 0)))
    }

    @Test
    fun `hintsUsed - row with number but empty hints`() {
        assertFalse(PuzzleSolverRowBased.hintsUsed(emptyList(), intArrayOf(1, 0, 0)))
    }

    // -------------------------------------------------------------------------
    // sumValid
    // -------------------------------------------------------------------------

    @Test
    fun `sumValid - zero numbers placed`() {
        assertTrue(PuzzleSolverRowBased.sumValid(listOf(3, 2), intArrayOf(CELL_UNSET, CELL_UNSET)))
    }

    @Test
    fun `sumValid - placed sum equals hint sum`() {
        assertTrue(PuzzleSolverRowBased.sumValid(listOf(3, 2), intArrayOf(3, 2, CELL_UNSET)))
    }

    @Test
    fun `sumValid - placed sum exceeds hint sum`() {
        assertFalse(PuzzleSolverRowBased.sumValid(listOf(3), intArrayOf(2, 2, CELL_UNSET)))
    }

    @Test
    fun `sumValid - CELL_EMPTY cells are not counted`() {
        // Row [2,0,UNSET]: only 2 is a positive value; hintSum=3; 2<=3 → true.
        assertTrue(PuzzleSolverRowBased.sumValid(listOf(3), intArrayOf(2, 0, CELL_UNSET)))
    }

    @Test
    fun `sumValid - empty hints sum is zero`() {
        // Any positive value would exceed 0.
        assertFalse(PuzzleSolverRowBased.sumValid(emptyList(), intArrayOf(1, CELL_UNSET)))
    }

    // -------------------------------------------------------------------------
    // isColPartialConsistent – partial columns (all cells CELL_EMPTY or 1..n)
    // -------------------------------------------------------------------------

    @Test
    fun `isColPartialConsistent - empty partial is always consistent`() {
        assertTrue(PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf()))
    }

    @Test
    fun `isColPartialConsistent - ongoing group within hint`() {
        // partial=[1,2], sum=3 ≤ hints[0]=3 → consistent (group may continue or end).
        assertTrue(PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf(1, 2)))
    }

    @Test
    fun `isColPartialConsistent - ongoing group exceeds hint`() {
        // partial=[2,2], sum=4 > hints[0]=3 → inconsistent.
        assertFalse(PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf(2, 2)))
    }

    @Test
    fun `isColPartialConsistent - completed group matches hint`() {
        // partial=[1,2,0], group [1,2]=3 = hints[0]=3 ✓.
        assertTrue(PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf(1, 2, 0)))
    }

    @Test
    fun `isColPartialConsistent - completed group does not match hint`() {
        // partial=[1,0,2]: group [1]=1 ≠ hints[0]=3 → false.
        assertFalse(PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf(1, 0, 2)))
    }

    @Test
    fun `isColPartialConsistent - first group correct second group ongoing`() {
        // hints=[3,2]: group [1,2]=3 ✓; then sum=1 ≤ hints[1]=2 ✓.
        assertTrue(
            PuzzleSolverRowBased.isColPartialConsistent(listOf(3, 2), intArrayOf(1, 2, 0, 1))
        )
    }

    @Test
    fun `isColPartialConsistent - first group correct second group exceeds hint`() {
        // hints=[3,2]: group [1,2]=3 ✓; then sum=3 > hints[1]=2 → false.
        assertFalse(
            PuzzleSolverRowBased.isColPartialConsistent(listOf(3, 2), intArrayOf(1, 2, 0, 3))
        )
    }

    @Test
    fun `isColPartialConsistent - more completed groups than hints`() {
        // hints=[3]: one group allowed. partial=[1,2,0,1]: after first group, v=1 triggers
        // hintIdx=1 >= hints.size=1 → false.
        assertFalse(
            PuzzleSolverRowBased.isColPartialConsistent(listOf(3), intArrayOf(1, 2, 0, 1))
        )
    }

    @Test
    fun `isColPartialConsistent - empty hints any number is inconsistent`() {
        assertFalse(PuzzleSolverRowBased.isColPartialConsistent(emptyList(), intArrayOf(1)))
    }

    @Test
    fun `isColPartialConsistent - empty hints all empty is consistent`() {
        assertTrue(PuzzleSolverRowBased.isColPartialConsistent(emptyList(), intArrayOf(0, 0)))
    }

    // -------------------------------------------------------------------------
    // getValidRows – row configuration enumeration
    // -------------------------------------------------------------------------

    @Test
    fun `getValidRows - n=1 gridSize=2 hints empty yields one all-empty row`() {
        val rows = PuzzleSolverRowBased.getValidRows(1, 2, emptyList())
        assertEquals(1, rows.size)
        assertArrayEquals(intArrayOf(0, 0), rows[0])
    }

    @Test
    fun `getValidRows - n=1 gridSize=2 hint 1 yields exactly two rows`() {
        val rows = PuzzleSolverRowBased.getValidRows(1, 2, listOf(1))
        assertEquals(2, rows.size)
        val asLists = rows.map { it.toList() }.toSet()
        assertTrue(asLists.contains(listOf(1, 0)))
        assertTrue(asLists.contains(listOf(0, 1)))
    }

    @Test
    fun `getValidRows - n=1 gridSize=2 hint 2 yields no valid rows`() {
        // Max possible value is 1, can never sum to 2.
        val rows = PuzzleSolverRowBased.getValidRows(1, 2, listOf(2))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getValidRows - n=2 gridSize=3 hint 3 yields four configurations`() {
        // One block summing to 3 using values from {1,2}: must use both (1+2=3).
        // Possible: [1,2,0], [2,1,0], [0,1,2], [0,2,1].
        val rows = PuzzleSolverRowBased.getValidRows(2, 3, listOf(3))
        assertEquals(4, rows.size)
    }

    @Test
    fun `getValidRows - every returned row satisfies uniqueness constraint`() {
        val n = 3
        val gridSize = 4
        val rows = PuzzleSolverRowBased.getValidRows(n, gridSize, listOf(6))
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            val numbers = row.filter { it > 0 }
            assertEquals("Row has duplicates: ${row.toList()}", numbers.size, numbers.toSet().size)
        }
    }

    @Test
    fun `getValidRows - every returned row satisfies its hints`() {
        val hints = listOf(3, 2)
        val rows = PuzzleSolverRowBased.getValidRows(3, 5, hints)
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            assertTrue(
                "Row ${row.toList()} does not satisfy hints $hints",
                PuzzleSolverRowBased.hintsUsed(hints, row)
            )
        }
    }

    @Test
    fun `getValidRows - returned rows contain no CELL_UNSET`() {
        val rows = PuzzleSolverRowBased.getValidRows(3, 4, listOf(6))
        for (row in rows) {
            assertFalse(
                "Row ${row.toList()} contains CELL_UNSET",
                row.any { it == CELL_UNSET }
            )
        }
    }

    // -------------------------------------------------------------------------
    // countSolutions / isUnique
    // -------------------------------------------------------------------------

    /**
     * n=1, gridSize=2.
     * Unique solution: grid[0][0]=1, all others empty.
     * rowHints=[[1],[]], colHints=[[1],[]] → exactly 1 solution.
     */
    @Test
    fun `countSolutions - unique puzzle n=1`() {
        val hints = GameHints(
            rowHints = listOf(listOf(1), emptyList()),
            colHints = listOf(listOf(1), emptyList())
        )
        assertEquals(1, PuzzleSolverRowBased.countSolutions(1, 2, hints))
    }

    /**
     * n=1, gridSize=2.
     * Both [[1,0],[0,1]] and [[0,1],[1,0]] satisfy rowHints=[[1],[1]], colHints=[[1],[1]].
     */
    @Test
    fun `countSolutions - non-unique puzzle n=1`() {
        val hints = GameHints(
            rowHints = listOf(listOf(1), listOf(1)),
            colHints = listOf(listOf(1), listOf(1))
        )
        assertTrue(PuzzleSolverRowBased.countSolutions(1, 2, hints) > 1)
    }

    /**
     * n=2, gridSize=3.
     * Unique solution (verified by exhaustion):
     *   2 1 0
     *   0 0 2
     *   1 0 0
     * rowHints=[[3],[2],[1]], colHints=[[2,1],[1],[2]].
     */
    @Test
    fun `countSolutions - unique puzzle n=2`() {
        val hints = GameHints(
            rowHints = listOf(listOf(3), listOf(2), listOf(1)),
            colHints = listOf(listOf(2, 1), listOf(1), listOf(2))
        )
        assertEquals(1, PuzzleSolverRowBased.countSolutions(2, 3, hints))
    }

    @Test
    fun `isUnique - returns true for unique puzzle`() {
        val hints = GameHints(
            rowHints = listOf(listOf(1), emptyList()),
            colHints = listOf(listOf(1), emptyList())
        )
        assertTrue(PuzzleSolverRowBased.isUnique(1, 2, hints))
    }

    @Test
    fun `isUnique - returns false for non-unique puzzle`() {
        val hints = GameHints(
            rowHints = listOf(listOf(1), listOf(1)),
            colHints = listOf(listOf(1), listOf(1))
        )
        assertFalse(PuzzleSolverRowBased.isUnique(1, 2, hints))
    }

    @Test
    fun `countSolutions - impossible hints yield zero solutions`() {
        // Row 0 needs a group summing to 100, but n=1 so max is 1.
        val hints = GameHints(
            rowHints = listOf(listOf(100), emptyList()),
            colHints = listOf(emptyList(), emptyList())
        )
        assertEquals(0, PuzzleSolverRowBased.countSolutions(1, 2, hints))
    }

    // -------------------------------------------------------------------------
    // Rule-compliance sanity checks
    // -------------------------------------------------------------------------

    @Test
    fun `solution satisfies no duplicate values in any row or column`() {
        val grid = arrayOf(
            intArrayOf(2, 1, 0),
            intArrayOf(0, 0, 2),
            intArrayOf(1, 0, 0)
        )
        val n = 2
        val gridSize = 3
        for (r in 0 until gridSize) {
            val vals = grid[r].filter { it > 0 }
            assertEquals("Duplicate in row $r", vals.size, vals.toSet().size)
        }
        for (c in 0 until gridSize) {
            val vals = (0 until gridSize).map { grid[it][c] }.filter { it > 0 }
            assertEquals("Duplicate in col $c", vals.size, vals.toSet().size)
        }
        val hints = PuzzleGenerator.computeHints(grid, gridSize)
        assertEquals(1, PuzzleSolverRowBased.countSolutions(n, gridSize, hints))
    }

    @Test
    fun `hints represent sums of blank-separated blocks`() {
        // Row [1,4,0,2] → block sums [5, 2].
        val row = intArrayOf(1, 4, 0, 2)
        val hints = listOf(5, 2)
        assertTrue(PuzzleSolverRowBased.hintsUsed(hints, row))
        assertTrue(PuzzleSolverRowBased.hintsFulfilled(hints, row))
    }
}
