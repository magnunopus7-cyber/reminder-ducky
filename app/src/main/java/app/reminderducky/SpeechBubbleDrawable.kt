package app.reminderducky

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * A rounded speech bubble with a tail on its left or right side, pointing at
 * Ducky. The view using it needs a software layer for the soft shadow.
 */
class SpeechBubbleDrawable(
    color: Int,
    private val radius: Float,
    private val tail: Float,
    private val shadow: Float,
) : Drawable() {
    var tailOnLeft = true
        set(v) { field = v; invalidateSelf() }
    /** Where the tail points, measured from the top of the bubble. */
    var tailY = 0f
        set(v) { field = v; invalidateSelf() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        setShadowLayer(shadow, 0f, shadow / 3f, 0x40000000)
    }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(
            b.left + shadow + if (tailOnLeft) tail else 0f,
            b.top + shadow,
            b.right - shadow - if (tailOnLeft) 0f else tail,
            b.bottom - shadow * 1.5f,
        )
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val y = tailY.coerceIn(rect.top + radius, rect.bottom - radius)
        val tip = if (tailOnLeft) rect.left - tail else rect.right + tail
        val base = if (tailOnLeft) rect.left + 2f else rect.right - 2f
        path.moveTo(base, y - tail * 0.9f)
        path.quadTo(base, y + tail * 0.1f, tip, y + tail * 0.3f)
        path.quadTo(base, y + tail * 0.6f, base, y + tail * 0.9f)
        path.close()
        path.fillType = Path.FillType.WINDING
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
