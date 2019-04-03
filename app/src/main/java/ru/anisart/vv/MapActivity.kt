package ru.anisart.vv

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import butterknife.BindString
import butterknife.ButterKnife
import butterknife.OnClick
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.telemetry.TelemetryEnabler
import com.mapbox.geojson.*
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.Source
import com.mapbox.mapboxsdk.style.sources.TileSet
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
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
        ServiceConnection, OnMapReadyCallback {

    private val CAMERA_ZOOM = 14.0
    private val BELOW_LAYER = "waterway-label"

    private val PLAY_SERVICES_RESOLUTION_REQUEST = 1
    private val SYNC_SETTINGS_REQUEST = 2

    private val EXPLORER_SOURCE_ID = "explorer_source"
    private val SQUARE_SOURCE_ID = "square_source"
    private val RIDES_SOURCE_ID = "rides_source"
    private val GRID_SOURCE_ID = "grid_source"
    private val TRACKING_LINE_SOURCE_ID = "tracking_line_source"
    private val TRACKING_TILES_SOURCE_ID = "tracking_tiles_source"
    private val HEATMAP_SOURCE_ID = "heatmap_source"
    private val EXPLORER_LAYER_ID = "explorer_layer"
    private val SQUARE_LAYER_ID = "square_layer"
    private val RIDES_LAYER_ID = "rides_layer"
    private val GRID_LAYER_ID = "grid_layer"
    private val TRACKING_LINE_LAYER_ID = "tracking_line_layer"
    private val TRACKING_TILES_LAYER_ID = "tracking_tiles_layer"
    private val HEATMAP_LAYER_ID = "heatmap_layer"
    private val TYPE_FLAG = "cluster"
    private val EXPLORER = 0
    private val CLUSTER = 1

    private val STATE_EXPLORER = "explorer"
    private val STATE_RIDES = "rides"
    private val STATE_GRID = "grid"
    private val STATE_HEATMAP = "heatmap"
    private val STATE_LOCATION = "location"
    private val PREFERENCE_CAMERA_POSITION = "camera_position"

    @BindString(R.string.key_color_explorer)
    lateinit var explorerKey: String
    @BindString(R.string.key_color_cluster)
    lateinit var clusterKey: String
    @BindString(R.string.key_color_max_square)
    lateinit var squareKey: String
    @BindString(R.string.key_color_rides)
    lateinit var ridesKey: String
    @BindString(R.string.key_color_grid)
    lateinit var gridKey: String
    @BindString(R.string.key_color_recorded_track)
    lateinit var recordedTrackKey: String
    @BindString(R.string.key_color_recorded_tiles)
    lateinit var recordedTilesKey: String
    @BindString(R.string.key_map_style)
    lateinit var mapKey: String
    @BindString(R.string.key_heatmap_type)
    lateinit var heatmapTypeKey: String
    @BindString(R.string.key_heatmap_style)
    lateinit var heatmapStyleKey: String

    @BindString(R.string.action_start)
    lateinit var startString: String
    @BindString(R.string.action_pause)
    lateinit var pauseString: String
    @BindString(R.string.action_resume)
    lateinit var resumeString: String
    @BindString(R.string.action_stop)
    lateinit var stopString: String
    @BindString(R.string.action_clear)
    lateinit var clearString: String
    @BindString(R.string.sync_settings)
    lateinit var syncSettingsString: String
    @BindString(R.string.style_settings)
    lateinit var styleSettingsString: String

    private lateinit var map: MapboxMap
    private lateinit var preferences: SharedPreferences
    private lateinit var settingsFragment: StyleSettingsFragment
    private lateinit var receiver: BroadcastReceiver
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var explorer = false
    private var rides = false
    private var grid = false
    private var heatmap = false
    private var onMapInitObservable: Observable<Any>? = null
    private var service: TrackingService? = null
    private var mapAllowed = false
    private var location = false
    private var cameraAtLocation = false

    private var onMapInitDisposable: Disposable? = null
    private var tilesDisposable: Disposable? = null
    private var maxSquareDisposable: Disposable? = null
    private var ridesDisposable: Disposable? = null
    private var trackingTilesDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ButterKnife.bind(this)
        supportActionBar?.hide()
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        settingsFragment = supportFragmentManager.findFragmentById(R.id.map_settings) as StyleSettingsFragment
        settingsFragment.setOnIconClickListener(object : StyleSettingsFragment.OnIconClickListener {
            override fun onIconClick() {
                showSettings(false)
            }
        })
        supportFragmentManager.beginTransaction().hide(settingsFragment).commit()

        savedInstanceState?.let {
            explorer = savedInstanceState.getBoolean(STATE_EXPLORER)
            rides = savedInstanceState.getBoolean(STATE_RIDES)
            grid = savedInstanceState.getBoolean(STATE_GRID)
            heatmap = savedInstanceState.getBoolean(STATE_HEATMAP)
            location = savedInstanceState.getBoolean(STATE_LOCATION)
        }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    TrackingService.ACTION_START ->
                        applicationContext.bindService(Intent(this@MapActivity, TrackingService::class.java), this@MapActivity, 0)
                    TrackingService.ACTION_TRACK ->
                        updateTracking(intent.getBooleanExtra(TrackingService.EXTRA_NEW_TILE, false))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
        registerReceiver(receiver, intentFilter(TrackingService.ACTION_START, TrackingService.ACTION_TRACK))
        onMapInitDisposable = onMapInitObservable?.subscribe()
        if (onMapInitObservable == null) onMapInitObservable = onMapInitObservable ?: Observable.just(Any())
                    .doOnNext { applicationContext.bindService(Intent(this, TrackingService::class.java), this, 0) }
    }

    override fun onPause() {
        onMapInitDisposable?.dispose()
        tilesDisposable?.dispose()
        maxSquareDisposable?.dispose()
        ridesDisposable?.dispose()
        trackingTilesDisposable?.dispose()
        if (mapAllowed) {
            val positionString = map.cameraPosition.toJson()
            preferences.edit().putString(PREFERENCE_CAMERA_POSITION, positionString).apply()
        }
        if (service != null) {
            applicationContext.unbindService(this)
        }
        unregisterReceiver(receiver)
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
        outState.putBoolean(STATE_HEATMAP, heatmap)
        outState.putBoolean(STATE_LOCATION, location)
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

    override fun onBackPressed() {
        if (!settingsFragment.isHidden) {
            showSettings(false)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSharedPreferenceChanged(preferences1: SharedPreferences?, key: String?) {
        val style = if (mapAllowed) map.style ?: return else return
        when (key) {
            explorerKey,
            clusterKey -> updateExplorerLayerColors(style)
            squareKey -> updateLayerColor(style, SQUARE_LAYER_ID, key)
            ridesKey -> updateLayerColor(style, RIDES_LAYER_ID, key)
            gridKey -> updateLayerColor(style, GRID_LAYER_ID, key)
            recordedTrackKey -> updateLayerColor(style, TRACKING_LINE_LAYER_ID, key)
            recordedTilesKey -> updateLayerColor(style, TRACKING_TILES_LAYER_ID, key)
            mapKey -> {
                val styleString = preferences.getString(mapKey, Style.OUTDOORS)
                map.setStyle(styleString) { newStyle ->
                    initMap(newStyle)
                }
            }
            heatmapTypeKey,
            heatmapStyleKey -> setupHeatmap(style)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PLAY_SERVICES_RESOLUTION_REQUEST ->
                toast(if (resultCode == Activity.RESULT_OK)
                    "Google Play Services have been enabled. Try again!"
                else
                    "Google Play Services has not been enabled. Tracking functionality is not available!")
            SYNC_SETTINGS_REQUEST -> if (resultCode == Activity.RESULT_OK && mapAllowed) map.style?.let { updateTilesAndRidesAndHeatmap(it) }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
        service = (binder as TrackingService.LocalBinder).getService()
        updateTracking(true)
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {
        service = null
    }

    private fun toggleLocationButton(enable: Boolean) {
        myLocationButton.setImageResource(if (enable) R.drawable.ic_my_location else R.drawable.ic_disable_location)
        cameraAtLocation = !enable
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationPermissionDenied() {
        Toast.makeText(this, "Permission is required to show your location!", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationNeverAskAgain() {
        Toast.makeText(this, "Check permissions for app in System Settings!", Toast.LENGTH_SHORT).show()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap
        mapAllowed = true
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        TelemetryEnabler.updateTelemetryState(TelemetryEnabler.State.DISABLED)
        val positionString = preferences.getString(PREFERENCE_CAMERA_POSITION, null)
        positionString?.let { s ->
            map.cameraPosition = Gson().fromJson(s)
        }
        val styleString = preferences.getString(mapKey, Style.OUTDOORS)
        map.setStyle(styleString) { style ->
            initMap(style)
            if (location) {
                enableLocationComponentWithPermissionCheck(style)
            }
        }
        onMapInitDisposable = onMapInitObservable?.subscribe()

        map.addOnMoveListener(object : MapboxMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {

            }
            override fun onMove(detector: MoveGestureDetector) {
                toggleLocationButton(true)
            }
            override fun onMoveEnd(detector: MoveGestureDetector) {

            }
        })

        map.addOnCameraMoveListener {
            if (BuildConfig.DEBUG) {
                debugInfo()
            }
            if (grid) {
                updateGrid()
            }
        }

        map.addOnMapClickListener {
            if (!settingsFragment.isHidden) {
                showSettings(false)
                return@addOnMapClickListener true
            }
            return@addOnMapClickListener false
        }
    }

    private fun initMap(style: Style) {
        updateTilesAndRidesAndHeatmap(style)
        setupGrid(style)
        setupTracking(style)
    }

    @SuppressLint("MissingPermission")
    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun enableLocationComponent(style: Style) {
        val options = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .build()
        val activationOptions = LocationComponentActivationOptions
                .builder(this, style)
                .locationComponentOptions(options)
                .build()
        val locationComponent = map.locationComponent

        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.applyStyle(options)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
        location = true
        toggleLocationButton(false)
    }

    private fun disableLocationComponent() {
        val locationComponent = map.locationComponent
        locationComponent.isLocationComponentEnabled = false
        location = false
        toggleLocationButton(true)
    }

    private fun updateTilesAndRidesAndHeatmap(style: Style) {
        listOf(EXPLORER_LAYER_ID, SQUARE_LAYER_ID, RIDES_LAYER_ID).forEach {
            style.removeLayer(it)
        }
        listOf(EXPLORER_SOURCE_ID, SQUARE_SOURCE_ID, RIDES_SOURCE_ID).forEach {
            style.removeSource(it)
        }

        tilesDisposable = Observable
                .just(preferences)
                .map { it.getString(App.PREFERENCE_TILES, null) }
                .map { Gson().fromJson(it) ?: HashSet<Tile>() }
                .filter { it.isNotEmpty() }
                .flatMapIterable { it }
                .map {
                    val bbox = tile2bbox(it.x, it.y)
                    Feature.fromGeometry(Polygon.fromLngLats(
                            listOf(
                                    listOf(
                                            Point.fromLngLat(bbox.west, bbox.north),
                                            Point.fromLngLat(bbox.east, bbox.north),
                                            Point.fromLngLat(bbox.east, bbox.south),
                                            Point.fromLngLat(bbox.west, bbox.south),
                                            Point.fromLngLat(bbox.west, bbox.north)))))
                            .apply { addNumberProperty(TYPE_FLAG, if (it.isCluster) CLUSTER else EXPLORER) }
                }
                .toList()
                .map {FeatureCollection.fromFeatures(it)}
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { GeoJsonSource(EXPLORER_SOURCE_ID, it) }
                .subscribe({
                    setupExplorerTiles(style, it)
                }, {
                    it.printStackTrace()
                })

        maxSquareDisposable = Observable
                .just(preferences)
                .map { it.getString(App.PREFERENCE_MAX_SQUARES, null) }
                .map { Gson().fromJson<List<MaxSquare>>(it) }
                .filter { it.isNotEmpty() }
                .flatMapIterable { it }
                .map {
                    val north = tile2lat(it.y)
                    val west = tile2lon(it.x)
                    val south = tile2lat(it.y + it.size)
                    val east = tile2lon(it.x + it.size)
                    Feature.fromGeometry(Polygon.fromLngLats(
                            listOf(
                                    listOf(
                                            Point.fromLngLat(west, north),
                                            Point.fromLngLat(east, north),
                                            Point.fromLngLat(east, south),
                                            Point.fromLngLat(west, south),
                                            Point.fromLngLat(west, north)))))
                }
                .toList()
                .map {FeatureCollection.fromFeatures(it)}
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { GeoJsonSource(SQUARE_SOURCE_ID, it) }
                .subscribe({
                    setupMaxSquares(style, it)
                }, {
                    it.printStackTrace()
                })

        ridesDisposable = Observable
                .just(preferences)
                .map { it.getString(App.PREFERENCE_RIDES_JSON, null) }
                .filter { it.isNotEmpty() }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { GeoJsonSource(RIDES_SOURCE_ID, it) }
                .subscribe({
                    setupRides(style, it)
                }, {
                    it.printStackTrace()
                })

        setupHeatmap(style)
    }

    private fun setupExplorerTiles(style: Style, source: Source) {
        style.addSource(source)
        style.addLayer(FillLayer(EXPLORER_LAYER_ID, EXPLORER_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (explorer) Property.VISIBLE else Property.NONE)
                ))
        updateExplorerLayerColors(style)
    }

    private fun setupMaxSquares(style: Style, source: Source) {
        style.addSource(source)
        style.addLayer(LineLayer(SQUARE_LAYER_ID, SQUARE_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (explorer) Property.VISIBLE else Property.NONE)
                ))
        updateLayerColor(style, SQUARE_LAYER_ID, squareKey)
    }

    private fun setupRides(style: Style, source: Source) {
        style.addSource(source)
        style.addLayerAbove(LineLayer(RIDES_LAYER_ID, RIDES_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (rides) Property.VISIBLE else Property.NONE)
                ), HEATMAP_LAYER_ID)
        updateLayerColor(style, RIDES_LAYER_ID, ridesKey)
    }

    private fun setupGrid(style: Style) {
        style.addSource(GeoJsonSource(GRID_SOURCE_ID))
        val gridLayer = LineLayer(GRID_LAYER_ID, GRID_SOURCE_ID)
                .withProperties(
                        PropertyFactory.visibility(if (grid) Property.VISIBLE else Property.NONE)
                )
        gridLayer.minZoom = 10f
        style.addLayer(gridLayer)
        updateLayerColor(style, GRID_LAYER_ID, gridKey)
    }

    private fun setupTracking(style: Style) {
        style.addSource(GeoJsonSource(TRACKING_LINE_SOURCE_ID))
        style.addLayer(LineLayer(TRACKING_LINE_LAYER_ID, TRACKING_LINE_SOURCE_ID))
        updateLayerColor(style, TRACKING_LINE_LAYER_ID, recordedTrackKey)
        style.addSource(GeoJsonSource(TRACKING_TILES_SOURCE_ID))
        style.addLayer(FillLayer(TRACKING_TILES_LAYER_ID, TRACKING_TILES_SOURCE_ID))
        updateLayerColor(style, TRACKING_TILES_LAYER_ID, recordedTilesKey)
    }

    private fun setupHeatmap(style: Style) {
        style.removeLayer(HEATMAP_LAYER_ID)
        style.removeSource(HEATMAP_SOURCE_ID)
        val heatmapType = preferences.getString(heatmapTypeKey, "")
        val heatmapStyle = preferences.getString(heatmapStyleKey, "")
        val authQuery = preferences.getString(App.PREFERENCE_HEATMAP_AUTH, null)
        val url = when {
            authQuery != null -> "https://heatmap-external-b.strava.com/tiles-auth/$heatmapType/$heatmapStyle/{z}/{x}/{y}.png$authQuery"
            else -> "https://heatmap-external-b.strava.com/tiles/$heatmapType/$heatmapStyle/{z}/{x}/{y}.png"
        }
        style.addSource(RasterSource(HEATMAP_SOURCE_ID,
                TileSet("2.1.0", url)
                        .apply { minZoom = 1f; maxZoom = 15f }, 256))
        style.addLayerBelow(RasterLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID)
                .withProperties(PropertyFactory.visibility(
                        if (heatmap) Property.VISIBLE else Property.NONE
                )), BELOW_LAYER)
    }

    @OnClick(R.id.myLocationButton)
    fun onLocationButtonClick() {
        if (location && cameraAtLocation) {
            disableLocationComponent()
        } else {
            map.style?.let { enableLocationComponentWithPermissionCheck(it) }
        }
    }

    @OnClick(R.id.explorerButton)
    fun onExplorerButtonClick() {
        if (!mapAllowed) return

        val layer = map.style?.getLayer(EXPLORER_LAYER_ID)
        if (layer != null) {
            explorer = !explorer
            layer.setProperties(PropertyFactory.visibility(
                    if (explorer) Property.VISIBLE else Property.NONE))
            map.style?.getLayer(SQUARE_LAYER_ID)?.setProperties(PropertyFactory.visibility(
                    if (explorer) Property.VISIBLE else Property.NONE))
        } else {
            Toast.makeText(this, "No tiles!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.ridesButton)
    fun onRidesButtonClick() {
        if (!mapAllowed) return

        val layer = map.style?.getLayer(RIDES_LAYER_ID)
        if (layer != null) {
            rides = !rides
            layer.setProperties(PropertyFactory.visibility(
                    if (rides) Property.VISIBLE else Property.NONE))
        } else {
            Toast.makeText(this, "No rides!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.gridButton)
    fun onGridButtonClick() {
        if (!mapAllowed) return

        val layer = map.style?.getLayer(GRID_LAYER_ID)
        if (layer != null) {
            grid = !grid
            layer.setProperties(PropertyFactory.visibility(
                    if (grid) Property.VISIBLE else Property.NONE))
            if (grid) updateGrid()
        } else {
            Toast.makeText(this, "No grid!", Toast.LENGTH_SHORT).show()
        }
    }

    @OnClick(R.id.heatmapButton)
    fun onHeatmapButtonClick() {
        if (!mapAllowed) return

        map.style?.getLayer(HEATMAP_LAYER_ID)?.let {
            heatmap = !heatmap
            it.setProperties(PropertyFactory.visibility(
                    if (heatmap) Property.VISIBLE else Property.NONE))
        }
    }

    @OnClick(R.id.recordButton)
    fun onRecordButtonClick() {
        val buttons = when (service?.state) {
            TrackingService.State.RECORDING -> listOf(pauseString, stopString)
            TrackingService.State.PAUSED -> listOf(resumeString, stopString)
            else -> listOf(startString, clearString)
        }

        selector(getString(R.string.title_record_dialog), buttons) { _, i ->
            when (buttons[i]) {
                startString -> startRecordingWithPermissionCheck()
                pauseString -> pauseRecording()
                resumeString -> resumeRecording()
                stopString -> stopRecording()
                clearString -> clearTracking()
            }
        }
    }

    @OnClick(R.id.settingsButton)
    fun onSettingsButtonClick() {
        val buttons = listOf(syncSettingsString, styleSettingsString)
        selector(null, buttons) { _, i ->
            when (buttons[i]) {
                syncSettingsString -> startActivityForResult(Intent(this, MainActivity::class.java), SYNC_SETTINGS_REQUEST)
                styleSettingsString -> showSettings(settingsFragment.isHidden)
            }
        }
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), CAMERA_ZOOM))
    }

    @SuppressLint("SetTextI18n")
    private fun debugInfo() {
        debugView.text = "z = %.2f, lat = %.2f, lon = %.2f".format(
                Locale.US,
                map.cameraPosition.zoom,
                map.cameraPosition.target.latitude,
                map.cameraPosition.target.longitude)
    }

    private fun updateGrid() {
        map.style?.getLayer(GRID_LAYER_ID) ?: return
        if (map.cameraPosition.zoom < 9.9) return

        val bounds = map.projection.visibleRegion.latLngBounds
        val x0 = lon2tile(bounds.lonWest)
        val x1 = lon2tile(bounds.lonEast)
        val y0 = lat2tile(bounds.latNorth)
        val y1 = lat2tile(bounds.latSouth)
        val gridLines = ArrayList<Feature>()
        for (x in x0 - 1..x1 + 1) {
            for (y in y0 - 1..y1 + 1) {
                val bbox = tile2bbox(x, y)
                val feature = Feature.fromGeometry(LineString.fromLngLats(
                        listOf(
                                Point.fromLngLat(bbox.west, bbox.south),
                                Point.fromLngLat(bbox.west, bbox.north),
                                Point.fromLngLat(bbox.east, bbox.north)
                        )))
                gridLines.add(feature)
            }
        }
        val gridCollection = FeatureCollection.fromFeatures(gridLines)
        (map.style?.getSource(GRID_SOURCE_ID) as GeoJsonSource).setGeoJson(gridCollection)
    }

    private fun updateTracking(newTile: Boolean) {
        if (service == null) return

        val lineSource = map.style?.getSourceAs<GeoJsonSource>(TRACKING_LINE_SOURCE_ID)
        lineSource?.setGeoJson(LineString.fromLngLats(
                service!!.track.map {
                    Point.fromLngLat(it.longitude, it.latitude)
                }.toList()))
        if (newTile) {
            trackingTilesDisposable = service!!.acquiredTiles.toObservable()
                    .map {
                        val bbox = tile2bbox(it.x, it.y)
                        Feature.fromGeometry(Polygon.fromLngLats(
                                listOf(
                                        listOf(
                                                Point.fromLngLat(bbox.west, bbox.north),
                                                Point.fromLngLat(bbox.east, bbox.north),
                                                Point.fromLngLat(bbox.east, bbox.south),
                                                Point.fromLngLat(bbox.west, bbox.south),
                                                Point.fromLngLat(bbox.west, bbox.north)))))
                    }
                    .toList()
                    .map(FeatureCollection::fromFeatures)
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map {
                        val tileSource = map.style?.getSourceAs<GeoJsonSource>(TRACKING_TILES_SOURCE_ID)
                        tileSource!!.setGeoJson(it)
                    }
                    .subscribe({}, {
                        it.printStackTrace()
                    })
        }
    }

    private fun clearTracking() {
        map.style?.getSourceAs<GeoJsonSource>(TRACKING_LINE_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(ArrayList()))
        map.style?.getSourceAs<GeoJsonSource>(TRACKING_TILES_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(ArrayList()))
    }

    private fun showSettings(isShow: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
//        transaction.setCustomAnimations(R.animator.enter_from_left, R.animator.exit_to_left)
        if (isShow) {
            settingsButton.hide()
            recordButton.hide()
            transaction.show(settingsFragment)
        } else {
            transaction.hide(settingsFragment)
            settingsButton.show()
            recordButton.show()
        }
        transaction.commit()
    }

    @SuppressLint("MissingPermission")
    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startRecording() {
        if (!checkPlayServices()) return

        val serviceIntent = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun pauseRecording() {
        sendBroadcast(Intent(TrackingService.ACTION_PAUSE))
    }

    private fun resumeRecording() {
        sendBroadcast(Intent(TrackingService.ACTION_RESUME))
    }

    private fun stopRecording() {
        sendBroadcast(Intent(TrackingService.ACTION_STOP))
    }

    private fun updateExplorerLayerColors(style: Style) {
        val layer = style.getLayer(EXPLORER_LAYER_ID) ?: return
        val colorE = preferences.getInt(explorerKey, 0)
        val colorC = preferences.getInt(clusterKey, 0)
        layer.setProperties(
                PropertyFactory.fillColor(
                        step(
                                get(TYPE_FLAG),
                                color(colorE),
                                stop(CLUSTER, color(colorC))))
                )
    }

    private fun updateLayerColor(style: Style, layerId: String, preferenceKey: String) {
        val layer = style.getLayer(layerId) ?: return
        val color = preferences.getInt(preferenceKey, 0)
        when (layer) {
            is LineLayer -> layer.setProperties(
                    PropertyFactory.lineColor(color(color)))
            is FillLayer -> layer.setProperties(
                    PropertyFactory.fillColor(color(color)))
        }

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
