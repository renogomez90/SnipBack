package com.hipoint.snipback.listener

import com.hipoint.snipback.videoControl.SpeedDetails

/**
 * To be used with the edit list adapter
 * shows the edit point to which the user should be taken to
 */
interface IJumpToEditPoint {

    fun editPoint(position: Int, speedDetails: SpeedDetails)
}