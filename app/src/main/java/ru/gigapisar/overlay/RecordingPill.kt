package ru.gigapisar.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The recording pill, as on the desktop apps: a small capsule with a wave that follows
 * the voice while recording, a calm ripple while recognizing, and a short message on
 * errors. It sits just above the text field the text will go into (or at the bottom
 * of the screen when there is none), never takes focus and ignores touches, so it
 * works the same with the floating button hidden and the volume key doing the work.
 */
class RecordingPill(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val view = PillView(service)
    private var attached = false
    private val hideRunnable = Runnable { hide() }

    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

    /** Recording started: the wave follows [level] (0..1), placed near [anchor] (screen coordinates). */
    fun showListening(
        anchor: Rect?,
        level: () -> Float,
    ) {
        view.removeCallbacks(hideRunnable)
        view.listen(level)
        place(anchor)
    }

    /** Recording is over, recognition runs. */
    fun showProcessing() {
        view.removeCallbacks(hideRunnable)
        view.process()
        place(lastAnchor)
    }

    /** A short message (an error or a hint) that goes away by itself. */
    fun showMessage(
        text: String,
        anchor: Rect? = lastAnchor,
        millis: Long = 2600,
    ) {
        view.removeCallbacks(hideRunnable)
        view.message(text)
        place(anchor)
        view.postDelayed(hideRunnable, millis)
    }

    fun hide() {
        view.removeCallbacks(hideRunnable)
        view.stop()
        if (!attached) return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // Already gone.
        }
        attached = false
    }

    private var lastAnchor: Rect? = null

    private fun place(anchor: Rect?) {
        lastAnchor = anchor
        val metrics = service.resources.displayMetrics
        val gap = (10 * metrics.density).roundToInt()
        val w = view.pillWidth()
        val h = view.pillHeight()
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        if (anchor != null && !anchor.isEmpty) {
            params.x = (anchor.centerX() - w / 2).coerceIn(gap, max(gap, screenW - w - gap))
            // Above the field; below it when the field is at the very top.
            params.y = if (anchor.top - h - gap > gap * 4) anchor.top - h - gap else anchor.bottom + gap
        } else {
            params.x = (screenW - w) / 2
            params.y = screenH - h - (140 * metrics.density).roundToInt()
        }
        params.y = params.y.coerceIn(gap, max(gap, screenH - h - gap))
        try {
            if (attached) {
                windowManager.updateViewLayout(view, params)
            } else {
                windowManager.addView(view, params)
                attached = true
            }
        } catch (_: Exception) {
            attached = false
        }
    }

    private class PillView(
        context: Context,
    ) : View(context) {
        private enum class Mode { IDLE, LISTENING, PROCESSING, MESSAGE }

        private val density = resources.displayMetrics.density
        private val barCount = 13
        private val barWidth = 3 * density
        private val barGap = 2 * density
        private val barMax = 20 * density
        private val padding = 14 * density
        private val height = 30 * density
        private val barsWidth = barCount * barWidth + (barCount - 1) * barGap

        // The website's wave palette, light green to teal.
        private val palette =
            listOf(
                0xFFA8E063 to 0xFF1FA03A,
                0xFFA8E063 to 0xFF1FA03A,
                0xFF9ADF55 to 0xFF17963F,
                0xFF8AD84C to 0xFF10884A,
                0xFF7FD648 to 0xFF0E9367,
                0xFF63CF62 to 0xFF009B82,
                0xFF4FC884 to 0xFF00A08C,
                0xFF3FC39B to 0xFF00A08C,
                0xFF38BFA5 to 0xFF008F92,
                0xFF35BCB0 to 0xFF008699,
                0xFF35BCB0 to 0xFF008699,
                0xFF35BCB0 to 0xFF008699,
                0xFF35BCB0 to 0xFF008699,
            )

        private val dark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xF22B2B2E.toInt() else 0xF5FFFFFF.toInt() }
        private val border =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = if (dark) 0x33FFFFFF else 0x1F000000
            }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13 * resources.displayMetrics.scaledDensity
                typeface = Typeface.DEFAULT
                color = if (dark) 0xE6FFFFFF.toInt() else 0xD9000000.toInt()
            }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var mode = Mode.IDLE
        private var level: () -> Float = { 0f }
        private var text = ""
        private val heights = FloatArray(barCount)
        private var phase = 0f

        private val frame =
            object : Runnable {
                override fun run() {
                    if (mode != Mode.LISTENING && mode != Mode.PROCESSING) return
                    tick()
                    invalidate()
                    postOnAnimation(this)
                }
            }

        fun pillWidth(): Int =
            when (mode) {
                Mode.MESSAGE -> (2 * padding + min(textPaint.measureText(text), resources.displayMetrics.widthPixels * 0.8f)).roundToInt()
                else -> (2 * padding + barsWidth).roundToInt()
            }

        fun pillHeight(): Int = height.roundToInt()

        fun listen(level: () -> Float) {
            this.level = level
            mode = Mode.LISTENING
            heights.fill(0f)
            restart()
        }

        fun process() {
            mode = Mode.PROCESSING
            phase = 0f
            restart()
        }

        fun message(text: String) {
            removeCallbacks(frame)
            this.text = text
            mode = Mode.MESSAGE
            requestLayout()
            invalidate()
        }

        fun stop() {
            removeCallbacks(frame)
            mode = Mode.IDLE
        }

        private fun restart() {
            removeCallbacks(frame)
            requestLayout()
            postOnAnimation(frame)
        }

        private fun tick() {
            if (mode == Mode.LISTENING) {
                // Level is already on the dB scale (0..1), see AudioRecorder.levelOf.
                val raw = level()
                val loud = raw.coerceIn(0f, 1f)
                for (i in 0 until barCount) {
                    val centre = 1f - kotlin.math.abs(i - (barCount - 1) / 2f) / barCount
                    val jitter = 0.75f + 0.5f * Math.random().toFloat()
                    val target = (0.15f + 0.85f * loud * centre * jitter).coerceIn(0.15f, 1f)
                    heights[i] += (target - heights[i]) * 0.35f
                }
            } else {
                phase += 0.18f
                for (i in 0 until barCount) {
                    heights[i] = 0.3f + 0.25f * (1f + sin(phase - i * 0.55f)) / 2f
                }
            }
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            // The window is WRAP_CONTENT; add room for the border.
            setMeasuredDimension(pillWidth() + 2, pillHeight() + 2)
        }

        override fun onDraw(canvas: Canvas) {
            if (mode == Mode.IDLE) return
            val w = pillWidth().toFloat()
            val r = RectF(1f, 1f, w + 1f, height + 1f)
            canvas.drawRoundRect(r, height / 2, height / 2, background)
            canvas.drawRoundRect(r, height / 2, height / 2, border)
            if (mode == Mode.MESSAGE) {
                val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                val shown = TextUtilsCompat.ellipsize(text, textPaint, w - 2 * padding)
                canvas.drawText(shown, r.left + padding, y, textPaint)
                return
            }
            var x = r.left + padding
            for (i in 0 until barCount) {
                val h = max(barWidth, barMax * heights[i])
                val top = r.centerY() - h / 2
                val (a, b) = palette[i % palette.size]
                barPaint.shader = LinearGradient(0f, top, 0f, top + h, a.toInt(), b.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(x, top, x + barWidth, top + h), barWidth / 2, barWidth / 2, barPaint)
                x += barWidth + barGap
            }
        }
    }

    private object TextUtilsCompat {
        fun ellipsize(
            text: String,
            paint: Paint,
            width: Float,
        ): String {
            if (paint.measureText(text) <= width) return text
            var end = text.length
            while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
            return text.substring(0, end) + "…"
        }
    }
}
