# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kakugo is an Android application for learning Japanese, focusing on Hiragana, Katakana, Kanji, and vocabulary. The app uses a Spaced Repetition System (SRS) to optimize learning and retention.

## Build Commands

### Building the App

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK (minified)
./gradlew build                # Build all variants and run tests
```

### Testing

```bash
./gradlew testDebugUnitTest    # Run unit tests for debug build
./gradlew test                 # Run all unit tests
./gradlew connectedDebugAndroidTest  # Run instrumented tests on device/emulator
```

**Note:** The project currently has no unit tests. The only test is an instrumented test (`DatabaseUpdaterTest.kt`) that validates database migration from all previous schema versions to the current version.

### Code Quality

```bash
./gradlew lint                 # Run lint checks
./gradlew lintFix              # Apply safe lint suggestions
```

## Architecture Overview

### Core Components

**TestEngine** (`TestEngine.kt`)

- Central quiz/test orchestration system
- Manages question selection using SRS algorithm
- Tracks answer history and scoring
- Coordinates with SrsCalculator for probability-based item selection
- Handles callbacks for correct/wrong/unknown answers

**SrsCalculator** (`SrsCalculator.kt`)

- Implements spaced repetition algorithm with two-stage probability calculation
- Manages short-term and long-term memory scores
- Calculates item selection probabilities based on:
  - Short score (recent performance)
  - Long score (long-term retention)
  - Days since last asked
  - Total weight distribution across items

**Database** (`model/Database.kt`)

- SQLite-based storage for all learning items (Kana, Kanji, Words)
- Pre-populated from compressed dictionary (`res/raw/dict`)
- Database migration is handled by DatabaseUpdater, which contains the full schema of the user database
- Singleton pattern with lazy initialization
- Provides specialized views (LearningDbView) for different item types
- Tracks scores, enabled state, and last-asked timestamps per knowledge type

**LearningDbView** (`model/LearningDbView.kt`)

- Filtered database view for specific item types and knowledge types
- Handles item selection based on classifiers (e.g., JLPT levels)
- Integrates with SrsCalculator for probability-based queries
- Manages enabled/disabled state for items

### Test System

**TestActivity** (`testactivities/TestActivity.kt`)

- Main activity for all quiz types
- Uses Jetpack Compose for UI
- Manages test fragments dynamically based on TestType
- Handles process death/restoration (important for Android lifecycle)
- Coordinates with TestEngine for question flow

**Test Fragments** (in `testactivities/`)

- `QuizTestFragment`: Multiple-choice questions
- `TextTestFragment`: Text input answers
- `DrawingTestFragment`: Draw characters (Kana/Kanji)
- `CompositionTestFragment`: Kanji composition questions

Each fragment:

- Receives questions from TestActivity/TestEngine
- Displays question and answer options
- Reports user responses back to TestEngine
- Handles different TestTypes (reading, meaning, drawing, etc.)

### Data Models

**Item Types** (`model/Model.kt`)

- `Kanji`: Character, readings (on/kun), meanings, similarities, parts, JLPT level
- `Kana`: Character, romaji mappings, similarities
- `Word`: Word, reading, meanings, similarities, kanaAlone flag

**TestType** (`model/TestType.kt`)
18 different test types covering:

- Hiragana/Katakana: Character↔Romaji (quiz/text), Drawing
- Kanji: Reading↔Kanji, Meaning↔Kanji, Drawing, Composition
- Words: Reading↔Word, Meaning↔Word

**KnowledgeType** (in `model/Model.kt`)
Separates different aspects of learning (Reading, Meaning, Writing) so items have independent scores for each knowledge type.

### UI Structure

**Compose Migration**
The app uses a hybrid approach:

- MainActivity and TestActivity: Full Compose
- Settings/Stats screens: Mix of Compose and traditional Views
- Test fragments: Embedded in Compose via AndroidFragment

**Theme** (`theme/Theme.kt`)
Material Design with KakugoTheme wrapper providing consistent styling.

## Key Development Patterns

### Database Initialization

On first launch, MainActivity extracts `res/raw/dict` (gzipped) and initializes the database via DatabaseUpdater. The database persists across app launches.

### Locale Management

LocaleManager handles dictionary locale selection (separate from app UI locale). Must be initialized before database access. The app can be restored without going through MainActivity, so locale initialization is defensive.

### Session Tracking

TestEngine uses sessionId to track test sessions and prevent history contamination across different test instances.

### State Preservation

TestActivity carefully manages state across configuration changes and process death:

- Saves current question, answers, history, scores
- Restores TestEngine state from Bundle
- Handles fragment restoration

### SRS Score Updates

Certainty levels (DONTKNOW, MAYBE, SURE) affect score updates differently. SrsCalculator uses non-linear functions to adjust short/long scores based on performance and time.

### Localization

All strings must be translated in all supported languages.

### Commit messages

Commit messages are concise, with a subject (usually the class) and a small message explaining the change. For example: "KanjiDisplayactivity: add Words tab". Do not sign commits.

Don't make commits unless asked to. Don't bother with the fact that the worktree is usually in detached HEAD.

## Package Organization

- `org.kaqui.mainmenu`: Main menu and category menu activities
- `org.kaqui.testactivities`: Test execution (TestActivity, fragments, drawing)
- `org.kaqui.settings`: Settings, item selection, search activities
- `org.kaqui.stats`: Statistics and progress tracking
- `org.kaqui.model`: Data models, database, test/item types
- `org.kaqui.theme`: Compose theming

## Important Constraints

- Uses Compose
- Proguard enabled for release builds
