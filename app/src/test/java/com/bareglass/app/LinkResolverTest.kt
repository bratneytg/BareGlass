package com.bareglass.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkResolverTest {
    @Test fun normalizeAddsScheme() {
        assertEquals("https://a.com/x.mp4", LinkResolver.normalize("a.com/x.mp4"))
        assertEquals("http://a.com/x", LinkResolver.normalize("  http://a.com/x "))
    }

    @Test fun detectsDirectFiles() {
        assertTrue(LinkResolver.looksDirect("https://a.com/v/clip.mp4?token=1"))
        assertTrue(LinkResolver.looksDirect("https://a.com/live/index.m3u8"))
        assertFalse(LinkResolver.looksDirect("https://a.com/watch/123"))
        assertFalse(LinkResolver.looksDirect("https://a.com/page.html"))
    }

    @Test fun hlsOnlyForM3u8() {
        assertTrue(LinkResolver.isHls("https://a.com/x.m3u8?a=b"))
        assertFalse(LinkResolver.isHls("https://a.com/x.mp4"))
    }

    @Test fun labelUsesHostAndFile() {
        assertEquals("a.com · clip.mp4", LinkResolver.labelFor("https://www.a.com/v/clip.mp4?x=1"))
        assertEquals("a.com", LinkResolver.labelFor("https://a.com/watch/123"))
    }
}
