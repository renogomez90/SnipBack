package com.hipoint.snipback.Utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.VectorDrawable
import android.util.DisplayMetrics
import java.io.File
import kotlin.math.roundToInt

fun Long.milliToFloatSecond(): Float{
    return this.toFloat() / 1000
}

fun Int.dpToPx(context: Context): Int {
    val displayMetrics = context.resources.displayMetrics
    return (this * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
}

/**
 * Checks if the required item is available in the list
 *
 * @param listOfPaths ArrayList<String>
 * @param filePath String?
 * @return Boolean
 */
fun ArrayList<String>.isPathInList(filePath: String?): Boolean {
    var isInList = false
    this.forEach {
        if (File(it).nameWithoutExtension == File(filePath!!).nameWithoutExtension)
            isInList = true
    }
    return isInList
}
