package com.example.threedmodelviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.threedmodelviewer.ui.theme.ThreeDModelViewerTheme
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

private const val ROTATE_SENSITIVITY = 0.4f
private const val GRID_SPACING = 1.3f
private const val GRID_COLUMNS = 3

/** One model instance placed in the shared scene: its own Filament model plus a fixed grid slot. */
private class PlacedModel(val id: Long, val model: Model, val normalizedScale: Float, val position: Position)

@OptIn(ExperimentalSceneViewApi::class)
@Composable
private fun ModelGallery() {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        cameraNode.position = Position(z = 14f)
    }

    val availableFiles = remember {
        context.assets.list("models")?.filter { it.endsWith(".glb") }?.sorted() ?: emptyList()
    }
    val models = remember { mutableStateListOf<PlacedModel>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun addModel(fileName: String) {
        val model = modelLoader.createModel("models/$fileName", releaseSourceData = false)
        val half = model.boundingBox.halfExtentSize
        val maxExtent = max(half.x, max(half.y, half.z)) * 2f
        val normalizedScale = if (maxExtent > 0f) 1f / maxExtent else 1f
        val slot = models.size
        val col = slot % GRID_COLUMNS
        val row = slot / GRID_COLUMNS
        val position = Position(
            x = (col - (GRID_COLUMNS - 1) / 2f) * GRID_SPACING,
            y = -row * GRID_SPACING,
            z = 0f
        )
        models += PlacedModel(nextId++, model, normalizedScale, position)
    }

    var yawDeg by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        yawDeg += dragAmount.x * ROTATE_SENSITIVITY
                        pitchDeg = (pitchDeg - dragAmount.y * ROTATE_SENSITIVITY).coerceIn(-89f, 89f)
                    }
                },
            engine = engine,
            modelLoader = modelLoader,
            environment = environment,
            cameraNode = cameraNode,
            mainLightNode = mainLightNode,
            fillLightNode = fillLightNode,
            cameraManipulator = null,
        ) {
            for (placed in models) {
                ModelNode(
                    modelInstance = placed.model.instance,
                    position = placed.position,
                    rotation = Rotation(x = pitchDeg, y = yawDeg, z = 0f),
                    scale = Scale(placed.normalizedScale)
                )
            }
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
