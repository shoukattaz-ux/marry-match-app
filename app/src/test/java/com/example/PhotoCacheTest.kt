package com.example

import com.example.core.PhotoCache
import com.example.data.ProfileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCacheTest {

    @Test
    fun signedLinksOfTheSamePhotoShareOneCacheKey() {
        val a = "https://x.supabase.co/storage/v1/object/sign/profile-photos/u1/p1.jpg?token=aaa"
        val b = "https://x.supabase.co/storage/v1/object/sign/profile-photos/u1/p1.jpg?token=bbb"
        assertEquals(PhotoCache.keyFor(a), PhotoCache.keyFor(b))
        assertEquals("supabase-photo:profile-photos/u1/p1.jpg", PhotoCache.keyFor(a))
    }

    @Test
    fun otherImagesKeepTheirNormalKey() {
        assertNull(PhotoCache.keyFor("https://example.com/a.jpg"))
        assertNull(PhotoCache.keyFor("file:///data/selfie.jpg"))
    }

    @Test
    fun thumbnailPathSitsNextToThePhoto() {
        assertEquals("u1/p1_t.jpg", ProfileService.thumbPathFor("u1/p1.jpg"))
    }
}
