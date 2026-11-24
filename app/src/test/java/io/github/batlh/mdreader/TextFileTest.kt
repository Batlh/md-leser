package io.github.batlh.mdreader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileTest {

    private fun roundTrip(bytes: ByteArray): Pair<String, FileFormat> {
        val (text, format) = TextFile.decode(bytes)
        assertArrayEquals("Datei muss unverändert zurückgeschrieben werden", bytes, TextFile.encode(text, format))
        return text to format
    }

    @Test
    fun utf8MitLf() {
        val (text, f) = roundTrip("# Titel\nÄöü ✓\n".toByteArray())
        assertEquals("# Titel\nÄöü ✓\n", text)
        assertEquals(Charsets.UTF_8, f.charset)
        assertFalse(f.bom)
        assertFalse(f.crlf)
        assertEquals("UTF-8 · Unix (LF)", f.label)
    }

    @Test
    fun crlfWirdImEditorZuLfUndBeimSpeichernZurueck() {
        val (text, f) = roundTrip("a\r\nb\r\n".toByteArray())
        assertEquals("a\nb\n", text)
        assertTrue(f.crlf)
        assertEquals("a\r\nb\r\nc\r\n", String(TextFile.encode(text + "c\n", f)))
    }

    @Test
    fun bomBleibtErhalten() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hallo".toByteArray()
        val (text, f) = roundTrip(bytes)
        assertEquals("hallo", text)
        assertTrue(f.bom)
        assertEquals("UTF-8-BOM · Unix (LF)", f.label)
    }

    @Test
    fun ansiWirdErkanntUndNichtZerstoert() {
        val bytes = byteArrayOf('G'.code.toByte(), 'r'.code.toByte(), 0xFC.toByte(), 0xDF.toByte(), 'e'.code.toByte())
        val (text, f) = roundTrip(bytes)
        assertEquals("Grüße", text)
        assertEquals(TextFile.ANSI, f.charset)
        assertEquals("ANSI · Unix (LF)", f.label)
    }

    @Test
    fun utf16MitBom() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Ä\r\nb".toByteArray(Charsets.UTF_16LE)
        val (text, f) = roundTrip(le)
        assertEquals("Ä\nb", text)
        assertEquals("UTF-16 LE BOM · Windows (CR LF)", f.label)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "x".toByteArray(Charsets.UTF_16BE)
        assertEquals(Charsets.UTF_16BE, roundTrip(be).second.charset)
    }

    @Test
    fun leereDatei() {
        val (text, f) = roundTrip(ByteArray(0))
        assertEquals("", text)
        assertEquals(FileFormat(), f)
    }
}
