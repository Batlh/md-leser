package io.github.batlh.mdreader

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Wie eine Datei auf der Platte aussieht: Zeichensatz, BOM und Zeilenenden.
 * Beim Speichern wird alles genau so zurückgeschrieben, damit Dateien nicht
 * unbemerkt von CRLF auf LF oder von ANSI auf UTF-8 wechseln.
 */
data class FileFormat(
    val charset: Charset = Charsets.UTF_8,
    val bom: Boolean = false,
    val crlf: Boolean = false,
) {
    /** Anzeige wie in der Statusleiste von Notepad++. */
    val label: String
        get() {
            val enc = when {
                charset == Charsets.UTF_16LE -> "UTF-16 LE BOM"
                charset == Charsets.UTF_16BE -> "UTF-16 BE BOM"
                charset != Charsets.UTF_8 -> "ANSI"
                bom -> "UTF-8-BOM"
                else -> "UTF-8"
            }
            return enc + " · " + if (crlf) "Windows (CR LF)" else "Unix (LF)"
        }
}

object TextFile {
    /** „ANSI“ wie unter Windows (Westeuropa). */
    val ANSI: Charset = Charset.forName("windows-1252")

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    private fun startsWith(bytes: ByteArray, prefix: ByteArray) =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    private fun lineEndings(text: String, charset: java.nio.charset.Charset, bom: Boolean): Pair<String, FileFormat> {
        val crlf = text.contains("\r\n")
        val normalized = if (crlf) text.replace("\r\n", "\n") else text
        return normalized to FileFormat(charset, bom, crlf)
    }

    /** Text im Editor hat immer nur \n als Zeilenende. */
    fun decode(bytes: ByteArray): Pair<String, FileFormat> {
        // UTF-16 erkennt man nur am BOM (wie Notepad++ ohne Heuristik)
        if (startsWith(bytes, BOM_LE)) return lineEndings(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), Charsets.UTF_16LE, true)
        if (startsWith(bytes, BOM_BE)) return lineEndings(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), Charsets.UTF_16BE, true)
        val bom = bytes.size >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]
        val data = if (bom) bytes.copyOfRange(3, bytes.size) else bytes

        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
        // Kein gültiges UTF-8: als ANSI lesen statt Zeichen zu zerstören
        val charset = if (utf8 != null) Charsets.UTF_8 else ANSI
        val text = utf8 ?: String(data, ANSI)

        val crlf = text.contains("\r\n")
        val normalized = if (crlf) text.replace("\r\n", "\n") else text
        return normalized to FileFormat(charset, bom && charset == Charsets.UTF_8, crlf)
    }

    fun encode(text: String, format: FileFormat): ByteArray {
        val body = (if (format.crlf) text.replace("\n", "\r\n") else text).toByteArray(format.charset)
        if (!format.bom) return body
        return when (format.charset) {
            Charsets.UTF_8 -> BOM + body
            Charsets.UTF_16LE -> BOM_LE + body
            Charsets.UTF_16BE -> BOM_BE + body
            else -> body
        }
    }
}
