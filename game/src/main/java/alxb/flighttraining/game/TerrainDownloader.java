package alxb.flighttraining.game;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Sechs Worker laden Höhendaten und Luftbilder in denselben Karten-Cache. */
public final class TerrainDownloader implements AutoCloseable {
    private static final int DATA_YEAR = 2024;
    private static final long RETRY_DELAY_NANOS = Duration.ofSeconds(30).toNanos();
    private static final Pattern CACHE_FILE = Pattern.compile(
            "(terrain|texture)_([0-9]+)-([0-9]+)[.](tif|png)"
    );

    private enum Kind { HEIGHT, TEXTURE }

    private record Resource(TerrainTile tile, Kind kind) {
        String fileName() {
            if (kind == Kind.HEIGHT) {
                return tile.fileName();
            }
            return "texture_" + tile.x() + "-" + tile.z() + ".png";
        }

        URI uri() {
            if (kind == Kind.HEIGHT) {
                return sourceUri(tile);
            }
            return textureUri(tile);
        }
    }

    private static final class Download {
        final CompletableFuture<Path> result = new CompletableFuture<>();
        FutureTask<Void> task;
        long retryAt;
    }

    private final HttpClient client;
    private final Path cacheDirectory;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
            6, 6, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            Thread.ofPlatform().daemon().name("map-download-", 0).factory()
    );
    private final HashMap<Resource, Download> downloads = new HashMap<>();
    private boolean closed;

    public TerrainDownloader() {
        this(Path.of("terrain-cache"), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    TerrainDownloader(Path cacheDirectory, HttpClient client) {
        this.cacheDirectory = cacheDirectory;
        this.client = client;
    }

    public CompletableFuture<Path> downloadTile(int tileX, int tileZ) {
        return request(new Resource(new TerrainTile(tileX, tileZ), Kind.HEIGHT));
    }

    public CompletableFuture<Path> downloadTextureTile(int tileX, int tileZ) {
        return request(new Resource(new TerrainTile(tileX, tileZ), Kind.TEXTURE));
    }

    private synchronized CompletableFuture<Path> request(Resource resource) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Downloader geschlossen"));
        }
        Download previous = downloads.get(resource);
        if (previous != null && (!previous.result.isCompletedExceptionally()
                || System.nanoTime() - previous.retryAt < 0)) {
            return previous.result;
        }
        Download download = new Download();
        download.task = new FutureTask<>(() -> {
            fetch(resource, download);
            return null;
        });
        downloads.put(resource, download);
        workers.execute(download.task);
        return download.result;
    }

    static URI sourceUri(TerrainTile tile) {
        String item = "swissalti3d_" + DATA_YEAR + "_" + tile.x() + "-" + tile.z();
        return URI.create("https://data.geo.admin.ch/ch.swisstopo.swissalti3d/"
                + item + "/" + item + "_2_2056_5728.tif");
    }

    private URI findHeightAsset(TerrainTile tile) throws IOException, InterruptedException {
        // Approximate LV95 to WGS84 at the centre of the 1 km tile.
        double e = (tile.x() * 1000.0 + 500.0 - 2600000.0) / 1000000.0;
        double n = (tile.z() * 1000.0 + 500.0 - 1200000.0) / 1000000.0;
        double lon = (2.6779094 + 4.728982 * e + 0.791484 * e * n
                + 0.1306 * e * n * n - 0.0436 * e * e * e) * 100.0 / 36.0;
        double lat = (16.9023892 + 3.238272 * n - 0.270978 * e * e
                - 0.002528 * n * n - 0.0447 * e * e * n - 0.0140 * n * n * n)
                * 100.0 / 36.0;
        String bbox = String.format(Locale.ROOT, "%.7f,%.7f,%.7f,%.7f",
                lon - 0.0001, lat - 0.0001, lon + 0.0001, lat + 0.0001);
        URI query = URI.create("https://data.geo.admin.ch/api/stac/v1/collections/"
                + "ch.swisstopo.swissalti3d/items?bbox=" + bbox + "&limit=100");
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(query).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            throw new IOException("Höhenkatalog antwortet mit HTTP " + response.statusCode());
        }

        Pattern fileName = Pattern.compile("swissalti3d_([0-9]{4})_"
                + tile.x() + "-" + tile.z() + "_2_2056_5728[.]tif");
        var features = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonArray("features");
        if (features == null) {
            throw new IOException("Höhenkatalog liefert keine Feature-Liste.");
        }
        URI newestAsset = null;
        int newestYear = 0;
        for (var feature : features) {
            JsonObject assets = feature.getAsJsonObject().getAsJsonObject("assets");
            if (assets == null) {
                continue;
            }
            for (var entry : assets.entrySet()) {
                var href = entry.getValue().getAsJsonObject().get("href");
                if (href == null || !href.isJsonPrimitive()) {
                    continue;
                }
                URI uri = URI.create(href.getAsString());
                String path = uri.getPath();
                if (!"https".equals(uri.getScheme())
                        || !"data.geo.admin.ch".equalsIgnoreCase(uri.getHost()) || path == null) {
                    continue;
                }
                var match = fileName.matcher(path.substring(path.lastIndexOf('/') + 1));
                if (match.matches()) {
                    int year = Integer.parseInt(match.group(1));
                    if (year > newestYear) {
                        newestYear = year;
                        newestAsset = uri;
                    }
                }
            }
        }
        return newestAsset;
    }

    private HttpResponse<Path> fetchFile(URI uri, Path temporary)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(temporary,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
    }

    static URI textureUri(TerrainTile tile) {
        int east = tile.x() * 1000;
        int north = tile.z() * 1000;
        return URI.create("https://wms.geo.admin.ch/?SERVICE=WMS&REQUEST=GetMap"
                + "&VERSION=1.3.0&LAYERS=ch.swisstopo.swissimage&STYLES=default"
                + "&CRS=EPSG:2056&BBOX=" + east + "," + north + ","
                + (east + 1000) + "," + (north + 1000)
                + "&WIDTH=1024&HEIGHT=1024&FORMAT=image/png");
    }

    private synchronized boolean isCurrent(Resource resource, Download download) {
        return !closed && downloads.get(resource) == download;
    }

    private void fetch(Resource resource, Download download) {
        Path temporary = null;
        try {
            if (!isCurrent(resource, download)) {
                return;
            }
            Files.createDirectories(cacheDirectory);
            Path destination = cacheDirectory.resolve(resource.fileName());
            if (Files.isRegularFile(destination)) {
                try {
                    validate(destination, resource.kind());
                    synchronized (this) {
                        if (isCurrent(resource, download)) {
                            download.result.complete(destination);
                        }
                    }
                    return;
                } catch (IOException e) {
                    synchronized (this) {
                        if (!isCurrent(resource, download)) {
                            return;
                        }
                        Files.deleteIfExists(destination);
                    }
                }
            }

            if (!isCurrent(resource, download)) {
                return;
            }
            URI uri = resource.uri();
            temporary = Files.createTempFile(cacheDirectory, resource.fileName() + "-", ".part");
            HttpResponse<Path> response = fetchFile(uri, temporary);
            if (response.statusCode() == 404 && resource.kind() == Kind.HEIGHT
                    && isCurrent(resource, download)) {
                URI catalogAsset = findHeightAsset(resource.tile());
                if (catalogAsset != null && isCurrent(resource, download)) {
                    uri = catalogAsset;
                    System.out.println("Höhenkachel aus swisstopo-Katalog: " + uri);
                    response = fetchFile(uri, temporary);
                    if (response.statusCode() == 404) {
                        throw new IOException("Höhenkatalog-Datei fehlt (HTTP 404): " + uri);
                    }
                }
            }
            if (response.statusCode() == 404) {
                synchronized (this) {
                    if (isCurrent(resource, download)) {
                        System.err.println("Kartendatei nicht verfügbar (HTTP 404): " + resource.uri());
                        download.result.complete(null);
                    }
                }
                return;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " für " + uri);
            }
            if (!isCurrent(resource, download)) {
                return;
            }
            validate(temporary, resource.kind());

            synchronized (this) {
                // Abgeschlossene alte Aufträge dürfen entfernte Dateien nicht wieder veröffentlichen.
                if (!isCurrent(resource, download)) {
                    return;
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                download.result.complete(destination);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(download, e);
        } catch (IOException | RuntimeException e) {
            fail(download, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    System.err.println("Temporäre Kartendatei konnte nicht gelöscht werden: " + temporary);
                }
            }
        }
    }

    private static void validate(Path path, Kind kind) throws IOException {
        int size = kind == Kind.HEIGHT ? 500 : 1024;
        var image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != size || image.getHeight() != size) {
            throw new IOException("Ungültige Kartendatei: " + path);
        }
    }

    private synchronized void fail(Download download, Exception error) {
        if (!download.result.isDone()) {
            System.err.println("Karten-Download fehlgeschlagen: " + error.getMessage());
        }
        download.retryAt = System.nanoTime() + RETRY_DELAY_NANOS;
        download.result.completeExceptionally(error);
    }

    /** Entfernt Höhe und Luftbild dieser Kachel. */
    public synchronized void deleteTile(int tileX, int tileZ) {
        if (closed) {
            return;
        }
        TerrainTile tile = new TerrainTile(tileX, tileZ);
        for (Kind kind : Kind.values()) {
            forgetResource(new Resource(tile, kind));
        }
    }

    private void forgetResource(Resource resource) {
        Download removed = downloads.remove(resource);
        if (removed != null) {
            removed.result.cancel(false);
            removed.task.cancel(true);
            workers.remove(removed.task);
        }
        workers.execute(() -> deleteUnusedFile(resource));
    }

    private synchronized void deleteUnusedFile(Resource resource) {
        if (downloads.containsKey(resource)) {
            return;
        }
        try {
            Files.deleteIfExists(cacheDirectory.resolve(resource.fileName()));
        } catch (IOException e) {
            System.err.println("Kartendatei konnte nicht gelöscht werden: " + resource.fileName());
        }
    }

    synchronized void retainTiles(Set<TerrainTile> wanted) {
        if (closed) {
            return;
        }
        for (Resource resource : Set.copyOf(downloads.keySet())) {
            if (!wanted.contains(resource.tile())) {
                forgetResource(resource);
            }
        }
        // Auch Dateien eines früheren Spielstarts berücksichtigen.
        workers.execute(() -> {
            if (!Files.isDirectory(cacheDirectory)) {
                return;
            }
            try (var files = Files.list(cacheDirectory)) {
                files.forEach(file -> {
                    var match = CACHE_FILE.matcher(file.getFileName().toString());
                    if (match.matches()) {
                        TerrainTile tile = new TerrainTile(
                                Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))
                        );
                        if (!wanted.contains(tile)) {
                            Kind kind = match.group(1).equals("terrain") ? Kind.HEIGHT : Kind.TEXTURE;
                            deleteUnusedFile(new Resource(tile, kind));
                        }
                    }
                });
            } catch (IOException e) {
                System.err.println("Karten-Cache konnte nicht bereinigt werden: " + e.getMessage());
            }
        });
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Download download : downloads.values()) {
            download.result.completeExceptionally(new CancellationException("Spiel beendet"));
        }
        downloads.clear();
        workers.shutdownNow();
        client.shutdownNow();
    }
}
