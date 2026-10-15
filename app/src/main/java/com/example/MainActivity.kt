package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.MarryMatchRepository
import com.example.ui.MainApp
import com.example.ui.theme.MarryMatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MarryMatchApplication
        setContent {
            MarryMatchTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp(repository = app.repository)
                }
            }
        }
    }
}
