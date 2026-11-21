package com.imaginstudio.imagetools.pixellab.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.imaginstudio.imagetools.pixellab.model.ArrowHeadStyle
import com.imaginstudio.imagetools.pixellab.model.BoundedShapeLabel
import com.imaginstudio.imagetools.pixellab.model.GradientDirection
import com.imaginstudio.imagetools.pixellab.model.GradientFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object ShapePathBuilder {

    fun createShader(
        grad: GradientFill,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): Shader {
        val stops = if (grad.stops.size >= 2) grad.stops.sortedBy { it.offset } else GradientFill().stops
        val colors = stops.map { it.color }.toIntArray()
        val positions = stops.map { it.offset.coerceIn(0f, 1f) }.toFloatArray()
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)

        return when (grad.type) {
            GradientDirection.LINEAR_H -> LinearGradient(
                left, top + h / 2f, left + w, top + h / 2f,
                colors, positions, Shader.TileMode.CLAMP
            )
            GradientDirection.LINEAR_DIAG -> {
                val rad = grad.angle * PI.toFloat() / 180f
                val cx = left + w / 2f
                val cy = top + h / 2f
                val dx = cos(rad) * w / 2f
                val dy = sin(rad) * h / 2f
                LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy,
                    colors, positions, Shader.TileMode.CLAMP
                )
            }
            GradientDirection.RADIAL -> {
                val cx = left + w / 2f
                val cy = top + h / 2f
                val r = (min(w, h) * 0.65f).coerceAtLeast(1f)
                RadialGradient(cx, cy, r, colors, positions, Shader.TileMode.CLAMP)
            }
            GradientDirection.LINEAR_V -> LinearGradient(
                left + w / 2f, top, left + w / 2f, top + h,
                colors, positions, Shader.TileMode.CLAMP
            )
        }
    }

    fun buildBoundedShapePath(
        label: BoundedShapeLabel,
        w: Float,
        h: Float,
        cornerRadiusPct: Int
    ): Path {
        val path = Path()
        val hw = w / 2f
        val hh = h / 2f
        val rect = RectF(-hw, -hh, hw, hh)
        val minDim = min(w, h)

        when (label) {
            BoundedShapeLabel.RECT -> {
                val cr = (cornerRadiusPct.coerceIn(0, 100) / 100f) * (minDim / 2f)
                path.addRoundRect(rect, cr, cr, Path.Direction.CW)
            }
            BoundedShapeLabel.OVAL -> {
                path.addOval(rect, Path.Direction.CW)
            }
            BoundedShapeLabel.TRI1 -> {
                path.moveTo(0f, -hh)
                path.lineTo(hw, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            BoundedShapeLabel.TRI2 -> {
                path.moveTo(-hw, -hh)
                path.lineTo(hw, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            BoundedShapeLabel.DIAMOND -> {
                path.moveTo(0f, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(0f, hh)
                path.lineTo(-hw, 0f)
                path.close()
            }
            BoundedShapeLabel.PENT -> buildPolygon(path, 5, hw, hh, -90.0)
            BoundedShapeLabel.HEX -> buildPolygon(path, 6, hw, hh, -90.0)
            BoundedShapeLabel.OCT -> buildPolygon(path, 8, hw, hh, -67.5)
            BoundedShapeLabel.STAR -> {
                val points = 5
                val outerRx = hw
                val outerRy = hh
                val innerRx = hw * 0.42f
                val innerRy = hh * 0.42f
                for (i in 0 until points * 2) {
                    val angle = ((-90.0 + i * 36.0) * PI / 180.0)
                    val rx = if (i % 2 == 0) outerRx else innerRx
                    val ry = if (i % 2 == 0) outerRy else innerRy
                    val px = (cos(angle) * rx).toFloat()
                    val py = (sin(angle) * ry).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            BoundedShapeLabel.HEART -> {
                path.moveTo(0f, hh * 0.85f)
                path.cubicTo(-hw * 1.1f, hh * 0.15f, -hw * 0.95f, -hh * 0.85f, 0f, -hh * 0.3f)
                path.cubicTo(hw * 0.95f, -hh * 0.85f, hw * 1.1f, hh * 0.15f, 0f, hh * 0.85f)
                path.close()
            }
            BoundedShapeLabel.LIGHTNING -> {
                path.moveTo(hw * 0.15f, -hh)
                path.lineTo(-hw * 0.55f, hh * 0.08f)
                path.lineTo(0f, hh * 0.08f)
                path.lineTo(-hw * 0.2f, hh)
                path.lineTo(hw * 0.55f, -hh * 0.08f)
                path.lineTo(0f, -hh * 0.08f)
                path.close()
            }
            BoundedShapeLabel.DROP -> {
                path.moveTo(0f, -hh)
                path.cubicTo(hw * 0.95f, hh * 0.1f, hw * 0.85f, hh, 0f, hh)
                path.cubicTo(-hw * 0.85f, hh, -hw * 0.95f, hh * 0.1f, 0f, -hh)
                path.close()
            }
            BoundedShapeLabel.ARC -> {
                val arcRect = RectF(-hw, -hh, hw, hh * 3f)
                path.addArc(arcRect, 180f, 180f)
                path.close()
            }
            BoundedShapeLabel.ARROW -> {
                val shaftH = hh * 0.42f
                val headX = hw * 0.25f
                path.moveTo(-hw, -shaftH)
                path.lineTo(headX, -shaftH)
                path.lineTo(headX, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(headX, hh)
                path.lineTo(headX, shaftH)
                path.lineTo(-hw, shaftH)
                path.close()
            }
            BoundedShapeLabel.DARROW -> {
                val shaftH = hh * 0.4f
                val lx = -hw * 0.35f
                val rx = hw * 0.35f
                path.moveTo(-hw, 0f)
                path.lineTo(lx, -hh)
                path.lineTo(lx, -shaftH)
                path.lineTo(rx, -shaftH)
                path.lineTo(rx, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(rx, hh)
                path.lineTo(rx, shaftH)
                path.lineTo(lx, shaftH)
                path.lineTo(lx, hh)
                path.close()
            }
            BoundedShapeLabel.VARROW -> {
                val shaftW = hw * 0.42f
                val headY = -hh * 0.2f
                path.moveTo(0f, -hh)
                path.lineTo(hw, headY)
                path.lineTo(shaftW, headY)
                path.lineTo(shaftW, hh)
                path.lineTo(-shaftW, hh)
                path.lineTo(-shaftW, headY)
                path.lineTo(-hw, headY)
                path.close()
            }
            BoundedShapeLabel.BUBBLE1 -> {
                val boxBottom = hh * 0.55f
                val r = minDim * 0.14f
                path.addRoundRect(RectF(-hw, -hh, hw, boxBottom), r, r, Path.Direction.CW)
                val tail = Path().apply {
                    moveTo(-hw * 0.35f, boxBottom - 2f)
                    lineTo(-hw * 0.5f, hh)
                    lineTo(-hw * 0.05f, boxBottom - 2f)
                    close()
                }
                path.op(tail, Path.Op.UNION)
            }
            BoundedShapeLabel.BUBBLE2 -> {
                val ovalBottom = hh * 0.6f
                path.addOval(RectF(-hw, -hh, hw, ovalBottom), Path.Direction.CW)
                val tail = Path().apply {
                    moveTo(-hw * 0.28f, ovalBottom - hh * 0.12f)
                    lineTo(-hw * 0.55f, hh)
                    lineTo(hw * 0.05f, ovalBottom - hh * 0.06f)
                    close()
                }
                path.op(tail, Path.Op.UNION)
            }
            BoundedShapeLabel.BUBBLE3 -> {
                path.addOval(RectF(-hw, -hh, hw, hh * 0.45f), Path.Direction.CW)
                path.addCircle(-hw * 0.32f, hh * 0.62f, minDim * 0.1f, Path.Direction.CW)
                path.addCircle(-hw * 0.52f, hh * 0.86f, minDim * 0.06f, Path.Direction.CW)
            }
            BoundedShapeLabel.EXIT -> {
                path.moveTo(-hw, -hh)
                path.lineTo(hw * 0.65f, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(hw * 0.65f, hh)
                path.lineTo(-hw, hh)
                path.lineTo(-hw * 0.72f, 0f)
                path.close()
            }
            BoundedShapeLabel.POST -> {
                path.moveTo(-hw, -hh)
                path.lineTo(hw * 0.7f, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(hw * 0.7f, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            BoundedShapeLabel.CPOST -> {
                path.moveTo(-hw, -hh * 0.7f)
                path.quadTo(0f, -hh * 1.15f, hw, -hh * 0.7f)
                path.lineTo(hw, hh * 0.7f)
                path.quadTo(0f, hh * 1.15f, -hw, hh * 0.7f)
                path.close()
            }
        }
        return path
    }

    private fun buildPolygon(path: Path, sides: Int, rx: Float, ry: Float, startDeg: Double) {
        for (i in 0 until sides) {
            val angle = ((startDeg + i * (360.0 / sides)) * PI / 180.0)
            val px = (cos(angle) * rx).toFloat()
            val py = (sin(angle) * ry).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun drawArrow(
        canvas: Canvas,
        fillPaint: Paint,
        strokePaint: Paint?,
        totalWidth: Float,
        stemWidth: Float,
        headSize: Float,
        startStyle: ArrowHeadStyle,
        endStyle: ArrowHeadStyle
    ) {
        val hw = totalWidth / 2f
        val startX = if (startStyle == ArrowHeadStyle.NONE) -hw else -hw + headSize * 0.7f
        val endX = if (endStyle == ArrowHeadStyle.NONE) hw else hw - headSize * 0.7f

        val shaftRect = RectF(startX, -stemWidth / 2f, endX.coerceAtLeast(startX + 4f), stemWidth / 2f)
        canvas.drawRect(shaftRect, fillPaint)
        strokePaint?.let { canvas.drawRect(shaftRect, it) }

        fun drawHead(tipX: Float, dir: Float, style: ArrowHeadStyle) {
            if (style == ArrowHeadStyle.NONE) return
            val path = Path()
            val hs = headSize
            when (style) {
                ArrowHeadStyle.TRIANGLE -> {
                    path.moveTo(tipX, 0f)
                    path.lineTo(tipX - dir * hs, -hs * 0.65f)
                    path.lineTo(tipX - dir * hs, hs * 0.65f)
                    path.close()
                }
                ArrowHeadStyle.STEALTH -> {
                    path.moveTo(tipX, 0f)
                    path.lineTo(tipX - dir * hs * 1.05f, -hs * 0.65f)
                    path.lineTo(tipX - dir * hs * 0.7f, 0f)
                    path.lineTo(tipX - dir * hs * 1.05f, hs * 0.65f)
                    path.close()
                }
                ArrowHeadStyle.CIRCLE -> {
                    path.addCircle(tipX - dir * (hs * 0.45f), 0f, hs * 0.45f, Path.Direction.CW)
                }
                ArrowHeadStyle.DIAMOND -> {
                    val mid = tipX - dir * (hs * 0.5f)
                    path.moveTo(tipX, 0f)
                    path.lineTo(mid, -hs * 0.55f)
                    path.lineTo(tipX - dir * hs, 0f)
                    path.lineTo(mid, hs * 0.55f)
                    path.close()
                }
                ArrowHeadStyle.NONE -> Unit
            }
            canvas.drawPath(path, fillPaint)
            strokePaint?.let { canvas.drawPath(path, it) }
        }

        drawHead(-hw, -1f, startStyle)
        drawHead(hw, 1f, endStyle)
    }
}
