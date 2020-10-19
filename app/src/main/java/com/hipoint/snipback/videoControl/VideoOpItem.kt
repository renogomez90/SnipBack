package com.hipoint.snipback.videoControl

import android.os.Parcel
import android.os.Parcelable
import com.hipoint.snipback.listener.IVideoOpListener
import kotlinx.android.parcel.Parcelize

@Parcelize
data class VideoOpItem(val operation: IVideoOpListener.VideoOp,
                       var clip1: String,
                       var clip2: String,
                       var outputPath: String,
                       var startTime: Int = -1,
                       var endTime: Int = -1,
                       var splitTime: Int = -1,
                       val speedDetailsList: ArrayList<SpeedDetails>? = arrayListOf()) : Parcelable {

    /*constructor(parcel: Parcel) : this(
            operation = parcel.readSerializable() as IVideoOpListener.VideoOp,
            clip1 = parcel.readString()!!,
            clip2 = parcel.readString()!!,
            outputPath = parcel.readString()!!,
            startTime = parcel.readInt(),
            endTime = parcel.readInt(),
            splitTime = parcel.readInt(),
            speedDetailsList = parcel.readArrayList(SpeedDetails::class.java.classLoader) as ArrayList<SpeedDetails>) {
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeSerializable(operation)
        dest?.writeString(clip1)
        dest?.writeString(clip2)
        dest?.writeString(outputPath)
        dest?.writeInt(startTime)
        dest?.writeInt(endTime)
        dest?.writeInt(splitTime)
        dest?.writeList(speedDetailsList as List<SpeedDetails>)
    }

    companion object CREATOR : Parcelable.Creator<VideoOpItem> {
        override fun createFromParcel(parcel: Parcel): VideoOpItem {
            return VideoOpItem(parcel)
        }

        override fun newArray(size: Int): Array<VideoOpItem?> {
            return arrayOfNulls(size)
        }
    }
*/
    override fun toString(): String {
        return """
            VideoOpItem(
            operation=$operation, 
            clip1='$clip1', 
            clip2='$clip2', 
            outputPath='$outputPath', 
            startTime=$startTime, 
            endTime=$endTime,
            splitTime=$splitTime),
            speedDetails=$speedDetailsList
            """.trimMargin()
    }
}