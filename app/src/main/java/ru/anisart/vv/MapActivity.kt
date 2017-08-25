package ru.anisart.vv

import android.Manifest
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnCheckedChanged
import butterknife.OnClick
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class MapActivity : AppCompatActivity() {

    val explorerZoom = 14

    class BBox(val north: Double, val south: Double, val west: Double, val east: Double)

    @BindView(R.id.mapView)
    lateinit var mapView: MapView

    lateinit var map: MapboxMap
    lateinit var preferences: SharedPreferences

    var explorerPolygonOptions = ArrayList<PolygonOptions>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        mapView = findViewById(R.id.mapView) as MapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
//            map.cameraPosition = CameraPosition.Builder()
//                    .target(LatLng(56.423040537284166, 44.65388874240455))
//                    .zoom(6.0)
//                    .build()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MapActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    @OnCheckedChanged(R.id.explorerButton)
    fun onExplorerButtonClick(button: CompoundButton, checked: Boolean) {
        if (explorerPolygonOptions.size == 0) {
            val explorerTiles = preferences
                    .getStringSet(App.PREFERENCE_TILES, HashSet())
                    .toList()

            if (explorerTiles.isEmpty()) {
                button.isChecked = false
                Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()
                return
            }

            for (tile in explorerTiles) {
                val xy = tile.split("-")
                if (xy.size != 2) continue

                val bbox = tile2boundingBox(xy[0].toInt(), xy[1].toInt(), explorerZoom)
                val polygonOptions = PolygonOptions()
                        .add(LatLng(bbox.north, bbox.west))
                        .add(LatLng(bbox.north, bbox.east))
                        .add(LatLng(bbox.south, bbox.east))
                        .add(LatLng(bbox.south, bbox.west))
                        .add(LatLng(bbox.north, bbox.west))
                        .strokeColor(Color.parseColor("#00FF00"))
                        .fillColor(Color.parseColor("#2000FF00"))
                explorerPolygonOptions.add(polygonOptions)
            }
        }
        if (checked) {
            map.addPolygons(explorerPolygonOptions)
        } else {
            for (polygonOption in explorerPolygonOptions) {
                map.removePolygon(polygonOption.polygon)
            }
        }
    }

    @OnCheckedChanged(R.id.ridesButton)
    fun onRidesButtonClick(button: CompoundButton, checked: Boolean) {
        val layer = map.getLayer("rides_layer")
        if (layer != null) {
            layer.setProperties(PropertyFactory.visibility(if (checked) Property.VISIBLE else Property.NONE))
        } else if (checked) {
            val geoJson = preferences.getString(App.PREFERENCE_RIDES_JSON, "")

            if (geoJson.isEmpty()) {
                button.isChecked = false
                Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()
                return
            }

            val gjSource = GeoJsonSource("rides", geoJson)
            map.addSource(gjSource)
            map.addLayer(LineLayer("rides_layer", "rides")
                    .withProperties(
                            PropertyFactory.lineColor(Color.RED)
                    ))
        }
    }

    @OnClick(R.id.myLocationButton)
    fun onLocationButtonClick(v: View) {
        MapActivityPermissionsDispatcher.getLastLocationWithCheck(this)
    }

    fun tile2lon(x: Int, z: Int): Double {
        return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
    }

    fun tile2lat(y: Int, z: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y.toDouble() / Math.pow(2.0, z.toDouble())
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }

    fun tile2boundingBox(x: Int, y: Int, zoom: Int): BBox {
        val north = tile2lat(y, zoom)
        val south = tile2lat(y + 1, zoom)
        val west = tile2lon(x, zoom)
        val east = tile2lon(x + 1, zoom)
        return BBox(north, south, west, east)
    }

    fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.getLatitude(), location.getLongitude()), explorerZoom.toDouble()))
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getLastLocation() {
        System.err.println("getLastLocation ${map.myLocation}")
        if (map.myLocation != null) {
            setCameraPosition(map.myLocation as Location)
        } else {
            map.isMyLocationEnabled = true
            map.setOnMyLocationChangeListener { location -> run {
                setCameraPosition(location as Location)
                map.setOnMyLocationChangeListener {  }
            } }

        }
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationPermissionDenied() {
        Toast.makeText(this, "Permission is required to show your location!", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationNeverAskAgain() {
        Toast.makeText(this, "Check permissions for app in System Settings!", Toast.LENGTH_SHORT).show()
    }
}
