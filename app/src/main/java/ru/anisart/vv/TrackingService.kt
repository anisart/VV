package ru.anisart.vv

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import com.google.android.gms.location.LocationRequest
import com.google.gson.Gson
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.patloew.rxlocation.RxLocation
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.toast
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


class TrackingService : Service() {

    companion object {
        val EXTRA_TARGET_BOUNDS = "target_bounds"
        val EXTRA_NEW_TILE = "new_tile"
        val PREFERENCE_ACQUIRED_TILES = "acquired_tiles"
        val PREFERENCE_TRACK = "track"
        val ACTION_START = TrackingService::class.java.canonicalName + "start"
        val ACTION_PAUSE = TrackingService::class.java.canonicalName + "pause"
        val ACTION_RESUME = TrackingService::class.java.canonicalName + "resume"
        val ACTION_STOP = TrackingService::class.java.canonicalName + "stop"
        val ACTION_TRACK = TrackingService::class.java.canonicalName + "track"
    }

    enum class State {
        INIT, RECORDING, PAUSED, STOPPED
    }

    private val SERVICE_ID = 1
    private val CHANNEL_ID = "CHANNEL_ID"
    private val CHANNEL_NAME = "Tile alerts"
    private val CHANNEL_DESCRIPTION = ""
    private val PREFERENCE_BOUNDS = "PREFERENCE_BOUNDS"

    private val binder = LocalBinder()
    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var rxLocation: RxLocation
    private lateinit var locationRequest: LocationRequest
    private lateinit var receiver: BroadcastReceiver
    var targetBounds: LatLngBounds? = null
        private set
    private var subscription: Disposable? = null
    val track = ArrayList<LatLng>()
    var state = State.INIT
        private set
    val acquiredTiles = HashSet<Tile>()

    lateinit var pauseString: String
    lateinit var resumeString: String
    lateinit var stopString: String

    inner class LocalBinder: Binder() {
        fun getService() = this@TrackingService
    }

    override fun onCreate() {
        pauseString = getString(R.string.action_pause)
        resumeString = getString(R.string.action_resume)
        stopString = getString(R.string.action_stop)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        rxLocation = RxLocation(this)
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_STOP -> stop()
                    ACTION_PAUSE -> pause()
                    ACTION_RESUME -> resume()
                }
            }
        }
        registerReceiver(receiver, intentFilter(ACTION_STOP, ACTION_PAUSE, ACTION_RESUME))
        if (!checkPermission()) {
            stop()
            return
        } else {
            preferences.getString(PREFERENCE_TRACK, null)
                    ?.let { track += Gson().fromJson<ArrayList<LatLng>>(it) }
            preferences.getString(PREFERENCE_ACQUIRED_TILES, null)
                    ?.let { acquiredTiles += Gson().fromJson<HashSet<Tile>>(it) }
            resume()
        }
        System.err.println("startForeground " + Date().toString())
        startForeground(SERVICE_ID, getNotification().build())
    }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!checkPermission()) {
            return START_NOT_STICKY
        }
        System.err.println("onStartCommand " + Date().toString())
        sendBroadcast(Intent(ACTION_START))
        resume()
        return START_STICKY
    }

    override fun onDestroy() {
        System.err.println("onDestroy " + Date().toString())
        subscription?.dispose()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun processIntent(intent: Intent?): Boolean {
        targetBounds = intent?.getParcelableExtra<LatLngBounds>(EXTRA_TARGET_BOUNDS) ?: return false
        preferences.edit().putString(PREFERENCE_BOUNDS, targetBounds!!.toJson()).apply()
        resume()
        return true
    }

    private fun stop() {
        state = State.STOPPED
        preferences.edit {
            putString(PREFERENCE_TRACK, null)
            putString(PREFERENCE_ACQUIRED_TILES, null)
        }
        stopSelf()
    }

    private fun pause() {
        state = State.PAUSED
        subscription?.dispose()
        val resumeIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_RESUME), PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = getNotification()
                .addAction(0, resumeString, resumeIntent)
                .addAction(0, stopString, stopIntent)
        notificationManager.notify(SERVICE_ID, notification.build())
    }

    private fun resume() {
        state = State.RECORDING
        startLocating()
        val pauseIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = getNotification()
                .addAction(0, pauseString, pauseIntent)
                .addAction(0, stopString, stopIntent)
        notificationManager.notify(SERVICE_ID, notification.build())
    }

    @SuppressLint("MissingPermission")
    private fun startLocating() {
        subscription?.dispose()
        subscription = rxLocation.location().updates(locationRequest)
                .map(::LatLng)
                .distinctUntilChanged()
                .doOnNext{
                    track.add(it)
                    val newTile = acquiredTiles.add(latLng2tile(it))
                    sendBroadcast(Intent(ACTION_TRACK)
                            .putExtra(EXTRA_NEW_TILE, newTile))
                    preferences.edit {
                        putString(PREFERENCE_TRACK, track.toJson())
                        if (newTile) putString(PREFERENCE_ACQUIRED_TILES, acquiredTiles.toJson())
                    }
                }
//                .filter { targetBounds!!.contains(it) }
                .subscribe({
//                    subscription?.dispose()
//                    alert()
                }, {
                    it.printStackTrace()
                })
    }

    private fun alert() {
        toast("Alert!")
        preferences.edit().putString(PREFERENCE_BOUNDS, null).apply()
        stop()

        val notification = getNotification()
                .setContentText("Tile has been acquired")
                .setAutoCancel(true)
                .setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                        + "://" + packageName + "/raw/timer"))
        notificationManager.notify(SERVICE_ID, notification.build())
    }

    private fun getNotification(): NotificationCompat.Builder {
        val intent = Intent(this, MapActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMainNotificationChannel()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Recording track")
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getMainNotificationChannelId(): String = CHANNEL_ID

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMainNotificationChannel() {
        val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        channel.description = CHANNEL_DESCRIPTION
        channel.enableLights(true)
        channel.lightColor = Color.RED
        channel.enableVibration(true)
        channel.setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://" + packageName + "/raw/timer"), attributes)
        channel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        channel.setShowBadge(true)
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkPermission() =
            PackageManager.PERMISSION_GRANTED ==
                    checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
}