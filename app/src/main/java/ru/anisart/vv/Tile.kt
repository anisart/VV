package ru.anisart.vv

import android.os.Parcel
import android.os.Parcelable

data class Tile(val x: Int, val y: Int, var isCluster: Boolean = false) : Parcelable {
    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tile

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    constructor(source: Parcel) : this(
            source.readInt(),
            source.readInt(),
            1 == source.readInt()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeInt(x)
        writeInt(y)
        writeInt((if (isCluster) 1 else 0))
    }

    companion object {
        fun fromString(string: String): Tile {
            val xy = string.split('-')
            return Tile(xy[0].toInt(), xy[1].toInt())
        }

        @JvmField
        val CREATOR: Parcelable.Creator<Tile> = object : Parcelable.Creator<Tile> {
            override fun createFromParcel(source: Parcel): Tile = Tile(source)
            override fun newArray(size: Int): Array<Tile?> = arrayOfNulls(size)
        }
    }
}