# MD-Leser

Kleine Android-App zum Lesen von Markdown-Dateien (`.md`). Offline, ohne Werbung, ohne Tracking.

## Was sie kann

- `.md`-Dateien über den Ordner-Knopf öffnen oder im Dateimanager mit **Öffnen mit → MD-Leser**
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

MIT. Markdown-Darstellung mit [Markwon](https://github.com/noties/Markwon) (Apache 2.0).
