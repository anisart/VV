package ru.anisart.vv

import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap

class MapActivity : AppCompatActivity() {

    @BindView(R.id.mapView)
    lateinit var mapView: MapView

    lateinit var map: MapboxMap
    lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        mapView = findViewById(R.id.mapView) as MapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(56.423040537284166, 44.65388874240455))
                    .zoom(6.0)
                    .build()
        }
    }

    fun onButtonClick(v: View) {
        val explorerTiles = preferences
                .getStringSet(App.PREFERENCE_TILES, HashSet())
                .toList()
        Toast.makeText(this, explorerTiles.toString(), Toast.LENGTH_LONG).show()
    }
}
