# Enroute Codebase - AI Agent Instructions

## Project Overview
**Enroute** is an Android indoor navigation app for pedestrian pathfinding in multi-floor buildings using floor plans. It combines Inertial Measurement Unit (IMU) for Pedestrian Dead Reckoning (PDR) with pathfinding algorithms. The app is built with **Kotlin, Jetpack Compose, and Android Architecture Components**.

**Key Technologies**: Android 29+, Compose UI, MVVM pattern, Coroutines, Repository pattern, JSON-based floor data, A* pathfinding, IMU sensors.

---

## Architecture Overview

### High-Level Data Flow
```
MainActivity (Compose entry point)
  └─ NavigationGraph (Compose routing)
      ├─ WelcomeScreen (campus selection)
      │   └─ CampusSearchOverlay
      ├─ HomeScreen (main interactive floor plan view) [per-campus]
      │   ├─ FloorPlanViewModel (floor plan state & canvas transforms)
      │   │   └─ FloorPlanRepository (JSON/Firebase floor data loading)
      │   ├─ PdrViewModel (heading & tracking state)
      │   │   ├─ SensorManager (IMU polling)
      │   │   ├─ MotionRepository (accelerometer/magnetometer data)
      │   │   └─ NavigationRepository (A* pathfinding)
      │   └─ FloorPlanCanvas (Compose rendering + gesture handling)
      ├─ SettingsScreen (app preferences)
      └─ AdminScreen (admin utilities - file upload, tests, JSON management)
```

### Multi-Campus Architecture
The app now supports multiple campuses with campus-wide coordinate systems:
- **CampusMetadata**: Campus-level configuration (loaded once per campus session)
- **Campus-wide coordinates**: Position tracking spans across all buildings in a campus
- **Per-campus navigation**: Home screen scoped to `campusId` parameter

---

## Architecture & Data Flow

### Core Layers
```
MainActivity → NavigationGraph (Compose navigation: Welcome → Home → Settings/Admin)
  ├─ WelcomeScreen (Campus selection gateway)
  ├─ HomeScreen(campusId) [MVVM per campus]
  │   ├─ FloorPlanViewModel
  │   ├─ PdrViewModel  
  │   └─ NavigationViewModel
  ├─ SettingsScreen (Preferences & configuration)
  └─ AdminScreen (Admin utilities - file ops, testing, data management)
      └─ Repositories (abstraction layer)
           ├─ LocalFloorPlanRepository (loads JSON from assets/)
           ├─ FirebaseFloorPlanRepository (loads from Firebase)
           ├─ MotionRepository (sensor data)
           ├─ PdrRepository (PDR lifecycle)
           └─ SettingsRepository (SharedPreferences)
                └─ Data Models (FloorPlanData, BuildingState, CampusMetadata, etc.)

### Key Components

#### **FloorPlanViewModel** ([feature/floorplan/FloorPlanViewModel.kt](feature/floorplan/FloorPlanViewModel.kt))
- Single source of truth for floor plan state: `uiState: StateFlow<FloorPlanUiState>`
- Manages multiple buildings independently via `buildingStates: Map<String, BuildingState>`
- Handles floor loading, canvas transforms (pan/zoom/rotate), and viewport calculations
- Uses `AndroidViewModel` for asset access via context
- **Key methods**: `loadAllFloors()`, `selectFloor()`, `updateCanvasState()`

#### **PdrViewModel** ([feature/pdr/PdrViewModel.kt](feature/pdr/PdrViewModel.kt))
- State: `uiState: StateFlow<PdrUiState>` (heading, origin, tracking status)
- Manages IMU heading collection via `heading: StateFlow<Float>` (radians)
- **Key methods**: `setOrigin()`, `enableTracking()`, `disableTracking()`, `cancelOriginSelection()`
- Uses `SensorManager` to poll accelerometer/magnetometer for compass heading
- PDR state is independent per floor (stored in `PdrState` data class)

#### **NavigationRepository** ([feature/navigation/NavigationRepository.kt](feature/navigation/NavigationRepository.kt))
- **A* algorithm** with distance transform-based heuristic
- **Grid-based pathfinding**: 20px cells on 4188×4329px floor plans (210×217 grid)
- **Distance transform**: Pre-computed minimum distance from each grid cell to nearest wall
- **Cost function**: Wall proximity drives movement cost (cells far from walls = lower cost)
  - Cells `< 0.3f` units from walls: blocked
  - `< 1f`: cost 500 (high penalty but reachable)
  - `< 3f`: cost 150, `< 5f`: cost 50
  - Open space: minimal cost based on distance
- **Path smoothing**: Removes zigzags while preserving wall-following behavior
- **Logging**: Verbose debug output (TAG: "NavigationRepository") with iteration/timing stats

#### **MotionRepository** ([feature/pdr/data/repository/MotionRepository.kt](feature/pdr/data/repository/MotionRepository.kt))
- Manages device sensor access (accelerometer, magnetometer)
- Provides raw IMU data for PDR calculations
- Polls sensors at configurable frequency via SensorManager
- Converts raw sensor values to heading (compass bearing in radians)

#### **PdrRepository** ([feature/pdr/data/repository/PdrRepository.kt](feature/pdr/data/repository/PdrRepository.kt))
- Manages PDR state lifecycle and persistence
- Tracks: origin point, current position, heading, floor level
- All positions stored in **campus-wide coordinates** (not per-floor)
- Converts between world coordinates and grid coordinates for pathfinding
- Manages PDR state transitions (idle → origin selection → tracking → idle)

#### **SettingsRepository** ([feature/settings/data/SettingsRepository.kt](feature/settings/data/SettingsRepository.kt))
- Persists app preferences via SharedPreferences
- Configuration: sensor sensitivity, display settings, coordinate system preferences
- Single instance per app; accessed by SettingsScreen and other features
- No ViewModels directly—used by settings UI for read/write operations

#### **FloorPlanCanvas** ([feature/floorplan/rendering/FloorPlanCanvas.kt](feature/floorplan/rendering/FloorPlanCanvas.kt))
- Composable that renders floor plan geometry (walls, stairwells, entrances, rooms, boundaries)
- Handles gesture detection (pan, zoom, rotate) via `detectTransformGestures`
- Applies canvas transformations: `CanvasState` (scale, offsetX/Y, rotation) + metadata scale/rotation
- Delegates rendering to `renderers/` subpackage functions

#### **BuildingState** ([feature/floorplan/state/BuildingState.kt](feature/floorplan/state/BuildingState.kt))
- Per-building state container: building config + floor map + current floor selection
- `floorsToRender` property: lists floors from bottom to selected floor (for stacking visualization)
- Enables independent floor navigation across buildings

#### **CanvasAnimator** ([feature/floorplan/utils/CanvasAnimator.kt](feature/floorplan/utils/CanvasAnimator.kt))
- Smoothly animates canvas transformations (center on coordinates with zoom)
- Accounts for floor plan's built-in rotation/scale (from metadata) + canvas layer transforms
- Used for "aim at location" feature

#### **WelcomeScreen** ([feature/welcome/WelcomeScreen.kt](feature/welcome/WelcomeScreen.kt))
- App entry point; handles campus selection flow
- Displays list of available campuses via `CampusSearchOverlay`
- Navigates to `HomeScreen` with selected `campusId` 
- No ViewModels needed – simple UI layer

#### **AdminScreen & AdminViewModel** ([feature/admin/AdminScreen.kt](feature/admin/AdminScreen.kt), [feature/admin/AdminViewModel.kt](feature/admin/AdminViewModel.kt))
- Administrative tools for developers/testers: file upload, JSON validation, testing utilities
- File management: import JSON floor plans from external storage
- Database operations: reset/clear cached data
- Test data generation and validation
- State: `uiState: StateFlow<AdminUiState>` for progress/error messaging

#### **CampusSearchOverlay** ([feature/campussearch/CampusSearchOverlay.kt](feature/campussearch/CampusSearchOverlay.kt))
- Reusable overlay component for campus selection
- Animated appearance with search/filter capabilities
- Used in WelcomeScreen and accessible from HomeScreen
- Morphing button animation (`MORPH_DURATION_MS` constant)

#### **CampusMetadata** ([data/model/CampusMetadata.kt](data/model/CampusMetadata.kt))
- Campus-level configuration loaded once per campus session
- Contains: campus ID, name, building map, coordinate system metadata
- Persists until user manually closes or switches campuses
- Enables campus-wide coordinate tracking across multiple buildings

---

## Data Models & JSON Structure

### Core Models
- **FloorPlanData**: Container with `floorId`, `metadata`, `walls`, `stairwells`, `entrances`, `rooms`, `boundaryPolygons`
- **Building**: Represents a building (id, name, etc.)
- **Geometric**: `BoundaryPoint`, `BoundaryPolygon`, `Wall`, `Stairwell` (list of `StairLine`), `Entrance`, `Room`
- **Metadata**: `FloorPlanMetadata` - contains scale factor and rotation for the floor plan
- **Wall**: `x1, y1, x2, y2` (line segment in world coordinates)
- **PdrState**: `origin: Offset?`, `isTracking: Boolean`, `currentFloor: Float`
- **SearchResult**: Room search result with coordinates and metadata

### Asset Files (in `app/src/main/assets/`)
All files are JSON. LocalFloorPlanRepository loads them via Gson:
- `building_1_metadata.json` - building scale/rotation
- `floor_*.json` files (walls, stairs, entrances, rooms, boundary) - geometry data
- Naming convention: `{floorId}_{element}.json` (floor IDs as floats: 1, 1.5, 2, 2.5)

---

## Key Workflows

### **Adding a Pathfinding Feature**
1. Modify `NavigationRepository.findPath()` for new algorithms
2. Adjust cost function in `getMovementCost()` to influence path behavior
3. Update distance transform calculation if grid resolution changes
4. Add logging (use TAG constant) for debug analysis
5. Test with `findPath(start: Offset, goal: Offset)` returning `List<Offset>`

### **Modifying PDR Tracking**
1. Edit `PdrViewModel.enableTracking()` for new tracking logic
2. `SensorManager` polling happens in `sensor/` subpackage
3. Update `PdrUiState` if new tracking properties needed
4. Origin selection flow: user taps → `setOrigin()` → PDR enables → path follows
5. Canvas animation uses `FollowingAnimator` to center on tracking position

---

## Build & Development

### Build System
- **Gradle 8+** with Kotlin DSL (`.kts`)
- **Dependency management**: `gradle/libs.versions.toml`
- **Key dependencies**: Compose (BOM), Navigation Compose, Lifecycle, Gson
- **Java 11** target compatibility
- **Compose enabled** in buildFeatures

### Firebase Integration
- **FirebaseFloorPlanRepository** ([data/repository/FirebaseFloorPlanRepository.kt](data/repository/FirebaseFloorPlanRepository.kt))
- Loads floor plans from Firebase (alternative to local JSON assets)
- Use `LocalFloorPlanRepository` for development; switch to Firebase in production
- Fallback strategy: Firebase → Local assets if network unavailable
- Configuration via Firebase console and app configuration

### Commands
```bash
./gradlew build          # Build APK
./gradlew assembleDebug  # Debug APK
./gradlew test           # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

### Package Structure
- `core/navigation/` - NavigationGraph & Screen routes
- `data/` - Models, Repository interfaces & implementations
- `feature/` - Feature modules (home, floorplan, settings)
  - Each feature has: screens, components, viewmodels, utils, state/rendering
- `ui/theme/` - Compose theme

---

## Developer Conventions

### Naming & Patterns
1. **Backtick package imports** for reserved keywords: `import in.project.enroute` → `import \`in\`.project.enroute`
2. **Data classes** for immutable state (BuildingState, CanvasState, FloorPlanUiState, etc.)
3. **StateFlow** for reactive UI state (never mutableStateOf at ViewModel level)
4. **Suspend functions** for async data loading (respects Coroutines best practices)
5. **Sealed class** for navigation routes: `sealed class Screen(val route: String)`

### ViewModels
- Extend `AndroidViewModel` (for context access) or `ViewModel`
- Use `viewModelScope` for coroutines
- Expose state via `StateFlow<UiState>`, not individual properties
- Update state via `_state.update { ... }` (from MutableStateFlow)

### Compose UI
- Use `@Composable` functions, no class-based components
- Collect state via `collectAsState()` in composables
- Use `LaunchedEffect` for side effects (loading data on first composition)
- Support Material3 design (from dependencies)

### Coordinate Spaces (Critical!)
- **World coordinates**: Floor plan pixel coordinates (0-4188 × 0-4329)
- **Grid coordinates**: 20px cell grid (0-210 × 0-217) used by A* pathfinding
- **Canvas coordinates**: After pan/zoom/rotate transforms
- **Screen coordinates**: Device screen pixels (rotate origin from center)
- **Conversion**: `gridCell = (worldCoord / 20f).toInt()`, then reverse with `(gridCell + 0.5f) * 20f`

### Asset Loading
- Assets stored in `app/src/main/assets/`
- Loaded synchronously via `context.assets.open()` in repository
- JSON parsing via Gson (already in dependencies)

### Logging
- Use `Log.d(TAG, "...")` for debug, `Log.e(TAG, "...")` for errors
- PathFinding logs: `"=== PATHFINDING START ==="`, iteration counts, timing
- PDR logs: origin setting, tracking enable/disable, heading updates

---

## Common Workflows

### Adding a New Floor Plan Element
1. Add data class in [data/model/](data/model/) (e.g., `Beacon.kt`)
2. Add JSON parsing in [data/repository/LocalFloorPlanRepository.kt](data/repository/LocalFloorPlanRepository.kt) (e.g., `loadBeacons()`)
3. Add to `FloorPlanData` model
4. Create renderer in [feature/floorplan/rendering/renderers/](feature/floorplan/rendering/renderers/) (e.g., `drawBeacons()`)
5. Call renderer in `FloorPlanCanvas.drawScope { ... }`
6. Add visibility toggle in `FloorPlanDisplayConfig`

### Adding a New Screen
1. Create new Composable in [feature/{featureName}/](feature/) folder
2. Add `Screen` sealed class entry in [core/navigation/NavigationGraph.kt](core/navigation/NavigationGraph.kt)
3. Add `composable()` route in `NavigationGraph`
4. For campus-scoped screens, use `Screen.Home.createRoute(campusId)` pattern with route arguments
5. Update navigation bar in [MainActivity.kt](MainActivity.kt) if needed

### Modifying Canvas Behavior
- **Gestures**: Edit `detectTransformGestures` block in [FloorPlanCanvas.kt](feature/floorplan/rendering/FloorPlanCanvas.kt#L95)
- **Transform math**: See `CanvasAnimator` for coordinate space accounting (floor plan transform + canvas layer)
- **State updates**: Call `onCanvasStateChange(newState)` callback from canvas

### Implementing Multi-Campus Features
1. Load `CampusMetadata` in `HomeScreen(campusId)` once per session
2. Pass `campusId` as parameter through navigation: `Screen.Home.createRoute(campusId)`
3. Store PDR positions in **campus-wide coordinates** (not per-floor)
4. Use `PdrRepository` to track position across buildings within campus
5. Switch campuses via `CampusSearchOverlay` → navigates to new `HomeScreen` instance
6. ViewModels are scoped per campus via `viewModel(backStackEntry)` in NavigationGraph

---

## Testing Conventions
- **Unit tests**: [app/src/test/java/](app/src/test/java/) (ExampleUnitTest.kt template)
- **Instrumented tests**: [app/src/androidTest/java/](app/src/androidTest/java/) (ExampleInstrumentedTest.kt template)
- Test runner: AndroidJUnitRunner
- Note: No existing tests yet; follow Android Architecture Components testing patterns

---

## Key Files Reference
| File | Purpose |
|------|---------|
| [MainActivity.kt](app/src/main/java/in/project/enroute/MainActivity.kt) | App entry point, navigation bar setup |
| [NavigationGraph.kt](core/navigation/NavigationGraph.kt) | Route definitions, composable routing |
| [WelcomeScreen.kt](feature/welcome/WelcomeScreen.kt) | Campus selection entry point |
| [HomeScreen.kt](feature/home/HomeScreen.kt) | Main floor plan view (per-campus) |
| [AdminScreen.kt](feature/admin/AdminScreen.kt) | Admin utilities & testing tools |
| [FloorPlanViewModel.kt](feature/floorplan/FloorPlanViewModel.kt) | Floor plan state management |
| [PdrViewModel.kt](feature/pdr/PdrViewModel.kt) | PDR tracking state and heading |
| [NavigationRepository.kt](feature/navigation/NavigationRepository.kt) | A* pathfinding algorithm |
| [LocalFloorPlanRepository.kt](data/repository/LocalFloorPlanRepository.kt) | Asset loading logic |
| [FirebaseFloorPlanRepository.kt](data/repository/FirebaseFloorPlanRepository.kt) | Firebase floor data loading |
| [FloorPlanCanvas.kt](feature/floorplan/rendering/FloorPlanCanvas.kt) | Main rendering & gesture handling |
| [CanvasAnimator.kt](feature/floorplan/utils/CanvasAnimator.kt) | Transform animation utilities |
| [BuildingState.kt](feature/floorplan/state/BuildingState.kt) | Per-building state model |
| [CampusMetadata.kt](data/model/CampusMetadata.kt) | Campus-level configuration |
| [CampusSearchOverlay.kt](feature/campussearch/CampusSearchOverlay.kt) | Campus selection UI component |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | App permissions & activities |

---

## Important Constraints & Gotchas
1. **Package naming**: Uses reserved keyword `in` → always use backticks in imports
2. **Asset naming**: Floor IDs use float format (floor_1, floor_1.5, floor_2.5) - respect in file paths
3. **Canvas coordinate space**: Floor plan has built-in scale/rotation (metadata) + canvas layer transforms - both affect gesture calculations
4. **Multi-floor rendering**: `floorsToRender` renders floors bottom→current (not all floors) for proper stacking visualization
5. **Coroutine scope**: ViewModels use `viewModelScope` for safety; avoid GlobalScope
6. **Compose state**: Use StateFlow in VMs, collectAsState in UI; never mutableStateOf at VM level
7. **A* path validity**: Always check `isValid(gridPos)` before accessing `distanceGrid`
8. **Origin selection**: Blocking (prevents most gestures) until origin set via `OriginSelectionOverlay`
