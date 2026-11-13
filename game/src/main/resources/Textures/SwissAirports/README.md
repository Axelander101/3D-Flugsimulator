# Schweizer Flughäfen — Geodaten-Grundmodelle

Dieses Paket bildet die elf ausgewählten Flughäfen auf Basis öffentlicher Geodaten ab. Es ist eine **georeferenzierte Modellgrundlage**, kein fertiges fotorealistisches Add-on und kein Satz originaler CAD-Dateien der Flughafenbetreiber.

## Daten und Qualität

Die Meshes wurden beim Export bereinigt: zusammenfallende Punkte bis 1,5 cm verschweißt, doppelte Flächen entfernt, Normalen vereinheitlicht und nahezu koplanare Dreiecke bis 1° zusammengeführt. Das reduziert Schattierungsartefakte; es ist keine neue Vermessung.

- Schweizer Gebäude: reale Geometrie aus swissBUILDINGS3D, einschließlich der dort erfassten Dachformen. Die Daten können älter sein als der Downloadtag; Objekt-Metadaten und Quellen sind im Paket dokumentiert.
- Dachfarben und Dachtexturen: SWISSIMAGE-Luftbilder für die zehn Schweizer Standorte, IGN BD ORTHO für Basel-Mulhouse. Schatten und Verdeckungen können in der Textur sichtbar sein. Fassaden sind neutral eingefärbt und **nicht fotografisch rekonstruiert**.
- Flughafenflächen, Startbahnen, Rollwege und Vorfelder: OpenStreetMap-Geometrie. Fehlende Breiten werden geschätzt und in `objects.json` als `estimated_dimensions` markiert.
- Basel-Mulhouse: Gebäude aus der französischen IGN BD TOPO, mit verfügbaren Dach-/Höhenattributen. Die Ausgangshöhen NGF-IGN69 wurden über EVRF2000 nach LN02 umgerechnet. Bei 16 Objekten fehlen nötige Höhenattribute; diese Fälle sind als geschätzt markiert. Auch IGN liefert hier keine ausgearbeitete Fassadengestaltung. Dachtexturen und Vorschau-Luftbild stammen von IGN BD ORTHO.
- Bodenflächen: aus realen Grundrissen trianguliert. Höhen sind näherungsweise aus verfügbaren Bahn-Endpunkthöhen interpoliert oder auf die nominelle Flugplatzhöhe gesetzt. Das ist **kein vermessenes, terrainangepasstes Rollwegenetz**. Vor dem Einsatz muss die Terrain-Anpassung geprüft werden.
- Nicht enthalten: ausgearbeitete Innenräume, fotografisch rekonstruierte Fassaden, exakte Beschilderung und Lichtsysteme, animierte Jetways, bewegliche Tore, Personen/Fahrzeuge, Kollisionen oder automatisch schaltende LODs.

Aus diesen Daten lässt sich keine durchgehend zentimetergenaue oder fotorealistische Flughafenszenerie ableiten. Die weitere Detaillierung muss pro Standort anhand von freigegebenen Fotos und Plänen erfolgen. Unbekannte Details wurden nicht als vermessene Originale erfunden.

## Ordner je Flughafen

- `Models/ICAO_Airport.glb`: Gesamtmodell zur Kontrolle und zum ersten Import.
- `Chunks/*.glb`: räumlich gruppierte Teile für dein eigenes Streaming. Gebäudeteile werden vollständig dem Bereich ihres Ursprungs zugeordnet; ihre Geometrie kann die nominelle 500-m-Zelle überschreiten. Verwende die tatsächlichen Modell-Bounds zum Sichtbarkeitstest.
- `placement.json`: Ursprung, jME-Weltposition und relative Chunk-Positionen.
- `objects.json`: Objektquellen, Lage und dokumentierte Schätzwerte.
- `ICAO.blend`: editierbare Szene. Die flache Luftbildfläche ist **nur eine Referenz in Blender** und wurde nicht mit in die GLBs exportiert.
- `Overview.png`: Blender-Vorschau; kein Screenshot aus dem Spiel. Die Luftbildfläche ist flach und stellt nicht die Terrainintegration dar.

## Koordinaten

1 Einheit = 1 Meter. Keine zusätzliche Skalierung anwenden.

Die GLBs sind Y-up: X nach Osten, Z nach Süden. Der vollständige Flughafen hat lokale Koordinaten um seinen Referenzpunkt. `placement.json` enthält dessen Lage in LV95 sowie die Referenzhöhe. Die Weltpositionen sind für deinen bisherigen Ursprung E=2621500 / N=1092500 berechnet.

Das Feld `worldTranslation_jme` enthält bereits die richtige Flughafentranslation einschließlich Höhe. Addiere **nicht noch einmal** die Geländehöhe. Sonst stehen die Gebäude doppelt zu hoch.

Die Höhen des verwendeten swisstopo-3D-Dienstes wurden stichprobenartig gegen den offiziellen Terrain-Höhendienst geprüft. Es wird keine zweite Geoidkorrektur auf die bereits passenden cartographischen Höhen angewendet.

## jME: Gesamtmodell importieren

Kopiere den gewünschten Flughafenordner z. B. unter den Ressourcenpfad `Models/Airports/LSMP`.

```java
Spatial airport = assetManager.loadModel(
        "Models/Airports/LSMP/Models/LSMP_Airport.glb"
);

// Die drei Werte aus LSMP/placement.json: worldTranslation_jme.
airport.setLocalTranslation(worldX, referenceHeight, worldZ);
rootNode.attachChild(airport);
```

**Entweder** das Gesamtmodell **oder** die Chunks anzeigen, nicht beides gleichzeitig.

Für Chunks: Erstelle einen gemeinsamen Flughafen-Node an `worldTranslation_jme`. Lade einen Chunk und setze dessen `localTranslation_jme` relativ zu diesem Node. Das Laden, Entladen und die Priorisierung implementierst du selbst. Die Zuordnung im Manifest ist nur die Aufteilung der Modelldateien, kein Streaming-System.

Bei Floating Origin die Weltverschiebung einmal vom Flughafen-Node abziehen. Die lokalen Chunk-Koordinaten bleiben gleich.

## Darstellung und Leistung

Die Modelle verwenden PBR-Materialien; gute Sonnen- und Umgebungsbeleuchtung sind erforderlich. Gesamtmodelle können viele Einzelmeshes und damit viele Draw Calls besitzen. Nach der visuellen Kontrolle im Spiel kannst du statische Geometrien je Chunk zusammenfassen oder batched laden. Keine Leistungszusage für die gleichzeitige Anzeige aller Flughäfen.

Die geometrischen Modelle aus swissBUILDINGS3D werden teilweise als „LoD2“ bezeichnet. Das ist eine **GIS-Detailklasse**, keine automatisch schaltende Spiele-LOD-Stufe. Dieses Paket enthält keine drei spielseitigen LOD-Versionen pro Flughafen.

## Quellenangaben im Spiel

Bitte die Quellenangaben aus `CREDITS.md` in die Spiel-Credits übernehmen. Dieses Paket verwendet echte Fremd-Geodaten und hat andere Nutzungsbedingungen als das zuvor frei erzeugte SwissWorldKit.
