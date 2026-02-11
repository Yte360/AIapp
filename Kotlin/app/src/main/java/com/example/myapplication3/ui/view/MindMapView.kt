package com.example.myapplication3.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.myapplication3.data.MindMapNode
import kotlin.math.max
import kotlin.math.min

class MindMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var root: MindMapNode? = null

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

    private val nodeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14000000
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2328.toInt()
        textSize = dp(12f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF19C4E9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        alpha = 210
    }

    private data class LayoutNode(
        val node: MindMapNode,
        var x: Float = 0f,
        var y: Float = 0f,
        var w: Float = 0f,
        var h: Float = 0f,
        val children: MutableList<LayoutNode> = mutableListOf()
    )

    private var layoutRoot: LayoutNode? = null

    // content bounds
    private var contentMinX = 0f
    private var contentMinY = 0f
    private var contentMaxX = 0f
    private var contentMaxY = 0f

    // transform
    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val minScale = 0.25f
    private val maxScale = 3.0f

    // gestures
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scale
            val target = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            if (target == prevScale) return true

            val focusX = detector.focusX
            val focusY = detector.focusY

            // world point under focus before scaling
            val wx = (focusX - translateX) / prevScale
            val wy = (focusY - translateY) / prevScale

            scale = target

            // keep the same world point under the focus after scaling
            translateX = focusX - wx * scale
            translateY = focusY - wy * scale

            invalidate()
            return true
        }
    })

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    fun setMindMap(node: MindMapNode?) {
        root = node
        layoutRoot = root?.let { buildLayoutTree(it) }
        computeLayoutAndFit()
        invalidate()
    }

    private fun computeLayoutAndFit() {
        val r = layoutRoot ?: return

        val padding = dp(16f)
        val nodeH = dp(38f)
        val colGap = dp(64f)
        val rowGap = dp(18f)

        // compute positions
        val nextY = floatArrayOf(padding)
        layoutTree(r, depth = 0, nodeH = nodeH, colGap = colGap, rowGap = rowGap, nextY = nextY)

        // compute bounds
        val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        computeBounds(r, bounds)
        contentMinX = bounds.left
        contentMinY = bounds.top
        contentMaxX = bounds.right
        contentMaxY = bounds.bottom

        fitToView()
    }

    private fun fitToView() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = dp(18f)
        val contentW = max(1f, contentMaxX - contentMinX)
        val contentH = max(1f, contentMaxY - contentMinY)

        val sx = (w - pad * 2) / contentW
        val sy = (h - pad * 2) / contentH
        scale = min(1f, min(sx, sy)).coerceIn(minScale, maxScale)

        val scaledW = contentW * scale
        val scaledH = contentH * scale
        translateX = (w - scaledW) / 2f - contentMinX * scale
        translateY = (h - scaledH) / 2f - contentMinY * scale
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (layoutRoot != null) {
            fitToView()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = layoutRoot ?: return

        // background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGrid(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scale, scale)

        drawConnections(canvas, r)
        drawNodes(canvas, r)

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (!isDragging || event.pointerCount != 1) return true

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                translateX += dx
                translateY += dy
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    private fun buildLayoutTree(node: MindMapNode): LayoutNode {
        val ln = LayoutNode(node)
        node.children.forEach { ln.children.add(buildLayoutTree(it)) }
        return ln
    }

    private fun layoutTree(
        ln: LayoutNode,
        depth: Int,
        nodeH: Float,
        colGap: Float,
        rowGap: Float,
        nextY: FloatArray
    ) {
        val nodeW = max(dp(120f), textPaint.measureText(ln.node.topic) + dp(30f))
        ln.w = nodeW
        ln.h = nodeH
        ln.x = dp(16f) + depth * (nodeW + colGap)

        if (ln.children.isEmpty()) {
            ln.y = nextY[0]
            nextY[0] = nextY[0] + nodeH + rowGap
            return
        }

        val before = nextY[0]
        ln.children.forEach { child ->
            layoutTree(child, depth + 1, nodeH, colGap, rowGap, nextY)
        }
        val childrenTop = ln.children.first().y
        val childrenBottom = ln.children.last().y + nodeH
        ln.y = (childrenTop + childrenBottom) / 2f - nodeH / 2f

        if (ln.y < before) {
            val delta = before - ln.y
            shiftSubtree(ln, delta)
        }
    }

    private fun shiftSubtree(ln: LayoutNode, dy: Float) {
        ln.y += dy
        ln.children.forEach { shiftSubtree(it, dy) }
    }

    private fun computeBounds(ln: LayoutNode, bounds: RectF) {
        bounds.left = min(bounds.left, ln.x)
        bounds.top = min(bounds.top, ln.y)
        bounds.right = max(bounds.right, ln.x + ln.w)
        bounds.bottom = max(bounds.bottom, ln.y + ln.h)
        ln.children.forEach { computeBounds(it, bounds) }
    }

    private fun drawConnections(canvas: Canvas, ln: LayoutNode) {
        for (child in ln.children) {
            val startX = ln.x + ln.w
            val startY = ln.y + ln.h / 2f
            val endX = child.x
            val endY = child.y + child.h / 2f

            val path = Path()
            path.moveTo(startX, startY)
            val midX = (startX + endX) / 2f
            path.cubicTo(midX, startY, midX, endY, endX, endY)
            canvas.drawPath(path, linePaint)

            drawConnections(canvas, child)
        }
    }

    private fun drawNodes(canvas: Canvas, ln: LayoutNode) {
        val rect = RectF(ln.x, ln.y, ln.x + ln.w, ln.y + ln.h)

        // gradient fill
        val shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            0xFFFFFFFF.toInt(),
            0xFFEAFBFF.toInt(),
            Shader.TileMode.CLAMP
        )
        nodeFill.shader = shader

        // subtle shadow under node
        val shadowRect = RectF(rect)
        shadowRect.offset(dp(2f), dp(3f))
        canvas.drawRoundRect(shadowRect, dp(12f), dp(12f), nodeShadowPaint)

        canvas.drawRoundRect(rect, dp(12f), dp(12f), nodeFill)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), nodeStroke)

        val text = ln.node.topic
        val textX = rect.left + dp(12f)
        val textY = rect.centerY() + (textPaint.textSize / 2f) - dp(3f)
        canvas.drawText(text, textX, textY, textPaint)

        ln.children.forEach { drawNodes(canvas, it) }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
