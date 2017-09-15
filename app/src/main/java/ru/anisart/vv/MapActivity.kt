package ru.anisart.vv

import android.Manifest
import android.app.Fragment
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import butterknife.BindString
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.functions.Function
import com.mapbox.mapboxsdk.style.functions.stops.Stop
import com.mapbox.mapboxsdk.style.functions.stops.Stops
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.Source
import com.mapbox.services.commons.geojson.Feature
import com.mapbox.services.commons.geojson.FeatureCollection
import com.mapbox.services.commons.geojson.LineString
import com.mapbox.services.commons.geojson.Polygon
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@RuntimePermissions
class MapActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private val explorerZoom = 14

    private val explorerSourceId = "explorer_source"
    private val ridesSourceId = "rides_source"
    private val gridSourceId = "grid_source"
    private val explorerLayerId = "explorer_layer"
    private val ridesLayerId = "rides_layer"
    private val gridLayerId = "grid_layer"
    private val clusterFlag = "cluster"

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

    @BindString(R.string.key_color_explorer_fill)
    lateinit var explorerKey: String
    @BindString(R.string.key_color_cluster_fill)
    lateinit var clusterKey: String
    @BindString(R.string.key_color_rides)
    lateinit var ridesKey: String
    @BindString(R.string.key_color_grid)
    lateinit var gridKey: String
    @BindString(R.string.key_map_style)
    lateinit var mapKey: String

    private lateinit var map: MapboxMap
    private lateinit var preferences: SharedPreferences
    private lateinit var settingsFragment: Fragment

    private var startBounds: LatLngBounds.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        settingsFragment = fragmentManager.findFragmentById(R.id.map_settings)
        fragmentManager.beginTransaction().hide(settingsFragment).commit()

        mapView.onCreate(savedInstanceState)
        val style = preferences.getString(mapKey, Style.OUTDOORS)
        mapView.setStyleUrl(style)
        mapView.getMapAsync {
            map = it
            initMap()
            if (savedInstanceState == null) {
                startBounds = LatLngBounds.Builder()
            }
            explorerButton.setOnCheckedChangeListener(this)
            ridesButton.setOnCheckedChangeListener(this)
            gridButton.setOnCheckedChangeListener(this)

            mapView.addOnMapChangedListener {
                when (it) {
                    MapView.REGION_DID_CHANGE,
                    MapView.REGION_DID_CHANGE_ANIMATED,
                    MapView.DID_FINISH_LOADING_MAP -> {
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
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        mapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MapActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.action_colors) {
            showSettings(settingsFragment.isHidden)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCheckedChanged(button: CompoundButton?, isChecked: Boolean) {
        when (button?.id) {
            R.id.explorerButton -> {
                val layer = map.getLayer(explorerLayerId)
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()

                }
            }
            R.id.ridesButton -> {
                val layer = map.getLayer(ridesLayerId)
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(
                            if (isChecked) Property.VISIBLE else Property.NONE))
                } else {
                    button.isChecked = false
                    Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()

                }
            }
            R.id.gridButton -> {
                val layer = map.getLayer(gridLayerId)
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

    override fun onBackPressed() {
        if (!settingsFragment.isHidden) {
            showSettings(false)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSharedPreferenceChanged(preferences1: SharedPreferences?, key: String?) {
        when (key) {
            explorerKey,
            clusterKey -> {
                val layer = map.getLayer(explorerLayerId) ?: return
                val colorE = preferences.getInt(explorerKey, 0)
                val alphaE = alfaFromColor(colorE)
                val colorC = preferences.getInt(clusterKey, 0)
                val alphaC = alfaFromColor(colorC)
                layer.setProperties(
                        PropertyFactory.fillOutlineColor(
                                Function.property(
                                        clusterFlag,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillOutlineColor(colorC))))
                                        .withDefaultValue(PropertyFactory.fillOutlineColor(colorE))),
                        PropertyFactory.fillColor(
                                Function.property(
                                        clusterFlag,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillColor(colorC))))
                                        .withDefaultValue(PropertyFactory.fillColor(colorE))),
                        PropertyFactory.fillOpacity(
                                Function.property(
                                        clusterFlag,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillOpacity(alphaC))))
                                        .withDefaultValue(PropertyFactory.fillOpacity(alphaE))))
            }
            ridesKey -> {
                val layer = map.getLayer(ridesLayerId) ?: return
                val color = preferences.getInt(ridesKey, 0)
                val alpha = alfaFromColor(color)
                layer.setProperties(
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineOpacity(alpha))
            }
            gridKey -> {
                val layer = map.getLayer(gridLayerId) ?: return
                val color = preferences.getInt(gridKey, 0)
                val alpha = alfaFromColor(color)
                layer.setProperties(
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineOpacity(alpha))
            }
            mapKey -> {
                val style = preferences.getString(mapKey, Style.OUTDOORS)
                map.layers.forEach { map.removeLayer(it) }
                map.sources.forEach { map.removeSource(it) }
                map.setStyleUrl(style, {
                    initMap()
                })
            }
        }
    }

    private fun initMap() {
        val clusterTiles = preferences
                .getStringSet(App.PREFERENCE_CLUSTER_TILES, HashSet())
        Observable
                .just(preferences)
                .map {
                    it.getStringSet(App.PREFERENCE_TILES, null)
                }
                .filter { it.isNotEmpty() }
                .flatMapIterable { it }
                .map { it.split("-") }
                .filter { it.size == 2 }
                .map {
                    val bbox = tile2boundingBox(it[0].toInt(), it[1].toInt(), explorerZoom)
                    val feature = Feature.fromGeometry(Polygon.fromCoordinates(
                            arrayOf(
                                    arrayOf(
                                            doubleArrayOf(bbox.west, bbox.north),
                                            doubleArrayOf(bbox.east, bbox.north),
                                            doubleArrayOf(bbox.east, bbox.south),
                                            doubleArrayOf(bbox.west, bbox.south),
                                            doubleArrayOf(bbox.west, bbox.north)))))
                    feature.addBooleanProperty(clusterFlag, clusterTiles.contains("%s-%s".format(it[0], it[1])))
                    startBounds?.includes(arrayListOf(
                            LatLng(bbox.north, bbox.west),
                            LatLng(bbox.north, bbox.east),
                            LatLng(bbox.south, bbox.east),
                            LatLng(bbox.south, bbox.west)))
                    feature
                }
                .toList()
                .map(FeatureCollection::fromFeatures)
                .map { GeoJsonSource(explorerSourceId, it) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    setupExplorerTiles(it)
                    startBounds?.build()?.let {
                        map.cameraPosition = CameraUpdateFactory.newLatLngBounds(it, 10)
                                .getCameraPosition(map)
                        startBounds = null
                    }
                }, {
                    it.printStackTrace()
                })

        Observable
                .just(preferences)
                .map { it.getString(App.PREFERENCE_RIDES_JSON, null) }
                .filter { it.isNotEmpty() }
                .map { GeoJsonSource(ridesSourceId, it) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    setupRides(it)
                }, {
                    it.printStackTrace()
                })

        setupGrid()
    }

    private fun setupExplorerTiles(source: Source) {
        map.addSource(source)
        map.addLayer(FillLayer(explorerLayerId, explorerSourceId)
                .withProperties(
                        PropertyFactory.visibility(if (explorerButton.isChecked) Property.VISIBLE else Property.NONE)
                ))
        onSharedPreferenceChanged(preferences, explorerKey)
    }

    private fun setupRides(source: Source) {
        map.addSource(source)
        map.addLayer(LineLayer(ridesLayerId, ridesSourceId)
                .withProperties(
                        PropertyFactory.visibility(if (ridesButton.isChecked) Property.VISIBLE else Property.NONE)
                ))
        onSharedPreferenceChanged(preferences, ridesKey)
    }

    private fun setupGrid() {
        map.addSource(GeoJsonSource(gridSourceId))
        val gridLayer = LineLayer(gridLayerId, gridSourceId)
                .withProperties(
                        PropertyFactory.visibility(if (gridButton.isChecked) Property.VISIBLE else Property.NONE)
                )
        gridLayer.minZoom = 10f
        map.addLayer(gridLayer)
        onSharedPreferenceChanged(preferences, gridKey)
    }

    @OnClick(R.id.myLocationButton)
    fun onLocationButtonClick(v: View) {
        MapActivityPermissionsDispatcher.getLastLocationWithCheck(this)
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
            map.setOnMyLocationChangeListener { location ->
                run {
                    setCameraPosition(location as Location)
                    map.setOnMyLocationChangeListener(null)
                }
            }

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
        map.getLayer(gridLayerId) ?: return
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
        (map.getSource(gridSourceId) as GeoJsonSource).setGeoJson(gridCollection)
    }

    private fun showSettings(isShow: Boolean) {
        val transaction = fragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.animator.enter_from_right, R.animator.exit_to_right)
        if (isShow) {
            transaction.show(settingsFragment)
        } else {
            transaction.hide(settingsFragment)
        }
        transaction.commit()
    }

    private fun alfaFromColor(rgba: Int) = ((rgba shr 24) and 0xFF) / 255f

}
