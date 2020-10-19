package com.hipoint.snipback.videoControl

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SpeedDetails(
     val isFast      : Boolean   = false,
     var multiplier  : Int       = 1,
     var timeDuration: Pair<Long, Long>? = null
):Parcelable{
    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        return "isFast = $isFast\n" +
                "multiplier = $multiplier\n" +
                "timeDuration = ${timeDuration?.first}:${timeDuration?.second}"
    }
}