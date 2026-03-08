# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Use Android Studio or the Gradle wrapper from the repo root:

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (JVM, fast)
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "com.sfluegel.numberfarm.PuzzleSolverTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew :app:connectedAndroidTest
```

The `PuzzleUtils` sibling library (`../PuzzleUtils`) is included as a composite build via `includeBuild("../PuzzleUtils")` in `settings.gradle.kts`. Both projects must be present for the build to succeed.

## Architecture

**Game rules:** Player fills an `n+1`×`n+1` (or `n+2`×`n+2` for n>5) grid with numbers 1..n. Each number appears at most once per row and column. Not every cell must be filled. Hints list the sums of consecutive filled-cell groups in each row/column (e.g. `[1, _, 4]` → hint `[1, 4]`).

**Data flow:**
1. `MainActivity` holds a single `encodedScreen` int (0=Home, 3/5/7=Game size). No ViewModel.
2. `GameScreen` launches `PuzzleGenerator.generateGame(n)` on `Dispatchers.Default` via `LaunchedEffect`, shows `WavyLoadingIndicator` while loading.
3. All puzzle state (cells, pencil marks, not-empty marks, undo history) lives as `mutableStateListOf` / `mutableStateOf` directly in `PuzzleBoard` composable.

**Key types (GameModel.kt):**
- `CELL_EMPTY = 0` — cell intentionally left blank
- `CELL_UNSET = -1` — cell not yet touched by player
- `GameState(n, gridSize, solution, hints)` — immutable puzzle
- `GameHints(rowHints, colHints)` — `List<List<Int>>` group sums per row/column

**Generation pipeline (PuzzleGenerator):**
1. `generateSolution` — random partial fill respecting uniqueness per row/column
2. `computeHints` — derives group-sum hints from the filled grid
3. `PuzzleSolverRowBased.isUnique` — verifies exactly one solution; retries up to 100×

**Two solver implementations:**
- `PuzzleSolverRowBased` (**active**) — pre-enumerates valid row configs via `getValidRows`, then does row-by-row backtracking with column-consistency pruning. This is what `PuzzleGenerator` uses.
- `PuzzleSolver` (**legacy, unused by generator**) — cell-by-cell backtracking with MRV heuristic and forward checking. Still present but not called from production code.

**PuzzleUtils library** (`com.sfluegel.puzzleutils`) provides shared UI components used directly in `GameScreen`:
- `PuzzleLayout` — orientation-aware two-panel layout (grid + controls panels)
- `PuzzleTopAppBar` — top bar with timer, back, reset (with confirm dialog), and help
- `PuzzleGridCell` — single tappable grid cell
- `PencilMarksGrid` — renders candidate pencil marks inside a cell
- `WavyLoadingIndicator` — animated loading dots

**UI layout pattern in GameScreen:**
- Grid area: column-hint row on top, then rows of `[row-hint | cells...]`
- Controls area: value picker (`FilterChip` row), then Undo / Clear / Not-empty / Pencil-mode buttons
- Cell size computed as `min(availableWidth, availableHeight) / (gridSize + 1)` to account for the hint strip

**Unit tests** (`PuzzleSolverTest.kt`) cover `PuzzleSolverRowBased` only — all helper functions and end-to-end `countSolutions`/`isUnique`. Tests use plain JUnit4 (no Android dependencies), so they run on the JVM.
