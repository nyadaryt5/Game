package com.nova.galaxysandbox.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.audio.Sfx
import com.nova.galaxysandbox.engine.GameEngine
import com.nova.galaxysandbox.engine.GameMode
import com.nova.galaxysandbox.render.GalaxyRenderer
import com.nova.galaxysandbox.render.PlanetRenderer
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The actual game view: owns the render thread, converts touches into engine
 * commands and composites the galaxy / planet scenes with a zoom transition.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(
    context: Context,
    val engine: GameEngine,
    private val sfx: Sfx?
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val galaxyRenderer = GalaxyRenderer(engine)
    private val planetRenderer = PlanetRenderer(engine)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastFrame = 0L

    // Touch state
    private var pointerCount = 0
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var painting = false
    private var lastPinchDist = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var holdFiring = false

    var onPlanetEntered: (() -> Unit)? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) { start() }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.setViewport(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }

    fun start() {
        if (running) return
        running = true
        lastFrame = System.nanoTime()
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    fun stop() {
        running = false
        try { thread?.join(800) } catch (_: InterruptedException) { }
        thread = null
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastFrame) / 1_000_000_000f
            lastFrame = now
            if (dt > 0.1f) dt = 0.1f
            if (dt <= 0f) dt = 0.0001f

            try {
                engine.update(dt)
            } catch (t: Throwable) {
                // Never let a simulation hiccup kill the render thread.
                t.printStackTrace()
            }

            val surface = holder.surface
            if (!surface.isValid) { Thread.sleep(8); continue }
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    surface.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) renderFrame(canvas, dt)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) { }
                }
            }

            // Aim for ~60fps without busy-waiting.
            val frameMs = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - frameMs
            if (sleep > 1) {
                try { Thread.sleep(sleep) } catch (_: InterruptedException) { }
            }
        }
    }

    private fun renderFrame(canvas: Canvas, dt: Float) {
        val t = engine.transition
        if (t < 0.999f) {
            galaxyRenderer.draw(canvas, dt)
        }
        if (t > 0.001f && engine.world != null) {
            val alpha = (t * 255).toInt().coerceIn(0, 255)
            val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alpha)
            planetRenderer.draw(canvas, dt)
            canvas.restoreToCount(layer)
        }
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                pointerCount = 1
                lastX = event.x; lastY = event.y
                downX = event.x; downY = event.y
                downTime = System.currentTimeMillis()
                moved = false
                painting = false
                planetRenderer.brushScreenX = event.x
                planetRenderer.brushScreenY = event.y
                if (engine.mode == GameMode.PLANET && engine.selectedTool != Tool.INSPECT) {
                    painting = true
                    engine.onDrag(event.x, event.y)
                    sfx?.tool(engine.selectedTool)
                } else if (engine.mode == GameMode.GALAXY && engine.weaponArmed &&
                    engine.selectedWeapon.targeting == com.nova.galaxysandbox.action.Weapon.Targeting.HOLD
                ) {
                    holdFiring = true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                painting = false
                holdFiring = false
                lastPinchDist = pinchDistance(event)
                lastX = centroidX(event); lastY = centroidY(event)
            }

            MotionEvent.ACTION_MOVE -> {
                planetRenderer.brushScreenX = event.x
                planetRenderer.brushScreenY = event.y
                if (event.pointerCount >= 2) {
                    val d = pinchDistance(event)
                    if (lastPinchDist > 0f && d > 0f) {
                        engine.pinch(d / lastPinchDist, centroidX(event), centroidY(event))
                    }
                    lastPinchDist = d
                    val cx = centroidX(event)
                    val cy = centroidY(event)
                    engine.pan(cx - lastX, cy - lastY)
                    lastX = cx; lastY = cy
                    moved = true
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(event.x - downX) > 14f || abs(event.y - downY) > 14f) moved = true
                    if (painting) {
                        engine.onDrag(event.x, event.y)
                    } else if (holdFiring) {
                        engine.onDrag(event.x, event.y)
                        if (moved) engine.onTap(event.x, event.y)
                    } else {
                        engine.pan(dx, dy)
                    }
                    lastX = event.x; lastY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pointerCount = event.pointerCount - 1
                lastX = centroidX(event); lastY = centroidY(event)
                lastPinchDist = 0f
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dur = System.currentTimeMillis() - downTime
                if (!moved && dur < 320 && pointerCount <= 1 && !painting) {
                    val now = System.currentTimeMillis()
                    val isDouble = now - lastTapTime < 300 &&
                        hypot(event.x - lastTapX, event.y - lastTapY) < 60f
                    if (isDouble) {
                        lastTapTime = 0
                        handleDoubleTap(event.x, event.y)
                    } else {
                        lastTapTime = now
                        lastTapX = event.x; lastTapY = event.y
                        handleTap(event.x, event.y)
                    }
                }
                painting = false
                holdFiring = false
                pointerCount = 0
                planetRenderer.brushScreenX = -1f
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val wasArmed = engine.weaponArmed && engine.mode == GameMode.GALAXY
        engine.onTap(x, y)
        if (wasArmed) sfx?.weapon(engine.selectedWeapon)
        else sfx?.click()
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        if (engine.mode == GameMode.GALAXY) {
            val p = engine.selectPlanetAt(x, y)
            if (p != null) {
                engine.enterPlanet(p)
                sfx?.whoosh()
                onPlanetEntered?.invoke()
            } else {
                engine.pinch(1.7f, x, y)
            }
        }
    }

    private fun pinchDistance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    }

    private fun centroidX(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getX(i)
        return sum / e.pointerCount
    }

    private fun centroidY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getY(i)
        return sum / e.pointerCount
    }
}
