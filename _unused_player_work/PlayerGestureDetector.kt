package `is`.xyz.mpv

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Gesture detector for the modern video player:
 * - Fling left/right = seek +/- 10 seconds
 * - Drag on left side = brightness up/down
 * - Drag on right side = volume up/down
 */
class PlayerGestureDetector(
    context: Context,
    private val onSeekDelta: (deltaMs: Long) -> Unit,
    private val onVolumeDelta: (delta: Float) -> Unit,
    private val onBrightnessDelta: (delta: Float) -> Unit,
    private val onPlayPause: () -> Unit,
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {

    private val detector = GestureDetector(context, this)
    private var lastX = 0f
    private var lastY = 0f
    private var trackingSide: Int? = null // -1 = left (brightness), 1 = right (volume)

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                trackingSide = if (event.x < v.width / 2f) -1 else 1
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y

                trackingSide?.let { side ->
                    when (side) {
                        -1 -> onBrightnessDelta(-dy / 10f) // drag up on left = brighter
                        1  -> onVolumeDelta(-dy / 100f)    // drag up on right = louder
                    }
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingSide = null
                false
            }

            else -> false
        }
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (Math.abs(velocityX) > Math.abs(velocityY) * 1.5f) {
            val delta = if (velocityX > 0) 10_000L else -10_000L // 10s skip
            onSeekDelta(delta)
            return true
        }
        return false
    }

    /** Call this from your View's onTouchEvent so gestures are captured. */
    fun dispatch(event: MotionEvent): Boolean = detector.onTouchEvent(event)
}
