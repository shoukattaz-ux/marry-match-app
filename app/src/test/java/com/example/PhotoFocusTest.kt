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
        val moved = PhotoFocus().dragged(dx = 50f, dy = 0f, widthPx = 200f)
        assertEquals(-0.5f, moved.x, 0.0001f)
        assertEquals(0f, moved.y, 0.0001f)
    }

    @Test
    fun draggingMovesLessWhenZoomedIn() {
        val moved = PhotoFocus(zoom = 2f).dragged(dx = 0f, dy = -50f, widthPx = 200f)
        assertEquals(0.25f, moved.y, 0.0001f)
    }

    @Test
    fun draggingAndZoomingAreLimited() {
        assertEquals(1f, PhotoFocus().dragged(-10_000f, 0f, 200f).x)
        assertEquals(4f, PhotoFocus(zoom = 3f).zoomedBy(5f).zoom)
        assertEquals(PhotoFocus(), PhotoFocus().dragged(10f, 10f, widthPx = 0f))
    }

    @Test
    fun aTallCardFrameMovesUpAndDownByItsOwnHeight() {
        val moved = PhotoFocus().dragged(dx = 50f, dy = 50f, widthPx = 200f, heightPx = 400f)
        assertEquals(-0.5f, moved.x, 0.0001f)
        assertEquals(-0.25f, moved.y, 0.0001f)
    }

    @Test
    fun aFaceBecomesTheFocusPoint() {
        assertEquals(PhotoFocus(), PhotoFocus.forFace(0.5f, 0.5f))
        val upperLeft = PhotoFocus.forFace(0.25f, 0.2f)
        assertEquals(-0.5f, upperLeft.x, 0.0001f)
        assertEquals(-0.6f, upperLeft.y, 0.0001f)
        assertEquals(PhotoFocus(1f, -1f), PhotoFocus.forFace(1.3f, -0.1f))
    }

    @Test
    fun everyPhotoHasItsOwnFraming() {
        val user = com.example.model.UserProfile(
            photos = listOf("a", "b", "c"),
            photoFocus = listOf(PhotoFocus(0.1f), PhotoFocus(-0.4f, 0.2f, 2f)),
            mainPhotoFocus = PhotoFocus(0.9f)
        )
        assertEquals(PhotoFocus(-0.4f, 0.2f, 2f), user.focusOf(1))
        assertEquals(PhotoFocus(), user.focusOf(2))
        assertEquals(PhotoFocus(0.9f), com.example.model.UserProfile(mainPhotoFocus = PhotoFocus(0.9f)).focusOf(0))
    }
}
