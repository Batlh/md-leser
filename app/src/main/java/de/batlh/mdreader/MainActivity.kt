package de.batlh.mdreader

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: View
    private lateinit var scroll: NestedScrollView
    private lateinit var textView: TextView
    private lateinit var editor: EditText
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var markwon: Markwon
    private lateinit var history: EditHistory

    private val prefs by lazy { getSharedPreferences("einstellungen", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Das geöffnete Dokument. Der Text selbst steht immer im Editor.
    private var hasDocument = false
    private var docUri: Uri? = null
    private var docName = ""
    private var format = FileFormat()

    private var editMode = false
    private var showSource = false
    private var pendingScrollY = 0
    private var pendingFraction: Float? = null
    private var loadToken = 0
    private var saving = false
    private var afterSaveAs: (() -> Unit)? = null

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                takePermission(uri)
                load(uri, 0)
            }
        }

    // octet-stream, damit der Dateiname genau so bleibt (sonst wird z. B. „.txt“ angehängt)
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val then = afterSaveAs
            afterSaveAs = null
            if (uri != null) {
                takePermission(uri)
                docUri = uri
                docName = displayName(uri)
                writeDocument(uri, then)
            }
        }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            confirmDiscard {
                isEnabled = false
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
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
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
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
        editor.textSize = size
        textView.setTextIsSelectable(true)
        textView.movementMethod = LinkMovementMethod.getInstance()

        history = EditHistory(editor) { onHistoryChanged() }
        editor.addTextChangedListener(history)
        onBackPressedDispatcher.addCallback(this, backCallback)

        showSource = savedInstanceState?.getBoolean(KEY_SOURCE) ?: false
        if (savedInstanceState != null && restoreState(savedInstanceState)) return

        val viewUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val lastUri = lastOpened()
        when {
            viewUri != null -> load(viewUri, 0)
            lastUri != null -> load(lastUri, 0)
            else -> showMessage(getString(R.string.leer))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) confirmDiscard { load(uri, 0) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SOURCE, showSource)
        outState.putBoolean(KEY_EDIT, editMode)
        outState.putBoolean(KEY_HAS_DOC, hasDocument)
        outState.putString(KEY_URI, docUri?.toString())
        outState.putInt(KEY_SCROLL, scroll.scrollY)
        // Ungespeicherte Änderungen als Entwurf sichern, falls Android die App beendet
        if (hasDocument && history.isDirty) {
            try {
                File(cacheDir, DRAFT_FILE).writeText(editor.text.toString())
                outState.putBoolean(KEY_DRAFT, true)
                outState.putString(KEY_NAME, docName)
                outState.putString(KEY_CHARSET, format.charset.name())
                outState.putBoolean(KEY_BOM, format.bom)
                outState.putBoolean(KEY_CRLF, format.crlf)
            } catch (_: Exception) {
            }
        }
    }

    private fun restoreState(state: Bundle): Boolean {
        val uri = state.getString(KEY_URI)?.let(Uri::parse)
        if (state.getBoolean(KEY_DRAFT)) {
            val text = try {
                File(cacheDir, DRAFT_FILE).readText()
            } catch (_: Exception) {
                return false
            }
            hasDocument = true
            docUri = uri
            docName = state.getString(KEY_NAME) ?: getString(R.string.unbenannt)
            format = FileFormat(
                Charset.forName(state.getString(KEY_CHARSET) ?: "UTF-8"),
                state.getBoolean(KEY_BOM),
                state.getBoolean(KEY_CRLF),
            )
            setEditorText(text)
            history.reset(dirty = true)
            if (state.getBoolean(KEY_EDIT)) {
                setEditMode(true)
            } else {
                showViewer(0)
            }
            return true
        }
        if (uri != null) {
            load(uri, state.getInt(KEY_SCROLL), state.getBoolean(KEY_EDIT))
            return true
        }
        if (state.getBoolean(KEY_HAS_DOC)) {
            newDocument()
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    // Tastenkürzel wie in Notepad++ (Hardware-Tastatur). Vor dem EditText abfangen,
    // weil der sonst Strg+Z mit seinem eigenen Rückgängig belegt.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    if (event.isShiftPressed) saveAs(null) else save(null)
                    true
                }
                KeyEvent.KEYCODE_O -> {
                    requestOpen()
                    true
                }
                KeyEvent.KEYCODE_N -> {
                    confirmDiscard { newDocument() }
                    true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (editMode) {
                        if (event.isShiftPressed) history.redo() else history.undo()
                    }
                    editMode
                }
                KeyEvent.KEYCODE_Y -> {
                    if (editMode) history.redo()
                    editMode
                }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        fun item(id: Int, visible: Boolean, enabled: Boolean = true) {
            menu.findItem(id)?.let {
                it.isVisible = visible
                it.isEnabled = enabled
                it.icon?.mutate()?.alpha = if (enabled) 255 else 90
            }
        }
        item(R.id.bearbeiten, hasDocument && !editMode)
        item(R.id.lesen, hasDocument && editMode)
        item(R.id.rueckgaengig, hasDocument && editMode, history.canUndo)
        item(R.id.wiederholen, hasDocument && editMode, history.canRedo)
        item(R.id.speichern, hasDocument, !saving && (history.isDirty || docUri == null))
        item(R.id.speichern_unter, hasDocument, !saving)
        item(R.id.quelltext, hasDocument && !editMode)
        menu.findItem(R.id.quelltext)?.isChecked = showSource
        item(R.id.neu_laden, docUri != null)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.bearbeiten -> setEditMode(true)
            R.id.lesen -> setEditMode(false)
            R.id.rueckgaengig -> history.undo()
            R.id.wiederholen -> history.redo()
            R.id.speichern -> save(null)
            R.id.speichern_unter -> saveAs(null)
            R.id.oeffnen -> requestOpen()
            R.id.neu -> confirmDiscard { newDocument() }
            R.id.quelltext -> {
                showSource = !showSource
                invalidateOptionsMenu()
                render()
            }
            R.id.groesser -> changeTextSize(2f)
            R.id.kleiner -> changeTextSize(-2f)
            R.id.neu_laden -> docUri?.let { uri ->
                val y = scroll.scrollY
                val edit = editMode
                confirmDiscard { load(uri, y, edit) }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- Dokument ----------

    private fun requestOpen() = confirmDiscard {
        openDocument.launch(arrayOf("text/*", "application/octet-stream"))
    }

    private fun newDocument() {
        loadToken++
        progress.visibility = View.GONE
        hasDocument = true
        docUri = null
        docName = getString(R.string.unbenannt)
        format = FileFormat()
        setEditorText("")
        history.reset()
        setEditMode(true)
    }

    private fun load(uri: Uri, scrollY: Int, startInEdit: Boolean = false) {
        val token = ++loadToken
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        scroll.visibility = View.GONE
        editor.visibility = View.GONE

        executor.execute {
            val result = runCatching {
                val name = displayName(uri)
                val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it) }
                    ?: throw IllegalStateException("Kein Zugriff auf die Datei")
                val (text, fmt) = TextFile.decode(bytes)
                Triple(name, text, fmt)
            }
            mainHandler.post {
                if (token != loadToken || isDestroyed) return@post
                progress.visibility = View.GONE
                result.onSuccess { (name, text, fmt) ->
                    hasDocument = true
                    docUri = uri
                    docName = name
                    format = fmt
                    setEditorText(text)
                    history.reset()
                    if (startInEdit) setEditMode(true) else showViewer(scrollY)
                }.onFailure { e ->
                    hasDocument = false
                    docUri = null
                    editMode = false
                    setEditorText("")
                    history.reset()
                    showMessage(
                        if (e is TooLarge) getString(R.string.zu_gross, MAX_MB)
                        else getString(R.string.fehler, e.message ?: e.javaClass.simpleName)
                    )
                }
                invalidateOptionsMenu()
            }
        }
    }

    private fun save(then: (() -> Unit)?) {
        if (!hasDocument || saving) return
        val uri = docUri
        if (uri == null) saveAs(then) else writeDocument(uri, then)
    }

    private fun saveAs(then: (() -> Unit)?) {
        if (!hasDocument || saving) return
        afterSaveAs = then
        createDocument.launch(docName)
    }

    private fun writeDocument(uri: Uri, then: (() -> Unit)?) {
        val text = editor.text.toString()
        val fmt = format
        val checkpoint = history.checkpoint()
        saving = true
        invalidateOptionsMenu()

        executor.execute {
            val result = runCatching { writeBytes(uri, TextFile.encode(text, fmt)) }
            mainHandler.post {
                if (isDestroyed) return@post
                saving = false
                result.onSuccess {
                    history.markSaved(checkpoint)
                    Toast.makeText(this, R.string.gespeichert, Toast.LENGTH_SHORT).show()
                    then?.invoke()
                }.onFailure { e ->
                    val readOnly = e is SecurityException || e is FileNotFoundException ||
                        e is IllegalArgumentException || e is UnsupportedOperationException
                    if (readOnly) {
                        Toast.makeText(this, R.string.schreibgeschuetzt, Toast.LENGTH_LONG).show()
                        saveAs(then)
                    } else {
                        val msg = e.message ?: e.javaClass.simpleName
                        Toast.makeText(this, getString(R.string.speichern_fehler, msg), Toast.LENGTH_LONG).show()
                    }
                }
                updateTitle()
                invalidateOptionsMenu()
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

    /** Fragt bei ungespeicherten Änderungen nach, bevor [action] das Dokument ersetzt. */
    private fun confirmDiscard(action: () -> Unit) {
        if (!hasDocument || !history.isDirty) {
            action()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.aenderungen_titel)
            .setMessage(getString(R.string.aenderungen_text, docName))
            .setPositiveButton(R.string.speichern) { _, _ -> save(action) }
            .setNegativeButton(R.string.verwerfen) { _, _ -> action() }
            .setNeutralButton(R.string.abbrechen, null)
            .show()
    }

    private fun takePermission(uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, read)
            } catch (_: SecurityException) {
                return
            }
        }
        prefs.edit().putString(KEY_LAST, uri.toString()).apply()
    }

    private fun lastOpened(): Uri? {
        val uri = prefs.getString(KEY_LAST, null)?.let(Uri::parse) ?: return null
        val allowed = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (allowed) uri else null
    }

    // ---------- Anzeige ----------

    private fun onHistoryChanged() {
        backCallback.isEnabled = history.isDirty
        updateTitle()
        invalidateOptionsMenu()
    }

    private fun updateTitle() {
        val bar = supportActionBar ?: return
        if (!hasDocument) {
            bar.title = getString(R.string.app_name)
            bar.subtitle = null
            return
        }
        bar.title = (if (history.isDirty) "*" else "") + docName
        bar.subtitle = if (editMode) format.label else null
    }

    private fun setEditorText(text: String) {
        history.suppressed = true
        try {
            editor.setText(text)
        } finally {
            history.suppressed = false
        }
        editor.setSelection(0)
    }

    /** Wechsel zwischen Lesen und Bearbeiten, ungefähr an derselben Stelle im Text. */
    private fun setEditMode(edit: Boolean) {
        val fraction = scrollFraction()
        editMode = edit
        emptyView.visibility = View.GONE
        if (edit) {
            scroll.visibility = View.GONE
            editor.visibility = View.VISIBLE
            editor.requestFocus()
            editor.doOnPreDraw {
                val layout = editor.layout ?: return@doOnPreDraw
                val visible = editor.height - editor.totalPaddingTop - editor.totalPaddingBottom
                val y = (fraction * (layout.height - visible).coerceAtLeast(0)).toInt()
                editor.setSelection(layout.getLineStart(layout.getLineForVertical(y)))
            }
        } else {
            hideKeyboard()
            editor.visibility = View.GONE
            pendingFraction = fraction
            render()
        }
        updateTitle()
        invalidateOptionsMenu()
    }

    private fun showViewer(scrollY: Int) {
        editMode = false
        editor.visibility = View.GONE
        pendingScrollY = scrollY
        render()
        updateTitle()
    }

    private fun scrollFraction(): Float = if (editor.visibility == View.VISIBLE) {
        val layout = editor.layout
        val visible = editor.height - editor.totalPaddingTop - editor.totalPaddingBottom
        val max = (layout?.height ?: 0) - visible
        if (max > 0) editor.scrollY.toFloat() / max else 0f
    } else {
        val max = textView.height - scroll.height
        if (max > 0) scroll.scrollY.toFloat() / max else 0f
    }

    private fun render() {
        if (!hasDocument || editMode) return
        val text = editor.text.toString()
        emptyView.visibility = View.GONE
        scroll.visibility = View.VISIBLE
        if (showSource) {
            textView.typeface = Typeface.MONOSPACE
            textView.text = text
        } else {
            textView.typeface = Typeface.DEFAULT
            markwon.setMarkdown(textView, text)
        }
        val y = pendingScrollY
        val f = pendingFraction
        pendingScrollY = 0
        pendingFraction = null
        scroll.doOnPreDraw {
            val max = (textView.height - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, f?.let { (it * max).toInt() } ?: y)
        }
    }

    private fun showMessage(message: String) {
        scroll.visibility = View.GONE
        editor.visibility = View.GONE
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        updateTitle()
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(editor.windowToken, 0)
    }

    private fun changeTextSize(delta: Float) {
        val sp = (prefs.getFloat(KEY_SIZE, 16f) + delta).coerceIn(10f, 40f)
        prefs.edit().putFloat(KEY_SIZE, sp).apply()
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (!editMode) {
            pendingScrollY = scroll.scrollY
            render()
        }
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
        return uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.unbenannt)
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
        private const val KEY_URI = "uri"
        private const val KEY_SOURCE = "quelltext"
        private const val KEY_SCROLL = "scroll"
        private const val KEY_EDIT = "bearbeiten"
        private const val KEY_HAS_DOC = "dokument"
        private const val KEY_DRAFT = "entwurf"
        private const val KEY_NAME = "name"
        private const val KEY_CHARSET = "zeichensatz"
        private const val KEY_BOM = "bom"
        private const val KEY_CRLF = "crlf"
        private const val DRAFT_FILE = "entwurf.txt"
        private const val MAX_MB = 10
    }
}
