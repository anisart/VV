package ru.anisart.vv

import com.google.gson.Gson
import com.mapbox.mapboxsdk.annotations.BasePointCollection
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLngBounds

class BBox(val north: Double, val south: Double, val west: Double, val east: Double)

fun tile2lon(x: Int, z: Int): Double {
    return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
}

fun tile2lat(y: Int, z: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * y.toDouble() / Math.pow(2.0, z.toDouble())
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

fun tile2bbox(x: Int, y: Int, zoom: Int): BBox {
    val north = tile2lat(y, zoom)
    val south = tile2lat(y + 1, zoom)
    val west = tile2lon(x, zoom)
    val east = tile2lon(x + 1, zoom)
    return BBox(north, south, west, east)
}

fun tile2bounds(x: Int, y: Int, zoom: Int): LatLngBounds {
    val north = tile2lat(y, zoom)
    val south = tile2lat(y + 1, zoom)
    val west = tile2lon(x, zoom)
    val east = tile2lon(x + 1, zoom)
    return LatLngBounds.from(north, east, south, west)
}

fun lon2tile(lon: Double, zoom: Int): Int {
    return Math.floor((lon + 180) / 360 * Math.pow(2.0, zoom.toDouble())).toInt()
}

fun lat2tile(lat: Double, zoom: Int): Int {
    return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) + 1 / Math.cos(lat * Math.PI / 180)) / Math.PI)
            / 2 * Math.pow(2.0, zoom.toDouble())).toInt()
}

fun point2bbox(lat: Double, lon: Double, zoom: Int): BBox {
    val x = lon2tile(lon, zoom)
    val y = lat2tile(lat, zoom)
    return tile2bbox(x, y, zoom)
}

fun point2bounds(lat: Double, lon: Double, zoom: Int): LatLngBounds {
    val x = lon2tile(lon, zoom)
    val y = lat2tile(lat, zoom)
    return tile2bounds(x, y, zoom)
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

inline fun <reified T: Any> Gson.fromJson(json: String): T = this.fromJson(json, T::class.java)