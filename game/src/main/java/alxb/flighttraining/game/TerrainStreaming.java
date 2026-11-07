package alxb.flighttraining.game;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.terrain.geomipmap.TerrainQuad;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


final class TerrainStreaming implements AutoCloseable {
    private static final int DOWNLOAD_RADIUS = 5; // 11 × 11 inklusive Mitte
    private static final int DISPLAY_RADIUS = 3;  // 7 × 7 inklusive Mitte
    private static final long RETRY_DELAY = Duration.ofSeconds(30).toNanos();

    private final AssetManager assetManager;
    private final Node rootNode;
    private final TerrainDownloader downloader;
    private static final int PREPARATION_THREADS = 2;
    private final ExecutorService preparation = Executors.newFixedThreadPool(
            PREPARATION_THREADS, Thread.ofPlatform().daemon().name("terrain-prepare-", 0).factory()
    );
    private final Map<TerrainTile, TerrainQuad> loadedTiles = new HashMap<>();
    private final Map<TerrainTile, CompletableFuture<Path>> downloads = new HashMap<>();
    private final Map<TerrainTile, CompletableFuture<Path>> textures = new HashMap<>();
    private final Map<TerrainTile, Future<TerrainQuad>> prepared = new LinkedHashMap<>();
    private final Map<TerrainTile, Long> retryAt = new HashMap<>();
    private Set<TerrainTile> visibleTiles = Set.of();
    private TerrainTile lastCenter;
    private long nextDownloadCheck;
    private boolean closed;

    TerrainStreaming(AssetManager assetManager, Node rootNode) {
        this(assetManager, rootNode, new TerrainDownloader());
    }

    TerrainStreaming(AssetManager assetManager, Node rootNode, TerrainDownloader downloader) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        this.downloader = downloader;
    }

    float getGroundHeight(Vector3f position) {
        Vector2f location = new Vector2f(position.x, position.z);
        for (TerrainQuad tile : loadedTiles.values()) {
            float height = tile.getHeight(location);
            if (!Float.isNaN(height)) {
                return height;
            }
        }
        return Float.NaN;
    }

    static Set<TerrainTile> window(TerrainTile center, int radius) {
        // Ringweise anfordern, damit die Kacheln unter dem Flugzeug zuerst geladen werden.
        Set<TerrainTile> result = new LinkedHashSet<>();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        result.add(center.offset(dx, dz));
                    }
                }
            }
        }
        return result;
    }

    void update(Vector3f position) {
        if (closed) {
            return;
        }
        TerrainTile center = TerrainTile.at(position.x, position.z);
        if (!center.equals(lastCenter)) {
            changeWindow(center);
        }
        if (System.nanoTime() >= nextDownloadCheck) {
            // Fehlgeschlagene Downloads nach der Wartezeit erneut versuchen.
            for (TerrainTile tile : downloads.keySet()) {
                requestTile(tile);
            }
            nextDownloadCheck = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        }

        // Auch ohne Kachelwechsel fertig gewordene Arbeit übernehmen.
        attachCompletedTile();
        prepareNextTile();
    }

    private void changeWindow(TerrainTile center) {
        lastCenter = center;
        visibleTiles = window(center, DISPLAY_RADIUS);
        Set<TerrainTile> wantedDownloads = window(center, DOWNLOAD_RADIUS);
        for (TerrainTile tile : new ArrayList<>(loadedTiles.keySet())) {
            if (!visibleTiles.contains(tile)) {
                loadedTiles.remove(tile).removeFromParent();
            }
        }
        for (TerrainTile tile : new ArrayList<>(prepared.keySet())) {
            if (!visibleTiles.contains(tile)) {
                prepared.remove(tile).cancel(true);
            }
        }
        downloads.keySet().retainAll(wantedDownloads);
        textures.keySet().retainAll(wantedDownloads);
        retryAt.keySet().retainAll(visibleTiles);
        downloader.retainTiles(wantedDownloads);
        for (TerrainTile tile : wantedDownloads) {
            requestTile(tile);
        }
        nextDownloadCheck = System.nanoTime() + Duration.ofSeconds(1).toNanos();
    }

    private void requestTile(TerrainTile tile) {
        downloads.put(tile, downloader.downloadTile(tile.x(), tile.z()));
        textures.put(tile, downloader.downloadTextureTile(tile.x(), tile.z()));
    }

    private void attachCompletedTile() {
        for (var entry : prepared.entrySet()) {
            Future<TerrainQuad> future = entry.getValue();
            if (!future.isDone()) {
                continue;
            }
            TerrainTile coordinate = entry.getKey();
            try {
                TerrainQuad tile = future.get();
                if (visibleTiles.contains(coordinate)) {
                    loadedTiles.put(coordinate, tile);
                    rootNode.attachChild(tile);
                }
            } catch (ExecutionException e) {
                System.err.println("Terrain konnte nicht vorbereitet werden: "
                        + coordinate + ": " + e.getCause().getMessage());
                retryAt.put(coordinate, System.nanoTime() + RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (CancellationException e) {
                // Abgebrochene Vorbereitungen werden bei Bedarf erneut eingeplant.
            }
            prepared.remove(coordinate);
            // Höchstens eine neue Kachel pro Frame einhängen.
            return;
        }
    }

    private void prepareNextTile() {
        for (TerrainTile tile : visibleTiles) {
            if (prepared.size() >= PREPARATION_THREADS) {
                return;
            }
            if (loadedTiles.containsKey(tile) || prepared.containsKey(tile)
                    || System.nanoTime() < retryAt.getOrDefault(tile, 0L)) {
                continue;
            }
            CompletableFuture<Path> texture = textures.get(tile);
            if (texture == null || !texture.isDone() || texture.isCompletedExceptionally()) {
                continue;
            }
            Map<TerrainTile, Path> paths = completedNeighborhood(tile);
            if (paths == null || !paths.containsKey(tile)) {
                continue;
            }
            Path texturePath = texture.getNow(null);
            prepared.put(tile, preparation.submit(() -> buildTile(tile, paths, texturePath)));
        }
    }

    private Map<TerrainTile, Path> completedNeighborhood(TerrainTile center) {
        Map<TerrainTile, Path> result = new HashMap<>();
        // Gemeinsame Randstützpunkte aus beiden Nachbarkacheln interpolieren.
        for (TerrainTile tile : window(center, 1)) {
            CompletableFuture<Path> future = downloads.get(tile);
            if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
                return null;
            }
            Path path = future.getNow(null);
            if (path != null) {
                result.put(tile, path);
            }
        }
        return result;
    }

    private TerrainQuad buildTile(TerrainTile coordinate, Map<TerrainTile, Path> paths,
                                  Path texturePath) throws IOException {
        float[] heights = TerrainHeightLoader.load(coordinate, paths);
        TerrainQuad tile = new TerrainQuad(
                "Terrain_" + coordinate.x() + "_" + coordinate.z(), 65, 513, heights
        );
        float spacing = TerrainTile.SIZE / 512f;
        tile.setLocalScale(spacing, 1f, spacing);
        tile.setLocalTranslation(
                (coordinate.x() - TerrainTile.ORIGIN_X) * TerrainTile.SIZE,
                0,
                -(coordinate.z() - TerrainTile.ORIGIN_Z) * TerrainTile.SIZE
        );
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        if (texturePath != null) {
            material.setTexture("ColorMap", TerrainTextureLoader.load(texturePath));
        } else {
            try {
                material.setTexture("ColorMap", assetManager.loadTexture(
                        "TerrainData/texture_" + coordinate.x() + "-" + coordinate.z() + ".png"
                ));
            } catch (AssetNotFoundException e) {
                material.setColor("Color", ColorRGBA.Green);
            }
        }
        tile.setMaterial(material);
        return tile;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Future<TerrainQuad> future : prepared.values()) {
            future.cancel(true);
        }
        prepared.clear();
        preparation.shutdownNow();

        boolean interrupted = Thread.interrupted();
        try {
            boolean stopped = false;
            while (!stopped) {
                try {
                    stopped = preparation.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            downloader.close();
        } finally {
            for (TerrainQuad tile : loadedTiles.values()) {
                tile.removeFromParent();
            }
            loadedTiles.clear();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
