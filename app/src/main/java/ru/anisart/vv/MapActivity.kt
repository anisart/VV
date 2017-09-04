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
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.functions.Function
import com.mapbox.mapboxsdk.style.functions.stops.IdentityStops
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.services.commons.geojson.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@RuntimePermissions
class MapActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {

    private val explorerZoom = 14

    private val explorerSource = "explorer_source"
    private val ridesSource = "rides_source"
    private val gridSource = "grid_source"
    private val explorerLayer = "explorer_layer"
    private val ridesLayer = "rides_layer"
    private val gridLayer = "grid_layer"

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
            val clusterTiles = preferences
                    .getStringSet(App.PREFERENCE_CLUSTER_TILES, HashSet())

            val bounds = LatLngBounds.Builder()
            val features = ArrayList<Feature>()
            for (tile in explorerTiles) {
                val xy = tile.split("-")
                if (xy.size != 2) {
                    continue
                }
                val bbox = tile2boundingBox(xy[0].toInt(), xy[1].toInt(), explorerZoom)

                val feature = Feature.fromGeometry(Polygon.fromCoordinates(
                        arrayOf(
                                arrayOf(
                                        doubleArrayOf(bbox.west, bbox.north),
                                        doubleArrayOf(bbox.east, bbox.north),
                                        doubleArrayOf(bbox.east, bbox.south),
                                        doubleArrayOf(bbox.west, bbox.south),
                                        doubleArrayOf(bbox.west, bbox.north)))))
                bounds.includes(arrayListOf(
                        LatLng(bbox.north, bbox.west),
                        LatLng(bbox.north, bbox.east),
                        LatLng(bbox.south, bbox.east),
                        LatLng(bbox.south, bbox.west)))

                if (clusterTiles.contains(tile)) {
                    feature.addStringProperty("stroke", "#000099")
                    feature.addStringProperty("fill", "#0000FF")
                } else {
                    feature.addStringProperty("stroke", "#009900")
                    feature.addStringProperty("fill", "#00FF00")
                }
                feature.addNumberProperty("fill-opacity", 0.3f)
                features.add(feature)
            }

            val geoJson = preferences.getString(App.PREFERENCE_RIDES_JSON, "")
            if (geoJson.isNotEmpty()) {
                map.addSource(GeoJsonSource(ridesSource, geoJson))
                map.addLayer(LineLayer(ridesLayer, ridesSource)
                        .withProperties(
                                PropertyFactory.lineColor(Color.RED),
                                PropertyFactory.visibility(Property.NONE)
                        ))
            }

            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                map.addSource(GeoJsonSource(explorerSource, featureCollection))
                map.addLayer(FillLayer(explorerLayer, explorerSource)
                        .withProperties(
                                PropertyFactory.fillOutlineColor(Function.property("stroke", IdentityStops<String>())),
                                PropertyFactory.fillColor(Function.property("fill", IdentityStops<String>())),
                                PropertyFactory.fillOpacity(Function.property("fill-opacity", IdentityStops<Float>())),
                                PropertyFactory.visibility(Property.NONE)
                        ))
                map.cameraPosition = CameraUpdateFactory.newLatLngBounds(bounds.build(), 10)
                        .getCameraPosition(map)
            }

            map.addSource(GeoJsonSource(gridSource))
            val gridLayer = LineLayer(gridLayer, gridSource)
                    .withProperties(PropertyFactory.visibility(Property.NONE))
            gridLayer.minZoom = 10f
            map.addLayer(gridLayer)

            if (savedInstanceState != null) {
                explorerButton.isChecked = false
                ridesButton.isChecked = false
                gridButton.isChecked = false
            }
            explorerButton.setOnCheckedChangeListener(this)
            ridesButton.setOnCheckedChangeListener(this)
            gridButton.setOnCheckedChangeListener(this)

            mapView.addOnMapChangedListener {
                if (it in arrayOf(MapView.REGION_DID_CHANGE, MapView.REGION_DID_CHANGE_ANIMATED)) {
                    if (BuildConfig.DEBUG) {
                        debugInfo()
                    }
                    if (gridButton.isChecked) {
                        calculateGrid()
                    }
                }
            }
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
                val layer = map.getLayer(explorerLayer)
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()

                }
            }
            R.id.ridesButton -> {
                val layer = map.getLayer(ridesLayer)
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()

                }
            }
            R.id.gridButton -> {
                val layer = map.getLayer(gridLayer)
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                    if (isChecked) calculateGrid()
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No grid!", Toast.LENGTH_SHORT).show()

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

    private fun debugInfo() {
        debugView.text = "z = %.2f, lat = %.2f, lon = %.2f".format(
                Locale.US,
                map.cameraPosition.zoom,
                map.cameraPosition.target.latitude,
                map.cameraPosition.target.longitude)
    }

    private fun calculateGrid() {
        map.getLayer(gridLayer) ?: return
        if (map.cameraPosition.zoom < 9.9) return

        val bounds = map.projection.visibleRegion.latLngBounds
        val x0 = lon2tile(bounds.lonWest, explorerZoom)
        val x1 = lon2tile(bounds.lonEast, explorerZoom)
        val y0 = lat2tile(bounds.latNorth, explorerZoom)
        val y1 = lat2tile(bounds.latSouth, explorerZoom)
        val gridLines = ArrayList<Feature>()
        for (x in x0-1..x1+1) {
            for (y in y0-1..y1+1) {
                val bbox = tile2boundingBox(x, y, explorerZoom)
                val feature = Feature.fromGeometry(LineString.fromCoordinates(
                        arrayOf(
                                doubleArrayOf(bbox.west, bbox.south),
                                doubleArrayOf(bbox.west, bbox.north),
                                doubleArrayOf(bbox.east, bbox.north)
                        )))
                gridLines.add(feature)
            }
        }
        val gridCollection = FeatureCollection.fromFeatures(gridLines)
        (map.getSource(gridSource) as GeoJsonSource).setGeoJson(gridCollection)
    }

    override fun onBackPressed() {
        Toast.makeText(this, "Back!", Toast.LENGTH_SHORT).show()
    }
}
