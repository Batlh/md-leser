package de.batlh.mdreader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.regex.PatternSyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var content: View
    private lateinit var scroll: NestedScrollView
    private lateinit var textView: TextView
    private lateinit var editor: CodeEditor
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var markwon: Markwon

    private lateinit var searchPanel: View
    private lateinit var replaceRow: View
    private lateinit var searchQuery: EditText
    private lateinit var replaceText: EditText
    private lateinit var searchCount: TextView
    private lateinit var optCase: CheckBox
    private lateinit var optWord: CheckBox
    private lateinit var optAllTabs: CheckBox
    private lateinit var optMode: RadioGroup
    private val searchRunnable = Runnable { runSearch() }

    // Makros
    private var recording = false
    private val recorded = ArrayList<MacroStep>()
    private var lastMacro: List<MacroStep> = emptyList()
    private var replaying = false
    private var inCommand = false
    private var forwardDelete = false

    private val prefs by lazy { getSharedPreferences("einstellungen", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionDir by lazy { File(filesDir, "sitzung").apply { mkdirs() } }

    private val docs = ArrayList<Doc>()
    private var current: Doc? = null
    private var suppressTabEvents = false
    private var showSource = false
    private var saving = false
    private var loading = 0
    private var menuState = ""

    private var saveAsDoc: Doc? = null
    private var afterSaveAs: ((Boolean) -> Unit)? = null

    private val openDocuments =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach {
                takePermission(it)
                openUri(it)
            }
        }

    // octet-stream, damit der Dateiname genau so bleibt (sonst wird z. B. „.txt“ angehängt)
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val doc = saveAsDoc
            val then = afterSaveAs
            saveAsDoc = null
            afterSaveAs = null
            if (uri == null || doc == null || doc !in docs) {
                then?.invoke(false)
                return@registerForActivityResult
            }
            takePermission(uri)
            doc.uri = uri
            doc.name = displayName(uri)
            Languages.detect(doc.name)?.let {
                doc.scope = it.scope
                if (doc === current) editor.setEditorLanguage(language(it.scope))
            }
            bindTab(doc)
            writeDocument(doc, uri, then)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        tabs = findViewById(R.id.tabs)
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        textView = findViewById(R.id.text)
        editor = findViewById(R.id.editor)
        emptyView = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        setSupportActionBar(toolbar)

        // Inhalt nicht unter Statusleiste, Navigationsleiste und Tastatur legen
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight()
            isAppearanceLightNavigationBars = !isNight()
        }

        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()

        val size = prefs.getFloat(KEY_SIZE, 16f)
        textView.textSize = size
        textView.setTextIsSelectable(true)
        textView.movementMethod = LinkMovementMethod.getInstance()
        setupEditor(size)
        setupSearch()

        findViewById<ImageButton>(R.id.tab_new).setOnClickListener { newDocument() }
        // Tipp auf Dateiname/Statuszeile oben: Sprache wählen (wie die Statusleiste in Notepad++)
        toolbar.setOnClickListener { current?.let { if (!it.viewMode) chooseLanguage(it) } }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!suppressTabEvents) docs.getOrNull(tab.position)?.let { switchTo(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        restoreSession {
            val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
            if (uri != null) openUri(uri)
        }
    }

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun setupEditor(size: Float) {
        Languages.init(this)
        applyTheme()
        try {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        } catch (_: Exception) {
        }
        editor.typefaceText = Typeface.MONOSPACE
        editor.setTextSize(size)
        applyViewPrefs()
        editor.subscribeAlways(ContentChangeEvent::class.java) { e ->
            if (e.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return@subscribeAlways
            val doc = current ?: return@subscribeAlways
            if (e.isCausedByUndoManager) doc.checkBackToSaved()
            if (recording && !replaying && !inCommand && !e.isCausedByUndoManager) recordChange(e)
            if (doc.bookmarks.isNotEmpty()) shiftBookmarks(doc, e)
            refreshState(doc)
        }
    }

    /** Zeilenumbruch, Steuerzeichen und Zeilennummern aus den Einstellungen. */
    private fun applyViewPrefs() {
        editor.tabWidth = prefs.getInt(KEY_TAB_WIDTH, 4)
        editor.setHighlightCurrentLine(prefs.getBoolean(KEY_CURRENT_LINE, true))
        editor.props.autoIndent = prefs.getBoolean(KEY_AUTO_INDENT, true)
        editor.props.symbolPairAutoCompletion = prefs.getBoolean(KEY_BRACKETS, true)
        editor.setScalable(prefs.getBoolean(KEY_ZOOM, true))
        editor.setWordwrap(prefs.getBoolean(KEY_WRAP, true))
        editor.setLineNumberEnabled(prefs.getBoolean(KEY_LINE_NUMBERS, true))
        editor.setNonPrintablePaintingFlags(
            if (prefs.getBoolean(KEY_ALL_CHARS, false)) {
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
                    CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR
            } else 0
        )
    }

    private fun togglePref(key: String, default: Boolean) {
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, default)).apply()
        applyViewPrefs()
        current?.let { if (key == KEY_SPACES || key == KEY_COMPLETION) editor.setEditorLanguage(language(it.scope)) }
        invalidateOptionsMenu()
    }

    private fun language(scope: String?) =
        Languages.create(
            scope,
            prefs.getBoolean(KEY_SPACES, false),
            prefs.getInt(KEY_TAB_WIDTH, 4),
            prefs.getBoolean(KEY_COMPLETION, false),
        )

    private fun applyTheme() {
        val chosen = prefs.getString(KEY_THEME, "auto") ?: "auto"
        Languages.setTheme(if (chosen == "auto") (if (isNight()) "darcula" else "quietlight") else chosen)
    }

    private fun chooseTheme() {
        val names = arrayOf<CharSequence>(getString(R.string.st_auto), *Languages.THEMES.map { it.second }.toTypedArray())
        val keys = listOf("auto") + Languages.THEMES.map { it.first }
        val checked = keys.indexOf(prefs.getString(KEY_THEME, "auto")).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.st_farben)
            .setSingleChoiceItems(names, checked) { d, which ->
                prefs.edit().putString(KEY_THEME, keys[which]).apply()
                applyTheme()
                d.dismiss()
            }
            .show()
    }

    private fun chooseTabWidth() {
        val widths = listOf(2, 4, 8)
        val names = widths.map { it.toString() as CharSequence }.toTypedArray()
        val checked = widths.indexOf(prefs.getInt(KEY_TAB_WIDTH, 4)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.st_tabbreite)
            .setSingleChoiceItems(names, checked) { d, which ->
                prefs.edit().putInt(KEY_TAB_WIDTH, widths[which]).apply()
                applyViewPrefs()
                current?.let { editor.setEditorLanguage(language(it.scope)) }
                d.dismiss()
            }
            .show()
    }

    private fun chooseLanguage(doc: Doc) {
        val langs = Languages.all
        val names = arrayOf<CharSequence>(getString(R.string.normaler_text), *langs.map { it.name }.toTypedArray())
        val checked = langs.indexOfFirst { it.scope == doc.scope } + 1
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sprache)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                doc.scope = if (which == 0) null else langs[which - 1].scope
                if (doc === current) editor.setEditorLanguage(language(doc.scope))
                updateTitle()
                dialog.dismiss()
            }
            .show()
    }

    /** Kodierung oder Zeilenende umstellen (wie „Konvertieren nach“ in Notepad++). */
    private fun changeFormat(doc: Doc, format: FileFormat) {
        if (format == doc.format) return
        if (format.charset != Charsets.UTF_8 && !format.charset.newEncoder().canEncode(doc.content.toString())) {
            Toast.makeText(this, R.string.ansi_warnung, Toast.LENGTH_LONG).show()
        }
        doc.format = format
        doc.formatChanged = true
        refreshState(doc)
    }

    // ---------- Bearbeiten, Zeilen, Lesezeichen ----------

    /** Befehl im Editor ausführen (aus der Leseansicht vorher umschalten). */
    private fun edit(block: () -> Unit): Boolean {
        val doc = current ?: return false
        if (doc.viewMode) setViewMode(false)
        try {
            block()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /** Zeilen der Auswahl bzw. die Cursorzeile. Endet die Auswahl am Zeilenanfang, zählt die Zeile nicht mit. */
    private fun lineRange(): IntRange {
        val c = editor.cursor
        var last = c.rightLine
        if (c.isSelected && c.rightColumn == 0 && last > c.leftLine) last--
        return c.leftLine..last
    }

    private fun linesOf(range: IntRange): List<String> = range.map { editor.text.getLineString(it) }

    private fun replaceLines(range: IntRange, lines: List<String>, selectFirst: Int = range.first, select: Boolean = true) {
        val text = editor.text
        text.beginBatchEdit()
        try {
            text.replace(range.first, 0, range.last, text.getColumnCount(range.last), lines.joinToString("\n"))
        } finally {
            text.endBatchEdit()
        }
        if (select && lines.isNotEmpty()) {
            val count = lines.size - (selectFirst - range.first).coerceAtLeast(0)
            val endLine = (selectFirst + count - 1).coerceAtMost(text.lineCount - 1)
            editor.setSelectionRegion(selectFirst, 0, endLine, text.getColumnCount(endLine))
        }
    }

    /** Sortieren, Bereinigen usw.: auf die markierten Zeilen, ohne Auswahl auf die ganze Datei. */
    private fun bulk(op: (List<String>) -> List<String>) {
        val range = if (editor.cursor.isSelected) lineRange() else 0 until editor.lineCount
        val result = op(linesOf(range))
        replaceLines(range, result.ifEmpty { listOf("") }, select = editor.cursor.isSelected)
    }

    private fun duplicate() {
        val text = editor.text
        val c = editor.cursor
        if (c.isSelected && c.leftLine == c.rightLine) {
            text.insert(c.rightLine, c.rightColumn, text.subSequence(c.left, c.right))
            return
        }
        val r = lineRange()
        text.insert(r.last, text.getColumnCount(r.last), "\n" + linesOf(r).joinToString("\n"))
    }

    private fun deleteLines() {
        val text = editor.text
        val r = lineRange()
        when {
            r.last + 1 < text.lineCount -> text.delete(r.first, 0, r.last + 1, 0)
            r.first > 0 -> text.delete(r.first - 1, text.getColumnCount(r.first - 1), r.last, text.getColumnCount(r.last))
            else -> text.delete(0, 0, r.last, text.getColumnCount(r.last))
        }
    }

    private fun moveLines(up: Boolean) {
        val r = lineRange()
        val block = linesOf(r)
        if (up) {
            if (r.first == 0) return
            val prev = editor.text.getLineString(r.first - 1)
            replaceLines(r.first - 1..r.last, block + prev, selectFirst = r.first - 1, select = false)
            editor.setSelectionRegion(r.first - 1, 0, r.last - 1, editor.text.getColumnCount(r.last - 1))
        } else {
            if (r.last >= editor.lineCount - 1) return
            val next = editor.text.getLineString(r.last + 1)
            replaceLines(r.first..r.last + 1, listOf(next) + block, select = false)
            editor.setSelectionRegion(r.first + 1, 0, r.last + 1, editor.text.getColumnCount(r.last + 1))
        }
    }

    private fun joinLines() {
        var r = lineRange()
        if (r.first == r.last) {
            if (r.last + 1 >= editor.lineCount) return
            r = r.first..r.last + 1
        }
        replaceLines(r, listOf(LineOps.join(linesOf(r))))
    }

    private fun toggleComment() {
        val doc = current ?: return
        val comments = Languages.comments(this, doc.scope)
        val r = lineRange()
        val line = comments?.line
        val block = comments?.block
        when {
            line != null -> replaceLines(r, LineOps.toggleLineComment(linesOf(r), line))
            block != null -> {
                val c = editor.cursor
                if (c.isSelected && c.leftLine == c.rightLine) {
                    val sel = editor.text.subSequence(c.left, c.right).toString()
                    editor.text.replace(c.left, c.right, LineOps.toggleBlockComment(sel, block.first, block.second))
                } else {
                    val joined = linesOf(r).joinToString("\n")
                    replaceLines(r, LineOps.toggleBlockComment(joined, block.first, block.second).split("\n"))
                }
            }
            else -> Toast.makeText(this, R.string.kein_kommentar, Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeCase(how: LineOps.Case) {
        val text = editor.text
        val c = editor.cursor
        if (c.isSelected) {
            val start = c.left
            val startLine = c.leftLine
            val startCol = c.leftColumn
            val changed = LineOps.changeCase(text.subSequence(c.left, c.right).toString(), how)
            text.replace(start, c.right, changed)
            val end = text.indexer.getCharPosition(start + changed.length)
            editor.setSelectionRegion(startLine, startCol, end.line, end.column)
        } else {
            val r = lineRange()
            replaceLines(r, linesOf(r).map { LineOps.changeCase(it, how) }, select = false)
        }
    }

    private fun indentLines(more: Boolean) {
        val r = lineRange()
        val unit = if (prefs.getBoolean(KEY_SPACES, false)) " ".repeat(editor.tabWidth) else "\t"
        val lines = linesOf(r)
        replaceLines(r, if (more) LineOps.indent(lines, unit) else LineOps.outdent(lines, editor.tabWidth))
    }

    private fun insertBlankLine(above: Boolean) {
        val line = editor.cursor.leftLine
        if (above) {
            editor.text.insert(line, 0, "\n")
            editor.setSelection(line, 0)
        } else {
            editor.text.insert(line, editor.text.getColumnCount(line), "\n")
            editor.setSelection(line + 1, 0)
        }
    }

    private fun insertDate(style: Int) {
        val text = DateFormat.getDateTimeInstance(style, if (style == DateFormat.SHORT) DateFormat.SHORT else DateFormat.MEDIUM)
            .format(Date())
        editor.commitText(text, false, false)
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
        Toast.makeText(this, R.string.kopiert, Toast.LENGTH_SHORT).show()
    }

    private fun setReadOnly(doc: Doc, readOnly: Boolean) {
        doc.readOnly = readOnly
        if (doc === current) editor.isEditable = !readOnly
        updateTitle()
        invalidateOptionsMenu()
    }

    /** Wie „Zusammenfassung“ in Notepad++. */
    private fun showSummary(doc: Doc) {
        val text = doc.content.toString()
        val words = Regex("\\S+").findAll(text).count()
        val chars = text.count { it != '\n' }
        val bytes = TextFile.encode(text, doc.format).size
        val c = editor.cursor
        val selected = if (doc === current && c.isSelected) c.right - c.left else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(doc.name)
            .setMessage(getString(R.string.info_text, chars, words, doc.content.lineCount, bytes, selected))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toggleBookmark() {
        val doc = current ?: return
        val line = editor.cursor.leftLine
        val msg = if (doc.bookmarks.remove(line)) {
            R.string.lz_entfernt
        } else {
            doc.bookmarks += line
            R.string.lz_gesetzt
        }
        Toast.makeText(this, getString(msg, line + 1), Toast.LENGTH_SHORT).show()
    }

    private fun jumpBookmark(next: Boolean) {
        val doc = current ?: return
        if (doc.bookmarks.isEmpty()) {
            Toast.makeText(this, R.string.lz_keine, Toast.LENGTH_SHORT).show()
            return
        }
        val line = editor.cursor.leftLine
        val target = if (next) doc.bookmarks.higher(line) ?: doc.bookmarks.first()
        else doc.bookmarks.lower(line) ?: doc.bookmarks.last()
        editor.setSelection(target.coerceAtMost(editor.lineCount - 1), 0)
    }

    private fun listBookmarks() {
        val doc = current ?: return
        val lines = doc.bookmarks.filter { it < editor.lineCount }
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.lz_keine, Toast.LENGTH_SHORT).show()
            return
        }
        val items = lines.map { "${it + 1}: ${editor.text.getLineString(it).trim().take(60)}" as CharSequence }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lesezeichen_menue)
            .setItems(items.toTypedArray()) { _, which -> editor.setSelection(lines[which], 0) }
            .setNeutralButton(R.string.lz_loeschen) { _, _ -> doc.bookmarks.clear() }
            .show()
    }

    /** Lesezeichen mitwandern lassen, wenn darüber Zeilen eingefügt oder gelöscht werden. */
    private fun shiftBookmarks(doc: Doc, e: ContentChangeEvent) {
        val start = e.changeStart
        val end = e.changeEnd
        val delta = end.line - start.line
        if (delta == 0) return
        val moved = doc.bookmarks.map { b ->
            when (e.action) {
                ContentChangeEvent.ACTION_INSERT ->
                    if (b > start.line || (b == start.line && start.column == 0)) b + delta else b
                ContentChangeEvent.ACTION_DELETE -> when {
                    b > end.line -> b - delta
                    b > start.line -> start.line
                    else -> b
                }
                else -> b
            }
        }
        doc.bookmarks.clear()
        doc.bookmarks.addAll(moved)
    }

    // ---------- Befehle und Makros ----------

    /** Alle Menübefehle, die ein Makro aufzeichnen und abspielen kann. */
    private val commands: Map<String, () -> Unit> by lazy {
        val map = linkedMapOf<String, () -> Unit>(
            "dup" to { duplicate() },
            "del" to { deleteLines() },
            "up" to { moveLines(true) },
            "down" to { moveLines(false) },
            "join" to { joinLines() },
            "comment" to { toggleComment() },
            "dedupe" to { bulk { LineOps.removeDuplicates(it) } },
            "empty" to { bulk { LineOps.removeEmpty(it) } },
            "trimEnd" to { bulk { LineOps.trimTrailing(it) } },
            "trimStart" to { bulk { LineOps.trimLeading(it) } },
            "tabs2spaces" to { bulk { LineOps.tabsToSpaces(it, editor.tabWidth) } },
            "spaces2tabs" to { bulk { LineOps.leadingSpacesToTabs(it, editor.tabWidth) } },
            "bm:toggle" to { toggleBookmark() },
            "bm:next" to { jumpBookmark(true) },
            "bm:prev" to { jumpBookmark(false) },
            "reverse" to { bulk { LineOps.reverse(it) } },
            "shuffle" to { bulk { LineOps.shuffle(it) } },
            "dedupeConsecutive" to { bulk { LineOps.removeConsecutiveDuplicates(it) } },
            "trimBoth" to { bulk { LineOps.trimBoth(it) } },
            "eol2space" to { bulk { listOf(LineOps.eolToSpace(it)) } },
            "indent" to { indentLines(true) },
            "outdent" to { indentLines(false) },
            "blankAbove" to { insertBlankLine(true) },
            "blankBelow" to { insertBlankLine(false) },
            "date:short" to { insertDate(DateFormat.SHORT) },
            "date:long" to { insertDate(DateFormat.LONG) },
        )
        LineOps.Case.values().forEach { c -> map["case:${c.name}"] = { changeCase(c) } }
        LineOps.Sort.values().forEach { o -> map["sort:${o.name}"] = { bulk { LineOps.sort(it, o) } } }
        map
    }

    private fun cmd(id: String): Boolean = edit {
        if (current?.readOnly == true && !id.startsWith("bm:")) {
            Toast.makeText(this, R.string.schreibgeschuetzt_kurz, Toast.LENGTH_SHORT).show()
            return@edit
        }
        if (recording && !replaying) recorded += MacroStep.Command(id)
        inCommand = true
        try {
            commands[id]?.invoke()
        } finally {
            inCommand = false
        }
    }

    private fun recordChange(e: ContentChangeEvent) {
        val text = e.changedText.toString()
        when (e.action) {
            ContentChangeEvent.ACTION_INSERT -> recorded += MacroStep.Insert(text)
            ContentChangeEvent.ACTION_DELETE -> recorded += MacroStep.Delete(text.length, forwardDelete)
        }
    }

    private fun toggleRecording() {
        if (!recording) {
            recorded.clear()
            recording = true
        } else {
            recording = false
            lastMacro = Macros.compact(recorded)
            Toast.makeText(this, getString(R.string.mk_aufgenommen, lastMacro.size), Toast.LENGTH_SHORT).show()
        }
        updateTitle()
        invalidateOptionsMenu()
    }

    /** Makro abspielen: [times] mal oder bis zum Dateiende. Alles zusammen ist ein Rückgängig-Schritt. */
    private fun play(steps: List<MacroStep>, times: Int, untilEnd: Boolean = false) {
        if (steps.isEmpty() || recording) {
            if (steps.isEmpty()) Toast.makeText(this, R.string.mk_leer, Toast.LENGTH_SHORT).show()
            return
        }
        val text = editor.text
        replaying = true
        text.beginBatchEdit()
        try {
            var runs = 0
            while (true) {
                val before = editor.cursor.left
                val lines = editor.lineCount
                steps.forEach { applyStep(it) }
                runs++
                if (!untilEnd) {
                    if (runs >= times) break
                } else if (editor.cursor.leftLine >= editor.lineCount - 1 ||
                    (editor.cursor.left == before && editor.lineCount == lines) || runs >= 100_000
                ) {
                    break
                }
            }
        } finally {
            text.endBatchEdit()
            replaying = false
        }
    }

    private fun applyStep(s: MacroStep) {
        when (s) {
            is MacroStep.Insert -> editor.commitText(s.text, false, false)
            is MacroStep.Delete -> if (s.forward) {
                val start = editor.cursor.left
                val end = minOf(start + s.count, editor.text.length)
                if (end > start) editor.text.delete(start, end)
            } else {
                repeat(s.count) { editor.deleteText() }
            }
            is MacroStep.Command -> {
                inCommand = true
                try {
                    commands[s.id]?.invoke()
                } finally {
                    inCommand = false
                }
            }
            is MacroStep.Key -> {
                val now = SystemClock.uptimeMillis()
                editor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, s.code, 0, s.meta))
                editor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, s.code, 0, s.meta))
            }
        }
    }

    private fun numberInput(initial: String): Pair<EditText, View> {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initial)
            selectAll()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        return input to box
    }

    private fun askRepeat() {
        if (lastMacro.isEmpty()) {
            Toast.makeText(this, R.string.mk_leer, Toast.LENGTH_SHORT).show()
            return
        }
        val (input, box) = numberInput("10")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mk_wie_oft)
            .setView(box)
            .setPositiveButton(R.string.mk_ausfuehren) { _, _ ->
                input.text.toString().toIntOrNull()?.takeIf { it > 0 }?.let { play(lastMacro, minOf(it, 100_000)) }
            }
            .setNeutralButton(R.string.mk_bis_ende) { _, _ -> play(lastMacro, 1, untilEnd = true) }
            .setNegativeButton(R.string.abbrechen, null)
            .show()
    }

    private fun savedMacros(): JSONObject = try {
        JSONObject(prefs.getString(KEY_MACROS, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun saveMacro() {
        if (lastMacro.isEmpty()) return
        val input = EditText(this).apply { hint = getString(R.string.mk_name) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mk_speichern)
            .setView(box)
            .setPositiveButton(R.string.speichern) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val all = savedMacros().put(name, Macros.toJson(lastMacro))
                    prefs.edit().putString(KEY_MACROS, all.toString()).apply()
                }
            }
            .setNegativeButton(R.string.abbrechen, null)
            .show()
    }

    private fun listMacros() {
        val all = savedMacros()
        val names = all.keys().asSequence().toList().sorted()
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.mk_keine, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mk_liste)
            .setItems(names.map { it as CharSequence }.toTypedArray()) { _, which ->
                val name = names[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle(name)
                    .setPositiveButton(R.string.mk_ausfuehren) { _, _ ->
                        lastMacro = Macros.fromJson(all.getJSONArray(name))
                        invalidateOptionsMenu()
                        edit { play(lastMacro, 1) }
                    }
                    .setNeutralButton(R.string.mk_loeschen) { _, _ ->
                        all.remove(name)
                        prefs.edit().putString(KEY_MACROS, all.toString()).apply()
                    }
                    .setNegativeButton(R.string.abbrechen, null)
                    .show()
            }
            .show()
    }

    // ---------- Suchen / Ersetzen ----------

    private fun setupSearch() {
        searchPanel = findViewById(R.id.search_panel)
        replaceRow = findViewById(R.id.replace_row)
        searchQuery = findViewById(R.id.search_query)
        replaceText = findViewById(R.id.replace_text)
        searchCount = findViewById(R.id.search_count)
        optCase = findViewById(R.id.opt_case)
        optWord = findViewById(R.id.opt_word)
        optAllTabs = findViewById(R.id.opt_all_tabs)
        optMode = findViewById(R.id.opt_mode)

        searchQuery.doAfterTextChanged {
            mainHandler.removeCallbacks(searchRunnable)
            mainHandler.postDelayed(searchRunnable, 250)
        }
        searchQuery.setOnEditorActionListener { _, _, _ ->
            findNext(true)
            true
        }
        optCase.setOnCheckedChangeListener { _, _ -> runSearch() }
        optWord.setOnCheckedChangeListener { _, _ -> runSearch() }
        optMode.setOnCheckedChangeListener { _, _ -> runSearch() }
        findViewById<ImageButton>(R.id.search_prev).setOnClickListener { findNext(false) }
        findViewById<ImageButton>(R.id.search_next).setOnClickListener { findNext(true) }
        findViewById<ImageButton>(R.id.search_close).setOnClickListener { closeSearch() }
        findViewById<Button>(R.id.replace_one).setOnClickListener { replaceOne() }
        findViewById<Button>(R.id.replace_all).setOnClickListener { replaceAll() }
        findViewById<Button>(R.id.search_count_all).setOnClickListener { countAll() }

        editor.subscribeAlways(PublishSearchResultEvent::class.java) { updateCount() }
        editor.subscribeAlways(SelectionChangeEvent::class.java) {
            if (searchPanel.visibility == View.VISIBLE) updateCount()
        }
    }

    private fun currentQuery(): SearchQuery {
        val mode = when (optMode.checkedRadioButtonId) {
            R.id.mode_extended -> SearchMode.EXTENDED
            R.id.mode_regex -> SearchMode.REGEX
            else -> SearchMode.NORMAL
        }
        return SearchQuery(searchQuery.text.toString(), mode, optCase.isChecked, optWord.isChecked)
    }

    private fun openSearch(replace: Boolean) {
        val doc = current ?: return
        if (doc.viewMode) setViewMode(false)
        searchPanel.visibility = View.VISIBLE
        replaceRow.visibility = if (replace) View.VISIBLE else View.GONE
        val c = editor.cursor
        if (c.isSelected && c.leftLine == c.rightLine) {
            searchQuery.setText(editor.text.subSequence(c.left, c.right))
        }
        searchQuery.requestFocus()
        searchQuery.selectAll()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(searchQuery, 0)
        runSearch()
    }

    private fun closeSearch() {
        mainHandler.removeCallbacks(searchRunnable)
        searchPanel.visibility = View.GONE
        try {
            editor.searcher.stopSearch()
        } catch (_: Exception) {
        }
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchQuery.windowToken, 0)
        editor.requestFocus()
    }

    /** Treffer im Editor markieren. Gesucht wird mit derselben Regel wie beim Ersetzen. */
    private fun runSearch() {
        val doc = current
        if (searchPanel.visibility != View.VISIBLE || doc == null || doc.viewMode) return
        val q = currentQuery()
        if (q.text.isEmpty()) {
            editor.searcher.stopSearch()
            searchCount.text = ""
            return
        }
        try {
            Search.pattern(q)
            val (pattern, type) = when (q.mode) {
                SearchMode.NORMAL, SearchMode.EXTENDED -> {
                    val text = if (q.mode == SearchMode.EXTENDED) Search.unescape(q.text) else q.text
                    text to if (q.wholeWord) EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    else EditorSearcher.SearchOptions.TYPE_NORMAL
                }
                SearchMode.REGEX -> (if (q.wholeWord) "\\b(?:${q.text})\\b" else q.text) to
                    EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            }
            editor.searcher.search(
                pattern,
                EditorSearcher.SearchOptions(type, !q.matchCase, RegexBackrefGrammar.DEFAULT),
            )
            searchCount.text = "…"
        } catch (_: PatternSyntaxException) {
            editor.searcher.stopSearch()
            searchCount.text = getString(R.string.regex_fehler)
        }
    }

    private fun updateCount() {
        val s = editor.searcher
        if (!s.hasQuery()) return
        try {
            val n = s.matchedPositionCount
            val i = s.currentMatchedPositionIndex
            searchCount.text = when {
                n == 0 -> "0"
                i >= 0 -> "${i + 1}/$n"
                else -> "$n"
            }
        } catch (_: IllegalStateException) {
        }
    }

    private fun findNext(forward: Boolean) {
        if (current == null) return
        if (searchPanel.visibility != View.VISIBLE) {
            openSearch(false)
            return
        }
        if (!editor.searcher.hasQuery()) runSearch()
        try {
            if (forward) editor.searcher.gotoNext() else editor.searcher.gotoPrevious()
        } catch (_: IllegalStateException) {
        }
    }

    /** Ersetzt den markierten Treffer und springt zum nächsten. Ist nichts markiert, erst zum Treffer springen. */
    private fun replaceOne() {
        if (current?.readOnly == true) {
            Toast.makeText(this, R.string.schreibgeschuetzt_kurz, Toast.LENGTH_SHORT).show()
            return
        }
        val q = currentQuery()
        if (q.text.isEmpty()) return
        val text = editor.text
        val c = editor.cursor
        try {
            val pattern = Search.pattern(q)
            val full = text.toString()
            val m = pattern.matcher(full)
            if (c.isSelected && m.find(c.left) && m.start() == c.left && m.end() == c.right) {
                val sb = StringBuffer()
                m.appendReplacement(sb, Search.replacement(q, replaceText.text.toString()))
                val replacement = sb.substring(c.left)
                text.replace(c.left, c.right, replacement)
            }
            editor.post { findNext(true) }
        } catch (_: PatternSyntaxException) {
            searchCount.text = getString(R.string.regex_fehler)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
        }
    }

    private fun targets(): List<Doc> = if (optAllTabs.isChecked) docs.toList() else listOfNotNull(current)

    private fun replaceAll() {
        val q = currentQuery()
        if (q.text.isEmpty()) return
        try {
            Search.pattern(q)
        } catch (_: PatternSyntaxException) {
            searchCount.text = getString(R.string.regex_fehler)
            return
        }
        var total = 0
        var files = 0
        for (doc in targets().filter { !it.readOnly }) {
            val (newText, n) = Search.replaceAll(doc.content.toString(), q, replaceText.text.toString())
            if (n == 0) continue
            total += n
            files++
            val line = doc.content.cursor.leftLine
            doc.content.replace(0, doc.content.length, newText)
            val safe = line.coerceIn(0, doc.content.lineCount - 1)
            doc.content.cursor.set(safe, 0)
            refreshState(doc)
        }
        Toast.makeText(this, getString(R.string.ersetzt, total, files), Toast.LENGTH_SHORT).show()
        runSearch()
    }

    private fun countAll() {
        val q = currentQuery()
        if (q.text.isEmpty()) return
        try {
            var total = 0
            var files = 0
            for (doc in targets()) {
                val n = Search.count(doc.content, q)
                if (n > 0) {
                    total += n
                    files++
                }
            }
            Toast.makeText(this, getString(R.string.treffer_gezaehlt, total, files), Toast.LENGTH_SHORT).show()
        } catch (_: PatternSyntaxException) {
            searchCount.text = getString(R.string.regex_fehler)
        }
    }

    private fun goToLine() {
        val doc = current ?: return
        if (doc.viewMode) setViewMode(false)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.zeile_hinweis, editor.lineCount)
            setText((editor.cursor.leftLine + 1).toString())
            selectAll()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.gehe_zu_zeile)
            .setView(box)
            .setPositiveButton(R.string.los) { _, _ ->
                input.text.toString().toIntOrNull()?.let { n ->
                    val line = (n - 1).coerceIn(0, editor.lineCount - 1)
                    editor.setSelection(line, 0)
                    editor.requestFocus()
                }
            }
            .setNegativeButton(R.string.abbrechen, null)
            .show()
        input.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) openUri(uri)
    }

    override fun onStop() {
        super.onStop()
        saveSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        editor.release()
    }

    // Tastenkürzel wie in Notepad++ (Hardware-Tastatur)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            forwardDelete = event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL
            if (recording && !replaying && !event.isCtrlPressed && event.keyCode in NAV_KEYS && current?.viewMode == false) {
                recorded += MacroStep.Key(event.keyCode, event.metaState and KeyEvent.META_SHIFT_MASK)
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            val doc = current
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    when {
                        event.isAltPressed -> doc?.let { saveAs(it, null) }
                        event.isShiftPressed -> saveAll()
                        else -> doc?.let { save(it, null) }
                    }
                    true
                }
                KeyEvent.KEYCODE_O -> {
                    requestOpen()
                    true
                }
                KeyEvent.KEYCODE_N -> {
                    newDocument()
                    true
                }
                KeyEvent.KEYCODE_F -> {
                    openSearch(false)
                    true
                }
                KeyEvent.KEYCODE_H -> {
                    openSearch(true)
                    true
                }
                KeyEvent.KEYCODE_G -> {
                    goToLine()
                    true
                }
                KeyEvent.KEYCODE_D -> cmd("dup")
                KeyEvent.KEYCODE_L -> event.isShiftPressed && cmd("del")
                KeyEvent.KEYCODE_DPAD_UP -> event.isShiftPressed && cmd("up")
                KeyEvent.KEYCODE_DPAD_DOWN -> event.isShiftPressed && cmd("down")
                KeyEvent.KEYCODE_J -> cmd("join")
                KeyEvent.KEYCODE_Q -> cmd("comment")
                KeyEvent.KEYCODE_U -> cmd(if (event.isShiftPressed) "case:UPPER" else "case:LOWER")
                KeyEvent.KEYCODE_R -> event.isShiftPressed && edit { toggleRecording() }
                KeyEvent.KEYCODE_P -> event.isShiftPressed && edit { play(lastMacro, 1) }
                KeyEvent.KEYCODE_F2 -> cmd("bm:toggle")
                KeyEvent.KEYCODE_W -> {
                    doc?.let { requestClose(it) }
                    true
                }
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_UP -> {
                    val back = event.isShiftPressed || event.keyCode == KeyEvent.KEYCODE_PAGE_UP
                    switchRelative(if (back) -1 else 1)
                    true
                }
                else -> false
            }
            if (handled) return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && !event.isCtrlPressed) {
            if (event.keyCode == KeyEvent.KEYCODE_F2) {
                cmd(if (event.isShiftPressed) "bm:prev" else "bm:next")
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_F3) {
                findNext(!event.isShiftPressed)
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && searchPanel.visibility == View.VISIBLE) {
                closeSearch()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------- Menü ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val doc = current
        val has = doc != null
        val viewing = doc != null && doc.viewMode
        fun item(id: Int, visible: Boolean, enabled: Boolean = true) {
            menu.findItem(id)?.let {
                it.isVisible = visible
                it.isEnabled = enabled
                it.icon?.mutate()?.alpha = if (enabled) 255 else 90
            }
        }
        item(R.id.bearbeiten, viewing)
        item(R.id.lesen, doc != null && !viewing && doc.isMarkdown)
        item(R.id.rueckgaengig, has && !viewing, editor.canUndo())
        item(R.id.wiederholen, has && !viewing, editor.canRedo())
        item(R.id.speichern, has, !saving && doc != null && (doc.isDirty || doc.uri == null))
        item(R.id.speichern_unter, has, !saving)
        item(R.id.alle_speichern, docs.size > 1, !saving)
        item(R.id.schliessen, has)
        item(R.id.quelltext, viewing)
        menu.findItem(R.id.quelltext)?.isChecked = showSource
        item(R.id.neu_laden, doc?.uri != null)
        item(R.id.sprache, has && !viewing)
        item(R.id.suchen, has)
        item(R.id.ersetzen, has)
        item(R.id.gehe_zu, has)
        item(R.id.bearbeiten_menue, has)
        item(R.id.zeilen_menue, has)
        item(R.id.lesezeichen_menue, has)
        item(R.id.makro_menue, has)
        menu.findItem(R.id.mk_aufnahme)?.setTitle(if (recording) R.string.mk_aufnahme_stopp else R.string.mk_aufnahme_start)
        menu.findItem(R.id.mk_abspielen)?.isEnabled = lastMacro.isNotEmpty() && !recording
        menu.findItem(R.id.mk_mehrfach)?.isEnabled = lastMacro.isNotEmpty() && !recording
        menu.findItem(R.id.mk_speichern)?.isEnabled = lastMacro.isNotEmpty()
        menu.findItem(R.id.st_leerzeichen)?.isChecked = prefs.getBoolean(KEY_SPACES, false)
        menu.findItem(R.id.st_zeile)?.isChecked = prefs.getBoolean(KEY_CURRENT_LINE, true)
        menu.findItem(R.id.st_einruecken)?.isChecked = prefs.getBoolean(KEY_AUTO_INDENT, true)
        menu.findItem(R.id.st_klammern)?.isChecked = prefs.getBoolean(KEY_BRACKETS, true)
        menu.findItem(R.id.st_zoom)?.isChecked = prefs.getBoolean(KEY_ZOOM, true)
        menu.findItem(R.id.st_vervollstaendigen)?.isChecked = prefs.getBoolean(KEY_COMPLETION, false)
        item(R.id.datei_menue, has)
        menu.findItem(R.id.dt_schreibschutz)?.isChecked = doc?.readOnly == true
        menu.findItem(R.id.dt_pfad)?.isEnabled = doc?.uri != null
        item(R.id.kodierung, has)
        item(R.id.zeilenende, has)
        if (doc != null) {
            val f = doc.format
            val enc = when {
                f.charset == Charsets.UTF_16LE -> R.id.enc_utf16le
                f.charset == Charsets.UTF_16BE -> R.id.enc_utf16be
                f.charset != Charsets.UTF_8 -> R.id.enc_ansi
                f.bom -> R.id.enc_utf8bom
                else -> R.id.enc_utf8
            }
            menu.findItem(enc)?.isChecked = true
            menu.findItem(if (f.crlf) R.id.eol_crlf else R.id.eol_lf)?.isChecked = true
        }
        menu.findItem(R.id.umbruch)?.isChecked = prefs.getBoolean(KEY_WRAP, true)
        menu.findItem(R.id.alle_zeichen)?.isChecked = prefs.getBoolean(KEY_ALL_CHARS, false)
        menu.findItem(R.id.zeilennummern)?.isChecked = prefs.getBoolean(KEY_LINE_NUMBERS, true)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val doc = current
        when (item.itemId) {
            R.id.bearbeiten -> setViewMode(false)
            R.id.lesen -> setViewMode(true)
            R.id.rueckgaengig -> editor.undo()
            R.id.wiederholen -> editor.redo()
            R.id.speichern -> doc?.let { save(it, null) }
            R.id.speichern_unter -> doc?.let { saveAs(it, null) }
            R.id.alle_speichern -> saveAll()
            R.id.schliessen -> doc?.let { requestClose(it) }
            R.id.oeffnen -> requestOpen()
            R.id.neu -> newDocument()
            R.id.quelltext -> {
                showSource = !showSource
                invalidateOptionsMenu()
                doc?.let { it.viewScroll = scroll.scrollY; render(it) }
            }
            R.id.groesser -> changeTextSize(2f)
            R.id.kleiner -> changeTextSize(-2f)
            R.id.neu_laden -> doc?.let { reload(it) }
            R.id.sprache -> doc?.let { chooseLanguage(it) }
            R.id.suchen -> openSearch(false)
            R.id.ersetzen -> openSearch(true)
            R.id.gehe_zu -> goToLine()
            R.id.enc_utf8 -> doc?.let { changeFormat(it, it.format.copy(charset = Charsets.UTF_8, bom = false)) }
            R.id.enc_utf8bom -> doc?.let { changeFormat(it, it.format.copy(charset = Charsets.UTF_8, bom = true)) }
            R.id.enc_ansi -> doc?.let { changeFormat(it, it.format.copy(charset = TextFile.ANSI, bom = false)) }
            R.id.eol_crlf -> doc?.let { changeFormat(it, it.format.copy(crlf = true)) }
            R.id.eol_lf -> doc?.let { changeFormat(it, it.format.copy(crlf = false)) }
            R.id.umbruch -> togglePref(KEY_WRAP, true)
            R.id.alle_zeichen -> togglePref(KEY_ALL_CHARS, false)
            R.id.zeilennummern -> togglePref(KEY_LINE_NUMBERS, true)
            R.id.op_duplizieren -> cmd("dup")
            R.id.op_zeile_loeschen -> cmd("del")
            R.id.op_hoch -> cmd("up")
            R.id.op_runter -> cmd("down")
            R.id.op_verbinden -> cmd("join")
            R.id.op_kommentar -> cmd("comment")
            R.id.op_gross -> cmd("case:UPPER")
            R.id.op_klein -> cmd("case:LOWER")
            R.id.op_wort_gross -> cmd("case:PROPER")
            R.id.op_satz_gross -> cmd("case:SENTENCE")
            R.id.op_umkehren -> cmd("case:INVERT")
            R.id.op_sort_auf -> cmd("sort:ASC")
            R.id.op_sort_ab -> cmd("sort:DESC")
            R.id.op_sort_ohne -> cmd("sort:ASC_IGNORE_CASE")
            R.id.op_sort_zahl -> cmd("sort:NUMERIC")
            R.id.op_doppelte -> cmd("dedupe")
            R.id.op_leere -> cmd("empty")
            R.id.op_trim_ende -> cmd("trimEnd")
            R.id.op_trim_anfang -> cmd("trimStart")
            R.id.op_tabs_leer -> cmd("tabs2spaces")
            R.id.op_leer_tabs -> cmd("spaces2tabs")
            R.id.lz_umschalten -> cmd("bm:toggle")
            R.id.lz_naechstes -> cmd("bm:next")
            R.id.lz_vorheriges -> cmd("bm:prev")
            R.id.lz_liste -> edit { listBookmarks() }
            R.id.lz_loeschen -> current?.bookmarks?.clear()
            R.id.kodierung, R.id.zeilenende, R.id.ansicht,
            R.id.bearbeiten_menue, R.id.zeilen_menue, R.id.lesezeichen_menue,
            R.id.makro_menue, R.id.einstellungen -> return false
            R.id.mk_aufnahme -> edit { toggleRecording() }
            R.id.mk_abspielen -> edit { play(lastMacro, 1) }
            R.id.mk_mehrfach -> edit { askRepeat() }
            R.id.mk_speichern -> saveMacro()
            R.id.mk_liste -> listMacros()
            R.id.st_farben -> chooseTheme()
            R.id.st_tabbreite -> chooseTabWidth()
            R.id.st_leerzeichen -> togglePref(KEY_SPACES, false)
            R.id.st_zeile -> togglePref(KEY_CURRENT_LINE, true)
            R.id.st_einruecken -> togglePref(KEY_AUTO_INDENT, true)
            R.id.st_klammern -> togglePref(KEY_BRACKETS, true)
            R.id.st_zoom -> togglePref(KEY_ZOOM, true)
            R.id.st_vervollstaendigen -> togglePref(KEY_COMPLETION, false)
            R.id.enc_utf16le -> doc?.let { changeFormat(it, it.format.copy(charset = Charsets.UTF_16LE, bom = true)) }
            R.id.enc_utf16be -> doc?.let { changeFormat(it, it.format.copy(charset = Charsets.UTF_16BE, bom = true)) }
            R.id.op_einzug_plus -> cmd("indent")
            R.id.op_einzug_minus -> cmd("outdent")
            R.id.op_leer_oben -> cmd("blankAbove")
            R.id.op_leer_unten -> cmd("blankBelow")
            R.id.op_datum_kurz -> cmd("date:short")
            R.id.op_datum_lang -> cmd("date:long")
            R.id.op_sort_laenge -> cmd("sort:LENGTH")
            R.id.op_umdrehen -> cmd("reverse")
            R.id.op_zufall -> cmd("shuffle")
            R.id.op_doppelte_folge -> cmd("dedupeConsecutive")
            R.id.op_trim_beide -> cmd("trimBoth")
            R.id.op_eol_leer -> cmd("eol2space")
            R.id.datei_menue -> return false
            R.id.dt_name -> doc?.let { copyToClipboard(it.name) }
            R.id.dt_pfad -> doc?.uri?.let { copyToClipboard(Uri.decode(it.toString())) }
            R.id.dt_schreibschutz -> doc?.let { setReadOnly(it, !it.readOnly) }
            R.id.dt_info -> doc?.let { showSummary(it) }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- Tabs ----------

    private fun addDoc(doc: Doc, select: Boolean = true) {
        docs.add(doc)
        val tab = tabs.newTab().setCustomView(R.layout.tab_item)
        suppressTabEvents = true
        tabs.addTab(tab, false)
        suppressTabEvents = false
        bindTab(doc)
        tabs.visibility = View.VISIBLE
        if (select) switchTo(doc)
    }

    private fun bindTab(doc: Doc) {
        val tab = tabs.getTabAt(docs.indexOf(doc)) ?: return
        val view = tab.customView ?: return
        view.findViewById<TextView>(R.id.tab_title).text = (if (doc.isDirty) "*" else "") + doc.name
        view.findViewById<ImageButton>(R.id.tab_close).setOnClickListener { requestClose(doc) }
        tab.view.setOnLongClickListener { showTabMenu(doc, it); true }
    }

    private fun showTabMenu(doc: Doc, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.schliessen)
            menu.add(0, 2, 1, R.string.andere_schliessen)
            menu.add(0, 3, 2, R.string.alle_schliessen)
            menu.add(0, 4, 3, R.string.links_schliessen)
            menu.add(0, 5, 4, R.string.rechts_schliessen)
            menu.add(0, 6, 5, R.string.unveraenderte_schliessen)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> requestClose(doc)
                    2 -> closeMany(docs.filter { d -> d !== doc })
                    3 -> closeMany(docs.toList())
                    4 -> closeMany(docs.subList(0, docs.indexOf(doc).coerceAtLeast(0)).toList())
                    5 -> closeMany(docs.subList((docs.indexOf(doc) + 1).coerceAtMost(docs.size), docs.size).toList())
                    6 -> closeMany(docs.filter { !it.isDirty })
                }
                true
            }
            show()
        }
    }

    private fun switchTo(doc: Doc) {
        current?.let { if (it.viewMode) it.viewScroll = scroll.scrollY }
        current = doc
        val index = docs.indexOf(doc)
        if (tabs.selectedTabPosition != index) {
            suppressTabEvents = true
            tabs.getTabAt(index)?.select()
            suppressTabEvents = false
        }
        editor.setText(doc.content)
        editor.isEditable = !doc.readOnly
        editor.setEditorLanguage(language(doc.scope))
        showDoc(doc)
        if (searchPanel.visibility == View.VISIBLE) runSearch()
        menuState = ""
        refreshState(doc)
    }

    private fun switchRelative(step: Int) {
        val doc = current ?: return
        if (docs.size < 2) return
        val i = (docs.indexOf(doc) + step + docs.size) % docs.size
        switchTo(docs[i])
    }

    private fun showDoc(doc: Doc) {
        emptyView.visibility = View.GONE
        progress.visibility = if (loading > 0) View.VISIBLE else View.GONE
        if (doc.viewMode && doc.isMarkdown) {
            editor.visibility = View.GONE
            render(doc)
        } else {
            doc.viewMode = false
            scroll.visibility = View.GONE
            editor.visibility = View.VISIBLE
            val c = doc.content.cursor
            editor.post { editor.ensurePositionVisible(c.leftLine, c.leftColumn, true) }
        }
    }

    private fun showEmpty() {
        current = null
        if (searchPanel.visibility == View.VISIBLE) closeSearch()
        editor.setText(Content())
        editor.visibility = View.GONE
        scroll.visibility = View.GONE
        emptyView.text = getString(R.string.leer)
        emptyView.visibility = View.VISIBLE
        updateTitle()
        invalidateOptionsMenu()
    }

    /** Titel, Tab-Stern und Menü nach einer Änderung auffrischen (Menü nur wenn nötig). */
    private fun refreshState(doc: Doc) {
        bindTab(doc)
        if (doc === current) updateTitle()
        val state = "${doc.isDirty}${editor.canUndo()}${editor.canRedo()}${doc.viewMode}$saving${doc.format}"
        if (state != menuState) {
            menuState = state
            invalidateOptionsMenu()
        }
    }

    private fun updateTitle() {
        val bar = supportActionBar ?: return
        val doc = current
        if (doc == null) {
            bar.title = getString(R.string.app_name)
            bar.subtitle = null
            return
        }
        bar.title = (if (doc.isDirty) "*" else "") + doc.name
        val lang = Languages.byScope(doc.scope)?.name ?: getString(R.string.normaler_text)
        bar.subtitle = when {
            recording -> getString(R.string.mk_laeuft)
            doc.viewMode -> null
            doc.readOnly -> "$lang · ${doc.format.label} · ${getString(R.string.schreibgeschuetzt_kurz)}"
            else -> "$lang · ${doc.format.label}"
        }
    }

    /** Wechsel zwischen gerendertem Markdown und Bearbeiten, ungefähr an derselben Stelle. */
    private fun setViewMode(view: Boolean) {
        val doc = current ?: return
        if (view) {
            val lines = editor.lineCount.coerceAtLeast(1)
            val fraction = editor.firstVisibleLine.toFloat() / lines
            doc.viewMode = true
            editor.hideSoftInput()
            editor.visibility = View.GONE
            render(doc, fraction)
        } else {
            val max = textView.height - scroll.height
            val fraction = if (max > 0) scroll.scrollY.toFloat() / max else 0f
            doc.viewMode = false
            scroll.visibility = View.GONE
            editor.visibility = View.VISIBLE
            val line = (fraction * doc.content.lineCount).toInt().coerceIn(0, doc.content.lineCount - 1)
            editor.post { editor.setSelection(line, 0) }
        }
        refreshState(doc)
        updateTitle()
    }

    private fun render(doc: Doc, fraction: Float? = null) {
        val text = doc.content.toString()
        emptyView.visibility = View.GONE
        scroll.visibility = View.VISIBLE
        if (showSource) {
            textView.typeface = Typeface.MONOSPACE
            textView.text = text
        } else {
            textView.typeface = Typeface.DEFAULT
            markwon.setMarkdown(textView, text)
        }
        val y = doc.viewScroll
        scroll.doOnPreDraw {
            val max = (textView.height - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, fraction?.let { (it * max).toInt() } ?: y)
        }
    }

    private fun changeTextSize(delta: Float) {
        val sp = (prefs.getFloat(KEY_SIZE, 16f) + delta).coerceIn(8f, 40f)
        prefs.edit().putFloat(KEY_SIZE, sp).apply()
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        editor.setTextSize(sp)
        current?.let {
            if (it.viewMode) {
                it.viewScroll = scroll.scrollY
                render(it)
            }
        }
    }

    // ---------- Öffnen, Neu, Schließen ----------

    private fun requestOpen() = openDocuments.launch(arrayOf("text/*", "application/*"))

    private fun newDocument() {
        val used = docs.filter { it.uri == null }.map { it.name }.toSet()
        var n = 1
        while (getString(R.string.unbenannt, n) in used) n++
        addDoc(Doc(uri = null, name = getString(R.string.unbenannt, n), format = FileFormat(), text = ""))
    }

    private fun openUri(uri: Uri) {
        docs.firstOrNull { it.uri == uri }?.let {
            switchTo(it)
            return
        }
        loading++
        progress.visibility = View.VISIBLE
        executor.execute {
            val result = runCatching { readDoc(uri) }
            mainHandler.post {
                if (isDestroyed) return@post
                loading--
                progress.visibility = if (loading > 0) View.VISIBLE else View.GONE
                result.onSuccess { doc ->
                    doc.viewMode = doc.isMarkdown
                    addDoc(doc)
                }.onFailure { e ->
                    val msg = if (e is TooLarge) getString(R.string.zu_gross, MAX_MB)
                    else getString(R.string.fehler, e.message ?: e.javaClass.simpleName)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Liest eine Datei (Hintergrund-Thread). */
    private fun readDoc(uri: Uri, id: String? = null): Doc {
        val name = displayName(uri)
        val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it) }
            ?: throw IllegalStateException("Kein Zugriff auf die Datei")
        val (text, fmt) = TextFile.decode(bytes)
        return if (id != null) Doc(uri, name, fmt, text, id = id) else Doc(uri, name, fmt, text)
    }

    private fun reload(doc: Doc) {
        val uri = doc.uri ?: return
        confirmIfDirty(doc) {
            executor.execute {
                val result = runCatching { readDoc(uri) }
                mainHandler.post {
                    if (isDestroyed) return@post
                    result.onSuccess { fresh ->
                        val i = docs.indexOf(doc)
                        if (i >= 0) {
                            fresh.viewMode = doc.viewMode
                            fresh.viewScroll = scroll.scrollY
                            docs[i] = fresh
                            File(sessionDir, doc.id + ".txt").delete()
                            if (current === doc) current = null
                            bindTab(fresh)
                            switchTo(fresh)
                        }
                    }.onFailure { e ->
                        Toast.makeText(this, getString(R.string.fehler, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun requestClose(doc: Doc) = confirmIfDirty(doc) { closeDoc(doc) }

    private fun closeMany(list: List<Doc>) {
        val doc = list.firstOrNull { it in docs } ?: return
        confirmIfDirty(doc) {
            closeDoc(doc)
            closeMany(list.drop(1))
        }
    }

    private fun closeDoc(doc: Doc) {
        val i = docs.indexOf(doc)
        if (i < 0) return
        docs.removeAt(i)
        suppressTabEvents = true
        tabs.removeTabAt(i)
        suppressTabEvents = false
        File(sessionDir, doc.id + ".txt").delete()
        if (current === doc) {
            current = null
            val next = docs.getOrNull(minOf(i, docs.size - 1))
            if (next != null) switchTo(next) else showEmpty()
        }
    }

    /** Bei ungespeicherten Änderungen nachfragen: Speichern, Verwerfen oder Abbrechen. */
    private fun confirmIfDirty(doc: Doc, action: () -> Unit) {
        if (!doc.isDirty) {
            action()
            return
        }
        if (current !== doc) switchTo(doc)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.aenderungen_titel)
            .setMessage(getString(R.string.aenderungen_text, doc.name))
            .setPositiveButton(R.string.speichern) { _, _ -> save(doc) { ok -> if (ok) action() } }
            .setNegativeButton(R.string.verwerfen) { _, _ -> action() }
            .setNeutralButton(R.string.abbrechen, null)
            .show()
    }

    // ---------- Speichern ----------

    private fun save(doc: Doc, then: ((Boolean) -> Unit)?) {
        if (saving) return
        val uri = doc.uri
        if (uri == null) saveAs(doc, then) else writeDocument(doc, uri, then)
    }

    private fun saveAs(doc: Doc, then: ((Boolean) -> Unit)?) {
        if (saving) return
        saveAsDoc = doc
        afterSaveAs = then
        val suggested = if ('.' in doc.name) doc.name else doc.name + ".txt"
        createDocument.launch(suggested)
    }

    private fun saveAll() {
        saveEach(docs.filter { it.isDirty })
    }

    private fun saveEach(list: List<Doc>) {
        val doc = list.firstOrNull() ?: return
        if (doc !in docs) return saveEach(list.drop(1))
        if (current !== doc) switchTo(doc)
        save(doc) { ok -> if (ok) saveEach(list.drop(1)) }
    }

    private fun writeDocument(doc: Doc, uri: Uri, then: ((Boolean) -> Unit)?) {
        val text = doc.content.toString()
        val version = doc.content.documentVersion
        val fmt = doc.format
        saving = true
        invalidateOptionsMenu()

        executor.execute {
            val result = runCatching { writeBytes(uri, TextFile.encode(text, fmt)) }
            mainHandler.post {
                if (isDestroyed) return@post
                saving = false
                result.onSuccess {
                    doc.markSaved(version, text.hashCode())
                    File(sessionDir, doc.id + ".txt").delete()
                    Toast.makeText(this, R.string.gespeichert, Toast.LENGTH_SHORT).show()
                    refreshState(doc)
                    then?.invoke(true)
                }.onFailure { e ->
                    val readOnly = e is SecurityException || e is FileNotFoundException ||
                        e is IllegalArgumentException || e is UnsupportedOperationException
                    if (readOnly) {
                        Toast.makeText(this, R.string.schreibgeschuetzt, Toast.LENGTH_LONG).show()
                        saveAs(doc, then)
                    } else {
                        val msg = e.message ?: e.javaClass.simpleName
                        Toast.makeText(this, getString(R.string.speichern_fehler, msg), Toast.LENGTH_LONG).show()
                        then?.invoke(false)
                    }
                }
                menuState = ""
                current?.let { refreshState(it) }
            }
        }
    }

    /** „wt“ kürzt die Datei vorher. Ein reines „w“ lässt bei manchen Anbietern alten Text am Ende stehen. */
    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        var last: Exception? = null
        for (mode in arrayOf("wt", "rwt")) {
            try {
                val out = contentResolver.openOutputStream(uri, mode)
                    ?: throw FileNotFoundException("Kein Schreibzugriff")
                out.use { it.write(bytes) }
                return
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Kein Schreibzugriff")
    }

    private fun takePermission(uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, read)
            } catch (_: SecurityException) {
            }
        }
    }

    // ---------- Sitzung ----------

    /**
     * Merkt sich alle Tabs wie Notepad++. Ungespeicherte Texte und neue Dateien
     * kommen als Entwurf in den App-Speicher und sind beim nächsten Start wieder da.
     */
    private fun saveSession() {
        try {
            current?.let { if (it.viewMode) it.viewScroll = scroll.scrollY }
            val keep = HashSet<String>()
            val list = JSONArray()
            for (d in docs) {
                val cursor = d.content.cursor
                val o = JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("uri", d.uri?.toString() ?: "")
                    .put("charset", d.format.charset.name())
                    .put("bom", d.format.bom)
                    .put("crlf", d.format.crlf)
                    .put("view", d.viewMode)
                    .put("scope", d.scope ?: "")
                    .put("formatChanged", d.formatChanged)
                    .put("bookmarks", JSONArray(d.bookmarks.toList()))
                    .put("readOnly", d.readOnly)
                    .put("scroll", d.viewScroll)
                    .put("line", cursor.leftLine)
                    .put("col", cursor.leftColumn)
                if (d.isDirty || d.uri == null) {
                    val file = File(sessionDir, d.id + ".txt")
                    if (d.needsDraftWrite() || !file.exists()) {
                        file.writeText(d.content.toString())
                        d.draftWritten()
                    }
                    keep += file.name
                    o.put("dirty", d.isDirty)
                }
                list.put(o)
            }
            val root = JSONObject().put("active", docs.indexOf(current)).put("docs", list)
            File(sessionDir, SESSION_FILE).writeText(root.toString())
            sessionDir.listFiles()?.forEach {
                if (it.name.endsWith(".txt") && it.name !in keep) it.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreSession(then: () -> Unit) {
        val file = File(sessionDir, SESSION_FILE)
        if (!file.exists()) {
            // Erster Start nach Version 1.1: zuletzt geöffnete Datei übernehmen
            showEmpty()
            lastOpened()?.let { openUri(it) }
            then()
            return
        }
        loading++
        progress.visibility = View.VISIBLE
        executor.execute {
            val restored = ArrayList<Doc>()
            val failed = ArrayList<String>()
            var active = 0
            try {
                val root = JSONObject(file.readText())
                active = root.optInt("active", 0)
                val list = root.getJSONArray("docs")
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val doc = restoreDoc(o)
                    if (doc == null) {
                        failed += o.optString("name")
                        continue
                    }
                    doc.viewMode = o.optBoolean("view") && doc.isMarkdown
                    doc.readOnly = o.optBoolean("readOnly")
                    o.optJSONArray("bookmarks")?.let { b ->
                        for (k in 0 until b.length()) doc.bookmarks += b.getInt(k)
                    }
                    if (o.has("scope")) doc.scope = o.optString("scope").takeIf { it.isNotEmpty() }
                    if (o.optBoolean("formatChanged")) {
                        doc.format = FileFormat(
                            Charset.forName(o.optString("charset", "UTF-8")),
                            o.optBoolean("bom"),
                            o.optBoolean("crlf"),
                        )
                        doc.formatChanged = true
                    }
                    doc.viewScroll = o.optInt("scroll")
                    val line = o.optInt("line")
                    val col = o.optInt("col")
                    if (line < doc.content.lineCount && col <= doc.content.getColumnCount(line)) {
                        doc.content.cursor.set(line, col)
                    }
                    restored += doc
                }
            } catch (_: Exception) {
            }
            mainHandler.post {
                if (isDestroyed) return@post
                loading--
                restored.forEach { addDoc(it, select = false) }
                val sel = restored.getOrNull(active) ?: restored.firstOrNull()
                if (sel != null) switchTo(sel) else showEmpty()
                progress.visibility = if (loading > 0) View.VISIBLE else View.GONE
                failed.filter { it.isNotEmpty() }.forEach {
                    Toast.makeText(this, getString(R.string.nicht_wiederhergestellt, it), Toast.LENGTH_SHORT).show()
                }
                then()
            }
        }
    }

    /** Ein Tab aus der Sitzung: Entwurf aus dem App-Speicher oder die Datei selbst (Hintergrund-Thread). */
    private fun restoreDoc(o: JSONObject): Doc? = try {
        val id = o.getString("id")
        val uri = o.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val draft = File(sessionDir, "$id.txt")
        when {
            draft.exists() -> {
                val fmt = FileFormat(
                    Charset.forName(o.optString("charset", "UTF-8")),
                    o.optBoolean("bom"),
                    o.optBoolean("crlf"),
                )
                Doc(uri, o.optString("name"), fmt, draft.readText(), dirty = o.optBoolean("dirty"), id = id)
            }
            uri != null -> readDoc(uri, id)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun lastOpened(): Uri? {
        val uri = prefs.getString(KEY_LAST, null)?.let(Uri::parse) ?: return null
        val allowed = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (allowed) uri else null
    }

    // ---------- Hilfen ----------

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) c.getString(i)?.let { return it }
                    }
                }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Datei"
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_MB * 1024 * 1024) throw TooLarge()
        }
        return out.toByteArray()
    }

    private class TooLarge : Exception()

    companion object {
        private const val KEY_LAST = "zuletzt"
        private const val KEY_SIZE = "schrift"
        private const val SESSION_FILE = "sitzung.json"
        private const val KEY_WRAP = "umbruch"
        private const val KEY_ALL_CHARS = "alle_zeichen"
        private const val KEY_LINE_NUMBERS = "zeilennummern"
        private const val KEY_TAB_WIDTH = "tabbreite"
        private const val KEY_SPACES = "leerzeichen_statt_tab"
        private const val KEY_CURRENT_LINE = "aktuelle_zeile"
        private const val KEY_AUTO_INDENT = "einruecken"
        private const val KEY_BRACKETS = "klammern"
        private const val KEY_ZOOM = "zoom"
        private const val KEY_THEME = "farbschema"
        private const val KEY_MACROS = "makros"
        private const val KEY_COMPLETION = "vervollstaendigen"
        private val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        )
        private const val MAX_MB = 10
    }
}
