package io.github.batlh.mdreader

import org.junit.Assert.assertEquals
import org.junit.Test

class MacroTest {

    @Test
    fun tippenWirdZusammengefasst() {
        val steps = listOf(
            MacroStep.Insert("Hal"),
            MacroStep.Insert("lp"),
            MacroStep.Delete(1),
            MacroStep.Insert("o"),
            MacroStep.Command("dup"),
            MacroStep.Delete(1),
            MacroStep.Delete(2),
        )
        assertEquals(
            listOf(MacroStep.Insert("Hallo"), MacroStep.Command("dup"), MacroStep.Delete(3)),
            Macros.compact(steps),
        )
    }

    @Test
    fun mehrLoeschenAlsGetippt() {
        assertEquals(listOf(MacroStep.Delete(2)), Macros.compact(listOf(MacroStep.Insert("a"), MacroStep.Delete(3))))
    }

    @Test
    fun jsonHinUndZurueck() {
        val steps = listOf(
            MacroStep.Insert("x\ny"),
            MacroStep.Delete(2, forward = true),
            MacroStep.Command("case:UPPER"),
            MacroStep.Key(20, 1),
        )
        assertEquals(steps, Macros.fromJson(Macros.toJson(steps)))
    }
}
