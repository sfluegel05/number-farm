# Number Farm

A number puzzle game for Android. Fill a grid with numbers so that every row and column hint is satisfied.

## Rules

- Choose a field size: **1..3**, **1..5**, **1..7**, or **1..9**
- Fill the grid with numbers from 1 to n
- Each number may appear **at most once per row** and **at most once per column**
- Not every cell needs a number — mark intentionally empty cells with **/**
- The hints next to each row and above each column show the **sums of consecutive filled groups**
  - For example, `5,2` means a group of adjacent cells summing to 5, then a gap, then a group summing to 2

## Features

- Four puzzle sizes (3×4, 5×6, 7×9, 9×11 grid)
- Pencil mode for candidate notes
- "Not empty" cell shading as a solving aid
- Undo history
- Auto-save: resume an unfinished game any time
- Solve statistics with fastest/slowest times per size

## Building

Requires the **PuzzleUtils** sibling library to be checked out at `../PuzzleUtils` relative to this repo. Both must be present for the build to succeed (they are linked via a Gradle composite build).

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests (JVM, no device needed)
./gradlew :app:test
```

Or open the project in Android Studio and run from there.

## Requirements

- Android 7.0+ (API 24)
- Android Studio Hedgehog or later recommended

## License

MIT — see [LICENSE](LICENSE).
