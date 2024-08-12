package com.mohammadkk.mymusicplayer.models

import android.os.Parcel
import android.os.Parcelable

data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString().orEmpty(),
        parcel.readInt()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeInt(songCount)
    }
    override fun describeContents(): Int {
        return 0
    }
    companion object CREATOR : Parcelable.Creator<Genre> {
        override fun createFromParcel(parcel: Parcel): Genre {
            return Genre(parcel)
        }
        override fun newArray(size: Int): Array<Genre?> {
            return arrayOfNulls(size)
        }
    }
}