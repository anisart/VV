package ru.anisart.vv

import android.content.IntentFilter
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.mapboxsdk.annotations.BasePointCollection
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds

import ru.anisart.vv.App.Companion.EXPLORER_ZOOM

class BBox(val north: Double, val south: Double, val west: Double, val east: Double)

fun tile2lon(x: Int, z: Int = EXPLORER_ZOOM): Double {
    return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
}

fun tile2lat(y: Int, z: Int = EXPLORER_ZOOM): Double {
    val n = Math.PI - 2.0 * Math.PI * y.toDouble() / Math.pow(2.0, z.toDouble())
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

fun tile2bbox(x: Int, y: Int, zoom: Int = EXPLORER_ZOOM): BBox {
    val north = tile2lat(y, zoom)
    val south = tile2lat(y + 1, zoom)
    val west = tile2lon(x, zoom)
    val east = tile2lon(x + 1, zoom)
    return BBox(north, south, west, east)
}

fun tile2bounds(x: Int, y: Int, zoom: Int = EXPLORER_ZOOM): LatLngBounds {
    val north = tile2lat(y, zoom)
    val south = tile2lat(y + 1, zoom)
    val west = tile2lon(x, zoom)
    val east = tile2lon(x + 1, zoom)
    return LatLngBounds.from(north, east, south, west)
}

fun lon2tile(lon: Double, zoom: Int = EXPLORER_ZOOM): Int {
    return Math.floor((lon + 180) / 360 * Math.pow(2.0, zoom.toDouble())).toInt()
}

fun lat2tile(lat: Double, zoom: Int = EXPLORER_ZOOM): Int {
    return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) + 1 / Math.cos(lat * Math.PI / 180)) / Math.PI)
            / 2 * Math.pow(2.0, zoom.toDouble())).toInt()
}

fun point2bbox(lat: Double, lon: Double, zoom: Int = EXPLORER_ZOOM): BBox {
    val x = lon2tile(lon, zoom)
    val y = lat2tile(lat, zoom)
    return tile2bbox(x, y, zoom)
}

fun point2bounds(lat: Double, lon: Double, zoom: Int = EXPLORER_ZOOM): LatLngBounds {
    val x = lon2tile(lon, zoom)
    val y = lat2tile(lat, zoom)
    return tile2bounds(x, y, zoom)
}

fun latLng2tile(latLng: LatLng, zoom: Int = EXPLORER_ZOOM): Tile {
    val x = lon2tile(latLng.longitude, zoom)
    val y = lat2tile(latLng.latitude, zoom)
    return Tile(x, y)
}

fun alfaFromColor(rgba: Int) = ((rgba shr 24) and 0xFF) / 255f

fun BasePointCollection.toBounds(): LatLngBounds? {
    return if (points.size < 2) {
        null
    } else {
        LatLngBounds.Builder()
                .includes(points)
                .build()
    }
}

fun Any.toJson(): String = Gson().toJson(this)

inline fun <reified T> Gson.fromJson(json: String): T = this.fromJson<T>(json, object: TypeToken<T>() {}.type)

inline fun SharedPreferences.edit(func: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.func()
    editor.apply()
}

fun intentFilter(vararg actions: String) = IntentFilter().apply {
    actions.forEach(this::addAction)
}

fun <T> MutableSet<T>.addReplace(element: T) {
    if (!add(element)) {
        remove(element)
        add(element)
    }
}