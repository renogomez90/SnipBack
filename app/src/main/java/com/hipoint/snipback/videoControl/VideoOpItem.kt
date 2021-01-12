package com.hipoint.snipback.videoControl

import android.os.Parcelable
import com.hipoint.snipback.enums.CurrentOperation
import com.hipoint.snipback.enums.SwipeAction
import com.hipoint.snipback.listener.IVideoOpListener
import kotlinx.android.parcel.Parcelize

@Parcelize
data class VideoOpItem(val operation: IVideoOpListener.VideoOp,
                       var clips: List<String>,
                       var outputPath: String,
                       var startTime: Float = -1F,
                       var endTime: Float = -1F,
                       var splitTime: Int = -1,
                       val speedDetailsList: ArrayList<SpeedDetails>? = arrayListOf(),
                       val comingFrom: CurrentOperation,
                       val swipeAction: SwipeAction = SwipeAction.NO_ACTION) : Parcelable {

    override fun toString(): String {
        return """
           | VideoOpItem(
           | operation=$operation, 
           | clips='$clips', 
           | outputPath='$outputPath', 
           | startTime=$startTime, 
           | endTime=$endTime,
           | splitTime=$splitTime,
           | speedDetails=$speedDetailsList,
           | comingFrom=$comingFrom,
           | swipeAction=$swipeAction
            """.trimMargin()
    }
}