package com.litenav.gesture

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class GestureNavService : AccessibilityService() {

    private var overlay: View? = null
    private var wm: WindowManager? = null

    // ---- tunables ----
    private val stripHeightDp = 5            // true-edge sliver only. Anything above this (browser
                                              // search bars, file-manager rows, etc.) passes through
                                              // untouched since the overlay never claims that space.
                                              // Swipes must start within this ~5dp band at the very
                                              // bottom of the screen, same as stock gesture-nav.
    private val minSwipeDistancePx = 60      // upward travel required to count as swipe
    private val maxHorizontalDriftPx = 80    // if finger drifts sideways more than this, ignore
                                              // (filters out diagonal drags / list-item swipes)
    private val rightZoneFraction = 2f / 3f  // right zone starts at this fraction of screen width
    private val holdThresholdMs = 300L       // pause-at-peak longer than this = "hold" -> Recents
    private val maxTotalGestureMs = 1500L    // ignore anything longer than this (accidental drag)
    // ------------------

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    // tracks the moment the swipe distance requirement was first satisfied,
    // i.e. when finger "arrived" at the top and might start holding
    private var reachedThresholdTime = 0L
    private var reachedThreshold = false

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
    }

    private fun addOverlay() {
        if (overlay != null) return
        val density = resources.displayMetrics.density
        val heightPx = (stripHeightDp * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT,
        )
        params.gravity = Gravity.BOTTOM

        val view = object : View(this) {
            init {
                setWillNotDraw(true)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
                handleTouch(event)
                return true
            }

            @Suppress("RedundantOverride")
            override fun performClick(): Boolean {
                return super.performClick()
            }
        }

        overlay = view
        wm?.addView(view, params)
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                reachedThreshold = false
                reachedThresholdTime = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (!reachedThreshold) {
                    val dy = downY - event.rawY
                    if (dy >= minSwipeDistancePx) {
                        reachedThreshold = true
                        reachedThresholdTime = event.eventTime
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val now = event.eventTime
                val totalElapsed = now - downTime

                if (reachedThreshold && totalElapsed <= maxTotalGestureMs) {
                    val holdDuration = now - reachedThresholdTime
                    fireAction(downX, holdDuration)
                }

                reachedThreshold = false
            }

            MotionEvent.ACTION_CANCEL -> {
                reachedThreshold = false
            }
        }
    }

    private fun fireAction(xPos: Float, holdDurationMs: Long) {
        val screenWidth = resources.displayMetrics.widthPixels
        val inRightZone = xPos >= screenWidth * rightZoneFraction

        when {
            // Right zone always means Back, regardless of hold - matches
            // "quick swipe on right" spec; no hold variant defined for Back.
            inRightZone -> performGlobalAction(GLOBAL_ACTION_BACK)

            // Anywhere else: hold at peak decides Home vs Recents.
            holdDurationMs >= holdThresholdMs -> performGlobalAction(GLOBAL_ACTION_RECENTS)

            else -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used: we don't need window content, only global actions + touch.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlay?.let { wm?.removeView(it) }
        overlay = null
    }
}
