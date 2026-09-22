# 3DView (ThreeDModelViewer)

An Android app for viewing multiple 3D models (`.glb`) at once on a single screen.
Tap **Add model**, pick a bundled file, and it appears as a draggable, resizable
card that can be dragged, pinch-resized, rotated (arcball-style), pinch-zoomed, and
have its part labels toggled on/off — independently of every other model already on
screen. Several models, including duplicates of the same file, can be on screen at
the same time.

## What it is

A single-`Activity`, single-screen Jetpack Compose app built as a small 3D-scene
"gallery": one shared 3D viewport hosts N independently-manipulable model cards,
each with its own position, size, rotation, zoom and label visibility, driven by
plain Compose state.

## Libraries used

| Library | Purpose |
|---|---|
| [SceneView (Compose)](https://github.com/SceneView/sceneview-android) `4.38.0` | Filament/gltfio-backed 3D rendering, wrapped as a Compose `SceneView { ... }` DSL (`ModelNode`, camera/light nodes, environment) |
| Jetpack **Compose** (BOM `2026.02.01`) + **Material3** | UI, layout, gestures, theming |
| `androidx.activity:activity-compose` | Edge-to-edge single-`Activity` host |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.core:core-ktx` | Standard AndroidX Kotlin extensions |
| `org.json` (platform) | Parsing each glTF node's `extras` JSON to pull out label text — no extra dependency |

Under the hood, SceneView pulls in Google's **Filament** rendering engine and
**gltfio** glTF loader (pinned transitively to `1.72.1`), which is what actually
parses `.glb` files, uploads geometry/textures to the GPU, and renders each frame.

## How it works

All screen logic lives in `ui/ModelGallery.kt`; `MainActivity.kt` just wraps it in
the app theme.

### One shared 3D scene, not one per model

There is exactly **one** `SceneView` composable filling the screen, backed by one
`rememberEngine()` / `ModelLoader` / `CameraNode` / light nodes. Every on-screen
model is a `ModelNode` added to that single scene, driven by a
`mutableStateListOf<PlacedModel>`. Five separate `SceneView`s would mean five
Filament `Engine`s and five render loops — one scene with many nodes is what
Filament is designed for.

### Orthographic camera + screen↔world mapping

The camera is orthographic (`Camera.Projection.ORTHO`), re-fit to the viewport size
in a `LaunchedEffect(viewSize)` — only when the view actually resizes (e.g. screen
rotation), never every frame. A constant `UNITS_PER_PIXEL` defines a fixed linear
mapping between screen pixels and world units, so a container's on-screen
center/size maps directly and cheaply to a world position/scale
(`screenToWorld()` in `util/CommonUtils.kt`).

Each model is normalized once at load time to a bounding-box max extent of 1 world
unit; from then on, `scale = normalizedScale * containerSizePx * UNITS_PER_PIXEL *
userZoom` — a single multiply, no per-frame geometry work.

### Per-model interaction, two gesture modes

Every model card has an invisible gesture `Box` using
`detectTransformGestures`, plus a row of three toggle buttons
(`ModelControlButton`): **interact**, **labels**, **close**.

- **Placement mode** (default): pan moves the container, pinch resizes it — both
  clamped to the viewport.
- **Interaction mode** (toggled on): pan drives yaw/pitch rotation
  (`Rotation(x = pitch, y = yaw)`, clamped to ±89° pitch to avoid gimbal flip),
  pinch drives a zoom multiplier clamped between `MIN_ZOOM`/`MAX_ZOOM`.

The two modes never fight over the same state — resize is frozen while rotating,
and vice versa. Gestures are handled entirely by app-owned `pointerInput`, not by
SceneView's built-in orbit-camera gesture handler.

### Labels from glTF `extras`

Each mesh node's label text is stored as `extras.prop` on the glTF node. Once a
model is added, `Iterable<Node>.toNodeLabels()` (`util/CommonUtils.kt`) walks its
renderable/empty child nodes, reads `Node.extras`, and pulls `"prop"` out with
`org.json.JSONObject` — no manual GLB byte parsing needed, since gltfio already
exposes `extras` per node.

Label positions are projected from world space to screen space every frame via
`cameraNode.worldToView(...)`, but **only when at least one model has labels
toggled on** — the projection loop early-outs to an empty list otherwise. All label
dots/connector lines are drawn in one `Canvas` pass; label text uses lightweight
`Text` composables positioned with `Modifier.offset`.

### Cleanup ordering

Each model's `DisposableEffect` is declared *before* its `ModelNode` in the same
`key(placed.id)` block, so Compose disposes it *after* the node (Compose disposes
sibling effects in reverse declaration order) — the node is torn down from the
scene first, then `modelLoader.destroyModel(...)` frees its GPU resources.

## Performance optimizations (and the trade-offs)

| Optimization | Trade-off |
|---|---|
| **Single shared `Engine`/`Scene`/`SceneView`** for all models instead of one per model | None meaningful — this is strictly cheaper; the only "cost" is that every model shares one render-quality/lighting setting. |
| **`RenderQuality.Performance` preset** — shadows, SSAO, bloom off, FXAA instead of MSAA, dynamic resolution on | Lower visual fidelity (no shadows/AA) in exchange for holding frame rate on low-end GPUs with several models on screen. |
| **Camera re-fit only on viewport size change**, not per frame | None — it's strictly correct; refitting every frame would force a render request every frame regardless of whether anything moved. |
| **Label projection/draw gated behind "any model has labels on"** | The check itself runs every frame (cheap boolean scan), but the expensive per-label matrix projection is skipped entirely when nothing is visible — the common case while just dragging/resizing. |
| **`cameraManipulator = null`**, all gestures handled by app `pointerInput` | Loses SceneView's built-in single-finger orbit/tap-to-select; not needed since every model already has its own explicit drag/pinch handling. |
| **One shared main light + fill light**, reused across all models | Simpler and cheaper than per-model lighting, at the cost of every model sharing the same light direction (acceptable for a flat "gallery" layout). |
| **Bounding-box normalization computed once per model at load** | On-screen scaling afterward is a single multiply per frame instead of re-measuring geometry. |

### Known trade-off: no model instance caching

Every "Add model" tap calls `modelLoader.createModel(...)` fresh — including for a
second/third copy of a file already on screen — so each duplicate re-parses and
re-uploads that `.glb`'s vertex/index/texture data rather than sharing it via
gltfio's cheap `createInstance()` path. This keeps the add/close code simple (no
refcounting), at the cost of repeated GPU upload for duplicates of large files
(e.g. `Fiagena.glb`, `solarsystem.glb`). Worth revisiting with an instance cache if
adding many duplicates of a large model becomes a real use case.

## Project structure

```
app/src/main/java/com/example/threedmodelviewer/
├── MainActivity.kt              # Activity shell, sets the Compose content
├── ui/
│   ├── ModelGallery.kt          # Scene, camera, per-model state, gestures, labels
│   ├── ModelPickerMenu.kt       # "Add model" dropdown
│   ├── ModelControlButton.kt    # Interact/label/close toggle button
│   └── theme/                   # Material3 theme, colors, typography
└── util/
    ├── Constants.kt             # Tunable constants (units-per-pixel, zoom clamp, etc.)
    └── CommonUtils.kt           # screenToWorld(), extras-based label extraction
app/src/main/assets/models/      # Bundled .glb files (Bulb, Lungs, Microscope, Fiagena, solarsystem)
```

## Building & running

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires `minSdk 24`+, Kotlin `2.4.20` (required to read `sceneview`'s Kotlin
2.4.x-compiled `kotlin-stdlib` metadata).
