package com.radioinfo.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333355")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888AA")
        textSize = 24f
    }
    private val linePaints = mutableListOf<Paint>()
    private val path = Path()

    private var lines = mutableListOf<List<Float>>()
    private var lineColors = listOf(
        Color.parseColor("#E94560"),
        Color.parseColor("#00FF88"),
        Color.parseColor("#4488FF"),
        Color.parseColor("#FFAA00"),
        Color.parseColor("#FF44FF")
    )
    private var yMin = -120f
    private var yMax = -40f
    private var yLabel = "dBm"
    private var maxPoints = 60
    private var gridRows = 5

    init {
        lineColors.forEach { color ->
            linePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = 3f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }

    fun setLines(newLines: List<List<Float>>) {
        lines = newLines.map { it.takeLast(maxPoints).toMutableList() }.toMutableList()
        invalidate()
    }

    fun setYRange(min: Float, max: Float, label: String) {
        yMin = min; yMax = max; yLabel = label
    }

    fun setMaxPoints(p: Int) { maxPoints = p }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 60f; val padR = 20f; val padT = 10f; val padB = 40f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // Grid
        for (i in 0..gridRows) {
            val y = padT + chartH * i / gridRows
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
            val val_at = yMax - (yMax - yMin) * i / gridRows
            canvas.drawText("%.0f".format(val_at), 5f, y + 8f, textPaint)
        }

        // Y axis label
        textPaint.textSize = 20f
        canvas.drawText(yLabel, 5f, padT - 2f, textPaint)
        textPaint.textSize = 24f

        // Lines
        for ((idx, line) in lines.withIndex()) {
            if (line.size < 2) continue
            val paint = linePaints[idx % linePaints.size]
            path.reset()
            val step = chartW / (maxPoints - 1).coerceAtLeast(1)
            val startIdx = (maxPoints - line.size).coerceAtLeast(0)
            for (i in line.indices) {
                val x = padL + (startIdx + i) * step
                val ratio = ((line[i] - yMin) / (yMax - yMin)).coerceIn(0f, 1f)
                val y = padT + chartH * (1f - ratio)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }

        // Legend
        val labels = listOf("Download", "Upload", "Signal")
        for (i in 0 until minOf(lines.size, labels.size)) {
            val lx = padL + i * 120f
            textPaint.color = lineColors[i % lineColors.size]
            textPaint.textSize = 20f
            canvas.drawText(labels[i], lx, h - 5f, textPaint)
        }
        textPaint.color = Color.parseColor("#8888AA")
        textPaint.textSize = 24f
    }
}