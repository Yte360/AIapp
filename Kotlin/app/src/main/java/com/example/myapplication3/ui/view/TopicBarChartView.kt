package com.example.myapplication3.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class TopicBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Entry(val label: String, val value: Int)

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDDDDD.toInt()
        strokeWidth = dp(1f)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF19C4E9.toInt()
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = dp(12f)
    }

    private var data: List<Entry> = emptyList()

    fun setData(entries: List<Entry>) {
        data = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val paddingLeft = dp(12f)
        val paddingRight = dp(12f)
        val paddingTop = dp(10f)
        val paddingBottom = dp(28f)

        val w = width.toFloat()
        val h = height.toFloat()

        val chartLeft = paddingLeft
        val chartRight = w - paddingRight
        val chartTop = paddingTop
        val chartBottom = h - paddingBottom

        // X axis
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        val maxValue = max(1, data.maxOf { it.value })
        val count = data.size
        val slot = (chartRight - chartLeft) / count
        val barWidth = slot * 0.55f

        for (i in 0 until count) {
            val entry = data[i]
            val xCenter = chartLeft + slot * i + slot / 2f
            val barLeft = xCenter - barWidth / 2f
            val barRight = xCenter + barWidth / 2f

            val ratio = entry.value.toFloat() / maxValue
            val barTop = chartBottom - (chartBottom - chartTop) * ratio

            canvas.drawRoundRect(
                barLeft,
                barTop,
                barRight,
                chartBottom,
                dp(6f),
                dp(6f),
                barPaint
            )

            // value label
            val valueText = entry.value.toString()
            val valueWidth = textPaint.measureText(valueText)
            canvas.drawText(valueText, xCenter - valueWidth / 2f, barTop - dp(6f), textPaint)

            // x label
            val label = entry.label
            val labelWidth = textPaint.measureText(label)
            canvas.drawText(label, xCenter - labelWidth / 2f, h - dp(10f), textPaint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
