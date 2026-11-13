# Swiss World Kit — v1

Frei gestaltete Modelle für FlightTraining. Keine maßgetreuen Nachbauten realer Schweizer Flughäfen. Erstes zusammenpassendes Set in einem vereinfachten realistischen Stil; kein fotorealistisches Architekturpaket.

## Inhalt

- Hangar 36 × 30 m, offener mittlerer Torbereich, Dachbinder, seitliche Torfelder.
- Kontrollturm, etwa 23 m hoch, Kanzel, Umgang und Antenne.
- Regionalterminal, etwa 32 × 14 m, Glasfront, Vordach und Dachaggregate.
- Verputztes Wohnhaus, Chalet mit Balkon, dreigeschossiges Wohngebäude.
- Fichte und Laubbaum.
- Gerades Startbahnsegment 45 × 100 m und Rollwegsegment 18 × 50 m.
- Zaunelement 10 m und Vorfeldleuchte 8 m.

`SwissWorldKit.blend` enthält die editierbaren LOD0-Modelle als Übersicht. Kamera, Licht und Boden dienen nur der Vorschau. Die einzelnen GLBs enthalten ausschließlich das jeweilige Modell, einschließlich Materialien und eingebetteter Farbtexturen.

`manifest.json` enthält Abmessungen und Dreieckszahlen für jede Datei. `Previews` enthält Übersicht und Einzelansichten.

## Maßstab und Ausrichtung

- 1 Einheit = 1 Meter. Beim Import zunächst **keine zusätzliche Skalierung** verwenden.
- Ursprung: Bodenmitte. Der Ursprung der Straßensegmente liegt auf der Oberfläche; der Unterbau reicht 0,3 m nach unten.
- Blender: Z nach oben, Vorderseite nach -Y. GLB / jME: Y nach oben, Vorderseite nach +Z.
- Die Anordnung in der Blender-Übersicht dient nur der Betrachtung. Die GLBs sind jeweils am Ursprung exportiert.
- Gebäudemaße schließen Dachüberstände mit ein; die Nominalmaße in Dateinamen sind keine Vermessungsdaten.

## Verwendung in jME

Den enthaltenen Ordner `Models` z. B. nach `game/src/main/resources/Models/WorldKit` kopieren. Dann:

```java
Spatial hangar = assetManager.loadModel(
        "Models/WorldKit/Hangar_36x30_LOD0.glb"
);
hangar.setLocalTranslation(worldX, groundHeight, worldZ);
rootNode.attachChild(hangar);
```

Die Farbmaterialien verwenden PBR. Beleuchtung und eine geeignete Umgebungsbeleuchtung sind für eine gute Darstellung erforderlich. Die Glasflächen sind bewusst getönte, undurchsichtige Flächen für einfache Engine-Kompatibilität; es gibt keinen ausgearbeiteten Cockpit-/Innenraum hinter den Gebäudefenstern. Die Vorfeldleuchte hat ein emissives Material, erzeugt aber **keine echte Spielweltbeleuchtung**. Ergänze dazu bei Bedarf einen jME-Lichtknoten.

## LODs und Streaming

LOD0, LOD1 und LOD2 werden als **separate GLBs** geliefert. Sie schalten sich nicht automatisch um; wähle beim Laden oder beim Entfernungswechsel genau eine Variante. Das Streaming bleibt bewusst bei dir.

- Gebäude: erste Versuchsbereiche LOD0 unter 300 m, LOD1 bis 1.000 m, LOD2 darüber.
- Bäume: deutlich kürzere Entfernungen verwenden und später Instancing oder vereinfachte Waldflächen ergänzen.
- Übergänge mit Hysterese versehen, damit Modelle an einer Grenzentfernung nicht ständig wechseln.
- Die Straßen-LOD-Dateien sind bewusst gleich detailliert: Sie haben sehr wenige Dreiecke und behalten so ihre Markierungen.
- LOD1/2 anderer Modelle sind geometrisch vereinfacht; nur aus entsprechender Entfernung verwenden. Keine automatische LOD-Steuerung und keine Impostor-Bäume enthalten.
- Modelle und Materialien wiederverwenden, nicht jedes Exemplar erneut von Platte laden. Material-/Mesh-Primitives verursachen mehrere Draw Calls; vor massenhafter Platzierung profilieren.

## Terrain und Kollisionen

Keine Kollisionsgeometrien oder Physik-Steuerungen enthalten. Für Gebäude zuerst einfache Boxen verwenden. Beim Hangar für die offene Einfahrt getrennte Wand-/Dachkollisionen vorsehen. Bäume können optional einen Stammzylinder erhalten.

Startbahn und Rollweg sind **ebene, modulare Segmente**, noch kein vollständiger Flughafen: keine Schwellen, Bahnnummern, Kreuzungen, Taxiway-Kurven, funktionsfähige Beleuchtung oder georeferenzierte Flughafenanordnung. Vor dem Platzieren muss das Terrain passend vorbereitet werden, damit nichts durch die Bahn ragt oder unter ihr schwebt.

Die Baumformen sind statisch, ohne Windanimation oder Blatt-Alpha-Karten. Bisher gibt es je eine Baumform; Rotation und dezente Skalierungsvariation helfen gegen Wiederholungen.

## Prüfung

Alle 36 GLBs werden mit den lokal vorhandenen jME-3.10.0-beta3-Bibliotheken geladen und auf vorhandene Geometrie/Materialien geprüft. Das ersetzt keinen Leistungstest mit tausenden Instanzen in deinem Spiel. Prüfergebnis und GLB-Strukturprüfung stehen in `Validation`.

## Nutzung

Die für dieses Paket neu erzeugten Geometrien und Texturen kannst du in deinem Spiel verändern, weitergeben und kommerziell nutzen. Keine fremden Bildtexturen oder heruntergeladenen Modelle enthalten. Siehe `LICENSE.txt`.
