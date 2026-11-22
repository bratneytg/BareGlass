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

    @Test fun sniffDetectsRealContent() {
        assertEquals(LinkResolver.Kind.HLS, LinkResolver.sniff("application/octet-stream", "#EXTM3U\n#EXT-X-VERSION:3"))
        assertEquals(LinkResolver.Kind.HLS, LinkResolver.sniff("application/vnd.apple.mpegurl", ""))
        assertEquals(LinkResolver.Kind.HTML, LinkResolver.sniff("video/mp4", "<!DOCTYPE html><html>"))
        assertEquals(LinkResolver.Kind.HTML, LinkResolver.sniff("text/html; charset=utf-8", "x"))
        assertEquals(LinkResolver.Kind.MEDIA, LinkResolver.sniff("video/mp4", "\u0000\u0000ftyp"))
        assertEquals(LinkResolver.Kind.MEDIA, LinkResolver.sniff("application/octet-stream", "abc"))
        assertEquals(LinkResolver.Kind.UNKNOWN, LinkResolver.sniff("application/json", "{}"))
    }
}
