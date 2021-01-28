package com.hipoint.snipback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.hipoint.snipback.Utils.dpToPx


class FocusView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var selectedX: Float = 0.0F
    private var selectedY: Float = 0.0F
    private var showFocusPoint = false

    fun setFocusPoint(x: Float, y: Float){
        selectedX = x
        selectedY = y
    }

    fun startShowing(){
        showFocusPoint = true
        invalidate()
        val handler = Handler()
        val runnable = Runnable {
            showFocusPoint = false
            invalidate()
        }
        handler.postDelayed(runnable, 500)
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if(showFocusPoint) {
            val paint = Paint()
            paint.color = Color.WHITE
            paint.alpha = 255
//            canvas?.drawCircle(selectedX, selectedY, 50.dpToPx(context).toFloat(), paint)
            canvas?.drawBitmap(getFocusDrawable()!!, selectedX - 50.dpToPx(context), selectedY - 50.dpToPx(context), paint)
        }
    }

    private fun getFocusDrawable(): Bitmap? {
        var drawable = ContextCompat.getDrawable(context!!, R.drawable.ic_focus)
        val bitmap = Bitmap.createBitmap(drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}