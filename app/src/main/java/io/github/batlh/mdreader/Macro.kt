package io.github.batlh.mdreader

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein Schritt eines Makros, wie Notepad++ es aufzeichnet: Tippen, Löschen,
 * Cursortasten und Menübefehle. Alles relativ zur Cursorposition beim Abspielen.
 */
sealed class MacroStep {
    data class Insert(val text: String) : MacroStep()
    data class Delete(val count: Int, val forward: Boolean = false) : MacroStep()
    data class Command(val id: String) : MacroStep()
    data class Key(val code: Int, val meta: Int) : MacroStep()
}

object Macros {

    /** Fasst Tipp-Schritte zusammen; Tippen und gleich wieder Löschen hebt sich auf (Wortvorschläge der Tastatur). */
    fun compact(steps: List<MacroStep>): List<MacroStep> {
        val out = ArrayList<MacroStep>()
        for (s in steps) {
            val last = out.lastOrNull()
            when {
                s is MacroStep.Insert && s.text.isEmpty() -> Unit
                s is MacroStep.Insert && last is MacroStep.Insert ->
                    out[out.size - 1] = MacroStep.Insert(last.text + s.text)
                s is MacroStep.Delete && !s.forward && last is MacroStep.Insert -> {
                    val keep = last.text.length - s.count
                    out.removeAt(out.size - 1)
                    if (keep > 0) out += MacroStep.Insert(last.text.substring(0, keep))
                    else if (keep < 0) out += MacroStep.Delete(-keep)
                }
                s is MacroStep.Delete && last is MacroStep.Delete && last.forward == s.forward ->
                    out[out.size - 1] = MacroStep.Delete(last.count + s.count, s.forward)
                else -> out += s
            }
        }
        return out
    }

    fun toJson(steps: List<MacroStep>): JSONArray {
        val a = JSONArray()
        for (s in steps) {
            a.put(
                when (s) {
                    is MacroStep.Insert -> JSONObject().put("t", "i").put("s", s.text)
                    is MacroStep.Delete -> JSONObject().put("t", "d").put("n", s.count).put("f", s.forward)
                    is MacroStep.Command -> JSONObject().put("t", "c").put("c", s.id)
                    is MacroStep.Key -> JSONObject().put("t", "k").put("k", s.code).put("m", s.meta)
                }
            )
        }
        return a
    }

    fun fromJson(a: JSONArray): List<MacroStep> = (0 until a.length()).mapNotNull {
        val o = a.getJSONObject(it)
        when (o.optString("t")) {
            "i" -> MacroStep.Insert(o.getString("s"))
            "d" -> MacroStep.Delete(o.getInt("n"), o.optBoolean("f"))
            "c" -> MacroStep.Command(o.getString("c"))
            "k" -> MacroStep.Key(o.getInt("k"), o.optInt("m"))
            else -> null
        }
    }
}
