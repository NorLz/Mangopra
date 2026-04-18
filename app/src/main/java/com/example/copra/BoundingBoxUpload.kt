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

class BoundingBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxes = mutableListOf<Box>()

    private val paint = Paint().apply {
        strokeWidth = 6f
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    data class Box(
        val rect: RectF,
        val label: String,
        val color: Int,
        val score: Float = 0f
    )

    fun setBoxes(newBoxes: List<Box>) {
        boxes.clear()
        boxes.addAll(newBoxes)
        invalidate()
    }

    fun clearBoxes() {
        boxes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (box in boxes) {

            // Draw rectangle
            paint.style = Paint.Style.STROKE
            paint.color = box.color
            canvas.drawRect(box.rect, paint)

            val scorePercent = (box.score * 100).toInt().coerceIn(0, 100)
            val labelText = if (box.score > 0f) {
                "${box.label} $scorePercent%"
            } else {
                box.label
            }
            val labelWidth = max(180f, paint.measureText(labelText) + 32f)
            val labelTop = max(0f, box.rect.top - 52f)

            paint.style = Paint.Style.FILL
            canvas.drawRect(
                box.rect.left,
                labelTop,
                box.rect.left + labelWidth,
                labelTop + 44f,
                paint
            )

            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawText(
                labelText,
                box.rect.left + 12f,
                labelTop + 32f,
                paint
            )
        }
    }
}
