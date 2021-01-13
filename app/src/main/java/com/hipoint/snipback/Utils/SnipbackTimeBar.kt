package com.hipoint.snipback.Utils

import android.content.Context
import android.util.AttributeSet
import com.google.android.exoplayer2.ui.DefaultTimeBar

class SnipbackTimeBar@JvmOverloads constructor(context: Context, val attrs: AttributeSet? = null, defStyleAttr: Int = 0):
    DefaultTimeBar(context, attrs, defStyleAttr) {

    override fun getPreferredUpdateDelay(): Long {
        return 5L
    }

}