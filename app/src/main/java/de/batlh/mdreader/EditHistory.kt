package de.batlh.mdreader

import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/**
 * Rückgängig/Wiederholen für ein EditText.
 *
 * Merkt sich jede Änderung als (Position, entfernter Text, eingefügter Text). Schnell
 * hintereinander getippte Zeichen werden zu einem Schritt zusammengefasst, damit
 * Rückgängig nicht Buchstabe für Buchstabe arbeitet. Auch die Wortvorschläge der
 * Tastatur, die das gerade getippte Wort ersetzen, landen im selben Schritt.
 */
class EditHistory(
    private val editText: EditText,
    private val onStateChanged: () -> Unit,
) : TextWatcher {

    private class Edit(var start: Int, var removed: String, var inserted: String, var time: Long)

    private val edits = ArrayList<Edit>()
    private var pos = 0
    private var savedPos = 0
    private var mergeTarget: Edit? = null

    private var pendingStart = 0
    private var pendingRemoved = ""

    /** Solange true, werden Änderungen nicht aufgezeichnet (Text per Code setzen). */
    var suppressed = false

    val canUndo get() = pos > 0
    val canRedo get() = pos < edits.size
    val isDirty get() = pos != savedPos

    /** Neuer Ausgangszustand, z. B. nach dem Laden einer Datei. */
    fun reset(dirty: Boolean = false) {
        edits.clear()
        pos = 0
        savedPos = if (dirty) -1 else 0
        mergeTarget = null
        onStateChanged()
    }

    /** Stand zum Zeitpunkt des Speicherns. Spätere Eingaben werden nicht mehr hineingemischt. */
    fun checkpoint(): Int {
        mergeTarget = null
        return pos
    }

    fun markSaved(checkpoint: Int) {
        savedPos = checkpoint
        onStateChanged()
    }

    fun undo() {
        if (!canUndo) return
        val e = edits[--pos]
        apply(e.start, e.inserted.length, e.removed)
    }

    fun redo() {
        if (!canRedo) return
        val e = edits[pos++]
        apply(e.start, e.removed.length, e.inserted)
    }

    private fun apply(start: Int, length: Int, text: String) {
        mergeTarget = null
        suppressed = true
        try {
            editText.text.replace(start, start + length, text)
        } finally {
            suppressed = false
        }
        editText.setSelection((start + text.length).coerceAtMost(editText.length()))
        onStateChanged()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        if (suppressed) return
        pendingStart = start
        pendingRemoved = s.subSequence(start, start + count).toString()
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (suppressed) return
        record(pendingStart, pendingRemoved, s.subSequence(start, start + count).toString())
    }

    override fun afterTextChanged(s: Editable) = Unit

    private fun record(start: Int, removed: String, inserted: String) {
        if (removed == inserted) return
        val wasDirty = isDirty
        val couldUndo = canUndo
        val couldRedo = canRedo
        val now = SystemClock.uptimeMillis()

        // Neue Eingabe nach Rückgängig: die wiederholbaren Schritte fallen weg
        if (pos < edits.size) {
            if (savedPos > pos) savedPos = -1
            edits.subList(pos, edits.size).clear()
        }

        val last = mergeTarget
        if (last != null && last === edits.lastOrNull() && now - last.time < MERGE_MS &&
            merge(last, start, removed, inserted)
        ) {
            last.time = now
        } else {
            val e = Edit(start, removed, inserted, now)
            edits.add(e)
            pos++
            mergeTarget = e
            if (edits.size > MAX_STEPS) {
                edits.removeAt(0)
                pos--
                savedPos = if (savedPos > 0) savedPos - 1 else -1
            }
        }

        if (wasDirty != isDirty || couldUndo != canUndo || couldRedo != canRedo) onStateChanged()
    }

    private fun merge(last: Edit, start: Int, removed: String, inserted: String): Boolean {
        val regionStart = last.start
        val regionEnd = last.start + last.inserted.length

        // Änderung innerhalb des gerade Getippten (weitertippen, korrigieren, Wortvorschlag)
        if (start >= regionStart && start + removed.length <= regionEnd && '\n' !in inserted) {
            val off = start - regionStart
            last.inserted = last.inserted.substring(0, off) + inserted +
                last.inserted.substring(off + removed.length)
            return true
        }
        if (inserted.isEmpty() && last.inserted.isEmpty()) {
            // Rücktaste mehrmals hintereinander
            if (start + removed.length == last.start) {
                last.removed = removed + last.removed
                last.start = start
                return true
            }
            // Entf mehrmals hintereinander
            if (start == last.start) {
                last.removed = last.removed + removed
                return true
            }
        }
        return false
    }

    private companion object {
        const val MERGE_MS = 1500L
        const val MAX_STEPS = 500
    }
}
