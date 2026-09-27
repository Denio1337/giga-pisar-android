package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import ru.gigapisar.R
import kotlin.math.abs

class RecordingButton(
    context: Context,
) : View(context) {
    enum class State {
        IDLE,
        RECORDING,
        PROCESSING,
    }

    companion object {
        private const val BUTTON_SIZE_DP = 64
        private const val VISIBILITY_ANIMATION_DURATION = 180L
        private const val PRESSED_SCALE = 0.94f
    }

    private val density = resources.displayMetrics.density

    private val buttonSize =
        (BUTTON_SIZE_DP * density).toInt()

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var state = State.IDLE

/**
     * True while the current touch gesture is being used
     * to move the floating button.
     */
    private var dragging = false

/**
     * Previous raw pointer position.
     *
     * We use deltas instead of calculating the complete position
     * here because the owner of the overlay should decide where
     * the WindowManager.LayoutParams should be placed.
     */
    private var lastRawX = 0f
    private var lastRawY = 0f

    private var downRawX = 0f
    private var downRawY = 0f

/**
     * Whether recording was started for the current gesture.
     */
    private var recordingForCurrentGesture = false

    var onRecordingStart: (() -> Unit)? = null

    var onRecordingStop: (() -> Unit)? = null

/**
     * Called once when the finger moves far enough
     * to turn the gesture into a drag.
     */
    var onDragStart: (() -> Unit)? = null

/**
     * Receives movement delta in screen coordinates.
     *
     * dx/dy are relative to the previous MotionEvent,
     * not relative to ACTION_DOWN.
     */
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null

    var onDragEnd: (() -> Unit)? = null

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
        }

    init {
        visibility = GONE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f

        isClickable = true
        isFocusable = true

        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES

        contentDescription =
            context.getString(R.string.button_idle)

        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(
            buttonSize,
            buttonSize,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state != State.IDLE) {
                    return false
                }

                dragging = false
                recordingForCurrentGesture = true

                downRawX = event.rawX
                downRawY = event.rawY

                lastRawX = event.rawX
                lastRawY = event.rawY

            /*
             * Start recording immediately.
             *
             * Previously this was delayed by
             * ViewConfiguration.getLongPressTimeout(),
             * which made the button feel unresponsive.
             */
                setState(State.RECORDING)
                onRecordingStart?.invoke()

            /*
             * Small visual feedback that the press was accepted.
             */
                animate()
                    .scaleX(PRESSED_SCALE)
                    .scaleY(PRESSED_SCALE)
                    .setDuration(80L)
                    .start()

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (state != State.RECORDING && !dragging) {
                    return true
                }

                val totalDx =
                    event.rawX - downRawX

                val totalDy =
                    event.rawY - downRawY

            /*
             * Use Android's standard touch slop.
             *
             * This avoids interpreting tiny finger movements
             * as a drag.
             */
                if (!dragging) {
                    val distanceExceeded =
                        abs(totalDx) > touchSlop ||
                            abs(totalDy) > touchSlop

                    if (!distanceExceeded) {
                        return true
                    }

                /*
                 * The user actually wants to move the button.
                 *
                 * Stop recording immediately and switch
                 * the current gesture to dragging.
                 */
                    dragging = true

                    if (recordingForCurrentGesture) {
                        recordingForCurrentGesture = false

                        setState(State.IDLE)
                        onRecordingStop?.invoke()
                    }

                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80L)
                        .start()

                    onDragStart?.invoke()

                /*
                 * Don't send the whole distance from ACTION_DOWN.
                 * The owner receives movement starting from this point.
                 */
                    lastRawX = event.rawX
                    lastRawY = event.rawY

                    return true
                }

            /*
             * Smooth drag:
             *
             * Instead of calculating the complete position and
             * maintaining another coordinate system here, only
             * report the actual movement since the previous event.
             */
                val dx =
                    event.rawX - lastRawX

                val dy =
                    event.rawY - lastRawY

                if (dx != 0f || dy != 0f) {
                    onDrag?.invoke(dx, dy)
                }

                lastRawX = event.rawX
                lastRawY = event.rawY

                return true
            }

            MotionEvent.ACTION_UP -> {
                finishGesture(cancelled = false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finishGesture(cancelled = true)
                return true
            }
        }

        return true
    }

    private fun finishGesture(cancelled: Boolean) {
        val wasDragging = dragging
        val wasRecording =
            recordingForCurrentGesture

        dragging = false
        recordingForCurrentGesture = false

        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80L)
            .start()

        when {
            wasDragging -> {
                onDragEnd?.invoke()
            }

            wasRecording -> {
                setState(State.PROCESSING)
                onRecordingStop?.invoke()
            }

            cancelled -> {
            /*
             * Nothing else to do.
             *
             * The recording callback has already been sent
             * if recording was active.
             */
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val center =
            width / 2f

        val radius =
            width * 0.38f

        when (state) {
            State.IDLE -> {
                paint.color =
                    0xB033B955.toInt()

                canvas.drawCircle(
                    center,
                    center,
                    radius,
                    paint,
                )
            }

            State.RECORDING -> {
                paint.color =
                    0xFF4CAF50.toInt()

                canvas.drawCircle(
                    center,
                    center,
                    radius + 3 * density,
                    paint,
                )

                paint.color =
                    0xFFFF3030.toInt()

                canvas.drawCircle(
                    center,
                    center,
                    8 * density,
                    paint,
                )
            }

            State.PROCESSING -> {
                paint.color =
                    0xCC2E7D32.toInt()

                canvas.drawCircle(
                    center,
                    center,
                    radius,
                    paint,
                )
            }
        }

        drawMicrophone(
            canvas = canvas,
            cx = center,
            cy = center,
        )
    }

    fun setState(value: State) {
        state = value

        contentDescription =
            context.getString(
                when (value) {
                    State.IDLE ->
                        R.string.button_idle

                    State.RECORDING ->
                        R.string.button_recording

                    State.PROCESSING ->
                        R.string.button_processing
                },
            )

        invalidate()
    }

    fun reset() {
        setState(State.IDLE)

        dragging = false
        recordingForCurrentGesture = false

        animate().cancel()

        scaleX = 1f
        scaleY = 1f
    }

    fun showAnimated(onEnd: (() -> Unit)? = null) {
        if (visibility == VISIBLE) {
            onEnd?.invoke()
            return
        }

        animate().cancel()

        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f

        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(VISIBILITY_ANIMATION_DURATION)
            .withEndAction {
                onEnd?.invoke()
            }.start()
    }

    fun hideAnimated(onEnd: (() -> Unit)? = null) {
        if (visibility != VISIBLE) {
            onEnd?.invoke()
            return
        }

        animate().cancel()

        animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(VISIBILITY_ANIMATION_DURATION)
            .withEndAction {
                visibility = GONE
                alpha = 0f
                scaleX = 0.9f
                scaleY = 0.9f

                onEnd?.invoke()
            }.start()
    }

    private fun drawMicrophone(
        canvas: Canvas,
        cx: Float,
        cy: Float,
    ) {
        val microphoneWidth =
            12 * density

        val microphoneHeight =
            20 * density

        val left =
            cx - microphoneWidth / 2

        val top =
            cy - microphoneHeight / 2

        val microphoneRect =
            RectF(
                left,
                top,
                left + microphoneWidth,
                top + microphoneHeight,
            )

        canvas.drawRoundRect(
            microphoneRect,
            microphoneWidth / 2,
            microphoneWidth / 2,
            iconPaint,
        )

        val arcRect =
            RectF(
                cx - 11 * density,
                cy - 5 * density,
                cx + 11 * density,
                cy + 11 * density,
            )

        canvas.drawArc(
            arcRect,
            0f,
            180f,
            false,
            strokePaint,
        )

        canvas.drawLine(
            cx,
            cy + 6 * density,
            cx,
            cy + 12 * density,
            strokePaint,
        )

        canvas.drawLine(
            cx - 5 * density,
            cy + 12 * density,
            cx + 5 * density,
            cy + 12 * density,
            strokePaint,
        )
    }
}
