package com.example.threedmodelviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threedmodelviewer.R
import com.example.threedmodelviewer.util.modelAvatarPalette

/** Dark, rounded replacement for the default DropdownMenu, with a colored initial avatar per model. */
@Composable
fun ModelPickerMenu(
    expanded: Boolean,
    availableFiles: List<String>,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 220.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF1C1B1F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(
            text = stringResource(R.string.pick_a_model),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        availableFiles.forEachIndexed { index, fileName ->
            val name = fileName.removeSuffix(".glb")
            DropdownMenuItem(
                text = { Text(name, color = Color.White) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(modelAvatarPalette[index % modelAvatarPalette.size]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = name.take(1).uppercase(), color = Color.White, fontSize = 14.sp)
                    }
                },
                colors = MenuDefaults.itemColors(textColor = Color.White),
                onClick = {
                    onDismissRequest()
                    onSelect(fileName)
                }
            )
        }
    }
}
