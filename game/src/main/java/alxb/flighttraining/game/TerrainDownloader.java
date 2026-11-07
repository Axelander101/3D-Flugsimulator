package alxb.flighttraining.game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Sechs Worker teilen sich Höhen- und PNG-Downloads. Jede Spielsitzung besitzt ihren Cache. */
public final class TerrainDownloader implements AutoCloseable {
    private static final int DATA_YEAR = 2024;
    private static final long RETRY_DELAY_NANOS = Duration.ofSeconds(30).toNanos();
    private static final Pattern LEGACY_CACHE_FILE = Pattern.compile(
            "(terrain|texture)_[0-9]+-[0-9]+[.](tif|png)(-.*[.]part)?"
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
        long retryAt;
    }

    private final HttpClient client;
    private final Path cacheDirectory;
    private final Path legacyDirectory;
    private final ExecutorService workers = Executors.newFixedThreadPool(
            6, Thread.ofPlatform().daemon().name("map-download-", 0).factory()
    );
    private final HashMap<Resource, Download> downloads = new HashMap<>();
    private final CompletableFuture<Void> cleanupDone = new CompletableFuture<>();
    private final Thread shutdownHook = new Thread(this::close, "map-cache-cleanup");
    private boolean closed;

    public TerrainDownloader() {
        this(createSessionDirectory(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), Path.of("terrain-cache"));
    }

    /** Das übergebene Verzeichnis gehört ausschließlich diesem Downloader. */
    TerrainDownloader(Path cacheDirectory, HttpClient client) {
        this(cacheDirectory, client, null);
    }

    private TerrainDownloader(Path cacheDirectory, HttpClient client, Path legacyDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.client = client;
        this.legacyDirectory = legacyDirectory;
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static Path createSessionDirectory() {
        try {
            Path root = Path.of("terrain-cache");
            Files.createDirectories(root);
            return Files.createTempDirectory(root, "session-");
        } catch (IOException e) {
            throw new UncheckedIOException("Karten-Cache konnte nicht angelegt werden", e);
        }
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
                || System.nanoTime() < previous.retryAt)) {
            return previous.result;
        }
        Download download = new Download();
        downloads.put(resource, download);
        workers.execute(() -> fetch(resource, download));
        return download.result;
    }

    static URI sourceUri(TerrainTile tile) {
        String item = "swissalti3d_" + DATA_YEAR + "_" + tile.x() + "-" + tile.z();
        return URI.create("https://data.geo.admin.ch/ch.swisstopo.swissalti3d/"
                + item + "/" + item + "_2_2056_5728.tif");
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
            HttpRequest request = HttpRequest.newBuilder(resource.uri())
                    .timeout(Duration.ofSeconds(60)).GET().build();
            temporary = Files.createTempFile(cacheDirectory, resource.fileName() + "-", ".part");
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() == 404) {
                download.result.complete(null);
                return;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " für " + resource.uri());
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
            Resource resource = new Resource(tile, kind);
            Download removed = downloads.remove(resource);
            if (removed != null) {
                removed.result.cancel(false);
            }
            workers.execute(() -> deleteUnusedFile(resource));
        }
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
        for (Resource resource : Set.copyOf(downloads.keySet())) {
            if (!wanted.contains(resource.tile())) {
                deleteTile(resource.tile().x(), resource.tile().z());
            }
        }
    }

    @Override
    public void close() {
        boolean performCleanup;
        synchronized (this) {
            performCleanup = !closed;
            if (performCleanup) {
                closed = true;
                for (Download download : downloads.values()) {
                    download.result.completeExceptionally(new CancellationException("Spiel beendet"));
                }
                downloads.clear();
                workers.shutdownNow();
                client.shutdownNow();
            }
        }
        if (!performCleanup) {
            cleanupDone.join();
            return;
        }

        boolean interrupted = Thread.interrupted();
        try {
            // Erst wenn kein Worker mehr schreibt, kann der Cache endgültig entfernt werden.
            boolean stopped = false;
            while (!stopped) {
                try {
                    stopped = workers.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            deleteSessionFiles();
        } catch (IOException e) {
            System.err.println("Karten-Cache konnte nicht vollständig gelöscht werden: " + e.getMessage());
        } finally {
            cleanupDone.complete(null);
            if (Thread.currentThread() != shutdownHook) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    // Während des JVM-Shutdowns wartet der Hook bereits auf cleanupDone.
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void deleteSessionFiles() throws IOException {
        if (Files.exists(cacheDirectory)) {
            try (var files = Files.walk(cacheDirectory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        if (legacyDirectory != null && Files.isDirectory(legacyDirectory)) {
            // Bekannte Dateien der vorherigen Version lagen direkt in terrain-cache.
            try (var files = Files.list(legacyDirectory)) {
                for (Path file : files.toList()) {
                    if (Files.isRegularFile(file)
                            && LEGACY_CACHE_FILE.matcher(file.getFileName().toString()).matches()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
            try {
                Files.deleteIfExists(legacyDirectory);
            } catch (DirectoryNotEmptyException e) {
                // Andere Spielsitzungen können hier noch eigene Unterordner besitzen.
            }
        }
    }
}
