package ru.anisart.vv

class BBox(val north: Double, val south: Double, val west: Double, val east: Double)

fun tile2lon(x: Int, z: Int): Double {
    return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
}

fun tile2lat(y: Int, z: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * y.toDouble() / Math.pow(2.0, z.toDouble())
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

fun tile2boundingBox(x: Int, y: Int, zoom: Int): BBox {
    val north = tile2lat(y, zoom)
    val south = tile2lat(y + 1, zoom)
    val west = tile2lon(x, zoom)
    val east = tile2lon(x + 1, zoom)
    return BBox(north, south, west, east)
}

fun lon2tile(lon: Double, zoom: Int): Int {
    return Math.floor((lon + 180) / 360 * Math.pow(2.0, zoom.toDouble())).toInt()
}

fun lat2tile(lat: Double, zoom: Int): Int {
    return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) + 1 / Math.cos(lat * Math.PI / 180)) / Math.PI)
            / 2 * Math.pow(2.0, zoom.toDouble())).toInt()
}