package app.reminderducky

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView

/** A transparent view that shows the animated 3D duck. */
@SuppressLint("ViewConstructor")
class DuckGLView(context: Context) : GLSurfaceView(context) {
    val renderer = DuckRenderer(context)

    /** Smooth 60 fps while moving or alerting, a battery-friendly 25 fps otherwise. */
    var fast = false
        set(value) {
            field = value
            if (ticking) restartTicker()
        }

    private var ticking = false
    private val ticker = object : Runnable {
        override fun run() {
            requestRender()
            postDelayed(this, if (fast) 16L else 40L)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun startTicking() {
        ticking = true
        restartTicker()
    }

    fun stopTicking() {
        ticking = false
        removeCallbacks(ticker)
    }

    private fun restartTicker() {
        removeCallbacks(ticker)
        post(ticker)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTicking()
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        super.onDetachedFromWindow()
    }
}
