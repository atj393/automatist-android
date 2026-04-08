package com.synapse.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.synapse.app.ui.navigation.SynapseNavGraph
import com.synapse.app.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    // Observable state so Compose reacts to onNewIntent deep links
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialSharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        val navigateTo = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        Log.d(TAG, "onCreate: action=${intent?.action}, navigateTo=$navigateTo")

        // Set initial deep link from the launching intent
        pendingDeepLink.value = navigateTo

        setContent {
            SynapseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SynapseNavGraph(
                        initialSharedText = initialSharedText,
                        pendingDeepLink = pendingDeepLink.value,
                        onDeepLinkConsumed = { pendingDeepLink.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navigateTo = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        Log.d(TAG, "onNewIntent: navigateTo=$navigateTo")
        if (navigateTo != null) {
            // Update the observable state — Compose will recompose and navigate
            pendingDeepLink.value = navigateTo
        }
    }
}
