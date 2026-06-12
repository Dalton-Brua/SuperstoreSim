package com.example.superstoresimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.ui.root.SuperstoreApp
import com.example.superstoresimulator.ui.viewmodels.GameViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var itemDao: ItemDao

    // Keep reference to ViewModel to save on lifecycle events
    private var gameViewModel: GameViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SuperstoreSimulatorTheme(
                dynamicColor = false  // Disable dynamic theming for consistent colors across all devices
            ) {
                // Instantiate ViewModel (Hilt handles dependency injection automatically)
                val viewModel: GameViewModel = viewModel()
                gameViewModel = viewModel // Keep reference for lifecycle callbacks
                SuperstoreApp(viewModel = viewModel, itemDao = itemDao)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        gameViewModel?.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        gameViewModel?.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        // Additional save on stop as a safety measure
        gameViewModel?.saveGameState()
    }
}
