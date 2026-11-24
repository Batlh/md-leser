# MD-Leser

Android-App zum Lesen und Bearbeiten von Markdown- und Textdateien. Offline, ohne Werbung, ohne Tracking. Wird nach und nach zu einem Editor im Stil von Notepad++ ausgebaut.

## Was sie kann

- **Lesen:** Markdown gerendert mit Tabellen, Aufgabenlisten und Links; Öffnen auch über „Öffnen mit“ aus dem Dateimanager
- **Bearbeiten** wie in Notepad++: Tabs, Sitzung samt ungespeicherter Texte, Zeilennummern, Syntax-Hervorhebung für rund 30 Sprachen
- **Suchen & Ersetzen:** Normal, Erweitert, Regex, in allen Tabs, Gehe zu Zeile
- **Zeilen & Text:** duplizieren, verschieben, sortieren, Doppelte entfernen, Groß/klein, Kommentar umschalten, Einzug, Lesezeichen
- **Kodierung:** UTF-8, UTF-8-BOM, ANSI, UTF-16, Windows-/Unix-Zeilenenden – bleiben beim Speichern erhalten
- **Makros** aufnehmen, abspielen, speichern
- **Einstellungen:** Farbschemata, Tabbreite, Einrücken, Wortvervollständigung …
- Tastenkürzel von Notepad++ mit angeschlossener Tastatur

Was genau aus Notepad++ vorhanden ist und was (noch) fehlt: [FUNKTIONEN.md](FUNKTIONEN.md)

| Spalte A | Spalte B |
|----------|----------|
| Tabellen | gehen    |

- [x] Aufgabenliste
- [ ] noch offen

## Installieren

Die fertige APK liegt unter [Releases](../../releases). Herunterladen, öffnen und die Installation aus unbekannten Quellen erlauben.

## Bauen

Jeder Push auf `main` baut die APK über GitHub Actions. Mit dem Secret `ANDROID_KEY_PASSWORD` wird sie mit dem eigenen Schlüssel signiert, sonst mit dem Ersatzschlüssel aus `ci/debug.keystore`.

Lokal: `./gradlew assembleRelease` (Android SDK nötig).

## Lizenz

Eigener Code: MIT. Die Funktionen sind Notepad++ nachempfunden, Code aus Notepad++ ist nicht enthalten.

Verwendete Fremdsoftware:
- [Sora Editor](https://github.com/Rosemoe/sora-editor) (LGPL 2.1), unverändert eingebunden, als Editor-Komponente
- [tm4e](https://github.com/eclipse-tm4e/tm4e) (EPL 2.0), steckt in Sora und sorgt für die Syntax-Hervorhebung
- TextMate-Grammatiken und Farbschemata aus [VS Code](https://github.com/microsoft/vscode) (MIT), Kotlin-Grammatik aus [vscode-kotlin](https://github.com/fwcd/vscode-kotlin) (MIT)
- [Markwon](https://github.com/noties/Markwon) (Apache 2.0) mit [commonmark-java](https://github.com/commonmark/commonmark-java) (BSD 2-Clause) für die Markdown-Ansicht
- [Gson](https://github.com/google/gson) und [SnakeYAML Engine](https://github.com/snakeyaml/snakeyaml-engine) (Apache 2.0), [LuaJ](https://github.com/luaj/luaj) (MIT) für „Syntax prüfen“
- [Joni/JCodings](https://github.com/jruby/joni) (MIT), AndroidX, Material Components, Kotlin (Apache 2.0)

Alle Lizenztexte liegen in `app/src/main/assets/licenses/` und sind in der App unter **⋮ → Über & Lizenzen** zu finden.
