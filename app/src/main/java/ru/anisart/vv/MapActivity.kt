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
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
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
import java.util.*
import kotlin.collections.ArrayList

@RuntimePermissions
class MapActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {

    private val explorerZoom = 14

    class BBox(val north: Double, val south: Double, val west: Double, val east: Double)

    @BindView(R.id.mapView)
    lateinit var mapView: MapView
    @BindView(R.id.explorerButton)
    lateinit var explorerButton: ToggleButton
    @BindView(R.id.ridesButton)
    lateinit var ridesButton: ToggleButton
    @BindView(R.id.gridButton)
    lateinit var gridButton: ToggleButton
    @BindView(R.id.debug)
    lateinit var debugView: TextView

    lateinit var map: MapboxMap
    lateinit var preferences: SharedPreferences

    var explorerPolygonOptions = ArrayList<PolygonOptions>()
    var gridPolylineOptions = ArrayList<PolylineOptions>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            map = it

            val explorerTiles = preferences
                    .getStringSet(App.PREFERENCE_TILES, HashSet())
                    .toList()

            explorerTiles
                    .map { it.split("-") }
                    .filter { it.size == 2 }
                    .map { tile2boundingBox(it[0].toInt(), it[1].toInt(), explorerZoom) }
                    .map {
                        PolygonOptions()
                                .add(LatLng(it.north, it.west))
                                .add(LatLng(it.north, it.east))
                                .add(LatLng(it.south, it.east))
                                .add(LatLng(it.south, it.west))
                                .add(LatLng(it.north, it.west))
                                .strokeColor(Color.parseColor("#00FF00"))
                                .fillColor(Color.parseColor("#2000FF00"))
                    }
                    .forEach { explorerPolygonOptions.add(it) }
            if (!explorerPolygonOptions.isEmpty()) {
                val bounds = LatLngBounds.Builder()
                explorerPolygonOptions.forEach { bounds.includes(it.polygon.points) }
                map.cameraPosition = CameraUpdateFactory.newLatLngBounds(bounds.build(), 10)
                        .getCameraPosition(map)
            }

            val geoJson = preferences.getString(App.PREFERENCE_RIDES_JSON, "")
            if (!geoJson.isEmpty()) {
                val gjSource = GeoJsonSource("rides", geoJson)
                map.addSource(gjSource)
                map.addLayer(LineLayer("rides_layer", "rides")
                        .withProperties(
                                PropertyFactory.lineColor(Color.RED),
                                PropertyFactory.visibility(Property.NONE)
                        ))
            }

            if (savedInstanceState != null) {
                explorerButton.isChecked = false
                ridesButton.isChecked = false
            }
            explorerButton.setOnCheckedChangeListener(this)
            ridesButton.setOnCheckedChangeListener(this)
            gridButton.setOnCheckedChangeListener(this)

            map.setOnCameraIdleListener({
                if (BuildConfig.DEBUG) {
                    debugView.text = "z = %.2f, lat = %.2f, lon = %.2f".format(
                            Locale.US,
                            map.cameraPosition.zoom,
                            map.cameraPosition.target.latitude,
                            map.cameraPosition.target.longitude)
                }

//                if (explorerButton.isChecked) {
//                    val bounds = map.projection.visibleRegion.latLngBounds
//                    for (option in explorerPolygonOptions) {
//                        for (point in option.polygon.points) {
//                            if (bounds.contains(point)) {
//                                map.addPolygon(option)
//                            }
//                        }
//                    }
//                }
            })
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

    override fun onCheckedChanged(button: CompoundButton?, isChecked: Boolean) {
        when (button?.id) {
            R.id.explorerButton -> {
                if (explorerPolygonOptions.size == 0) {
                    button.isChecked = false
                    Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()
                    return
                }
                if (isChecked) {
                    map.addPolygons(explorerPolygonOptions)
                } else {
                    explorerPolygonOptions.forEach { map.removePolygon(it.polygon) }
                }
            }
            R.id.ridesButton -> {
                val layer = map.getLayer("rides_layer")
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()

                }
            }
            R.id.gridButton -> {
                if (isChecked) {
                    if (map.cameraPosition.zoom < 10) {
                        Toast.makeText(this, "Zoom is too small!", Toast.LENGTH_SHORT).show()
                        gridButton.isChecked = false
                        return
                    }

                    val bounds = map.projection.visibleRegion.latLngBounds
                    val x0 = lon2tile(bounds.lonWest, explorerZoom)
                    val x1 = lon2tile(bounds.lonEast, explorerZoom)
                    val y0 = lat2tile(bounds.latNorth, explorerZoom)
                    val y1 = lat2tile(bounds.latSouth, explorerZoom)

                    gridPolylineOptions.clear()
                    for (x in x0..x1) {
                        for (y in y0..y1) {
                            val bbox = tile2boundingBox(x, y, explorerZoom)
                            val polylineOptions = PolylineOptions()
                                    .add(LatLng(bbox.south, bbox.west))
                                    .add(LatLng(bbox.north, bbox.west))
                                    .add(LatLng(bbox.north, bbox.east))
                                    .color(Color.BLACK)
                                    .width(1f)
                            gridPolylineOptions.add(polylineOptions)
                        }
                    }
                    map.addPolylines(gridPolylineOptions)
                } else {
                    gridPolylineOptions.forEach { map.removePolyline(it.polyline) }
                }
            }
        }
    }

    @OnClick(R.id.myLocationButton)
    fun onLocationButtonClick(v: View) {
        MapActivityPermissionsDispatcher.getLastLocationWithCheck(this)
    }

    private fun tile2lon(x: Int, z: Int): Double {
        return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
    }

    private fun tile2lat(y: Int, z: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y.toDouble() / Math.pow(2.0, z.toDouble())
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }

    private fun tile2boundingBox(x: Int, y: Int, zoom: Int): BBox {
        val north = tile2lat(y, zoom)
        val south = tile2lat(y + 1, zoom)
        val west = tile2lon(x, zoom)
        val east = tile2lon(x + 1, zoom)
        return BBox(north, south, west, east)
    }

    private fun lon2tile(lon: Double, zoom: Int): Int {
        return Math.floor((lon + 180) / 360 * Math.pow(2.0, zoom.toDouble())).toInt()
    }

    private fun lat2tile(lat: Double, zoom: Int): Int {
        return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) + 1 / Math.cos(lat * Math.PI / 180)) / Math.PI)
                / 2 * Math.pow(2.0, zoom.toDouble())).toInt()
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), explorerZoom.toDouble()))
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
                map.setOnMyLocationChangeListener(null)
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
