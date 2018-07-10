package ru.anisart.vv

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

class App : Application() {

    companion object {
        val EXPLORER_ZOOM = 14
        val PREFERENCE_TILES = "explorer_tiles"
        val PREFERENCE_MAX_SQUARES = "max_squares"
        val PREFERENCE_RIDES_JSON = "rides_json"
        val PREFERENCE_HEATMAP_AUTH = "heatmap_auth"
    }

    private val PUBLIC_KEY = "pk.eyJ1IjoiYW5pc2FydCIsImEiOiJjaWtsMHNuZWswMDZqdm1tNmYydWl6M2pvIn0.OFsPC78TmKIRtp9WhlsN_w"

    override fun onCreate() {
        super.onCreate()

        Mapbox.getInstance(applicationContext, PUBLIC_KEY)
//        RxJava2Debug.enableRxJava2AssemblyTracking(arrayOf("ru.anisart.vv"))
    }
}