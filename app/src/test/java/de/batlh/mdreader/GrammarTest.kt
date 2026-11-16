package de.batlh.mdreader

import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.registry.Registry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lädt jede mitgelieferte Grammatik und zerlegt eine Beispielzeile, damit kaputte Grammatiken im Build auffallen. */
class GrammarTest {

    private val assets = File("src/main/assets")

    @Test
    fun alleGrammatikenLadenUndZerlegen() {
        val list = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        val paths = (0 until list.length()).associate {
            val o = list.getJSONObject(it)
            o.getString("scopeName") to o.getString("grammar")
        }
        val registry = Registry(object : IRegistryOptions {
            override fun getGrammarSource(scopeName: String): IGrammarSource? =
                paths[scopeName]?.let { IGrammarSource.fromFile(File(assets, it)) }
        })
        val failures = ArrayList<String>()
        for (scope in paths.keys) {
            try {
                val grammar = registry.loadGrammar(scope) ?: throw IllegalStateException("nicht geladen")
                grammar.tokenizeLine("if (a == \"b\") { return 1; } // Kommentar <tag> # x")
            } catch (e: Throwable) {
                failures += "$scope: $e"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun jedeSpracheHatEineGrammatik() {
        val list = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        val scopes = (0 until list.length()).map { list.getJSONObject(it).getString("scopeName") }.toSet()
        val types = JSONObject(File(assets, "textmate/types.json").readText())
        val missing = types.keys().asSequence().filter { it !in scopes }.toList()
        assertTrue("Ohne Grammatik: $missing", missing.isEmpty())
    }
}
