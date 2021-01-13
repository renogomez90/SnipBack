package com.hipoint.snipback

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.DisplayMetrics
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar
import com.example.crystalrangeseekbar.R
import kotlin.math.roundToInt

class RangeSeekbarCustom @JvmOverloads constructor(context: Context, val attrs: AttributeSet? = null, defStyleAttr: Int = 0): CrystalRangeSeekbar(context,attrs,defStyleAttr) {
    private var minStart = 0f
    private var maxStart = 100f

    override fun getBarHeight(): Float {
        return thumbHeight
    }

    override fun getHorizontalScrollbarHeight(): Int {
        return thumbHeight.toInt()
    }

    override fun setMinStartValue(minStartValue: Float): CrystalRangeSeekbar {
        this.minStart = minStartValue
        return super.setMinStartValue(minStartValue)
    }

    override fun setMaxStartValue(maxStartValue: Float): CrystalRangeSeekbar {
        this.maxStart = maxStartValue
        return super.setMaxStartValue(maxStartValue)
    }

    override fun setupHighlightBar(canvas: Canvas?, paint: Paint?, rect: RectF?) {
        rect!!.top = rect.top - thumbHeight
        rect.bottom = rect.bottom + thumbHeight

        super.setupHighlightBar(canvas, paint, rect)
    }

    override fun getMaxStartValue(typedArray: TypedArray?): Float {
        return super.getMaxStartValue(typedArray)
    }

    fun maxSelection(): Float {
        val maxval = getMaxStartValue(context.obtainStyledAttributes(attrs, R.styleable.CrystalRangeSeekbar))
        return maxval
    }

    override fun getMinStartValue(typedArray: TypedArray?): Float {
        return super.getMinStartValue(typedArray)
    }

    fun minSelection(): Float {
        return getMinStartValue(context.obtainStyledAttributes(attrs, R.styleable.CrystalRangeSeekbar))
    }
}