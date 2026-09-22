package com.example.threedmodelviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threedmodelviewer.R
import com.example.threedmodelviewer.util.CONTAINER_SIZE_DP
import com.example.threedmodelviewer.util.MAX_ZOOM
import com.example.threedmodelviewer.util.MIN_CONTAINER_DP
import com.example.threedmodelviewer.util.MIN_ZOOM
import com.example.threedmodelviewer.util.NodeLabel
import com.example.threedmodelviewer.util.ROTATE_SENSITIVITY
import com.example.threedmodelviewer.util.UNITS_PER_PIXEL
import com.example.threedmodelviewer.util.screenToWorld
import com.example.threedmodelviewer.util.toNodeLabels
import com.google.android.filament.Camera
import io.github.sceneview.ExperimentalSceneViewApi
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.environment.EnvironmentPresets
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.halfExtentSize
import io.github.sceneview.model.Model
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import kotlin.math.max
import kotlin.math.roundToInt

/** A model instance placed in the gallery, with its on-screen transform and interaction state. */
private class PlacedModel(val id: Long, val model: Model, val normalizedScale: Float) {
    var centerPx by mutableStateOf(Offset.Zero)
    var sizePx by mutableFloatStateOf(0f)
    var interactionMode by mutableStateOf(false)
    var labelsVisible by mutableStateOf(false)
    var yawDeg by mutableFloatStateOf(0f)
    var pitchDeg by mutableFloatStateOf(0f)
    var userZoom by mutableFloatStateOf(1f)
    var labels: List<NodeLabel> = emptyList()
}

/** A label's projected screen anchor (on the part) and text position (offset for the connector line). */
private data class LabelPlacement(val anchor: Offset, val textPos: Offset, val text: String)

@OptIn(ExperimentalSceneViewApi::class)
@Composable
fun ModelGallery() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val mainLightNode = rememberMainLightNode(engine)
    val fillLightNode = rememberFillLightNode(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = remember(environmentLoader) {
        environmentLoader.createKTX1Environment(
            EnvironmentPresets.NEUTRAL,
            "environments/neutral/neutral_skybox.ktx"
        )
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Orthographic projection sized to the viewport, in the same UNITS_PER_PIXEL space used to place models.
    LaunchedEffect(viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            val halfW = (viewSize.width / 2f * UNITS_PER_PIXEL).toDouble()
            val halfH = (viewSize.height / 2f * UNITS_PER_PIXEL).toDouble()
            cameraNode.position = Position(z = 10f)
            cameraNode.rotation = Rotation(0f, 0f, 0f)
            cameraNode.setProjection(Camera.Projection.ORTHO, -halfW, halfW, -halfH, halfH, 0.1, 100.0)
        }
    }

    val availableFiles = remember {
        context.assets.list("models")?.filter { it.endsWith(".glb") }?.sorted() ?: emptyList()
    }
    val models = remember { mutableStateListOf<PlacedModel>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var menuExpanded by remember { mutableStateOf(false) }
    val defaultContainerPx = with(density) { CONTAINER_SIZE_DP.dp.toPx() }
    val minContainerPx = with(density) { MIN_CONTAINER_DP.dp.toPx() }
    val labelOffsetPx = with(density) { 28.dp.toPx() }
    var labelPlacements by remember { mutableStateOf<List<LabelPlacement>>(emptyList()) }

    // Re-projects every visible label's world position through the camera each frame, so labels
    // stay glued to their part while a model is rotated/zoomed. Skips the work entirely when no
    // model has labels toggled on, which is the common case while dragging.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val visibleModels = models.filter { it.labelsVisible }
                if (visibleModels.isEmpty()) {
                    if (labelPlacements.isNotEmpty()) labelPlacements = emptyList()
                } else {
                    labelPlacements = visibleModels.flatMap { placed ->
                        placed.labels.mapNotNull { label ->
                            cameraNode.worldToView(label.node.worldPosition)?.let { view ->
                                val anchor = Offset(view.x * viewSize.width, (1f - view.y) * viewSize.height)
                                LabelPlacement(anchor, Offset(anchor.x + labelOffsetPx, anchor.y - labelOffsetPx), label.text)
                            }
                        }
                    }
                }
            }
        }
    }

    fun addModel(fileName: String) {
        val model = modelLoader.createModel("models/$fileName")
        val half = model.boundingBox.halfExtentSize
        val maxExtent = max(half.x, max(half.y, half.z)) * 2f
        val normalizedScale = if (maxExtent > 0f) 1f / maxExtent else 1f
        val placed = PlacedModel(nextId++, model, normalizedScale)
        placed.sizePx = defaultContainerPx
        val stagger = (models.size % 5) * (defaultContainerPx * 0.15f)
        placed.centerPx = Offset(viewSize.width / 2f + stagger, viewSize.height / 2f + stagger)
        models += placed
    }

    fun closeModel(placed: PlacedModel) {
        models -= placed
        modelLoader.destroyModel(placed.model)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it },
            engine = engine,
            modelLoader = modelLoader,
            environment = environment,
            cameraNode = cameraNode,
            mainLightNode = mainLightNode,
            fillLightNode = fillLightNode,
            cameraManipulator = null,
            autoCenterContent = false,
            // Disables shadows/SSAO/bloom and enables dynamic resolution — required to hold
            // frame rate with several models loaded on low-end GPUs.
            renderQuality = RenderQuality.Performance,
        ) {
            for (placed in models) {
                ModelNode(
                    modelInstance = placed.model.instance,
                    position = screenToWorld(placed.centerPx, viewSize),
                    rotation = Rotation(x = placed.pitchDeg, y = placed.yawDeg, z = 0f),
                    scale = Scale(placed.normalizedScale * placed.sizePx * UNITS_PER_PIXEL * placed.userZoom),
                    apply = { placed.labels = renderableNodes.toNodeLabels() + emptyNodes.toNodeLabels() }
                )
            }
        }

        for (placed in models) {
            val sizeDp = with(density) { placed.sizePx.toDp() }
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .graphicsLayer {
                        translationX = placed.centerPx.x - placed.sizePx / 2
                        translationY = placed.centerPx.y - placed.sizePx / 2
                    }
                    .pointerInput(placed.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (placed.interactionMode) {
                                // Interaction mode: pan rotates the model, pinch zooms it in place.
                                placed.yawDeg += pan.x * ROTATE_SENSITIVITY
                                placed.pitchDeg = (placed.pitchDeg - pan.y * ROTATE_SENSITIVITY).coerceIn(-89f, 89f)
                                placed.userZoom = (placed.userZoom * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            } else {
                                // Placement mode: pan moves the container, pinch resizes it, both clamped to the viewport.
                                val halfSize = placed.sizePx / 2
                                val maxX = (viewSize.width - halfSize).coerceAtLeast(halfSize)
                                val maxY = (viewSize.height - halfSize).coerceAtLeast(halfSize)
                                placed.centerPx = Offset(
                                    (placed.centerPx.x + pan.x).coerceIn(halfSize, maxX),
                                    (placed.centerPx.y + pan.y).coerceIn(halfSize, maxY)
                                )
                                val maxContainerPx = minOf(viewSize.width, viewSize.height) * 0.9f
                                placed.sizePx = (placed.sizePx * zoom).coerceIn(minContainerPx, maxContainerPx)
                            }
                        }
                    }
            )
        }

        for (placed in models) {
            val sizeDp = with(density) { placed.sizePx.toDp() }
            Box(
                modifier = Modifier
                    .size(width = sizeDp, height = 44.dp)
                    .offset {
                        IntOffset(
                            (placed.centerPx.x - placed.sizePx / 2).roundToInt(),
                            (placed.centerPx.y + placed.sizePx / 2 + with(density) { 8.dp.toPx() }).roundToInt()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelControlButton(R.drawable.interact, placed.interactionMode) { placed.interactionMode = !placed.interactionMode }
                    ModelControlButton(R.drawable.label, placed.labelsVisible) { placed.labelsVisible = !placed.labelsVisible }
                    ModelControlButton(R.drawable.close, false) { closeModel(placed) }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            labelPlacements.forEach { label ->
                drawLine(Color.Yellow, label.anchor, label.textPos, strokeWidth = 2.dp.toPx())
                drawCircle(Color.Yellow, radius = 4.dp.toPx(), center = label.anchor)
            }
        }
        for (label in labelPlacements) {
            Text(
                text = label.text,
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .offset { IntOffset(label.textPos.x.roundToInt(), label.textPos.y.roundToInt()) }
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Button(onClick = { menuExpanded = true }) {
                Text(stringResource(R.string.add_model))
            }
            ModelPickerMenu(
                expanded = menuExpanded,
                availableFiles = availableFiles,
                onDismissRequest = { menuExpanded = false },
                onSelect = { fileName -> addModel(fileName) }
            )
        }
    }
}
