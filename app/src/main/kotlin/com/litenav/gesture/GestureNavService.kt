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
    private val stripHeightDp = 5            // true-edge sliver only.
    private val minSwipeDistancePx = 60      // upward travel required to count as swipe
    private val holdThresholdMs = 300L       // pause-at-peak longer than this = "hold" -> Recents
    private val maxTotalGestureMs = 1500L    // ignore anything longer than this (accidental drag)
    private val rightZoneFraction = 0.33f    // right ~1/3 is Back, rest is Home/Recents
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
                handleTouch(event)
                return true
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
                    // Driftlock removed as requested
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
        
        when {
            // Hold anywhere: Recents
            holdDurationMs >= holdThresholdMs -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            
            // Right zone: Back
            xPos > screenWidth * (1 - rightZoneFraction) -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            
            // Everywhere else: Home
            else -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
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
