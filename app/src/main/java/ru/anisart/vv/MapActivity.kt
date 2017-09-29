package ru.anisart.vv

import android.Manifest
import android.app.Activity
import android.app.Fragment
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import butterknife.BindString
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.gson.Gson
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.functions.Function
import com.mapbox.mapboxsdk.style.functions.stops.Stop
import com.mapbox.mapboxsdk.style.functions.stops.Stops
import com.mapbox.mapboxsdk.style.layers.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.Source
import com.mapbox.services.Constants.PRECISION_6
import com.mapbox.services.api.directions.v5.DirectionsCriteria
import com.mapbox.services.api.directions.v5.models.DirectionsRoute
import com.mapbox.services.api.rx.directions.v5.MapboxDirectionsRx
import com.mapbox.services.commons.geojson.*
import com.mapbox.services.commons.models.Position
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@RuntimePermissions
class MapActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener,
        ServiceConnection {

    private val EXPLORER_ZOOM = 14
    private val PLAY_SERVICES_RESOLUTION_REQUEST = 1

    private val EXPLORER_SOURCE_ID = "explorer_source"
    private val RIDES_SOURCE_ID = "rides_source"
    private val GRID_SOURCE_ID = "grid_source"
    private val EXPLORER_LAYER_ID = "explorer_layer"
    private val RIDES_LAYER_ID = "rides_layer"
    private val GRID_LAYER_ID = "grid_layer"
    private val CLUSTER_FLAG = "cluster"

    private val STATE_EXPLORER = "explorer"
    private val STATE_RIDES = "rides"
    private val STATE_GRID = "grid"
    private val STATE_TARGET_POLYGON = "target_polygon"
    private val STATE_ROUTE_POINT = "route_point"
    private val PREFERENCE_CAMERA_POSITION = "camera_position"

    @BindView(R.id.mapView)
    lateinit var mapView: MapView
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

    private var explorer = false
    private var rides = false
    private var grid = false
    private var tagretPolygon: PolygonOptions? = null
    private var routeLine: PolylineOptions? = null
    private var routePoint: LatLng? = null
    private var bound = false
    private var onMapInitObservable: Observable<Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        settingsFragment = fragmentManager.findFragmentById(R.id.map_settings)
        fragmentManager.beginTransaction().hide(settingsFragment).commit()

        savedInstanceState?.let {
            explorer = savedInstanceState.getBoolean(STATE_EXPLORER)
            rides = savedInstanceState.getBoolean(STATE_RIDES)
            grid = savedInstanceState.getBoolean(STATE_GRID)
            tagretPolygon = savedInstanceState.getParcelable(STATE_TARGET_POLYGON)
            routePoint = savedInstanceState.getParcelable(STATE_ROUTE_POINT)
        }

        mapView.onCreate(savedInstanceState)
        val style = preferences.getString(mapKey, Style.OUTDOORS)
        mapView.setStyleUrl(style)
        mapView.getMapAsync {
            map = it
            val positionString = preferences.getString(PREFERENCE_CAMERA_POSITION, null)
            positionString?.let {
                map.cameraPosition = Gson().fromJson<CameraPosition>(it)
            }
            initMap()
            onMapInitObservable?.subscribe()
            routePoint?.let(this::route)

            mapView.addOnMapChangedListener {
                when (it) {
                    MapView.REGION_DID_CHANGE,
                    MapView.REGION_DID_CHANGE_ANIMATED,
                    MapView.DID_FINISH_LOADING_MAP -> {
                        if (BuildConfig.DEBUG) {
                            debugInfo()
                        }
                        if (grid) {
                            calculateGrid()
                        }
                    }
                }
            }

            map.setOnMapClickListener {
                if (!settingsFragment.isHidden) {
                    showSettings(false)
                }
            }

            map.setOnMapLongClickListener {
                val actions = listOf("Alert", "Route")
                selector("Lat %.3f Lon %.3f".format(Locale.US, it.latitude, it.longitude), actions,
                        { _, i ->
                            when (i) {
                                0 -> alertTileWithPermissionCheck(it)
                                1 -> route(it)
                            }
                        })
            }

            map.setOnInfoWindowClickListener {
                routeLine?.polyline?.let(map::removePolyline)
                map.removeMarker(it)
                routeLine = null
                routePoint = null
                true
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        System.err.println(intent.toString())
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
        onMapInitObservable?.subscribe()
        if (onMapInitObservable == null)
        onMapInitObservable = onMapInitObservable ?: Observable.just(Any())
                .doOnNext { bindService(Intent(this, AlertService::class.java), this, 0) }
    }

    override fun onPause() {
        val positionString = map.cameraPosition.toJson()
        preferences.edit().putString(PREFERENCE_CAMERA_POSITION, positionString).apply()
        tagretPolygon?.polygon?.let(map::removePolygon)
        tagretPolygon = null
        unbindService(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_EXPLORER, explorer)
        outState.putBoolean(STATE_RIDES, rides)
        outState.putBoolean(STATE_GRID, grid)
        outState.putParcelable(STATE_TARGET_POLYGON, tagretPolygon)
        outState.putParcelable(STATE_ROUTE_POINT, routePoint)
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
        onRequestPermissionsResult(requestCode, grantResults)
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
                val layer = map.getLayer(EXPLORER_LAYER_ID) ?: return
                val colorE = preferences.getInt(explorerKey, 0)
                val alphaE = alfaFromColor(colorE)
                val colorC = preferences.getInt(clusterKey, 0)
                val alphaC = alfaFromColor(colorC)
                layer.setProperties(
                        PropertyFactory.fillOutlineColor(
                                Function.property(
                                        CLUSTER_FLAG,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillOutlineColor(colorC))))
                                        .withDefaultValue(PropertyFactory.fillOutlineColor(colorE))),
                        PropertyFactory.fillColor(
                                Function.property(
                                        CLUSTER_FLAG,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillColor(colorC))))
                                        .withDefaultValue(PropertyFactory.fillColor(colorE))),
                        PropertyFactory.fillOpacity(
                                Function.property(
                                        CLUSTER_FLAG,
                                        Stops.categorical(Stop.stop(true, PropertyFactory.fillOpacity(alphaC))))
                                        .withDefaultValue(PropertyFactory.fillOpacity(alphaE))))
            }
            ridesKey -> {
                val layer = map.getLayer(RIDES_LAYER_ID) ?: return
                val color = preferences.getInt(ridesKey, 0)
                val alpha = alfaFromColor(color)
                layer.setProperties(
                        PropertyFactory.lineColor(color),
                        PropertyFactory.lineOpacity(alpha))
            }
            gridKey -> {
                val layer = map.getLayer(GRID_LAYER_ID) ?: return
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PLAY_SERVICES_RESOLUTION_REQUEST ->
                toast(if (resultCode == Activity.RESULT_OK)
                    "Google Play Services have been enabled. Try again!"
                else
                    "Google Play Services has not been enabled. Alert functionality is not available!")
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {
        bound = false
        tagretPolygon?.polygon?.let(map::removePolygon)
        tagretPolygon = null
    }

    override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
        bound = true
        val targetBounds = (binder as AlertService.LocalBinder).getService().targetBounds
        targetBounds?.let(this::drawTargetTile)
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
                    val bbox = tile2bbox(it[0].toInt(), it[1].toInt(), EXPLORER_ZOOM)
                    val feature = Feature.fromGeometry(Polygon.fromCoordinates(
                            arrayOf(
                                    arrayOf(
                                            doubleArrayOf(bbox.west, bbox.north),
                                            doubleArrayOf(bbox.east, bbox.north),
                                            doubleArrayOf(bbox.east, bbox.south),
                                            doubleArrayOf(bbox.west, bbox.south),
                                            doubleArrayOf(bbox.west, bbox.north)))))
                    feature.addBooleanProperty(CLUSTER_FLAG, clusterTiles.contains("%s-%s".format(it[0], it[1])))
                    feature
                }
                .toList()
                .map(FeatureCollection::fromFeatures)
                .map { GeoJsonSource(EXPLORER_SOURCE_ID, it) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    setupExplorerTiles(it)
                }, {
                    it.printStackTrace()
                })

        Observable
                .just(preferences)
                .map { it.getString(App.PREFERENCE_RIDES_JSON, null) }
                .filter { it.isNotEmpty() }
                .map { GeoJsonSource(RIDES_SOURCE_ID, it) }
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
        map.addLayer(FillLayer(EXPLORER_LAYER_ID, EXPLORER_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (explorer) Property.VISIBLE else Property.NONE)
                ))
        onSharedPreferenceChanged(preferences, explorerKey)
    }

    private fun setupRides(source: Source) {
        map.addSource(source)
        map.addLayer(LineLayer(RIDES_LAYER_ID, RIDES_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (rides) Property.VISIBLE else Property.NONE)
                ))
        onSharedPreferenceChanged(preferences, ridesKey)
    }

    private fun setupGrid() {
        map.addSource(GeoJsonSource(GRID_SOURCE_ID))
        val gridLayer = LineLayer(GRID_LAYER_ID, GRID_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (grid) Property.VISIBLE else Property.NONE)
                )
        gridLayer.minZoom = 10f
        map.addLayer(gridLayer)
        onSharedPreferenceChanged(preferences, gridKey)
    }

    @OnClick(R.id.myLocationButton)
    fun onLocationButtonClick(v: View) {
        getLastLocationWithPermissionCheck()
    }

    @OnClick(R.id.explorerButton)
    fun onExplorerButtonClick(v: View) {
        explorer = !explorer
        val layer = map.getLayer(EXPLORER_LAYER_ID)
        if (layer != null) {
            layer.setProperties(PropertyFactory.visibility(
                    if (explorer) Property.VISIBLE else Property.NONE))
        } else {
            Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.ridesButton)
    fun onRidesButtonClick(v: View) {
        rides = !rides
        val layer = map.getLayer(RIDES_LAYER_ID)
        if (layer != null) {
            layer.setProperties(PropertyFactory.visibility(
                    if (rides) Property.VISIBLE else Property.NONE))
        } else {
            Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.gridButton)
    fun onGridButtonClick(v: View) {
        grid = !grid
        val layer = map.getLayer(GRID_LAYER_ID)
        if (layer != null) {
            layer.setProperties(PropertyFactory.visibility(
                    if (grid) Property.VISIBLE else Property.NONE))
            if (grid) calculateGrid()
        } else {
            Toast.makeText(this, "No grid!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.styleButton)
    fun onStyleButtonClick(v: View) {
        showSettings(settingsFragment.isHidden)
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), EXPLORER_ZOOM.toDouble()))
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getLastLocation() {
        if (map.myLocation != null) {
            setCameraPosition(map.myLocation as Location)
        } else {
            map.setOnMyLocationChangeListener(null)
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
        map.getLayer(GRID_LAYER_ID) ?: return
        if (map.cameraPosition.zoom < 9.9) return

        val bounds = map.projection.visibleRegion.latLngBounds
        val x0 = lon2tile(bounds.lonWest, EXPLORER_ZOOM)
        val x1 = lon2tile(bounds.lonEast, EXPLORER_ZOOM)
        val y0 = lat2tile(bounds.latNorth, EXPLORER_ZOOM)
        val y1 = lat2tile(bounds.latSouth, EXPLORER_ZOOM)
        val gridLines = ArrayList<Feature>()
        for (x in x0 - 1..x1 + 1) {
            for (y in y0 - 1..y1 + 1) {
                val bbox = tile2bbox(x, y, EXPLORER_ZOOM)
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
        (map.getSource(GRID_SOURCE_ID) as GeoJsonSource).setGeoJson(gridCollection)
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

    private fun addDestinationMarker(point: LatLng) {
        map.markers.forEach(map::removeMarker)
        map.addMarker(MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .title("Destination")
                .snippet("Click to remove"))
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun alertTile(point: LatLng) {
        if (!checkPlayServices()) return

        val bounds = point2bounds(point.latitude, point.longitude, EXPLORER_ZOOM)
//        drawTargetTile(bounds)

        val serviceIntent = Intent(this, AlertService::class.java)
        serviceIntent.putExtra(App.EXTRA_TARGET_BOUNDS, bounds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, this, 0)
    }

    private fun drawTargetTile(bounds: LatLngBounds) {
        tagretPolygon?.polygon?.let(map::removePolygon)
        tagretPolygon = PolygonOptions()
                .add(bounds.northWest)
                .add(bounds.northEast)
                .add(bounds.southEast)
                .add(bounds.southWest)
                .add(bounds.northWest)
                .fillColor(Color.MAGENTA)
                .alpha(0.3f)
        tagretPolygon?.let(map::addPolygon)
    }

    private fun route(point: LatLng) {
        routeLine?.polyline?.let(map::removePolyline)
        addDestinationMarker(point)
        val myLocation = map.myLocation ?: return
        val origin = Position.fromLngLat(myLocation.longitude, myLocation.latitude)
        val destination = Position.fromLngLat(point.longitude, point.latitude)
        MapboxDirectionsRx.Builder()
                .setOrigin(origin)
                .setDestination(destination)
                .setOverview(DirectionsCriteria.OVERVIEW_FULL)
                .setProfile(DirectionsCriteria.PROFILE_CYCLING)
                .setAccessToken(Mapbox.getAccessToken())
                .build()
                .observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    drawRoute(it.routes.first())
                    routePoint = point
                }, {
                    map.markers.forEach(map::removeMarker)
                    it.printStackTrace()
                    toast("Route not found.")
                })
    }

    private fun drawRoute(route: DirectionsRoute) {
        val lineString = LineString.fromPolyline(route.geometry, PRECISION_6)
        val points = arrayListOf<LatLng>()
        lineString.coordinates.forEach { points.add(LatLng(
                it.latitude,
                it.longitude)) }
        routeLine = PolylineOptions()
                .addAll(points)
                .color(Color.parseColor("#009688"))
                .width(5f)
        routeLine?.let(map::addPolyline)
    }

    private fun checkPlayServices(): Boolean {
        val googleAPI = GoogleApiAvailability.getInstance()
        val result = googleAPI.isGooglePlayServicesAvailable(this)
        if (result != ConnectionResult.SUCCESS)
        {
            if (googleAPI.isUserResolvableError(result))
            {
                googleAPI.getErrorDialog(this, result,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show()
            }
            return false
        }
        return true
    }
}
