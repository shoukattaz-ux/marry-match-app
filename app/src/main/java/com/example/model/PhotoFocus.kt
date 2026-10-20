package com.example.model

import kotlinx.serialization.Serializable

/**
 * How the round avatar shows a photo (migration 026). [x] and [y] run from -1 (left/top) to 1
 * (right/bottom), 0 is the middle; [zoom] runs from 1 (the circle-filling crop) to 4.
 */
@Serializable
data class PhotoFocus(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f) {

    /** The same framing with all values inside the allowed range (the server refuses others). */
    fun clamped() = PhotoFocus(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f), zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** Moves the framing by a finger drag of ([dx], [dy]) pixels in a circle [sizePx] wide. */
    fun dragged(dx: Float, dy: Float, sizePx: Float): PhotoFocus {
        if (sizePx <= 0f) return this
        // Dragging the picture right shows more of its left side, so the focus moves left.
        val step = 2f / (sizePx * zoom)
        return copy(x = x - dx * step, y = y - dy * step).clamped()
    }

    fun zoomedBy(factor: Float) = copy(zoom = zoom * factor).clamped()

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}
