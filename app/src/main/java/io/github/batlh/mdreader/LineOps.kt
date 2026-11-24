package io.github.batlh.mdreader

import java.util.Locale

/** Zeilen- und Textbefehle wie in den Menüs „Bearbeiten“ von Notepad++. Reine Textfunktionen. */
object LineOps {

    enum class Sort { ASC, DESC, ASC_IGNORE_CASE, NUMERIC, LENGTH }

    fun sort(lines: List<String>, how: Sort): List<String> = when (how) {
        Sort.ASC -> lines.sorted()
        Sort.DESC -> lines.sortedDescending()
        Sort.ASC_IGNORE_CASE -> lines.sortedWith(String.CASE_INSENSITIVE_ORDER)
        Sort.NUMERIC -> lines.sortedWith(compareBy<String> { leadingNumber(it) ?: Double.MAX_VALUE }.thenBy { it })
        Sort.LENGTH -> lines.sortedBy { it.length }
    }

    fun reverse(lines: List<String>) = lines.reversed()
    fun shuffle(lines: List<String>) = lines.shuffled()

    /** Nur direkt aufeinanderfolgende gleiche Zeilen entfernen. */
    fun removeConsecutiveDuplicates(lines: List<String>): List<String> =
        lines.filterIndexed { i, l -> i == 0 || l != lines[i - 1] }

    fun trimBoth(lines: List<String>) = lines.map { it.trim() }

    /** „Zeilenende zu Leerzeichen“: alles in eine Zeile, Zeilen durch ein Leerzeichen getrennt. */
    fun eolToSpace(lines: List<String>): String = lines.joinToString(" ")

    fun indent(lines: List<String>, unit: String): List<String> = lines.map { if (it.isEmpty()) it else unit + it }

    /** Eine Einrückungsstufe entfernen: ein Tab oder bis zu [width] Leerzeichen. */
    fun outdent(lines: List<String>, width: Int): List<String> = lines.map { line ->
        when {
            line.startsWith("\t") -> line.substring(1)
            else -> line.substring(line.takeWhile { it == ' ' }.length.coerceAtMost(width))
        }
    }

    private fun leadingNumber(s: String): Double? =
        Regex("^\\s*-?\\d+([.,]\\d+)?").find(s)?.value?.trim()?.replace(',', '.')?.toDoubleOrNull()

    fun removeDuplicates(lines: List<String>): List<String> = LinkedHashSet(lines).toList()

    fun removeEmpty(lines: List<String>): List<String> = lines.filter { it.isNotBlank() }

    fun trimTrailing(lines: List<String>) = lines.map { it.trimEnd() }
    fun trimLeading(lines: List<String>) = lines.map { it.trimStart() }

    /** Zeilen verbinden: Zeilenumbrüche durch ein Leerzeichen ersetzen. */
    fun join(lines: List<String>): String =
        lines.mapIndexed { i, l -> if (i == 0) l.trimEnd() else l.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    fun tabsToSpaces(lines: List<String>, width: Int): List<String> = lines.map { line ->
        val sb = StringBuilder()
        for (c in line) {
            if (c == '\t') {
                val n = width - sb.length % width
                repeat(n) { sb.append(' ') }
            } else {
                sb.append(c)
            }
        }
        sb.toString()
    }

    /** Nur führende Leerzeichen in Tabs umwandeln. */
    fun leadingSpacesToTabs(lines: List<String>, width: Int): List<String> = lines.map { line ->
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val cols = tabsToSpaces(listOf(indent), width)[0].length
        "\t".repeat(cols / width) + " ".repeat(cols % width) + line.substring(indent.length)
    }

    enum class Case { UPPER, LOWER, PROPER, SENTENCE, INVERT }

    fun changeCase(text: String, how: Case): String = when (how) {
        Case.UPPER -> text.uppercase(Locale.GERMAN)
        Case.LOWER -> text.lowercase(Locale.GERMAN)
        Case.PROPER -> Regex("\\p{L}+").replace(text.lowercase(Locale.GERMAN)) { m ->
            m.value.replaceFirstChar { it.titlecase(Locale.GERMAN) }
        }
        Case.SENTENCE -> {
            val sb = StringBuilder(text.lowercase(Locale.GERMAN))
            var start = true
            for (i in sb.indices) {
                val c = sb[i]
                if (start && c.isLetter()) {
                    sb.setCharAt(i, c.uppercaseChar())
                    start = false
                } else if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    start = true
                }
            }
            sb.toString()
        }
        Case.INVERT -> String(text.map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.toCharArray())
    }

    /**
     * Zeilenkommentar umschalten wie Strg+Q in Notepad++: Sind alle nicht leeren Zeilen
     * kommentiert, wird der Kommentar entfernt, sonst wird er auf gleicher Einrückung eingefügt.
     */
    fun toggleLineComment(lines: List<String>, marker: String): List<String> {
        val content = lines.filter { it.isNotBlank() }
        if (content.isEmpty()) return lines
        val allCommented = content.all { it.trimStart().startsWith(marker) }
        if (allCommented) {
            return lines.map { line ->
                val i = line.indexOf(marker)
                if (line.isBlank() || i < 0) line
                else {
                    var end = i + marker.length
                    if (end < line.length && line[end] == ' ') end++
                    line.substring(0, i) + line.substring(end)
                }
            }
        }
        val indent = content.minOf { it.length - it.trimStart().length }
        return lines.map { line ->
            if (line.isBlank()) line else line.substring(0, indent) + marker + " " + line.substring(indent)
        }
    }

    /** Blockkommentar um den Text legen oder entfernen (HTML, CSS, XML …). */
    fun toggleBlockComment(text: String, start: String, end: String): String {
        val t = text.trim()
        if (t.startsWith(start) && t.endsWith(end) && t.length >= start.length + end.length) {
            val lead = text.substring(0, text.indexOf(start))
            val trail = text.substring(text.lastIndexOf(end) + end.length)
            val inner = text.substring(text.indexOf(start) + start.length, text.lastIndexOf(end))
            return lead + inner.removePrefix(" ").removeSuffix(" ") + trail
        }
        val lead = text.takeWhile { it == ' ' || it == '\t' }
        return lead + start + " " + text.substring(lead.length) + " " + end
    }
}
