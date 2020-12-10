package com.hipoint.snipback.videoControl

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SpeedDetails(
        var startWindowIndex: Int       = 0,
        var endWindowIndex  : Int       = 0,
        var isFast          : Boolean   = false,
        var multiplier      : Int       = 1,
        var timeDuration    : Pair<Long, Long>? = null
): Parcelable{
    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        return "startWindowIndex = ${startWindowIndex}\n" +
                "endWindowIndex = ${endWindowIndex}\n" +
                "isFast = $isFast\n" +
                "multiplier = $multiplier\n" +
                "timeDuration = ${timeDuration?.first}:${timeDuration?.second}"
    }
}