package com.example

import com.example.model.PhotoFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFocusTest {

    @Test
    fun defaultIsTheMiddleWithoutZoom() {
        assertEquals(PhotoFocus(0f, 0f, 1f), PhotoFocus())
    }

    @Test
    fun valuesStayInTheRangeTheServerAccepts() {
        assertEquals(PhotoFocus(1f, -1f, 4f), PhotoFocus(3f, -2f, 9f).clamped())
        assertEquals(1f, PhotoFocus(zoom = 0.2f).clamped().zoom)
    }

    @Test
    fun draggingRightShowsMoreOfTheLeftSide() {
        val moved = PhotoFocus().dragged(dx = 50f, dy = 0f, sizePx = 200f)
        assertEquals(-0.5f, moved.x, 0.0001f)
        assertEquals(0f, moved.y, 0.0001f)
    }

    @Test
    fun draggingMovesLessWhenZoomedIn() {
        val moved = PhotoFocus(zoom = 2f).dragged(dx = 0f, dy = -50f, sizePx = 200f)
        assertEquals(0.25f, moved.y, 0.0001f)
    }

    @Test
    fun draggingAndZoomingAreLimited() {
        assertEquals(1f, PhotoFocus().dragged(-10_000f, 0f, 200f).x)
        assertEquals(4f, PhotoFocus(zoom = 3f).zoomedBy(5f).zoom)
        assertEquals(PhotoFocus(), PhotoFocus().dragged(10f, 10f, sizePx = 0f))
    }
}
