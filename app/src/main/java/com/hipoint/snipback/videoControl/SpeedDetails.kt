package com.hipoint.snipback.videoControl

data class SpeedDetails(val isFast: Boolean,
     var multiplier: Int = 1,
     var timeDuration: Pair<Long, Long>? = null
)