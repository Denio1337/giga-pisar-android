package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import ru.gigapisar.R

class RecordingButton(
    context: Context,
) : View(context) {
    enum class State {
        IDLE,
        RECORDING,
        PROCESSING,
    }

    private val density =
        resources.displayMetrics.density

    private val size =
        (64 * density).toInt()

    private var state =
        State.IDLE

    var onRecordingStart:
        (() -> Unit)? = null

    var onRecordingStop:
        (() -> Unit)? = null

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
        }

    init {
        isClickable = true
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES

        contentDescription =
            context.getString(
                R.string.button_idle,
            )

        setBackgroundColor(
            android.graphics.Color.TRANSPARENT,
        )
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(size, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.IDLE) {
                    setState(
                        State.RECORDING,
                    )
                    onRecordingStart?.invoke()
                }

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                if (state == State.RECORDING) {
                    setState(State.PROCESSING)
                    onRecordingStop?.invoke()
                }

                return true
            }
        }

        return true
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
            canvas,
            center,
            center,
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
    }

    private fun drawMicrophone(
        canvas: Canvas,
        cx: Float,
        cy: Float,
    ) {
        val width =
            12 * density

        val height =
            20 * density

        val left =
            cx - width / 2

        val top =
            cy - height / 2

        val rect =
            RectF(
                left,
                top,
                left + width,
                top + height,
            )

        canvas.drawRoundRect(
            rect,
            width / 2,
            width / 2,
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
