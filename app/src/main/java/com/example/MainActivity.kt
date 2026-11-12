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

    override fun onResume() {
        super.onResume()
        // Coming back to the app counts as being active and starts the online heartbeat (only after login).
        val repository = (application as MarryMatchApplication).repository
        repository.onAppActive()
        // Received photos, voice messages and videos download per Settings > Chat media.
        if (repository.authState.value == com.example.data.AuthState.LOGGED_IN) {
            com.example.data.media.ChatMediaManager.sync(this, null)
        }
    }

    override fun onPause() {
        super.onPause()
        // Not on screen any more: the online heartbeat stops.
        (application as MarryMatchApplication).repository.onAppInactive()
    }
}
