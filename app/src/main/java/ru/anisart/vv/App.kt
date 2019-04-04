package ru.anisart.vv

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

class App : Application() {

    companion object {
        const val EXPLORER_ZOOM = 14
        const val PREFERENCE_TILES = "explorer_tiles"
        const val PREFERENCE_MAX_SQUARES = "max_squares"
        const val PREFERENCE_RIDES_JSON = "rides_json"
        const val PREFERENCE_HEATMAP_AUTH = "heatmap_auth"
    }

    private val PUBLIC_KEY = "pk.eyJ1IjoiYW5pc2FydCIsImEiOiJjaWtsMHNuZWswMDZqdm1tNmYydWl6M2pvIn0.OFsPC78TmKIRtp9WhlsN_w"

    override fun onCreate() {
        super.onCreate()

        Mapbox.getInstance(applicationContext, PUBLIC_KEY)
    }
}