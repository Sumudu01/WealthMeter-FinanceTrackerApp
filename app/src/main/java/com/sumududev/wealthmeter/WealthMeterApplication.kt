package com.sumududev.wealthmeter

import android.app.Application
import com.sumududev.wealthmeter.data.AppDatabase

class WealthMeterApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Initialize any other dependencies here
    }
} 