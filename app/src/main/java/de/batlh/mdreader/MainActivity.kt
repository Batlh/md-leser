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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var scroll: NestedScrollView
    private lateinit var textView: TextView
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var markwon: Markwon

    private val prefs by lazy { getSharedPreferences("einstellungen", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentUri: Uri? = null
    private var currentText: String? = null
    private var showSource = false
    private var pendingScrollY = 0
    private var loadToken = 0

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                // Dauerhaftes Leserecht, damit die Datei beim nächsten Start wieder aufgeht
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    prefs.edit().putString(KEY_LAST, uri.toString()).apply()
                } catch (_: SecurityException) {
                }
                load(uri, 0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        scroll = findViewById(R.id.scroll)
        textView = findViewById(R.id.text)
        emptyView = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        setSupportActionBar(toolbar)

        // Inhalt nicht unter Statusleiste und Navigationsleiste legen
        scroll.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, 0)
            scroll.setPadding(0, 0, 0, bars.bottom)
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

        textView.textSize = prefs.getFloat(KEY_SIZE, 16f)
        textView.setTextIsSelectable(true)
        textView.movementMethod = LinkMovementMethod.getInstance()

        showSource = savedInstanceState?.getBoolean(KEY_SOURCE) ?: false
        val savedUri = savedInstanceState?.getString(KEY_URI)?.let(Uri::parse)
        val viewUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val lastUri = lastOpened()
        when {
            savedUri != null -> load(savedUri, savedInstanceState?.getInt(KEY_SCROLL) ?: 0)
            viewUri != null -> load(viewUri, 0)
            lastUri != null -> load(lastUri, 0)
            else -> showMessage(getString(R.string.leer))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) load(uri, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URI, currentUri?.toString())
        outState.putBoolean(KEY_SOURCE, showSource)
        outState.putInt(KEY_SCROLL, scroll.scrollY)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.quelltext)?.apply {
            isChecked = showSource
            isEnabled = currentText != null
        }
        menu.findItem(R.id.neu_laden)?.isEnabled = currentUri != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.oeffnen -> {
            openDocument.launch(arrayOf("text/*", "application/octet-stream"))
            true
        }
        R.id.quelltext -> {
            showSource = !showSource
            invalidateOptionsMenu()
            render()
            true
        }
        R.id.groesser -> {
            changeTextSize(2f)
            true
        }
        R.id.kleiner -> {
            changeTextSize(-2f)
            true
        }
        R.id.neu_laden -> {
            currentUri?.let { load(it, scroll.scrollY) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun lastOpened(): Uri? {
        val uri = prefs.getString(KEY_LAST, null)?.let(Uri::parse) ?: return null
        val allowed = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (allowed) uri else null
    }

    private fun load(uri: Uri, scrollY: Int) {
        currentUri = uri
        pendingScrollY = scrollY
        val token = ++loadToken
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        executor.execute {
            val result = runCatching {
                val name = displayName(uri)
                val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it) }
                    ?: throw IllegalStateException("Kein Zugriff auf die Datei")
                name to String(bytes, Charsets.UTF_8).removePrefix("﻿")
            }
            mainHandler.post {
                if (token != loadToken || isDestroyed) return@post
                progress.visibility = View.GONE
                result.onSuccess { (name, text) ->
                    supportActionBar?.title = name
                    currentText = text
                    render()
                }.onFailure { e ->
                    currentText = null
                    showMessage(
                        if (e is TooLarge) getString(R.string.zu_gross, MAX_MB)
                        else getString(R.string.fehler, e.message ?: e.javaClass.simpleName)
                    )
                }
                invalidateOptionsMenu()
            }
        }
    }

    private fun render() {
        val text = currentText ?: return
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
        pendingScrollY = 0
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun showMessage(message: String) {
        scroll.visibility = View.GONE
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        if (currentText == null && currentUri == null) supportActionBar?.title = getString(R.string.app_name)
    }

    private fun changeTextSize(delta: Float) {
        val sp = (prefs.getFloat(KEY_SIZE, 16f) + delta).coerceIn(10f, 40f)
        prefs.edit().putFloat(KEY_SIZE, sp).apply()
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        pendingScrollY = scroll.scrollY
        render()
    }

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
        return uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.app_name)
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
        private const val MAX_MB = 10
    }
}
