package io.github.lobadzip.strela

import android.app.Application
import io.github.lobadzip.strela.app.AppGraph
import io.github.lobadzip.strela.app.platform.AndroidPlatform

/** Holds the object graph for the process, so the session survives activity recreation. */
class StrelaApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AndroidPlatform.createGraph(this, BuildConfig.DEFAULT_SERVER_URL)
    }
}
