package com.hipoint.snipback.Utils

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Nullable
import com.google.android.exoplayer2.ui.DefaultTimeBar
import java.util.concurrent.TimeUnit

class EditTimeBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): DefaultTimeBar(context) {
    override fun getPreferredUpdateDelay(): Long {
        return 50L
    }
}