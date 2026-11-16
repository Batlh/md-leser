package de.batlh.mdreader

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {

    @Test
    fun erweitertWandeltEscapesUm() {
        assertEquals("a\nb\tc\\d", Search.unescape("a\\nb\\tc\\\\d"))
        assertEquals("\n", Search.unescape("\\r\\n"))
        assertEquals("A€", Search.unescape("\\x41\\u20AC"))
        assertEquals("\\q", Search.unescape("\\q"))
    }

    @Test
    fun normalSuchtWoertlichUndOhneGrossKlein() {
        val q = SearchQuery("a.b")
        assertEquals(2, Search.count("a.b A.B axb", q))
        assertEquals(1, Search.count("a.b A.B axb", q.copy(matchCase = true)))
    }

    @Test
    fun ganzesWort() {
        val q = SearchQuery("cat", wholeWord = true)
        assertEquals(1, Search.count("cat concat cats", q))
    }

    @Test
    fun regexMitRueckverweisWieNotepad() {
        val q = SearchQuery("(\\w+)@(\\w+)", SearchMode.REGEX)
        assertEquals("b at a" to 1, Search.replaceAll("a@b", q, "\\2 at \\1"))
        assertEquals("b-a" to 1, Search.replaceAll("a@b", q, "$2-$1"))
    }

    @Test
    fun regexZeilenanfang() {
        val q = SearchQuery("^x", SearchMode.REGEX)
        assertEquals("- a\n- b" to 2, Search.replaceAll("x a\nx b", q, "-"))
    }

    @Test
    fun erweitertErsetztZeilenenden() {
        val q = SearchQuery("\\n", SearchMode.EXTENDED)
        assertEquals("a, b, c" to 2, Search.replaceAll("a\nb\nc", q, ", "))
    }

    @Test
    fun normalErsetztDollarWoertlich() {
        assertEquals("5 $1" to 1, Search.replaceAll("5 x", SearchQuery("x"), "$1"))
    }

    @Test
    fun leererRegexHaengtNicht() {
        assertEquals(4, Search.count("abc", SearchQuery("", SearchMode.REGEX).copy(text = "x*")))
    }
}
