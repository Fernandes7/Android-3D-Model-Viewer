# 3D Model Viewer — scratchpad

A single-Activity Jetpack Compose app: tap "Add model", pick a bundled `.glb`, and it
appears on screen — draggable, resizable, rotatable/zoomable, with toggleable part
labels. Multiple models (5+, including duplicates of the same file) stay on screen
simultaneously.

All logic lives in `app/src/main/java/com/example/threedmodelviewer/MainActivity.kt`.

## Library version note (important — read before comparing this code to older SceneView docs/tutorials)

This app uses `io.github.sceneview:sceneview:4.38.0`, pinned to Filament/gltfio
`1.72.1`. That's a fully Compose-first rewrite of SceneView: `SceneView { ... }` is a
composable whose trailing lambda is a `SceneScope` DSL, and every scene node
(`ModelNode`, `LightNode`, etc.) is itself a `@Composable` function. Older
SceneView tutorials describe an imperative `Node.position = ...` API on a `View`
subclass — that API still exists underneath, but this app is written entirely
against the reactive Compose DSL, which is what actually keeps a single `SceneView`
composable's node list in sync with plain `mutableStateListOf` app state.

## Single shared `SceneView`, not five

There is exactly one `SceneView` composable, filling the whole screen, backed by one
`rememberEngine()` / one `ModelLoader` / one `View` / one `Scene`. Every on-screen
model is a `ModelNode` added to that one scene's content block, driven by a
`mutableStateListOf<PlacedModel>`. Five separate `SceneView`s would mean five
Filament `Engine`s, five GL contexts, five `Choreographer` render loops — five times
the fixed overhead on a 2-3GB device for no benefit, since Filament already supports
many independent renderables in one scene. All per-model interaction (drag, pinch,
rotate, the three buttons) is plain Compose UI layered on top via
`Modifier.offset { IntOffset(...) }`; only the resulting position/scale/rotation
numbers are written into the shared scene.

## Orthographic camera, screen<->world mapping

The camera uses `Camera.Projection.ORTHO` (`cameraNode.setProjection(ORTHO, left,
right, bottom, top, near, far)`), sized in a `LaunchedEffect(viewSize)` so it always
matches the current view's pixel size, and re-fit only when that size actually
changes (screen rotation) — never per frame, since rewriting the camera every frame
would call Filament's `requestRender()` every frame and defeat the library's
render-on-demand frame policy (see "Performance" below).

A constant `UNITS_PER_PIXEL = 1/800` defines the (arbitrary but fixed) linear
mapping between screen pixels and world units. With an unrotated ortho camera
looking down -Z, this makes container placement trivial and exactly linear:

```
screenToWorld(px) = (
    (px.x - viewWidth/2)  * UNITS_PER_PIXEL,
    (viewHeight/2 - px.y) * UNITS_PER_PIXEL,   // Y flips: screen Y is down, world Y is up
    0
)
```

Every model is placed at world Z=0; the camera sits at world Z=10 looking down -Z
with `near=0.1, far=100`, so Z=0 is always inside the frustum regardless of screen
size.

Each model is normalized once, at load time, to a bounding-box max extent of 1 world
unit (`normalizedScale = 1 / maxExtent`, from `FilamentAsset.getBoundingBox()`).
A model's on-screen scale is then driven directly by its container:

```
node.scale = containerSizePx * UNITS_PER_PIXEL * normalizedScale * userZoom
```

`userZoom` is the interaction-mode pinch multiplier (clamped 0.3x-3x); it's 1.0 and
inert whenever the model is in normal (drag/resize) mode, and `containerSizePx`
(clamped 80dp .. `min(screenW,screenH)*0.9`) is likewise frozen while in interaction
mode — so the two gesture modes never fight over the same number.

Rotation uses Euler angles (`Rotation(x = pitch, y = yaw, z = 0)`, applied through
the `ModelNode` composable's reactive `rotation` parameter) rather than manual
quaternion composition — it produces the same "drag dx -> yaw, drag dy -> pitch"
arcball-style behavior the spec describes, with far less code, since the library's
own `Node.rotation` setter already does the Euler->quaternion conversion. Pitch is
clamped to ±89° to avoid the gimbal flip at the poles.

## Asset / instance caching

`ModelCache` (in `MainActivity.kt`) loads each unique filename through
`modelLoader.createModel("models/$name", releaseSourceData = false)` **once**,
caches the resulting `Model` (`FilamentAsset`) by filename, and computes its
normalized scale and label map at that point. `releaseSourceData = false` is
required so the model can be re-instanced later — gltfio's own docs say
`createInstance` "cannot be called after `FilamentAsset#releaseSourceData()`".

Every subsequent "Add model" of an already-loaded file calls
`modelLoader.createInstance(cachedModel)` — a cheap glTF instance that shares the
already-loaded vertex/index buffers and textures with the primary instance, instead
of re-parsing and re-uploading the whole file. This is the real win for
Fiagena.glb/solarsystem.glb (5+MB, many meshes): adding a second copy costs a small
transform-hierarchy allocation, not another multi-megabyte GPU upload.

**Closing one instance while siblings remain doesn't free GPU memory, by design —
and that's correct, not a leak.** gltfio has no `destroyInstance()` — its own
`ModelLoader.kt` docs state this explicitly ("gltfio favors flat arrays for storage
of entity lists ... instances can be recycled by removing and re-adding them"), so
the only teardown primitive is destroying the whole shared `Model`
(`modelLoader.destroyModel`), which is exactly what would free the memory still
backing a sibling on-screen instance. `ModelCache` therefore reference-counts at the
**file** level: closing a model decrements `activeInstanceCount`; the whole
`Model` (all its vertex/index/texture GPU memory) is destroyed only when the last
on-screen instance of that file is closed. Removing a `ModelNode` from the Compose
tree still immediately drops it from the Filament `Scene` (it stops being rendered
and costing per-frame draw calls); only the GPU buffers persist until the file's
refcount hits zero.

## Render cost cuts

`SceneView(renderQuality = RenderQuality.Performance, ...)` is the library's own
low-end preset: shadows off, SSAO off, bloom off, FXAA instead of MSAA, dynamic
resolution on. This is a direct match for the spec's "disable shadows,
bloom/DOF/other post-processing, heavy AA" — using it instead of hand-tuning each
`View` flag is one line and exactly as effective.

`fillLightNode = null` — one directional light (the default `mainLightNode`) is
enough for a floating-UI model viewer; no reason to pay for a second light's
shading pass.

`environment = rememberEnvironment(engine)` (the `engine`-only overload, not the
`environmentLoader` overload SceneView defaults to) — the library's own default
environment decodes a bundled neutral IBL KTX file for indirect lighting;
overriding it to the `engine`-only overload gives a flat solid-color skybox with no
indirect light and skips that decode/upload entirely, matching the spec's "cheap/no
IBL + flat/solid background is fine, skip HDR skybox decode+upload".

`cameraManipulator = null` and `onGestureListener = null` — the library's built-in
orbit-camera gesture handling and per-node tap forwarding are switched off
entirely; every touch is owned by this app's own Compose `pointerInput` /
`detectTransformGestures` on each model's overlay `Box`, so there's no competing
gesture recognizer running underneath.

## Label pipeline — one deviation from the spec, and why

The spec's description assumes Filament's gltfio loader does not expose glTF
`extras`, and describes parsing the raw GLB JSON chunk by hand (12-byte header,
`org.json.JSONObject` over chunk 0) to build a `nodeName -> label` map.

That assumption doesn't hold for the pinned dependency version actually in use:
`gltfio-android:1.72.1`'s `FilamentAsset` has a public
`getExtras(entity): String?` (verified directly against
`google/filament` tag `v1.72.1`, `FFilamentAsset::getExtras` →
`mNodeManager->getExtras(...)`). So this app builds the `nodeName -> label` map
directly from that native accessor, once per cached file, from the primary
instance's entities:

```kotlin
for (entity in model.instance.entities) {
    val extras = model.getExtras(entity) ?: continue      // native gltfio accessor
    val name = model.getName(entity) ?: continue           // NameComponentManager
    val prop = JSONObject(extras).optString("prop")        // org.json — still no new dependency
    if (prop.isNotBlank()) map[name] = prop
}
```

`org.json` is still used (exactly as the spec intended — no new dependency), just to
pull `"prop"` out of the small per-node extras string gltfio already handed back,
instead of to parse the whole file's JSON chunk by hand. This is strictly less code
and more robust (no manual GLB chunk/byte-offset handling), and it keeps the part of
the design the spec actually cares about: **matching by glTF node name**
(`FilamentAsset.getName(entity)`) against each *instance's own* entities, because a
second/third on-screen copy of the same file gets its own fresh entity IDs (that's
the whole point of gltfio instancing) — `getExtras` is only ever read once, from the
primary instance, to build the name→label map; every placed instance (primary or
not) is matched into that map purely by name via
`labelAnchorsFor(model, instance, nodeNameToLabel)`.

Everything else about the label pipeline matches the spec: per-frame cost is fully
gated behind "does any on-screen model currently have labels on" (checked first
thing in `onFrame`, before touching any matrices), driven by `SceneView`'s own
`onFrame` callback (itself driven by Filament's Choreographer-backed render loop,
not a Compose recomposition timer), and the actual projection is a manual
world->clip->screen transform using `Camera.getViewMatrix`/`getProjectionMatrix`
and the raw `TransformManager.getWorldTransform(instance, floatArray)` call with a
single set of reused `FloatArray` scratch buffers (`viewMatrix`, `projMatrixF`,
`viewProj`, `worldMatrix`, `clip`) — no per-label, per-frame allocation beyond the
final small `List<LabelDraw>` handed to the Compose `Canvas` overlay. Labels and
their connector lines are drawn in one `Canvas` pass, not as per-label Composables.

## What was skipped / simplified, and why

- Manual GLB JSON-chunk parsing — replaced by gltfio's native `getExtras`, see above.
- Quaternion arcball math — replaced by `Rotation(x=pitch, y=yaw)` Euler angles fed
  through the library's own reactive `rotation` parameter; same user-visible
  behavior, far less code, at the cost of a (harmless, clamped) gimbal limit at the
  poles that a real arcball wouldn't have.
- No bottom sheet for "Add model" — a `DropdownMenu` anchored to the button; the
  spec explicitly allows either, and a dropdown is less code for a flat list of 5
  filenames.
- No bounds-clamping on drag (a model can be dragged fully off-screen) — the spec
  only asks that drag move the container "anywhere on the full screen"; clamping
  wasn't requested and would be one more knob to maintain.

## Performance — how this was actually verified

- `./gradlew assembleDebug` builds clean (see below for the one real fix required:
  the project's original Kotlin version, 2.2.10, could not read the Kotlin 2.4.x
  metadata that `sceneview:4.38.0`'s transitive `kotlin-stdlib` ships; bumped the
  project to Kotlin 2.4.20 — the newest available at build time — to fix a hard
  compiler error, not a style choice).
- Installed on the attached physical device (`adb install -r`) and smoke-tested:
  app launches without crashing; added multiple models including the two large
  stress-test files (Fiagena.glb, solarsystem.glb); toggled labels on and confirmed
  via `adb logcat` / a pulled `screencap` that label text renders near the model
  rather than crashing or silently producing zero labels.
- Watched `adb logcat` throughout for crashes/ANRs/OOM while repeatedly
  adding/closing models (including the same large file multiple times in a row) to
  sanity-check the instance-caching/destroy path doesn't leak or crash.
- What this did **not** include: a formal `gfxinfo`/systrace frame-time capture, or
  a long-duration soak test. Multi-touch pinch/rotate gestures cannot be scripted
  through `adb shell input` (it only synthesizes single-pointer events), so the
  interaction-mode pinch-to-zoom and two-finger paths were verified by code review
  only, not by an on-device multi-touch trace — see the final report for the exact
  breakdown of what was and wasn't exercised on the device.
