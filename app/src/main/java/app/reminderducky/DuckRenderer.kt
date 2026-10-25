package app.reminderducky

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Calendar
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Mood { NORMAL, HAPPY, EXCITED, SLEEPY, SURPRISED }

/**
 * Draws the duck with a single full-screen fragment shader (assets/duck.frag)
 * and gives it a little personality: idle bobbing and blinking, turning to
 * face the screen, moods with matching eyes and symbols, and a squish on tap.
 */
class DuckRenderer(context: Context) : GLSurfaceView.Renderer {
    private val assets = context.applicationContext.assets

    @Volatile var look = DuckLook()
    @Volatile var facingRight = true
    /** True while a reminder is waiting: the duck stays excited. */
    @Volatile var alerting = false
    /** False keeps the duck awake at night (used for the in-app preview). */
    @Volatile var sleepsAtNight = true
    /** Doze off when nobody has tapped Ducky for a while (off for the in-app preview). */
    @Volatile var sleepsWhenIdle = true

    @Volatile private var squashStartNanos = 0L
    /** Last tap, drag or reminder; Ducky dozes off after a quiet while. */
    @Volatile private var lastPokeNanos = System.nanoTime()
    @Volatile private var reaction = Mood.NORMAL
    @Volatile private var reactionStartNanos = 0L
    @Volatile private var reactionEndNanos = 0L

    private var program = 0
    private var aPos = 0
    private val u = HashMap<String, Int>()
    private var width = 1
    private var height = 1

    private val startNanos = System.nanoTime()
    private var lastNanos = 0L
    private var yaw = YAW_RIGHT
    private val rot = FloatArray(9)
    private var nextBlink = 2f
    private var blinkStart = -10f
    private var night = false
    private var nightCheckedAt = -100f

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    fun squash() {
        squashStartNanos = System.nanoTime()
        poke()
    }

    /** Wakes Ducky up and restarts the doze timer. */
    fun poke() {
        lastPokeNanos = System.nanoTime()
    }

    /** Shows a mood for a few seconds, then goes back to normal. */
    fun react(mood: Mood, seconds: Float) {
        val now = System.nanoTime()
        reactionStartNanos = now
        reactionEndNanos = now + (seconds * 1e9f).toLong()
        reaction = mood
        poke()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(readAsset("duck.vert"), readAsset("duck.frag"))
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        u.clear()
        for (name in UNIFORMS) u[name] = GLES20.glGetUniformLocation(program, name)
        yaw = if (facingRight) YAW_RIGHT else YAW_LEFT
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    private fun currentMood(now: Long, t: Float): Mood {
        if (now < reactionEndNanos) return reaction
        if (alerting) return Mood.EXCITED
        if (sleepsWhenIdle && now - lastPokeNanos > IDLE_SLEEP_NANOS) return Mood.SLEEPY
        if (t - nightCheckedAt > 30f) {
            nightCheckedAt = t
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            night = hour >= 23 || hour < 7
        }
        return if (night && sleepsAtNight) Mood.SLEEPY else Mood.NORMAL
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val t = (now - startNanos) / 1e9f
        val dt = min((now - lastNanos) / 1e9f, 0.1f)
        lastNanos = now
        val mood = currentMood(now, t)
        val inMood = (now - reactionStartNanos) / 1e9f

        // Turn smoothly towards the middle of the screen, past the camera.
        val target = if (facingRight) YAW_RIGHT else YAW_LEFT
        yaw += (target - yaw) * min(1f, dt * 5f)

        var y = yaw + 0.15f * sin(t * 0.7f)
        var roll = 0.05f * sin(t * 1.5f + 1f)
        var bob = 0.03f * sin(t * 1.5f)
        var sx = 1f
        var sy = 1f
        var eyeMode = 0f
        var eye = floatArrayOf(0.058f, 0.085f, 0.05f)
        var deco = 0f
        var decoAmt = 0f

        when (mood) {
            Mood.NORMAL -> Unit
            Mood.HAPPY -> {
                eyeMode = 1f
                bob = 0.06f * abs(sin(t * 6f))
                roll = 0.08f * sin(t * 6f)
                deco = 1f; decoAmt = 0.6f + 0.4f * sin(t * 4f)
            }
            Mood.EXCITED -> {
                eye = floatArrayOf(0.062f, 0.09f, 0.055f)
                bob = 0.08f * abs(sin(t * 7f))
                roll = 0.22f * sin(t * 7f)
                y += 0.2f * sin(t * 3f)
                deco = 1f; decoAmt = 0.6f + 0.4f * sin(t * 5f)
            }
            Mood.SLEEPY -> {
                eyeMode = 2f
                bob = 0.02f * sin(t * 0.9f)
                roll = -0.12f + 0.03f * sin(t * 0.9f)
                y = yaw + 0.05f * sin(t * 0.4f)
                deco = 2f; decoAmt = 0.5f + 0.5f * sin(t * 1.4f)
            }
            Mood.SURPRISED -> {
                eye = floatArrayOf(0.072f, 0.105f, 0.06f)
                val jump = max(0f, 1f - inMood * 3f)
                bob = 0.12f * jump
                sy = 1.06f; sx = 0.97f
                deco = 3f; decoAmt = 1f
            }
        }

        // Blink now and then while the eyes are open.
        if (t > nextBlink) {
            blinkStart = t
            nextBlink = t + 2.5f + Random.nextFloat() * 3f
        }
        val b = (t - blinkStart) / 0.16f
        if (eyeMode == 0f && b in 0f..1f) {
            eye = floatArrayOf(eye[0], eye[1] * max(0.12f, 1f - sin(b * PI.toFloat()) * 0.95f), eye[2])
        }

        val s = (now - squashStartNanos) / 1e9f
        if (s < SQUASH_SECONDS) {
            val k = sin(s / SQUASH_SECONDS * PI.toFloat()) * 0.16f
            sx *= 1f + k * 0.6f
            sy *= 1f - k
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
        val l = look
        GLES20.glUniform2f(u("uRes"), width.toFloat(), height.toFloat())
        GLES20.glUniformMatrix3fv(u("uRot"), 1, false, rot, 0)
        GLES20.glUniform1f(u("uBob"), bob)
        GLES20.glUniform3f(u("uScale"), sx, sy, sx)
        GLES20.glUniform1f(u("uMat"), l.material.code)
        GLES20.glUniform3fv(u("uBody"), 1, l.color.bodyRgb, 0)
        GLES20.glUniform3fv(u("uHead"), 1, l.color.headRgb, 0)
        GLES20.glUniform3fv(u("uBeak"), 1, l.beakRgb, 0)
        GLES20.glUniform1f(u("uAcc"), l.accessory.code)
        GLES20.glUniform3fv(u("uAccA"), 1, l.accessory.aRgb, 0)
        GLES20.glUniform3fv(u("uAccB"), 1, l.accessory.bRgb, 0)
        GLES20.glUniform1f(u("uEyeMode"), eyeMode)
        GLES20.glUniform3fv(u("uEyeR"), 1, eye, 0)
        GLES20.glUniform1f(u("uDeco"), deco)
        GLES20.glUniform1f(u("uDecoAmt"), decoAmt)
        GLES20.glUniform1f(u("uDecoSide"), if (facingRight) 1f else -1f)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun u(name: String) = u[name] ?: -1

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
        private const val IDLE_SLEEP_NANOS = 2 * 60 * 1_000_000_000L
        // Three-quarter views: beak to the right or left, turned towards the viewer.
        private const val YAW_RIGHT = -1.3f
        private const val YAW_LEFT = -(PI.toFloat()) + 1.3f
        private val UNIFORMS = listOf(
            "uRes", "uRot", "uBob", "uScale", "uMat", "uBody", "uHead", "uBeak", "uAcc",
            "uAccA", "uAccB", "uEyeMode", "uEyeR", "uDeco", "uDecoAmt", "uDecoSide",
        )
    }
}
