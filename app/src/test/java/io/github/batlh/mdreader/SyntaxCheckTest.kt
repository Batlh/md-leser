package io.github.batlh.mdreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxCheckTest {

    private fun ok(scope: String, text: String) = assertNull(SyntaxCheck.check(scope, text))
    private fun bad(scope: String, text: String) =
        SyntaxCheck.check(scope, text).also { assertNotNull("Fehler erwartet in: $text", it) }!!

    @Test
    fun json() {
        ok("source.json", "{\n  \"a\": [1, 2.5, true, null],\n  \"b\": {\"c\": \"x\"}\n}")
        assertEquals(3, bad("source.json", "{\n  \"a\": 1,\n}").line)
        bad("source.json", "{\"a\": 1} {\"b\": 2}")
        bad("source.json", "")
        bad("source.json", "{'a': 1}")
        ok("source.json.comments", "{\n  // Kommentar\n  \"a\": 1\n}")
    }

    @Test
    fun xml() {
        ok("text.xml", "<?xml version=\"1.0\"?>\n<a x=\"1\"><b/></a>")
        assertEquals(2, bad("text.xml", "<a>\n<b></a>").line)
        bad("text.xml", "<a><b></b>")
    }

    @Test
    fun yaml() {
        ok("source.yaml", "a: 1\nb:\n  - x\n  - y\nc: {d: 2}\n---\nzweites: dokument\n")
        val p = bad("source.yaml", "a: 1\nb: [1, 2\nc: 3\n")
        assertTrue(p.line >= 2)
        bad("source.yaml", "a: b: c\n")
    }

    @Test
    fun lua() {
        ok("source.lua", "local f = CreateFrame(\"Frame\")\nf:SetScript(\"OnEvent\", function(self, e, ...)\n  print(e)\nend)\n")
        assertEquals(1, bad("source.lua", "local x = = 2").line)
        val p = bad("source.lua", "local a = 1\nif a then\n  print(a)\n")
        assertTrue(p.message, p.message.contains("end"))
    }

    @Test
    fun nichtUnterstuetzt() {
        assertNull(SyntaxCheck.languageName("source.python"))
        assertEquals("Lua", SyntaxCheck.languageName("source.lua"))
    }
}
