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
import com.mapbox.mapboxsdk.style.functions.Function
import com.mapbox.mapboxsdk.style.functions.stops.IdentityStops
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.services.commons.geojson.Feature
import com.mapbox.services.commons.geojson.FeatureCollection
import com.mapbox.services.commons.geojson.GeometryCollection
import com.mapbox.services.commons.geojson.Polygon
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

    class BBox(val north: Double, val south: Double, val west: Double, val east: Double)
    class Tile(val x: Int, val y: Int, val z: Int)

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
            val clusterTiles = preferences
                    .getStringSet(App.PREFERENCE_CLUSTER_TILES, HashSet())

            val features = ArrayList<Feature>()
            for ((index, tile) in explorerTiles.withIndex()) {
                val xy = tile.split("-")
                if (xy.size != 2) {
                    continue
                }
                val bbox = tile2boundingBox(xy[0].toInt(), xy[1].toInt(), explorerZoom)
                val polygonOption = PolygonOptions()
                        .add(LatLng(bbox.north, bbox.west))
                        .add(LatLng(bbox.north, bbox.east))
                        .add(LatLng(bbox.south, bbox.east))
                        .add(LatLng(bbox.south, bbox.west))
                        .add(LatLng(bbox.north, bbox.west))

                val feature = Feature.fromGeometry(Polygon.fromCoordinates(
                        arrayOf(
                                arrayOf(
                                        doubleArrayOf(bbox.west, bbox.north),
                                        doubleArrayOf(bbox.east, bbox.north),
                                        doubleArrayOf(bbox.east, bbox.south),
                                        doubleArrayOf(bbox.west, bbox.south),
                                        doubleArrayOf(bbox.west, bbox.north)))))

                if (clusterTiles.contains(tile)) {
                    polygonOption
                            .strokeColor(Color.parseColor("#0000FF"))
                            .fillColor(Color.parseColor("#100000FF"))
                    feature.addStringProperty("stroke", "#000099")
                    feature.addStringProperty("fill", "#0000FF")
                } else {
                    polygonOption
                            .strokeColor(Color.parseColor("#00FF00"))
                            .fillColor(Color.parseColor("#1000FF00"))
                    feature.addStringProperty("stroke", "#009900")
                    feature.addStringProperty("fill", "#00FF00")
                }
                feature.addNumberProperty("fill-opacity", 0.3f)

                explorerPolygonOptions.add(polygonOption)
                features.add(feature)
            }

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

            val featureCollection = FeatureCollection.fromFeatures(features)
            val explorerSource = GeoJsonSource("explorer", featureCollection)
            map.addSource(explorerSource)
            map.addLayer(FillLayer("explorer_layer", "explorer")
                    .withProperties(
                            PropertyFactory.fillOutlineColor(Function.property("stroke", IdentityStops<String>())),
                            PropertyFactory.fillColor(Function.property("fill", IdentityStops<String>())),
                            PropertyFactory.fillOpacity(Function.property("fill-opacity", IdentityStops<Float>())),
                            PropertyFactory.visibility(Property.NONE)
                    ))

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
                /*if (isChecked) {
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
                }*/
                val layer = map.getLayer("explorer_layer")
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()

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
