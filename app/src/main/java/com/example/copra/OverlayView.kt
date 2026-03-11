package com.yourpackage

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    data class Detection(
        val rect: RectF,
        val grade: String,
        val color: Int
    )

    var detections: List<Detection> = listOf()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (detection in detections) {

            boxPaint.color = detection.color
            canvas.drawRect(detection.rect, boxPaint)

            val labelPaint = Paint().apply {
                color = detection.color
                style = Paint.Style.FILL
            }

            canvas.drawRect(
                detection.rect.left,
                detection.rect.top - 50,
                detection.rect.left + 200,
                detection.rect.top,
                labelPaint
            )

            canvas.drawText(
                detection.grade,
                detection.rect.left + 10,
                detection.rect.top - 15,
                textPaint
            )
        }
    }
}