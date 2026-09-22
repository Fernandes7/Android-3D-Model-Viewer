package com.example.threedmodelviewer.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.ModelNode as ModelNodeImpl
import org.json.JSONObject

/** A glTF node whose `extras.prop` supplied label text, paired with that text. */
class NodeLabel(val node: Node, val text: String)

/** Converts a screen-space point (e.g. a container's center) to the SceneView world position directly below it. */
fun screenToWorld(px: Offset, viewSize: IntSize): Position = Position(
    x = (px.x - viewSize.width / 2f) * UNITS_PER_PIXEL,
    y = (viewSize.height / 2f - px.y) * UNITS_PER_PIXEL,
    z = 0f
)

// Labels come from each glTF node's extras.prop (see AssetLoader docs); nodes without it are skipped.
fun <T> Iterable<T>.toNodeLabels(): List<NodeLabel> where T : Node, T : ModelNodeImpl.ChildNode =
    mapNotNull { child ->
        child.extras
            ?.let { runCatching { JSONObject(it).optString("prop") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> NodeLabel(child, text) }
    }
