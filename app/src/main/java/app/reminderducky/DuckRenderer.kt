package app.reminderducky

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the duck with a single full-screen fragment shader (assets/duck.frag)
 * and animates it: idle bobbing, turning to face the screen, wobbling when a
 * reminder is due, and a squish when tapped.
 */
class DuckRenderer(context: Context) : GLSurfaceView.Renderer {
    private val assets = context.applicationContext.assets

    @Volatile var finish: DuckFinish = DuckFinish.GOLD
    @Volatile var facingRight = true
    @Volatile var alerting = false
    @Volatile private var squashStartNanos = 0L

    private var program = 0
    private var aPos = 0
    private var uRes = 0
    private var uRot = 0
    private var uBob = 0
    private var uScale = 0
    private var uBody = 0
    private var uBeak = 0
    private var width = 1
    private var height = 1

    private val startNanos = System.nanoTime()
    private var lastNanos = 0L
    private var yaw = YAW_RIGHT
    private val rot = FloatArray(9)

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    fun squash() {
        squashStartNanos = System.nanoTime()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(readAsset("duck.vert"), readAsset("duck.frag"))
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uRes = GLES20.glGetUniformLocation(program, "uRes")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uBob = GLES20.glGetUniformLocation(program, "uBob")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uBody = GLES20.glGetUniformLocation(program, "uBody")
        uBeak = GLES20.glGetUniformLocation(program, "uBeak")
        yaw = if (facingRight) YAW_RIGHT else YAW_LEFT
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val t = (now - startNanos) / 1e9f
        val dt = min((now - lastNanos) / 1e9f, 0.1f)
        lastNanos = now

        // Turn smoothly towards the middle of the screen, past the camera.
        val target = if (facingRight) YAW_RIGHT else YAW_LEFT
        yaw += (target - yaw) * min(1f, dt * 5f)

        var y = yaw + 0.18f * sin(t * 0.7f)
        var roll = 0.06f * sin(t * 1.5f + 1f)
        var bob = 0.035f * sin(t * 1.5f)
        if (alerting) {
            roll += 0.28f * sin(t * 11f)
            bob += 0.06f * abs(sin(t * 5.5f))
            y += 0.2f * sin(t * 3f)
        }

        var sx = 1f
        var sy = 1f
        val s = (now - squashStartNanos) / 1e9f
        if (s < SQUASH_SECONDS) {
            val k = sin(s / SQUASH_SECONDS * PI.toFloat()) * 0.16f
            sx = 1f + k * 0.6f
            sy = 1f - k
        }

        // Rotation R = Ry(yaw) * Rz(roll), written row-major. GL reads the
        // array column-major, which hands the shader R transposed: exactly
        // the world-to-duck rotation it needs.
        val cy = cos(y); val sny = sin(y); val cr = cos(roll); val sr = sin(roll)
        rot[0] = cy * cr;  rot[1] = -cy * sr; rot[2] = sny
        rot[3] = sr;       rot[4] = cr;       rot[5] = 0f
        rot[6] = -sny * cr; rot[7] = sny * sr; rot[8] = cy

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())
        GLES20.glUniformMatrix3fv(uRot, 1, false, rot, 0)
        GLES20.glUniform1f(uBob, bob)
        GLES20.glUniform3f(uScale, sx, sy, sx)
        val f = finish
        GLES20.glUniform3fv(uBody, 1, f.body, 0)
        GLES20.glUniform3fv(uBeak, 1, f.beak, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun readAsset(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "Link failed: " + GLES20.glGetProgramInfoLog(p))
            return 0
        }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(s))
            return 0
        }
        return s
    }

    companion object {
        private const val TAG = "DuckRenderer"
        private const val SQUASH_SECONDS = 0.45f
        // Three-quarter views: beak to the right or left, turned towards the viewer.
        private const val YAW_RIGHT = -0.65f
        private const val YAW_LEFT = -(PI.toFloat()) + 0.65f
    }
}
