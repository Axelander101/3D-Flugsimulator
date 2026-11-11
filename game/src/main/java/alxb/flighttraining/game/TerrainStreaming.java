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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import com.jme3.bounding.BoundingBox;
import com.jme3.renderer.Camera;


final class TerrainStreaming implements AutoCloseable {
    private static final int DOWNLOAD_RADIUS = 5; // 11 × 11 inklusive Mitte
    private static final int DISPLAY_RADIUS = 3;  // 7 × 7 inklusive Mitte
    private static final int MAX_PENDING_HEIGHTS = 4;
    private static final int MAX_PENDING_TEXTURES = 2;
    private static final float MIN_TILE_HEIGHT = -500f;
    private static final float MAX_TILE_HEIGHT = 9000f;
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
    private final Map<TerrainTile, Future<Texture>> preparedTextures = new LinkedHashMap<>();
    private final Set<TerrainTile> finishedTextures = new HashSet<>();
    private final Map<TerrainTile, Long> retryAt = new HashMap<>();
    private Set<TerrainTile> visibleTiles = Set.of();
    private Set<TerrainTile> wantedDownloads = Set.of();
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

    String getGroundLoadStatus(Vector3f position) {
        TerrainTile tile = TerrainTile.at(position.x, position.z);
        CompletableFuture<Path> height = downloads.get(tile);
        String name = tile.x() + "-" + tile.z();

        if (height == null || isPending(height)) {
            return "Höhendaten werden geladen: " + name;
        }
        if (height.isCompletedExceptionally()) {
            return "Höhendownload fehlgeschlagen: " + name + ". Neuer Versuch folgt.";
        }
        if (completedPath(height) == null) {
            return "Keine Höhendaten für " + name + " (HTTP 404). Startposition prüfen.";
        }
        if (loadedTiles.containsKey(tile)) {
            return "Keine gültige Bodenhöhe am Startpunkt in Kachel " + name;
        }
        return "Startgelände wird vorbereitet: " + name;
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
        update(position, null);
    }

    void update(Vector3f position, Camera cam) {
        if (closed) {
            return;
        }
        TerrainTile center = TerrainTile.at(position.x, position.z);
        if (!center.equals(lastCenter)) {
            changeWindow(center);
        }

        List<TerrainTile> ordered = orderTiles(center, cam);
        requestNextDownloads(ordered);
        attachCompletedTile();
        applyCompletedTexture();
        // Ein fertiges Luftbild bekommt einen freien Vorbereitungsplatz,
        // bevor die nächste Geometrie denselben Platz belegt.
        prepareNextTexture(ordered);
        prepareNextTile(ordered);
    }

    private void changeWindow(TerrainTile center) {
        lastCenter = center;
        visibleTiles = window(center, DISPLAY_RADIUS);
        wantedDownloads = window(center, DOWNLOAD_RADIUS);
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
        for (TerrainTile tile : new ArrayList<>(preparedTextures.keySet())) {
            if (!visibleTiles.contains(tile)) {
                preparedTextures.remove(tile).cancel(true);
            }
        }
        finishedTextures.retainAll(visibleTiles);
        downloads.keySet().retainAll(wantedDownloads);
        textures.keySet().retainAll(wantedDownloads);
        retryAt.keySet().retainAll(visibleTiles);
        downloader.retainTiles(wantedDownloads);
    }

    private record TilePriority(TerrainTile tile, int group, float distanceSquared) {}

    private List<TerrainTile> orderTiles(TerrainTile center, Camera cam) {
        if (cam == null) {
            return new ArrayList<>(visibleTiles);
        }

        List<TilePriority> ordered = new ArrayList<>(visibleTiles.size());
        float halfSize = TerrainTile.SIZE * 0.5f;
        float halfHeight = (MAX_TILE_HEIGHT - MIN_TILE_HEIGHT) * 0.5f;
        float centerY = (MAX_TILE_HEIGHT + MIN_TILE_HEIGHT) * 0.5f;
        Vector3f cameraPosition = cam.getLocation();
        for (TerrainTile tile : visibleTiles) {
            Vector3f position = tilePosition(tile);
            int ring = Math.max(Math.abs(tile.x() - center.x()),
                    Math.abs(tile.z() - center.z()));
            int group = ring;
            if (ring > 1) {
                BoundingBox box = new BoundingBox(
                        new Vector3f(position.x, centerY, position.z),
                        halfSize, halfHeight, halfSize
                );
                group = isInView(cam, box) ? 2 : 3;
            }
            float dx = position.x - cameraPosition.x;
            float dz = position.z - cameraPosition.z;
            ordered.add(new TilePriority(tile, group, dx * dx + dz * dz));
        }
        ordered.sort(Comparator.comparingInt(TilePriority::group)
                .thenComparingDouble(TilePriority::distanceSquared));
        return ordered.stream().map(TilePriority::tile).toList();
    }

    private Vector3f tilePosition(TerrainTile tile) {
        return new Vector3f(
                (tile.x() - TerrainTile.ORIGIN_X) * TerrainTile.SIZE,
                0f,
                -(tile.z() - TerrainTile.ORIGIN_Z) * TerrainTile.SIZE
        );
    }

    private void requestNextDownloads(List<TerrainTile> ordered) {
        long now = System.nanoTime();
        boolean retry = nextDownloadCheck == 0 || now - nextDownloadCheck >= 0;
        if (retry) {
            nextDownloadCheck = now + TimeUnit.SECONDS.toNanos(1);
        }

        Set<TerrainTile> heights = new LinkedHashSet<>();
        for (TerrainTile tile : ordered) {
            heights.addAll(window(tile, 1));
        }
        heights.addAll(wantedDownloads);
        requestDownloads(heights, downloads, MAX_PENDING_HEIGHTS, retry,
                tile -> downloader.downloadTile(tile.x(), tile.z()));

        // Übertrage Luftbilder bereits während die Geometrie vorbereitet wird.
        List<TerrainTile> images = ordered.stream()
                .filter(tile -> completedPath(downloads.get(tile)) != null)
                .toList();
        requestDownloads(images, textures, MAX_PENDING_TEXTURES, retry,
                tile -> downloader.downloadTextureTile(tile.x(), tile.z()));
    }

    private void requestDownloads(Iterable<TerrainTile> candidates,
                                  Map<TerrainTile, CompletableFuture<Path>> requests,
                                  int limit, boolean retry,
                                  Function<TerrainTile, CompletableFuture<Path>> download) {
        long inFlight = requests.values().stream().filter(TerrainStreaming::isPending).count();
        for (TerrainTile tile : candidates) {
            if (inFlight >= limit) {
                break;
            }
            CompletableFuture<Path> current = requests.get(tile);
            if (current == null || (retry && current.isCompletedExceptionally())) {
                CompletableFuture<Path> next = download.apply(tile);
                requests.put(tile, next);
                if (isPending(next)) {
                    inFlight++;
                }
            }
        }
    }

    private static boolean isPending(CompletableFuture<Path> future) {
        return future != null && !future.isDone();
    }

    private static Path completedPath(CompletableFuture<Path> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }
        return future.getNow(null);
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

    private void prepareNextTile(List<TerrainTile> ordered) {
        for (TerrainTile tile : ordered) {
            if (prepared.size() + preparedTextures.size() >= PREPARATION_THREADS) {
                return;
            }
            if (loadedTiles.containsKey(tile) || prepared.containsKey(tile)
                    || (retryAt.containsKey(tile) && System.nanoTime() - retryAt.get(tile) < 0)) {
                continue;
            }
            Map<TerrainTile, Path> paths = completedNeighborhood(tile);
            if (paths != null) {
                prepared.put(tile, preparation.submit(() -> buildTile(tile, paths)));
            }
        }
    }

    private Map<TerrainTile, Path> completedNeighborhood(TerrainTile center) {
        Path centerPath = completedPath(downloads.get(center));
        if (centerPath == null) {
            return null;
        }

        Map<TerrainTile, Path> result = new HashMap<>();
        result.put(center, centerPath);
        for (TerrainTile tile : window(center, 1)) {
            if (tile.equals(center)) {
                continue;
            }
            CompletableFuture<Path> future = downloads.get(tile);
            if (future == null || isPending(future)) {
                return null;
            }
            // Fehlgeschlagene Nachbarhöhen am Rand aus der eigenen Tile ergänzen.
            Path path = completedPath(future);
            if (path != null) {
                result.put(tile, path);
            }
        }
        return result;
    }

    private void prepareNextTexture(List<TerrainTile> ordered) {
        if (!preparedTextures.isEmpty() || prepared.size() >= PREPARATION_THREADS) {
            return;
        }
        for (TerrainTile tile : ordered) {
            if (!loadedTiles.containsKey(tile) || finishedTextures.contains(tile)) {
                continue;
            }
            Path path = completedPath(textures.get(tile));
            if (path != null) {
                preparedTextures.put(tile, preparation.submit(() -> TerrainTextureLoader.load(path)));
                return;
            }
        }
    }

    private void applyCompletedTexture() {
        var iterator = preparedTextures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getValue().isDone()) {
                continue;
            }
            TerrainTile coordinate = entry.getKey();
            try {
                Texture texture = entry.getValue().get();
                TerrainQuad tile = loadedTiles.get(coordinate);
                if (tile != null) {
                    Material material = tile.getMaterial();
                    material.setTexture("ColorMap", texture);
                    material.clearParam("Color");
                }
                finishedTextures.add(coordinate);
            } catch (ExecutionException e) {
                System.err.println("Luftbild konnte nicht gelesen werden: "
                        + coordinate + ": " + e.getCause().getMessage());
                finishedTextures.add(coordinate);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (CancellationException e) {
                // Die Kachel hat den Anzeigebereich verlassen.
            }
            iterator.remove();
        }
    }

    private TerrainQuad buildTile(TerrainTile coordinate, Map<TerrainTile, Path> paths)
            throws IOException {
        float[] heights = TerrainHeightLoader.load(coordinate, paths);
        TerrainQuad tile = new TerrainQuad(
                "Terrain_" + coordinate.x() + "_" + coordinate.z(), 65, 513, heights
        );
        float spacing = TerrainTile.SIZE / 512f;
        tile.setLocalScale(spacing, 1f, spacing);
        tile.setLocalTranslation(tilePosition(coordinate));
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        try {
            material.setTexture("ColorMap", assetManager.loadTexture(
                    "TerrainData/texture_" + coordinate.x() + "-" + coordinate.z() + ".png"
            ));
        } catch (AssetNotFoundException e) {
            material.setColor("Color", new ColorRGBA(0.36f, 0.40f, 0.34f, 1f));
        }
        tile.setMaterial(material);
        return tile;
    }


    private boolean isInView(Camera cam, BoundingBox bounds) {
        int savedPlaneState = cam.getPlaneState();

        try {
            cam.setPlaneState(0);
            return cam.contains(bounds) != Camera.FrustumIntersect.Outside;
        } finally {
            cam.setPlaneState(savedPlaneState);
        }


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
        for (Future<Texture> future : preparedTextures.values()) {
            future.cancel(true);
        }
        preparedTextures.clear();
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