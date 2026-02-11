package com.example.myapplication3.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.myapplication3.data.KnowledgeGraph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class KnowledgeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var graph: KnowledgeGraph? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7FBFD.toInt()
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A19C4E9.toInt()
        strokeWidth = dp(1f)
    }

    private val nodeFill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val nodeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF19C4E9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }

    private val nodeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14000000
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2328.toInt()
        textSize = dp(12f)
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF19C4E9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        alpha = 180
    }

    private val edgeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B7280.toInt()
        textSize = dp(11f)
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF999999.toInt()
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
    }

    private val nodeRects = LinkedHashMap<String, RectF>()
    private val nodeCenters = LinkedHashMap<String, Pair<Float, Float>>()

    // transform
    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f
    private val minScale = 0.25f
    private val maxScale = 3.0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prev = scale
            val target = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            if (target == prev) return true

            val focusX = detector.focusX
            val focusY = detector.focusY
            val wx = (focusX - translateX) / prev
            val wy = (focusY - translateY) / prev

            scale = target
            translateX = focusX - wx * scale
            translateY = focusY - wy * scale

            invalidate()
            return true
        }
    })

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    fun setGraph(g: KnowledgeGraph?) {
        graph = g
        nodeCenters.clear()
        nodeRects.clear()
        requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // background + grid
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGrid(canvas)

        val g = graph
        if (g == null || g.nodes.isEmpty()) {
            canvas.drawText("暂无图谱数据", width / 2f, height / 2f, placeholderPaint)
            return
        }

        if (nodeCenters.isEmpty()) {
            if (width <= 0 || height <= 0) return
            layoutNodes(g)
            fitToView()
        }

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scale, scale)

        // edges (curved)
        val idToCenter = nodeCenters
        for (e in g.edges) {
            val p1 = idToCenter[e.from] ?: continue
            val p2 = idToCenter[e.to] ?: continue

            val path = Path()
            path.moveTo(p1.first, p1.second)
            // simple quadratic curve
            val mx = (p1.first + p2.first) / 2f
            val my = (p1.second + p2.second) / 2f
            val ctrlX = mx
            val ctrlY = my - dp(30f)
            path.quadTo(ctrlX, ctrlY, p2.first, p2.second)
            canvas.drawPath(path, edgePaint)

            if (e.label.isNotBlank()) {
                canvas.drawText(e.label, mx + dp(4f), my - dp(6f), edgeLabelPaint)
            }
        }

        // nodes
        for (n in g.nodes) {
            val rect = nodeRects[n.id] ?: continue

            val shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                0xFFFFFFFF.toInt(),
                0xFFEAFBFF.toInt(),
                Shader.TileMode.CLAMP
            )
            nodeFill.shader = shader

            val shadowRect = RectF(rect)
            shadowRect.offset(dp(2f), dp(3f))
            canvas.drawRoundRect(shadowRect, dp(12f), dp(12f), nodeShadow)

            canvas.drawRoundRect(rect, dp(12f), dp(12f), nodeFill)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), nodeStroke)

            val x = rect.left + dp(10f)
            val y = rect.centerY() + (textPaint.textSize / 2f) - dp(3f)
            canvas.drawText(n.label, x, y, textPaint)
        }

        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val step = dp(28f)
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun layoutNodes(g: KnowledgeGraph) {
        nodeRects.clear()
        nodeCenters.clear()

        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val cx = w / 2f
        val cy = h / 2f

        val radius = min(w, h) * 0.33f
        val count = g.nodes.size

        for (i in 0 until count) {
            val n = g.nodes[i]
            val angle = 2.0 * PI * i / count
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()

            val nodeW = max(dp(120f), textPaint.measureText(n.label) + dp(30f))
            val nodeH = dp(38f)
            val rect = RectF(x - nodeW / 2f, y - nodeH / 2f, x + nodeW / 2f, y + nodeH / 2f)

            nodeRects[n.id] = rect
            nodeCenters[n.id] = Pair(x, y)
        }
    }

    private fun fitToView() {
        if (nodeRects.isEmpty()) return

        val rect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (r in nodeRects.values) {
            rect.left = min(rect.left, r.left)
            rect.top = min(rect.top, r.top)
            rect.right = max(rect.right, r.right)
            rect.bottom = max(rect.bottom, r.bottom)
        }

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = dp(18f)
        val contentW = max(1f, rect.width())
        val contentH = max(1f, rect.height())

        val sx = (w - pad * 2) / contentW
        val sy = (h - pad * 2) / contentH
        scale = min(1f, min(sx, sy)).coerceIn(minScale, maxScale)

        val scaledW = contentW * scale
        val scaledH = contentH * scale
        translateX = (w - scaledW) / 2f - rect.left * scale
        translateY = (h - scaledH) / 2f - rect.top * scale
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        nodeCenters.clear()
        nodeRects.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (!dragging || event.pointerCount != 1) return true

                val dx = event.x - lastX
                val dy = event.y - lastY
                translateX += dx
                translateY += dy
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
