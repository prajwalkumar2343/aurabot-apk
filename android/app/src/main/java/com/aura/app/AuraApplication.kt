package com.aura.app

import android.app.Application

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Application.auraContainer: AppContainer
    get() = (this as AuraApplication).container
