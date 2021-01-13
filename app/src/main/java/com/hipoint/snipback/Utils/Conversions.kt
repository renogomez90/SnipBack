package com.hipoint.snipback.Utils

import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.roundToInt

fun Long.milliToFloatSecond(): Float{
    return this.toFloat() / 1000
}

fun Int.dpToPx(context: Context): Int {
    val displayMetrics = context.resources.displayMetrics
    return (this * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
}