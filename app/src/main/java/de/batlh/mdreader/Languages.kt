package de.batlh.mdreader

import android.content.Context
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject

/**
 * Syntax-Hervorhebung über TextMate-Grammatiken (aus VS Code, MIT) in assets/textmate.
 * types.json ordnet Dateiendungen den Sprachen zu, wie das Menü „Sprache“ in Notepad++.
 */
object Languages {

    class Lang(val scope: String, val name: String, val extensions: List<String>, val filenames: List<String>)

    /** Themes: Name in assets/textmate/themes, Anzeigename, dunkel? */
    val THEMES = listOf(
        Triple("quietlight", "Hell (Quiet Light)", false),
        Triple("darcula", "Dunkel (Darcula)", true),
        Triple("abyss", "Dunkelblau (Abyss)", true),
        Triple("solarized_dark", "Solarized Dark", true),
    )

    /** Kommentarzeichen aus der language-configuration.json einer Sprache. */
    class Comments(val line: String?, val block: Pair<String, String>?)

    private var configPaths: Map<String, String> = emptyMap()
    private val commentCache = HashMap<String, Comments>()

    @Volatile
    private var ready = false
    var all: List<Lang> = emptyList()
        private set

    fun init(context: Context) = synchronized(this) {
        if (ready) return@synchronized
        val app = context.applicationContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(app.assets))
        val themes = ThemeRegistry.getInstance()
        // Fehler hier dürfen die App nicht abstürzen lassen, dann eben ohne Farben
        for ((file, _, dark) in THEMES) {
            try {
                val path = "textmate/themes/$file.json"
                val source = IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                )
                themes.loadTheme(ThemeModel(source, file).apply { setDark(dark) })
            } catch (_: Exception) {
            }
        }
        val grammarsOk = try {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            true
        } catch (_: Exception) {
            false
        }

        try {
            val defs = JSONObject(app.assets.open("textmate/languages.json").bufferedReader().use { it.readText() })
                .getJSONArray("languages")
            configPaths = (0 until defs.length()).mapNotNull {
                val o = defs.getJSONObject(it)
                o.optString("languageConfiguration").takeIf { p -> p.isNotEmpty() }?.let { p -> o.getString("scopeName") to p }
            }.toMap()
        } catch (_: Exception) {
        }

        val types = JSONObject(app.assets.open("textmate/types.json").bufferedReader().use { it.readText() })
        val list = ArrayList<Lang>()
        for (scope in types.keys()) {
            val o = types.getJSONObject(scope)
            val ext = o.optJSONArray("extensions")
            val names = o.optJSONArray("filenames")
            list += Lang(
                scope,
                o.getString("name"),
                (0 until (ext?.length() ?: 0)).map { ext!!.getString(it).lowercase() },
                (0 until (names?.length() ?: 0)).map { names!!.getString(it).lowercase() },
            )
        }
        all = if (grammarsOk) list.sortedBy { it.name.lowercase() } else emptyList()
        ready = true
    }

    fun comments(context: Context, scope: String?): Comments? {
        if (scope == null) return null
        synchronized(commentCache) {
            commentCache[scope]?.let { return it }
            val result = try {
                val path = configPaths[scope] ?: return null
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                val o = JSONObject(json).optJSONObject("comments")
                val line = when (val lc = o?.opt("lineComment")) {
                    is String -> lc
                    is JSONObject -> lc.optString("comment").takeIf { it.isNotEmpty() }
                    else -> null
                }
                val bc = o?.optJSONArray("blockComment")
                Comments(line, if (bc != null && bc.length() == 2) bc.getString(0) to bc.getString(1) else null)
            } catch (_: Exception) {
                Comments(null, null)
            }
            commentCache[scope] = result
            return result
        }
    }

    fun byScope(scope: String?): Lang? = all.firstOrNull { it.scope == scope }

    /** Sprache anhand des Dateinamens, null = normaler Text. */
    fun detect(fileName: String): Lang? {
        val lower = fileName.lowercase()
        all.firstOrNull { lower in it.filenames }?.let { return it }
        val dot = lower.lastIndexOf('.')
        if (dot < 0) return null
        val ext = lower.substring(dot)
        return all.firstOrNull { ext in it.extensions }
    }

    /** Sprache für den Editor. Tab-Taste: Tab-Zeichen oder Leerzeichen, wie eingestellt. */
    fun create(scope: String?, spaces: Boolean = false, tabSize: Int = 4): Language {
        val plain = object : EmptyLanguage() {
            override fun useTab() = !spaces
        }
        if (scope == null || byScope(scope) == null) return plain
        return try {
            TextMateLanguage.create(scope, false).apply {
                setTabSize(tabSize)
                useTab(!spaces)
            }
        } catch (_: Exception) {
            plain
        }
    }

    fun setTheme(name: String) {
        try {
            ThemeRegistry.getInstance().setTheme(name)
        } catch (_: Exception) {
        }
    }
}
