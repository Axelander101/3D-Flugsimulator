# FT-IR — Spielrakete

Frei gestaltetes, Sidewinder-inspiriertes Modell für dein Spiel. Kein maßgetreuer Nachbau einer bestimmten Variante, keine technische Funktionsvorlage.

## Dateien

- FT_IR_Missile.blend: editierbares LOD0-Modell und Vorschauumgebung.
- FT_IR_Missile_LOD0.glb: 3.310 Dreiecke.
- FT_IR_Missile_LOD1.glb: 1.678 Dreiecke.
- FT_IR_Missile_LOD2.glb: 686 Dreiecke.
- Alle GLBs enthalten Materialien; keine zusätzlichen Texturdateien erforderlich.

Länge 3,06 m, maximale Spannweite 0,58 m. 1 Einheit = 1 Meter. Ursprung im Rumpfzentrum. Blender-Vorderseite -Y, glTF/jME-Vorderseite +Z. Alle LODs besitzen den leeren Knoten ExhaustSocket am Heck. Keine Kollisionen, Flugsteuerung, Suchlogik oder Partikeleffekte enthalten; diese kommen aus deinem Java-Code. Die einzelnen LODs schalten nicht selbst um.

## jME

Die GLBs z. B. nach `game/src/main/resources/Models/Weapons/` kopieren:

```java
Spatial missileModel = assetManager.loadModel(
        "Models/Weapons/FT_IR_Missile_LOD0.glb"
);
rootNode.attachChild(missileModel);
```

Deine bisherige InfraredMissile-Klasse bewegt das Modell nur und richtet es noch nicht aus. Nach der Aktualisierung von `direction` kannst du dort ergänzen:

```java
Vector3f up = Math.abs(direction.dot(Vector3f.UNIT_Y)) > 0.99f
        ? Vector3f.UNIT_X
        : Vector3f.UNIT_Y;
model.setLocalRotation(new Quaternion().lookAt(direction, up));
```

Damit zeigt die Modellnase (+Z) in Flugrichtung. Keine zusätzliche pauschale 180°-Drehung anwenden. Das Beispiel setzt wie deine bisherige Klasse ein direkt unter rootNode platziertes Modell und Weltkoordinaten voraus.

Die Vorschau wurde mit Studiolichtern gerendert. PBR-Materialien benötigen auch im Spiel passende Beleuchtung.

## Nutzung

Neu erzeugte Geometrie und Materialien: frei in deinem Spiel veränderbar, nutzbar und weitergebbar, auch kommerziell; CC0 1.0, soweit rechtlich möglich. Keine fremden Modelle/Texturen verwendet. Keine Verbindung zu Herstellern oder Betreibern.
https://creativecommons.org/publicdomain/zero/1.0/
