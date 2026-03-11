package com.example.copra

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

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
        val color: Int
    )

    fun generateFakeResults(viewWidth: Int, viewHeight: Int) {

        if (viewWidth <= 250 || viewHeight <= 250) return

        boxes.clear()

        val grades = listOf("Grade A", "Grade B", "Grade C")
        val boxSize = 200f

        repeat(5) {

            val maxWidth = (viewWidth - boxSize).toInt()
            val maxHeight = (viewHeight - boxSize).toInt()

            if (maxWidth <= 50 || maxHeight <= 50) return

            val left = Random.nextInt(50, maxWidth).toFloat()
            val top = Random.nextInt(50, maxHeight).toFloat()

            val rect = RectF(left, top, left + boxSize, top + boxSize)

            val grade = grades.random()

            val color = when (grade) {
                "Grade A" -> Color.GREEN
                "Grade B" -> Color.YELLOW
                else -> Color.RED
            }

            boxes.add(Box(rect, grade, color))
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (box in boxes) {

            // Draw rectangle
            paint.style = Paint.Style.STROKE
            paint.color = box.color
            canvas.drawRect(box.rect, paint)

            // Draw label
            paint.style = Paint.Style.FILL
            canvas.drawText(
                box.label,
                box.rect.left,
                box.rect.top - 10,
                paint
            )
        }
    }
}