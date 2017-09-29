package ru.anisart.vv

import android.Manifest
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
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.patloew.rxlocation.RxLocation
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.toast


class AlertService: Service() {

    private val SERVICE_ID = 1
    private val CHANNEL_ID = "CHANNEL_ID"
    private val CHANNEL_NAME = "Tile alerts"
    private val CHANNEL_DESCRIPTION = ""
    private val ACTION_STOP = "stop_service"
    private val ACTION_ENTER = "enter_zone"
    private val PREFERENCE_BOUNDS = "PREFERENCE_BOUNDS"
    private val RADIUS = 1000f / Math.sqrt(2.0).toFloat()

    private val binder = LocalBinder()
    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var rxLocation: RxLocation
    private lateinit var receiver: BroadcastReceiver
    var targetBounds: LatLngBounds? = null
        private set
    private var subscription: Disposable? = null

    inner class LocalBinder: Binder() {
        fun getService() = this@AlertService
    }

    override fun onCreate() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        rxLocation = RxLocation(this)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_STOP -> stopSelf()
                    ACTION_ENTER -> alert()
                }
            }
        }
        registerReceiver(receiver, IntentFilter(ACTION_STOP).apply { addAction(ACTION_ENTER) })
        val boundsString = preferences.getString(PREFERENCE_BOUNDS, null)
        if (!boundsString.isNullOrEmpty() && checkPermission()) {
            targetBounds = Gson().fromJson<LatLngBounds>(boundsString)
            startLocating()
        } else {
            stopSelf()
            return
        }
        val stopIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = getNotification()
                .addAction(0, "Stop", stopIntent)
        startForeground(SERVICE_ID, notification.build())
    }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetBounds = intent?.getParcelableExtra<LatLngBounds>(App.EXTRA_TARGET_BOUNDS) ?: return START_NOT_STICKY
        preferences.edit().putString(PREFERENCE_BOUNDS, targetBounds!!.toJson()).apply()
        startLocating()
        return START_STICKY
    }

    override fun onDestroy() {
        subscription?.dispose()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private var client: GoogleApiClient? = null

    private fun startLocating() {
//        val locationRequest = LocationRequest.create()
//                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
//                .setInterval(5000)
//        subscription = rxLocation.location().updates(locationRequest)
//                .filter { targetBounds!!.contains(LatLng(it)) }
//                .subscribe({
//                    subscription?.dispose()
//                    alert()
//                }, {
//                    it.printStackTrace()
//                })
        val center = targetBounds!!.center
        val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(Geofence.Builder()
                        .setRequestId("id")
                        .setCircularRegion(center.latitude, center.longitude, RADIUS)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        .setExpirationDuration(60*60*1000)
                        .build())
                .build()
        val pendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_ENTER), PendingIntent.FLAG_UPDATE_CURRENT)
//        subscription = rxLocation.geofencing()
//                .remove(pendingIntent)
//                .flatMap {
//                    rxLocation.geofencing().add(geofencingRequest, pendingIntent)
//                }
//                .subscribe({
//                    toast("Geofence added!")
//                    subscription?.dispose()
//                }, {
//                    it.printStackTrace()
//                })
        client = GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(p0: Bundle?) {
                        toast("onConnected")
                        LocationServices.GeofencingApi.addGeofences(client, geofencingRequest, pendingIntent)
                                .setResultCallback { if (it.isSuccess) toast("added") }
                    }

                    override fun onConnectionSuspended(p0: Int) {
                        toast("onConnectionSuspended")
                    }
                })
                .addOnConnectionFailedListener { toast("OnConnectionFailed") }
                .build()
    }

    private fun alert() {
        toast("Alert!")
        preferences.edit().putString(PREFERENCE_BOUNDS, null).apply()
        stopSelf()

        val notification = getNotification()
                .setContentText("Tile has been acquired")
                .setAutoCancel(true)
        notificationManager.notify(SERVICE_ID, notification.build())
    }

    private fun getNotification(): NotificationCompat.Builder {
        val intent = Intent(this, MapActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMainNotificationChannel()
            NotificationCompat.Builder(this, getMainNotificationChannelId())
        } else {
            NotificationCompat.Builder(this)
        }
        return notification
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Alert tile")
                .setContentIntent(contentIntent)
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
                + "://" + packageName + "/raw/timer.ogg"), attributes)
        channel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        channel.setShowBadge(true)
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkPermission() =
            PackageManager.PERMISSION_GRANTED ==
                    checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
}