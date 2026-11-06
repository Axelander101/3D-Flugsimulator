package alxb.flighttraining.game;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Texture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;

import javax.imageio.ImageIO;

final class TerrainStreaming {
    private static final int STREAM_RADIUS = 3;
    private static final float TILE_SIZE = 1000f;

    private final AssetManager assetManager;
    private final Node rootNode;
    private final HashMap<String, TerrainQuad> loadedTiles = new HashMap<>();
    private int lastTileX = Integer.MIN_VALUE;
    private int lastTileZ = Integer.MIN_VALUE;

    TerrainStreaming(AssetManager assetManager, Node rootNode) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
    }

    float getGroundHeight(Vector3f position) {
        Vector2f location = new Vector2f(position.x, position.z);
        for (TerrainQuad tile : loadedTiles.values()) {
            float groundHeight = tile.getHeight(location);
            if (!Float.isNaN(groundHeight)) {
                return groundHeight;
            }
        }
        return Float.NaN;
    }

    private void loadTerrainTile(String fileName, int tileX, int tileZ) {
        String key = getTileKey(tileX, tileZ);

        String path = "TerrainData/" + fileName;
        float[] heights;

        try (var input = TerrainStreaming.class.getResourceAsStream("/" + path)) {
            if (input == null) {
                return;
            }

            var image = ImageIO.read(input);

            if (image == null || image.getWidth() != 513 || image.getHeight() != 513) {
                throw new IllegalStateException(
                        "Ungültige Höhenkarte: " + path
                );
            }

            heights = image.getRaster().getSamples(
                    0,
                    0,
                    513,
                    513,
                    0,
                    (float[]) null
            );

        } catch (IOException e) {
            throw new UncheckedIOException("Höhenkarte konnte nicht geladen werden: " + path, e);
        }

        TerrainQuad tile = new TerrainQuad(
                "Terrain_" + tileX + "_" + tileZ,
                65,
                513,
                heights
        );

        float spacing = TILE_SIZE / 512f;

        tile.setLocalScale(
                spacing,
                1f,
                spacing
        );

        float worldX = (tileX - 2621) * TILE_SIZE;

        float worldZ = -(tileZ - 1092) * TILE_SIZE;

        tile.setLocalTranslation(
                worldX,
                0,
                worldZ
        );

        Material terrainMat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );

        String textureFile = "TerrainData/texture_" + tileX + "-" + tileZ + ".png";

        try {
            Texture terrainTexture = assetManager.loadTexture(textureFile);

            terrainMat.setTexture(
                    "ColorMap",
                    terrainTexture
            );

        } catch (AssetNotFoundException e) {
            terrainMat.setColor(
                    "Color",
                    ColorRGBA.Green
            );
        }

        tile.setMaterial(terrainMat);

        loadedTiles.put(key, tile);

        rootNode.attachChild(tile);
    }

    private void unloadTile(String key) {
        TerrainQuad tile = loadedTiles.get(key);

        if (tile == null) {
            return;
        }

        tile.removeFromParent();

        loadedTiles.remove(key);
    }

    void update(Vector3f position) {
        int currentTileX = (int) Math.floor(position.x / TILE_SIZE);

        int currentTileZ = (int) Math.floor(position.z / TILE_SIZE);

        int centerTileX = 2621 + currentTileX;

        int centerTileZ = 1092 - currentTileZ;

        // Falls im gleichen tile, muss nichts gemacht werden
        if (centerTileX == lastTileX && centerTileZ == lastTileZ) {
            return;
        }

        lastTileX = centerTileX;
        lastTileZ = centerTileZ;

        HashSet<String> wantedTiles = new HashSet<>();

        // 7x7 Bereich
        for (int dx = -STREAM_RADIUS; dx <= STREAM_RADIUS; dx++) {
            for (int dz = -STREAM_RADIUS;
                 dz <= STREAM_RADIUS;
                 dz++) {
                int wantedTileX = centerTileX + dx;

                int wantedTileZ = centerTileZ + dz;

                String key = getTileKey(wantedTileX, wantedTileZ);

                wantedTiles.add(key);

                if (!loadedTiles.containsKey(key)) {
                    String fileName = "terrain_" + wantedTileX + "-" + wantedTileZ + ".png";

                    loadTerrainTile(
                            fileName,
                            wantedTileX,
                            wantedTileZ
                    );
                }
            }
        }

        // Kopie erzeugen, damit wir aus loadedTiles löschen dürfen
        HashSet<String> currentlyLoaded = new HashSet<>(loadedTiles.keySet());

        for (String key : currentlyLoaded) {
            if (!wantedTiles.contains(key)) {
                unloadTile(key);
            }
        }
    }

    private String getTileKey(int tileX, int tileZ) {
        return tileX + "_" + tileZ;
    }
}
