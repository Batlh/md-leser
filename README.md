# MD-Leser

Android-App zum Lesen und Bearbeiten von Markdown- und Textdateien. Offline, ohne Werbung, ohne Tracking. Wird nach und nach zu einem Editor im Stil von Notepad++ ausgebaut.

## Was sie kann

- `.md`-Dateien über den Ordner-Knopf öffnen oder im Dateimanager mit **Öffnen mit → MD-Leser**
- Bearbeiten mit Rückgängig/Wiederholen, Speichern, Speichern unter, neue Datei
- Zeichensatz und Zeilenenden bleiben erhalten (UTF-8, UTF-8-BOM, ANSI, LF/CRLF)
- Tastenkürzel: Strg+S, Strg+Umschalt+S, Strg+O, Strg+N, Strg+Z, Strg+Y
- Überschriften, **fett**, *kursiv*, ~~durchgestrichen~~, Listen, Zitate, Codeblöcke
- Tabellen und Aufgabenlisten
- Links sind anklickbar, Text lässt sich markieren und kopieren
- Quelltext-Ansicht, Schriftgröße einstellbar
- Hell und dunkel, je nach Systemeinstellung
- Öffnet beim Start die zuletzt geöffnete Datei wieder

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

Eigener Code: MIT.

Verwendet:
- [Sora Editor](https://github.com/Rosemoe/sora-editor) (LGPL 2.1) als Editor-Komponente
- TextMate-Grammatiken aus [VS Code](https://github.com/microsoft/vscode) (MIT, siehe `app/src/main/assets/textmate/LICENSE-vscode.txt`)
- [Markwon](https://github.com/noties/Markwon) (Apache 2.0) für die Markdown-Ansicht
