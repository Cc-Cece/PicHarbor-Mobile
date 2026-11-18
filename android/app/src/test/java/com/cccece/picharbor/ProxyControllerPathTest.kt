package com.cccece.picharbor

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class ProxyControllerPathTest {
    @Test fun nodeNamesRoundTripAsOnePathSegment() {
        for(name in listOf("香港 01", "node+a", "A/B", "100% ready #1?", "🇯🇵 日本", "x%2Fy", "line\nbreak", "node&next=value")) {
            val encoded=proxyPathSegment(name)
            val uri=URI("http://127.0.0.1/proxies/$encoded/delay")
            assertEquals("/proxies/$name/delay",uri.path)
            assertEquals(4,uri.rawPath.split('/').size)
            assertFalse(encoded.contains('+'))
            assertNull(uri.query)
            assertNull(uri.fragment)
        }
    }
    @Test fun spacesAndLiteralPlusAreDifferentNodes() {
        assertEquals("node%20one",proxyPathSegment("node one"))
        assertEquals("node%2Bone",proxyPathSegment("node+one"))
        assertNotEquals(proxyPathSegment("node one"),proxyPathSegment("node+one"))
    }
}
