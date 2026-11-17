# Funktionsabgleich mit Notepad++

Grundlage: das Hauptmenü von Notepad++ (`PowerEditor/src/Notepad_plus.rc`, Stand November 2026).
Übernommen wurde das Verhalten, kein Code. Deshalb steht der MD-Leser weiter unter MIT.

✅ vorhanden · ◐ teilweise · ✗ fehlt

## Datei

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Neu, Öffnen, Speichern, Speichern unter, Alle speichern | ✅ | Strg+N, Strg+O, Strg+S, Strg+Alt+S, Strg+Umschalt+S |
| Neu laden | ✅ | |
| Schließen, Alle schließen, Alle anderen, links, rechts, unveränderte | ✅ | Langes Drücken auf einen Tab |
| Sitzung merken (inkl. ungespeicherter Texte) | ✅ | automatisch, wie mit Sicherung in Notepad++ |
| Sitzung laden/speichern unter Namen | ✗ | |
| Umbenennen, Kopie speichern, in Papierkorb | ✗ | Android-Dateizugriff erlaubt das nur eingeschränkt |
| Drucken | ✗ | |
| Ordner als Arbeitsbereich, Explorer/cmd öffnen | ✗ | gibt es auf Android so nicht |

## Bearbeiten

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Rückgängig, Wiederholen, Ausschneiden, Kopieren, Einfügen, Alles markieren | ✅ | |
| Datum/Uhrzeit einfügen (kurz, lang) | ✅ | eigenes Format ✗ |
| Dateiname / Pfad kopieren | ✅ | Ordnerpfad ✗ |
| Einzug vergrößern/verkleinern | ✅ | |
| Groß/klein: GROSS, klein, Jedes Wort, Satzanfang, umkehren | ✅ | Strg+Umschalt+U, Strg+U; „blend“ und „zufällig“ ✗ |
| Zeile duplizieren, löschen, nach oben/unten, verbinden | ✅ | Strg+D, Strg+Umschalt+L, Strg+Umschalt+↑/↓, Strg+J |
| Leerzeile darüber/darunter | ✅ | |
| Doppelte entfernen, direkt aufeinanderfolgende Doppelte entfernen | ✅ | |
| Leere Zeilen entfernen | ✅ | entfernt auch Zeilen nur mit Leerzeichen |
| Zeilen umkehren, zufällig | ✅ | |
| Sortieren A→Z, Z→A, ohne Groß/klein, Zahl, Länge | ✅ | Gebietsschema- und Dezimal-Varianten ✗ |
| Zeilen teilen | ✗ | |
| Kommentar ein/aus (Zeile, Block) | ✅ | Strg+Q, Zeichen je Sprache |
| Leerzeichen am Anfang/Ende/beides entfernen, Zeilenende zu Leerzeichen | ✅ | |
| Tab → Leerzeichen, Leerzeichen → Tab | ✅ | nur führende Leerzeichen → Tab |
| Zeilenende umwandeln Windows / Unix | ✅ | Macintosh (CR) ✗ |
| Wortvervollständigung | ✅ | in den Einstellungen einschaltbar; Funktionsparameter-Hinweise ✗ |
| Schreibgeschützt (in der App) | ✅ | |
| Spaltenmodus, Mehrfachauswahl, Spalteneditor | ✗ | kann die Editor-Komponente nicht |
| Zeichentabelle, Zwischenablage-Verlauf, Einfügen als HTML/RTF/binär | ✗ | |

## Suchen

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Suchen, Ersetzen, Weitersuchen vor/zurück | ✅ | Strg+F, Strg+H, F3, Umschalt+F3 |
| Modi Normal, Erweitert, Regex; Groß/klein; ganzes Wort | ✅ | \1 und $1 im Ersetzen |
| Alle ersetzen, Zählen, in allen offenen Dateien | ✅ | |
| Gehe zu Zeile | ✅ | Strg+G |
| Lesezeichen setzen, nächstes, vorheriges, alle löschen | ✅ | Strg+F2, F2, Umschalt+F2, mit Liste |
| Lesezeichen-Zeilen kopieren/ausschneiden/entfernen, umkehren | ✗ | |
| In Dateien suchen | ✗ | |
| Markieren mit 5 Farben, Inkrementelle Suche, Suchergebnis-Fenster | ✗ | Treffer werden im Text hervorgehoben |
| Gehe zu passender Klammer | ✗ | Klammerpaare werden hervorgehoben |

## Ansicht

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Zeilenumbruch | ✅ | |
| Alle Zeichen anzeigen (Leerzeichen, Tabs, Zeilenenden) | ✅ | einzeln umschaltbar ✗ |
| Zoomen | ✅ | zwei Finger oder Schrift größer/kleiner |
| Zusammenfassung | ✅ | |
| Tabs wechseln, nächster/vorheriger | ✅ | Strg+Tab, Strg+Bild↑/↓ |
| Tabs verschieben, einfärben | ✗ | |
| Code falten, Zeilen ausblenden | ✗ | kann die Editor-Komponente nicht |
| Dokumentkarte, Funktionsliste, Projekte, Geteilte Ansicht | ✗ | |
| Ablenkungsfreier Modus, Vollbild, Post-it | ✗ | |
| Im Browser anzeigen | ◐ | Markdown hat eine eigene Leseansicht |
| Textrichtung RTL, Überwachen (tail -f) | ✗ | |

## Kodierung

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| ANSI, UTF-8, UTF-8-BOM, UTF-16 LE/BE BOM | ✅ | wird beim Öffnen erkannt und beim Speichern beibehalten |
| Umwandeln in diese Kodierungen | ✅ | |
| Weitere Zeichensätze (Kyrillisch, Japanisch …) | ✗ | |

## Sprache

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Hervorhebung nach Dateiendung, Sprache von Hand wählen | ✅ | rund 30 Sprachen (TextMate-Grammatiken aus VS Code) |
| Über 90 Sprachen | ◐ | häufige Sprachen sind dabei, Exoten wie COBOL, Fortran, Ada fehlen |
| Benutzerdefinierte Sprachen | ✗ | |

## Einstellungen

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Farbschema | ✅ | automatisch hell/dunkel und 4 Schemata |
| Tabbreite, Leerzeichen statt Tab, automatisch einrücken | ✅ | |
| Aktuelle Zeile hervorheben, Klammern schließen | ✅ | |
| Tastenkürzel ändern | ✗ | |

## Makro

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Aufnehmen, Abspielen, Mehrfach / bis Dateiende | ✅ | Strg+Umschalt+R, Strg+Umschalt+P |
| Makro speichern, gespeicherte ausführen | ✅ | |
| Aufgenommen wird | ◐ | Tippen, Löschen, Cursortasten einer Tastatur, Menübefehle; Bewegungen per Fingertipp nicht (in Notepad++ ebenso keine Mausklicks) |

## Ausführen, Plugins, Fenster

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| Ausführen, Plugins, Plugin-Verwaltung | ✗ | gibt es auf Android so nicht |
| Fensterliste | ◐ | Tabs |

## Prüfen (in Notepad++ über Plugins)

| Notepad++ | MD-Leser | Anmerkung |
|---|---|---|
| XML Tools: Syntax prüfen | ✅ | XML, XSL |
| JSON Viewer | ◐ | Syntax prüfen; Baumansicht ✗ |
| YAML, Lua prüfen | ✅ | Lua mit der Syntax von Lua 5.2, deckt WoW-Lua (5.1) ab |
| Compiler/Interpreter starten (NppExec) | ✗ | gibt es auf Android so nicht |

## Nur im MD-Leser

- Leseansicht für Markdown mit Tabellen, Aufgabenlisten und Links
- „Öffnen mit“ aus jedem Dateimanager
- Bedienung mit Touch und Tastatur
