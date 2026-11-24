package io.github.batlh.mdreader

import org.junit.Assert.assertEquals
import org.junit.Test

class LineOpsTest {

    @Test
    fun sortieren() {
        val l = listOf("b", "A", "a", "10 x", "2 y")
        assertEquals(listOf("10 x", "2 y", "A", "a", "b"), LineOps.sort(l, LineOps.Sort.ASC))
        assertEquals(listOf("b", "a", "A", "2 y", "10 x"), LineOps.sort(l, LineOps.Sort.DESC))
        assertEquals(listOf("2 y", "10 x", "A", "a", "b"), LineOps.sort(l, LineOps.Sort.NUMERIC))
    }

    @Test
    fun weitereZeilenbefehle() {
        assertEquals(listOf("a", "bb", "ccc"), LineOps.sort(listOf("ccc", "a", "bb"), LineOps.Sort.LENGTH))
        assertEquals(listOf("c", "b", "a"), LineOps.reverse(listOf("a", "b", "c")))
        assertEquals(listOf("a", "b", "a"), LineOps.removeConsecutiveDuplicates(listOf("a", "a", "b", "a", "a")))
        assertEquals("a b  c", LineOps.eolToSpace(listOf("a", "b ", "c")))
        assertEquals(listOf("\tx", "", "\ty"), LineOps.indent(listOf("x", "", "y"), "\t"))
        assertEquals(listOf("x", "  y", "z"), LineOps.outdent(listOf("\tx", "      y", "z"), 4))
        assertEquals(setOf("a", "b", "c"), LineOps.shuffle(listOf("a", "b", "c")).toSet())
    }

    @Test
    fun doppelteUndLeere() {
        assertEquals(listOf("a", "b"), LineOps.removeDuplicates(listOf("a", "b", "a")))
        assertEquals(listOf("a", "b"), LineOps.removeEmpty(listOf("a", "  ", "", "b")))
    }

    @Test
    fun verbinden() {
        assertEquals("eins zwei drei", LineOps.join(listOf("eins ", "  zwei", "", "drei")))
    }

    @Test
    fun tabs() {
        assertEquals(listOf("    a", "ab  c"), LineOps.tabsToSpaces(listOf("\ta", "ab\tc"), 4))
        assertEquals(listOf("\t\t a"), LineOps.leadingSpacesToTabs(listOf("         a"), 4))
    }

    @Test
    fun grossKlein() {
        assertEquals("ÄPFEL UND BIRNEN", LineOps.changeCase("Äpfel und Birnen", LineOps.Case.UPPER))
        assertEquals("Äpfel Und Birnen", LineOps.changeCase("äPFEL und birnen", LineOps.Case.PROPER))
        assertEquals("Hallo welt. Wie geht's?", LineOps.changeCase("hALLO WELT. wie geht's?", LineOps.Case.SENTENCE))
        assertEquals("hALLO", LineOps.changeCase("Hallo", LineOps.Case.INVERT))
    }

    @Test
    fun zeilenkommentarUmschalten() {
        val code = listOf("  a = 1", "", "    b = 2")
        val on = LineOps.toggleLineComment(code, "//")
        assertEquals(listOf("  // a = 1", "", "  //   b = 2"), on)
        assertEquals(code, LineOps.toggleLineComment(on, "//"))
    }

    @Test
    fun blockkommentarUmschalten() {
        val on = LineOps.toggleBlockComment("  <b>x</b>", "<!--", "-->")
        assertEquals("  <!-- <b>x</b> -->", on)
        assertEquals("  <b>x</b>", LineOps.toggleBlockComment(on, "<!--", "-->"))
    }
}
