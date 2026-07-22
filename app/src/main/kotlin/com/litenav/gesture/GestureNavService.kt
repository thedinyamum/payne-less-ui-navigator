package com.litenav.gesture

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
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
    private val rightZoneFraction = 0.25f    // right 1/4 is Back, rest is Home/Recents
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayout()
    }

    private fun updateLayout() {
        val overlay = overlay ?: return
        val wm = wm ?: return
        val params = overlay.layoutParams as? WindowManager.LayoutParams ?: return
        val rotation = wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val density = resources.displayMetrics.density
        val thicknessPx = (stripHeightDp * density).toInt()

        when (rotation) {
            Surface.ROTATION_0 -> {
                params.gravity = Gravity.BOTTOM
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = thicknessPx
            }
            Surface.ROTATION_90 -> {
                params.gravity = Gravity.RIGHT
                params.width = thicknessPx
                params.height = WindowManager.LayoutParams.MATCH_PARENT
            }
            Surface.ROTATION_180 -> {
                params.gravity = Gravity.TOP
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = thicknessPx
            }
            Surface.ROTATION_270 -> {
                params.gravity = Gravity.LEFT
                params.width = thicknessPx
                params.height = WindowManager.LayoutParams.MATCH_PARENT
            }
        }
        wm.updateViewLayout(overlay, params)
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
        updateLayout()
    }

    private fun handleTouch(event: MotionEvent) {
        val rotation = wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val viewHeight = overlay?.height?.toFloat() ?: 1f
        val viewWidth = overlay?.width?.toFloat() ?: 1f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                reachedThreshold = false
                reachedThresholdTime = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (!reachedThreshold) {
                    val dy = when (rotation) {
                        Surface.ROTATION_0 -> downY - event.y
                        Surface.ROTATION_90 -> downX - event.x
                        Surface.ROTATION_270 -> event.x - downX
                        Surface.ROTATION_180 -> event.y - downY
                        else -> downY - event.y
                    }
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
                    
                    val posAlongBar = when (rotation) {
                        Surface.ROTATION_0 -> downX
                        Surface.ROTATION_90 -> viewHeight - downY
                        Surface.ROTATION_270 -> downY
                        Surface.ROTATION_180 -> viewWidth - downX
                        else -> downX
                    }
                    fireAction(posAlongBar, holdDuration)
                }

                reachedThreshold = false
            }

            MotionEvent.ACTION_CANCEL -> {
                reachedThreshold = false
            }
        }
    }

    private fun fireAction(posAlongBar: Float, holdDurationMs: Long) {
        val rotation = wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val overlay = overlay ?: return
        val barLength = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            overlay.height.toFloat()
        } else {
            overlay.width.toFloat()
        }
        
        when {
            // Hold anywhere: Recents
            holdDurationMs >= holdThresholdMs -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            
            // Right zone: Back
            posAlongBar > barLength * (1 - rightZoneFraction) -> {
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
