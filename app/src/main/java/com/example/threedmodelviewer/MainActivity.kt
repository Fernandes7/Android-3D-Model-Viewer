package com.example.threedmodelviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.threedmodelviewer.ui.theme.ThreeDModelViewerTheme
import com.google.android.filament.Camera
import io.github.sceneview.ExperimentalSceneViewApi
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThreeDModelViewerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ModelGallery()
                }
            }
        }
    }
}

private const val UNITS_PER_PIXEL = 1f / 800f
private const val CONTAINER_SIZE_DP = 220
private const val MIN_CONTAINER_DP = 80
private class PlacedModel(val id: Long, val model: Model, val normalizedScale: Float) {
    var centerPx by mutableStateOf(Offset.Zero)
    var sizePx by mutableFloatStateOf(0f)
}

private fun screenToWorld(px: Offset, viewSize: IntSize): Position = Position(
    x = (px.x - viewSize.width / 2f) * UNITS_PER_PIXEL,
    y = (viewSize.height / 2f - px.y) * UNITS_PER_PIXEL,
    z = 0f
)

@OptIn(ExperimentalSceneViewApi::class)
@Composable
private fun ModelGallery() {
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

    fun addModel(fileName: String) {
        val model = modelLoader.createModel("models/$fileName", releaseSourceData = false)
        val half = model.boundingBox.halfExtentSize
        val maxExtent = max(half.x, max(half.y, half.z)) * 2f
        val normalizedScale = if (maxExtent > 0f) 1f / maxExtent else 1f
        val placed = PlacedModel(nextId++, model, normalizedScale)
        placed.sizePx = defaultContainerPx
        val stagger = (models.size % 5) * (defaultContainerPx * 0.15f)
        placed.centerPx = Offset(viewSize.width / 2f + stagger, viewSize.height / 2f + stagger)
        models += placed
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
        ) {
            for (placed in models) {
                ModelNode(
                    modelInstance = placed.model.instance,
                    position = screenToWorld(placed.centerPx, viewSize),
                    scale = Scale(placed.normalizedScale * placed.sizePx * UNITS_PER_PIXEL)
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
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
            Button(onClick = { menuExpanded = true }) {
                Text("Add model")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                availableFiles.forEach { fileName ->
                    DropdownMenuItem(
                        text = { Text(fileName.removeSuffix(".glb")) },
                        onClick = {
                            menuExpanded = false
                            addModel(fileName)
                        }
                    )
                }
            }
        }
    }
}
