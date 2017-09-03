package ru.anisart.vv

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

class App : Application() {

    companion object {
        val PREFERENCE_TILES = "explorer_tiles"
        val PREFERENCE_CLUSTER_TILES = "cluster_tiles"
        val PREFERENCE_RIDES_JSON = "rides_json"
    }

    private val PUBLIC_KEY = "pk.eyJ1IjoiYW5pc2FydCIsImEiOiJjaWtsMHNuZWswMDZqdm1tNmYydWl6M2pvIn0.OFsPC78TmKIRtp9WhlsN_w"

    override fun onCreate() {
        super.onCreate()

        Mapbox.getInstance(applicationContext, PUBLIC_KEY)
    }
}