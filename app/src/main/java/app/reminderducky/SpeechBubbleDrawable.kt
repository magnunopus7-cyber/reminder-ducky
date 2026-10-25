package app.reminderducky

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * A comic-style speech bubble: a very round body with an outline and a curved
 * tail pointing at Ducky, on the left, right or top edge. The view using it
 * needs a software layer for the soft shadow.
 */
class SpeechBubbleDrawable(
    fill: Int,
    outline: Int,
    private val radius: Float,
    /** How far the tail sticks out of the body. */
    private val tail: Float,
    private val shadow: Float,
    strokeWidth: Float,
) : Drawable() {
    var tailOnLeft = true
        set(v) { field = v; invalidateSelf() }
    /** Tail on the top edge instead, used when the bubble sits below Ducky. */
    var tailOnTop = false
        set(v) { field = v; invalidateSelf() }
    /** Where a top tail points, measured from the left of the bubble. */
    var tailX = 0f
        set(v) { field = v; invalidateSelf() }
    /** Where a side tail points, measured from the top of the bubble. */
    var tailY = 0f
        set(v) { field = v; invalidateSelf() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
        setShadowLayer(shadow, 0f, shadow / 3f, 0x40000000)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outline
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeJoin = Paint.Join.ROUND
    }
    private val body = Path()
    private val tailPath = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val inset = shadow + strokePaint.strokeWidth
        val side = !tailOnTop
        rect.set(
            b.left + inset + if (side && tailOnLeft) tail else 0f,
            b.top + inset + if (tailOnTop) tail else 0f,
            b.right - inset - if (side && !tailOnLeft) tail else 0f,
            b.bottom - inset - shadow * 0.5f,
        )
        val r = min(radius, min(rect.width(), rect.height()) / 2f)
        body.reset()
        body.addRoundRect(rect, r, r, Path.Direction.CW)

        // The tail's wide base sits inside the body so the two merge into one outline.
        val half = tail * 0.75f
        tailPath.reset()
        if (tailOnTop) {
            val x = tailX.coerceIn(rect.left + r, rect.right - r)
            val base = rect.top + tail * 0.6f
            tailPath.moveTo(x - half, base)
            tailPath.quadTo(x - half * 0.2f, rect.top - tail * 0.2f, x - half * 0.6f, rect.top - tail)
            tailPath.quadTo(x + half * 0.4f, rect.top - tail * 0.1f, x + half, base)
        } else {
            val y = tailY.coerceIn(rect.top + r * 0.6f, rect.bottom - r * 0.6f)
            val dir = if (tailOnLeft) -1f else 1f
            val edge = if (tailOnLeft) rect.left else rect.right
            val base = edge - dir * tail * 0.6f
            tailPath.moveTo(base, y - half)
            tailPath.quadTo(edge + dir * tail * 0.2f, y - half * 0.2f, edge + dir * tail, y - half * 0.6f)
            tailPath.quadTo(edge + dir * tail * 0.1f, y + half * 0.4f, base, y + half)
        }
        tailPath.close()
        body.op(tailPath, Path.Op.UNION)

        canvas.drawPath(body, fillPaint)
        canvas.drawPath(body, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
