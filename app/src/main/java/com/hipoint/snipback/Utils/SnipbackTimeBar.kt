package com.hipoint.snipback.Utils

import android.content.Context
import android.util.AttributeSet
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar

class SnipbackTimeBar@JvmOverloads constructor(context: Context, val attrs: AttributeSet? = null, defStyleAttr: Int = 0):
    DefaultTimeBar(context, attrs, defStyleAttr){

    private var player: SimpleExoPlayer? = null

    override fun getPreferredUpdateDelay(): Long {
        return 5L
    }


}