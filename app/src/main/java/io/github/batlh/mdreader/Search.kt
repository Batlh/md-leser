package io.github.batlh.mdreader

import java.util.regex.Matcher
import java.util.regex.Pattern

/** Suchmodus wie im Suchen-Dialog von Notepad++. */
enum class SearchMode { NORMAL, EXTENDED, REGEX }

data class SearchQuery(
    val text: String,
    val mode: SearchMode = SearchMode.NORMAL,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
)

object Search {

    /** „Erweitert“: \n, \r, \t, \0, \\, \xHH und \uHHHH. Zeilenenden sind im Editor immer \n. */
    fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                out.append(c)
                i++
                continue
            }
            val n = s[i + 1]
            when (n) {
                'n' -> out.append('\n')
                'r' -> {
                    // \r\n wie ein Zeilenende behandeln, ein einzelnes \r ebenso
                    if (s.startsWith("\\n", i + 2)) i += 2
                    out.append('\n')
                }
                't' -> out.append('\t')
                '0' -> out.append('\u0000')
                '\\' -> out.append('\\')
                'x', 'u' -> {
                    val len = if (n == 'x') 2 else 4
                    val hex = s.substring(i + 2, minOf(s.length, i + 2 + len))
                    val code = if (hex.length == len) hex.toIntOrNull(16) else null
                    if (code != null) {
                        out.append(code.toChar())
                        i += len
                    } else {
                        out.append(c).append(n)
                    }
                }
                else -> out.append(c).append(n)
            }
            i += 2
        }
        return out.toString()
    }

    /** Suchbegriff als regulärer Ausdruck, für alle Modi gleich. */
    fun pattern(q: SearchQuery): Pattern {
        val core = when (q.mode) {
            SearchMode.NORMAL -> Pattern.quote(q.text)
            SearchMode.EXTENDED -> Pattern.quote(unescape(q.text))
            SearchMode.REGEX -> q.text
        }
        val full = if (q.wholeWord) "\\b(?:$core)\\b" else core
        var flags = Pattern.MULTILINE
        if (!q.matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(full, flags)
    }

    /** Ersetzungstext für Matcher. Bei Regex gehen $1 und wie in Notepad++ auch \1. */
    fun replacement(q: SearchQuery, replace: String): String = when (q.mode) {
        SearchMode.NORMAL -> Matcher.quoteReplacement(replace)
        SearchMode.EXTENDED -> Matcher.quoteReplacement(unescape(replace))
        SearchMode.REGEX -> regexReplacement(replace)
    }

    /** \1 → $1, \n und \t als Zeichen, sonst wie Java. */
    private fun regexReplacement(r: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < r.length) {
            val c = r[i]
            if (c == '\\' && i + 1 < r.length) {
                val n = r[i + 1]
                when {
                    n.isDigit() -> out.append('$').append(n)
                    n == 'n' -> out.append('\n')
                    n == 't' -> out.append('\t')
                    n == 'r' -> out.append('\n')
                    else -> out.append('\\').append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    fun count(text: CharSequence, q: SearchQuery): Int {
        if (q.text.isEmpty()) return 0
        val m = pattern(q).matcher(text)
        var n = 0
        while (m.find()) {
            n++
            if (m.end() == m.start() && m.end() >= text.length) break
        }
        return n
    }

    /** Ergebnis: neuer Text und Anzahl der Ersetzungen. */
    fun replaceAll(text: String, q: SearchQuery, replace: String): Pair<String, Int> {
        if (q.text.isEmpty()) return text to 0
        val m = pattern(q).matcher(text)
        val repl = replacement(q, replace)
        val sb = StringBuffer()
        var n = 0
        while (m.find()) {
            m.appendReplacement(sb, repl)
            n++
        }
        m.appendTail(sb)
        return sb.toString() to n
    }
}
