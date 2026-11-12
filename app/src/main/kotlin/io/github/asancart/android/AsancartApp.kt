package io.github.asancart.android

import android.app.Application

class AsancartApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // REQ-APP-011: every start re-scans the inbox from the last checkpoint.
        container.capture.start()
    }
}
