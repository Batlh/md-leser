package io.github.batlh.mdreader

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.luaj.vm2.LuaError
import org.luaj.vm2.compiler.LuaC
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * „Syntax prüfen“: findet den ersten Syntaxfehler, offline, ohne den Code auszuführen.
 * Entspricht in Notepad++ den Plugins XML Tools, JSON Viewer usw.
 */
object SyntaxCheck {

    /** Fundstelle, Zeile und Spalte ab 1. */
    data class Problem(val line: Int, val column: Int, val message: String)

    private val LANGUAGES = mapOf(
        "source.json" to "JSON",
        "source.json.comments" to "JSON mit Kommentaren",
        "text.xml" to "XML",
        "text.xml.xsl" to "XSL",
        "source.yaml" to "YAML",
        "source.lua" to "Lua",
    )

    /** Name der prüfbaren Sprache, null = für diese Sprache gibt es keine Prüfung. */
    fun languageName(scope: String?): String? = LANGUAGES[scope]

    fun check(scope: String?, text: String): Problem? = when (scope) {
        "source.json" -> json(text, comments = false)
        "source.json.comments" -> json(text, comments = true)
        "text.xml", "text.xml.xsl" -> xml(text)
        "source.yaml" -> yaml(text)
        "source.lua" -> lua(text)
        else -> throw IllegalArgumentException("Keine Prüfung für $scope")
    }

    private val AT_LINE = Regex("at line (\\d+) column (\\d+)")

    private fun json(text: String, comments: Boolean): Problem? {
        val reader = JsonReader(StringReader(text))
        reader.setStrictness(if (comments) Strictness.LENIENT else Strictness.STRICT)
        return try {
            reader.skipValue()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                Problem(1, 1, "Nach dem Ende kommt noch weiterer Inhalt")
            } else {
                null
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            val m = AT_LINE.find(msg)
            val clean = msg.substringBefore("\nSee ").replace(AT_LINE, "").substringBefore(" path ")
                .replace("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON", "Ungültiges JSON")
                .trim()
            Problem(m?.groupValues?.get(1)?.toInt() ?: 1, m?.groupValues?.get(2)?.toInt() ?: 1, clean)
        }
    }

    private fun xml(text: String): Problem? = try {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.newSAXParser().parse(InputSource(StringReader(text)), object : DefaultHandler() {
            // Keine externen DTDs nachladen: offline und sicher
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
        })
        null
    } catch (e: SAXParseException) {
        Problem(e.lineNumber.coerceAtLeast(1), e.columnNumber.coerceAtLeast(1), e.message ?: "Ungültiges XML")
    }

    private fun yaml(text: String): Problem? = try {
        Parse(LoadSettings.builder().build()).parseString(text).forEach { _ -> }
        null
    } catch (e: MarkedYamlEngineException) {
        val mark = e.problemMark.orElse(null) ?: e.contextMark.orElse(null)
        val msg = listOfNotNull(e.context, e.problem).joinToString(", ").ifEmpty { "Ungültiges YAML" }
        Problem((mark?.line ?: 0) + 1, (mark?.column ?: 0) + 1, msg)
    } catch (e: Exception) {
        Problem(1, 1, e.message ?: "Ungültiges YAML")
    }

    private val LUA_LINE = Regex("^[^:]*:(\\d+):\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

    private fun lua(text: String): Problem? = try {
        LuaC.instance.compile(ByteArrayInputStream(text.toByteArray()), "datei")
        null
    } catch (e: LuaError) {
        val msg = e.message ?: "Ungültiges Lua"
        val m = LUA_LINE.find(msg)
        Problem(m?.groupValues?.get(1)?.toInt() ?: 1, 1, (m?.groupValues?.get(2) ?: msg).trim())
    }
}
