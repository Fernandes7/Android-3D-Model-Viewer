package com.example.threedmodelviewer.util

import com.example.threedmodelviewer.ui.theme.Pink40
import com.example.threedmodelviewer.ui.theme.Pink80
import com.example.threedmodelviewer.ui.theme.Purple40
import com.example.threedmodelviewer.ui.theme.Purple80
import com.example.threedmodelviewer.ui.theme.PurpleGrey40

/** World units represented by one screen pixel; keeps model placement in sync with drag/zoom gestures. */
const val UNITS_PER_PIXEL = 1f / 800f

/** Default and minimum size (dp) of a model's draggable container. */
const val CONTAINER_SIZE_DP = 220
const val MIN_CONTAINER_DP = 80

/** Degrees of rotation applied per pixel of drag while in interaction mode. */
const val ROTATE_SENSITIVITY = 0.4f

/** Clamp range for a model's user-driven zoom multiplier. */
const val MIN_ZOOM = 0.3f
const val MAX_ZOOM = 3f

/** Colors cycled per row in [com.example.threedmodelviewer.ui.ModelPickerMenu]'s avatar badges. */
val modelAvatarPalette = listOf(Purple40, PurpleGrey40, Pink40, Purple80, Pink80)
