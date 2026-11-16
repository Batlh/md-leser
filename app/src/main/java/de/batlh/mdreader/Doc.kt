package de.batlh.mdreader

import android.net.Uri
import io.github.rosemoe.sora.text.Content
import java.util.UUID

/** Eine geöffnete Datei (ein Tab). Der Text liegt im [content] des Editors, samt Cursor und Rückgängig. */
class Doc(
    var uri: Uri?,
    var name: String,
    var format: FileFormat,
    text: String,
    dirty: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
) {
    val content = Content(text)

    private var savedVersion: Long = if (dirty) -1 else content.documentVersion
    private var savedHash: Int? = if (dirty) null else text.hashCode()
    private var draftVersion = -1L

    /** Sprache für die Syntax-Hervorhebung (TextMate-Scope), null = normaler Text. */
    var scope: String? = Languages.detect(name)?.scope

    /** Kodierung oder Zeilenende umgestellt, aber noch nicht gespeichert. */
    var formatChanged = false

    /** Markdown gerendert anzeigen statt bearbeiten. */
    var viewMode = false
    var viewScroll = 0

    val isDirty get() = formatChanged || content.documentVersion != savedVersion

    val extension get() = name.substringAfterLast('.', "").lowercase()
    val isMarkdown get() = extension in MARKDOWN

    fun markSaved(version: Long, hash: Int) {
        savedVersion = version
        savedHash = hash
        formatChanged = false
    }

    /** Nach Rückgängig/Wiederholen: wieder genau der gespeicherte Stand? */
    fun checkBackToSaved() {
        val hash = savedHash ?: return
        if (content.toString().hashCode() == hash) savedVersion = content.documentVersion
    }

    fun needsDraftWrite() = draftVersion != content.documentVersion
    fun draftWritten() {
        draftVersion = content.documentVersion
    }

    companion object {
        val MARKDOWN = setOf("md", "markdown", "mdown", "mkd")
    }
}
