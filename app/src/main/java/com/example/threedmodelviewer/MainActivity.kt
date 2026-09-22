package com.example.threedmodelviewer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.threedmodelviewer.ui.ModelGallery
import com.example.threedmodelviewer.ui.theme.ThreeDModelViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lightBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBarStyle, navigationBarStyle = lightBarStyle)
        setContent {
            ThreeDModelViewerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ModelGallery()
                }
            }
        }
    }
}
