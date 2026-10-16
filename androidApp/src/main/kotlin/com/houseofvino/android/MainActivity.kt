package com.houseofvino.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.houseofvino.ui.HouseOfVinoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as HouseOfVinoApplication).graph
        setContent { HouseOfVinoApp(graph) }
    }
}
