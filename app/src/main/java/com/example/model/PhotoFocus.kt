package com.example.model

import kotlinx.serialization.Serializable

/**
 * How a photo sits in its frame (migration 026): the round avatar and the Discover card both use
 * it. [x] and [y] run from -1 (left/top) to 1 (right/bottom), 0 is the middle; [zoom] runs from 1
 * (the frame-filling crop) to 4.
 */
@Serializable
data class PhotoFocus(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f) {

    /** The same framing with all values inside the allowed range (the server refuses others). */
    fun clamped() = PhotoFocus(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f), zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** Moves the framing by a finger drag of ([dx], [dy]) pixels in a frame [widthPx] x [heightPx]. */
    fun dragged(dx: Float, dy: Float, widthPx: Float, heightPx: Float = widthPx): PhotoFocus {
        if (widthPx <= 0f || heightPx <= 0f) return this
        // Dragging the picture right shows more of its left side, so the focus moves left.
        return copy(x = x - dx * 2f / (widthPx * zoom), y = y - dy * 2f / (heightPx * zoom)).clamped()
    }

    fun zoomedBy(factor: Float) = copy(zoom = zoom * factor).clamped()

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f

        /**
         * Framing that keeps a face in view: the face centre at ([centerX], [centerY]), each 0..1 of
         * the photo width / height, becomes the focus point.
         */
        fun forFace(centerX: Float, centerY: Float) = PhotoFocus(centerX * 2f - 1f, centerY * 2f - 1f).clamped()
    }
}
