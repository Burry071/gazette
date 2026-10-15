package com.gazette

import android.app.Application
import com.gazette.engine.LedgerEngine
import com.gazette.engine.ModelRuntime

class GazetteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerEngine.init(this)
        ModelRuntime.start(this)
    }
}
