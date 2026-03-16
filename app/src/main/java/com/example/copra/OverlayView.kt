package com.example.copra

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    data class Detection(
        val rectPreview: RectF,
        val label: String,
        val score: Float,
        val color: Int
    )

    var detections: List<Detection> = emptyList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (detection in detections) {
            boxPaint.color = detection.color
            canvas.drawRect(detection.rectPreview, boxPaint)

            val scorePercent = (detection.score * 100).toInt().coerceIn(0, 100)
            val labelText = "${detection.label} ${scorePercent}%"
            val labelWidth = max(180f, textPaint.measureText(labelText) + 36f)
            val labelHeight = 46f
            val labelTop = max(0f, detection.rectPreview.top - labelHeight)

            labelPaint.color = detection.color
            canvas.drawRect(
                detection.rectPreview.left,
                labelTop,
                detection.rectPreview.left + labelWidth,
                labelTop + labelHeight,
                labelPaint
            )

            canvas.drawText(
                labelText,
                detection.rectPreview.left + 12f,
                labelTop + labelHeight - 12f,
                textPaint
            )
        }
    }
}
