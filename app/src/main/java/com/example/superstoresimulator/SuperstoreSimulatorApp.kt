package com.example.superstoresimulator

import android.app.Application
import com.example.superstoresimulator.domain.research.ResearchGates
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SuperstoreSimulatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fail fast if any non-item research gate points at an upgrade id that no
        // longer exists in the registry (see ResearchGates).
        ResearchGates.validate()
    }
}
