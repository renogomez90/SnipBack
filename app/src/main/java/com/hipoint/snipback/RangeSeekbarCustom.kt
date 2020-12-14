package com.hipoint.snipback

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.DisplayMetrics
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar
import kotlin.math.roundToInt


class RangeSeekbarCustom : CrystalRangeSeekbar {
    private var minStart = 0f
    private var maxStart = 100f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

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

    private fun Int.dpToPx(): Int {
        val displayMetrics = context.resources.displayMetrics
        return (this * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }
}