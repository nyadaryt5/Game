package com.nova.galaxysandbox.engine

import com.nova.galaxysandbox.core.MathX

/** 2D camera with smooth follow, clamping and screen<->world conversion. */
class Camera {
    var x = 0f
    var y = 0f
    var zoom = 1f

    var targetX = 0f
    var targetY = 0f
    var targetZoom = 1f

    var minZoom = 0.05f
    var maxZoom = 6f
    var viewW = 1080f
    var viewH = 1920f
    var shakeX = 0f
    var shakeY = 0f
    private var shake = 0f
    private var shakeTime = 0f

    fun snapTo(wx: Float, wy: Float, z: Float) {
        x = wx; y = wy; zoom = z
        targetX = wx; targetY = wy; targetZoom = z
    }

    fun moveTo(wx: Float, wy: Float, z: Float = targetZoom) {
        targetX = wx; targetY = wy; targetZoom = MathX.clamp(z, minZoom, maxZoom)
    }

    fun panBy(dxScreen: Float, dyScreen: Float) {
        targetX -= dxScreen / zoom
        targetY -= dyScreen / zoom
        x = targetX; y = targetY
    }

    fun zoomBy(factor: Float, focusScreenX: Float, focusScreenY: Float) {
        val before = screenToWorld(focusScreenX, focusScreenY)
        targetZoom = MathX.clamp(targetZoom * factor, minZoom, maxZoom)
        zoom = targetZoom
        val after = screenToWorld(focusScreenX, focusScreenY)
        targetX += before[0] - after[0]
        targetY += before[1] - after[1]
        x = targetX; y = targetY
    }

    fun addShake(amount: Float) {
        shake = kotlin.math.max(shake, amount)
    }

    fun update(dt: Float) {
        val k = 1f - Math.pow(0.0005, dt.toDouble()).toFloat()
        x = MathX.lerp(x, targetX, k)
        y = MathX.lerp(y, targetY, k)
        zoom = MathX.lerp(zoom, targetZoom, k)
        if (shake > 0.001f) {
            shakeTime += dt * 46f
            val mag = shake * 26f
            shakeX = kotlin.math.sin(shakeTime) * mag
            shakeY = kotlin.math.cos(shakeTime * 1.37f) * mag
            shake = kotlin.math.max(0f, shake - dt * 1.9f)
        } else {
            shakeX = 0f; shakeY = 0f
        }
    }

    private val tmp = FloatArray(2)

    fun screenToWorld(sx: Float, sy: Float): FloatArray {
        tmp[0] = (sx - viewW / 2f) / zoom + x
        tmp[1] = (sy - viewH / 2f) / zoom + y
        return tmp
    }

    fun worldToScreenX(wx: Float): Float = (wx - x) * zoom + viewW / 2f + shakeX
    fun worldToScreenY(wy: Float): Float = (wy - y) * zoom + viewH / 2f + shakeY

    fun visibleLeft(): Float = x - viewW / (2f * zoom)
    fun visibleTop(): Float = y - viewH / (2f * zoom)
    fun visibleRight(): Float = x + viewW / (2f * zoom)
    fun visibleBottom(): Float = y + viewH / (2f * zoom)

    fun isVisible(wx: Float, wy: Float, pad: Float): Boolean =
        wx > visibleLeft() - pad && wx < visibleRight() + pad &&
            wy > visibleTop() - pad && wy < visibleBottom() + pad
}
