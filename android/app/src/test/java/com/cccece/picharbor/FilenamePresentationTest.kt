package com.cccece.picharbor

import org.junit.Assert.*
import org.junit.Test

class FilenamePresentationTest {
    private fun shorten(name:String,width:Int)=ellipsizeFilename(name,width.toFloat()){it.codePointCount(0,it.length).toFloat()}
    @Test fun keepsTheNameBeginningExtensionAndStemTailWithinTheAvailableWidth() {
        val name="IMG_20261118_1234567890_edited_final_version.HEIC"
        val result=shorten(name,28)
        assertTrue(result.startsWith("IMG_"));assertTrue(result.endsWith("_version.HEIC"))
        assertTrue(result.contains("..."));assertTrue(result.length<=28)
    }
    @Test fun shortNamesRemainUntouchedAndNarrowRowsKeepBothEnds() {
        assertEquals("x.jpg",shorten("x.jpg",20))
        assertEquals("",shorten("long_name.jpg",0))
        assertEquals("...",shorten("long_name.jpg",4))
        val name="very_long_camera_filename.mov"
        val result=shorten(name,12)
        assertTrue(result.startsWith("v"));assertTrue(result.endsWith(".mov"));assertTrue(result.length<=12)
    }
    @Test fun handlesUnicodeAndMultipleExtensionsWithoutBreakingSurrogates() {
        val result=shorten("旅行📷相片_非常长的原始文件名_最终版本_123456789.tar.gz",22)
        assertTrue(result.endsWith(".tar.gz"));assertTrue(result.contains("..."))
        result.forEachIndexed{index,char->
            if(char.isHighSurrogate())assertTrue(index<result.lastIndex&&result[index+1].isLowSurrogate())
            if(char.isLowSurrogate())assertTrue(index>0&&result[index-1].isHighSurrogate())
        }
        assertEquals("",shorten("",0))
    }
}
