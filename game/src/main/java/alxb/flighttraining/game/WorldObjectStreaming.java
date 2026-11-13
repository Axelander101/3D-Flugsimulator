package alxb.flighttraining.game;

import com.google.gson.*;
import com.jme3.asset.*;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Called only from the game thread. Model parsing runs on one background worker. */
final class WorldObjectStreaming implements AutoCloseable {
    private static final float LOAD_RANGE = 1800, UNLOAD_RANGE = 2500;
    private final AssetManager assets;
    private final Node node = new Node("WorldObjects");
    private final Function<Vector3f, Float> ground;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Entry, Spatial> loaded = new HashMap<>();
    private final Map<Entry, Long> retry = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("world-model-loader").factory());
    private final ModelPreparation preparation = new ModelPreparation();
    private Spatial warming;
    private final java.util.ArrayDeque<Geometry> uploads = new java.util.ArrayDeque<>();
    private Entry pendingEntry;
    private Future<Spatial> pending;
    private boolean closed;
    private record Entry(String path, Vector3f position, boolean onGround,
                         float offset, float heading, float scale) {}

    WorldObjectStreaming(AssetManager assets, Node root, Function<Vector3f, Float> ground) {
        this.assets = assets;
        this.ground = ground;
        root.attachChild(node);
        loadAirports();
        loadPlacements();
        System.out.println("WorldObjects: " + entries.size() + " Modellteile registriert");
    }

    private JsonElement json(String path) throws IOException {
        AssetInfo info = assets.locateAsset(new AssetKey<>(path));
        if (info == null) throw new FileNotFoundException(path);
        try (Reader r = new InputStreamReader(info.openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r);
        }
    }

    private void loadAirports() {
        String base = "Textures/SwissAirports/";
        try {
            for (JsonElement value : json(base + "airports.json").getAsJsonArray()) {
                JsonObject airport = value.getAsJsonObject();
                try {
                    String id = airport.get("icao").getAsString();
                    Vector3f origin = vector(airport.getAsJsonArray("origin_lv95_ln02"));
                    float worldX = origin.x - (TerrainTile.ORIGIN_X * TerrainTile.SIZE + 500);
                    float worldZ = TerrainTile.ORIGIN_Z * TerrainTile.SIZE + 500 - origin.y;
                    for (JsonElement c : airport.getAsJsonArray("chunks")) {
                        JsonObject chunk = c.getAsJsonObject();
                        Vector3f offset = vector(chunk.getAsJsonArray("localTranslation_jme"));
                        add(base + id + "/" + chunk.get("file").getAsString(),
                                new Vector3f(worldX + offset.x, origin.z + offset.y, worldZ + offset.z),
                                false, 0, 0, 1);
                    }
                } catch (RuntimeException ex) {
                    System.err.println("Ungültiger Flughafen-Eintrag: " + ex.getMessage());
                }
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("Flughafenmodelle nicht registriert: " + ex.getMessage());
        }
    }

    private void loadPlacements() {
        String path = "WorldPlacements.json";
        if (assets.locateAsset(new AssetKey<>(path)) == null) return;
        try {
            for (JsonElement value : json(path).getAsJsonObject().getAsJsonArray("objects")) {
                JsonObject o = value.getAsJsonObject();
                if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                float x = o.get("east").getAsFloat() - (TerrainTile.ORIGIN_X * TerrainTile.SIZE + 500);
                float z = TerrainTile.ORIGIN_Z * TerrainTile.SIZE + 500 - o.get("north").getAsFloat();
                boolean onGround = !o.has("heightAboveSea");
                add(o.get("model").getAsString(), new Vector3f(x, onGround ? 0 : o.get("heightAboveSea").getAsFloat(), z),
                        onGround, number(o, "groundOffset", 0), number(o, "headingDeg", 0), number(o, "scale", 1));
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("WorldPlacements ungültig: " + ex.getMessage());
        }
    }

    private static float number(JsonObject o, String name, float fallback) {
        return o.has(name) ? o.get(name).getAsFloat() : fallback;
    }
    private static Vector3f vector(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
    private void add(String path, Vector3f position, boolean onGround, float offset, float heading, float scale) {
        if (!Float.isFinite(position.x) || !Float.isFinite(position.y) || !Float.isFinite(position.z)
                || !Float.isFinite(offset) || !Float.isFinite(heading) || !Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Ungültige Position/Skalierung: " + path);
        entries.add(new Entry(path, position, onGround, offset, heading, scale));
    }
    private static float distance(Entry e, Vector3f player) {
        float dx = e.position.x - player.x, dz = e.position.z - player.z;
        return dx * dx + dz * dz;
    }

    void update(Vector3f player, Camera camera) {
        if (closed) return;
        loaded.entrySet().removeIf(e -> {
            if (distance(e.getKey(), player) <= UNLOAD_RANGE * UNLOAD_RANGE) return false;
            e.getValue().removeFromParent(); return true;
        });
        if (pending != null && pending.isDone()) {
            Entry e = pendingEntry;
            try {
                Spatial model = pending.get();
                if (distance(e, player) <= UNLOAD_RANGE * UNLOAD_RANGE) {
                    float y = e.position.y;
                    if (e.onGround) y = ground.apply(e.position);
                    if (Float.isFinite(y)) {
                        model.setLocalTranslation(e.position.x, y + e.offset, e.position.z);
                        model.setLocalScale(e.scale);
                        // GLBs face +Z; geographic heading 0 points north (-Z).
                        model.rotate(0, (float) Math.PI - (float) Math.toRadians(e.heading), 0);
                        // Airport chunks already contain geographic orientation.
                        if (!e.onGround && e.path.startsWith("Textures/SwissAirports/"))
                            model.setLocalRotation(com.jme3.math.Quaternion.IDENTITY);
                        model.setCullHint(Spatial.CullHint.Always);
                        node.attachChild(model); loaded.put(e, model);
                        warming = model;
                        collectUploads(model);
                    }
                }
            } catch (ExecutionException ex) {
                System.err.println("Modell konnte nicht geladen werden: " + e.path + ": " + ex.getCause());
                retry.put(e, System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); return;
            } catch (CancellationException ignored) {
            } finally { pending = null; pendingEntry = null; }
        }
        if (pending != null || warming != null) return;
        long now = System.nanoTime();
        Entry best = null; float bestScore = Float.POSITIVE_INFINITY;
        for (Entry e : entries) {
            if (loaded.containsKey(e) || (retry.containsKey(e) && now - retry.get(e) < 0)) continue;
            float d = distance(e, player);
            if (d > LOAD_RANGE * LOAD_RANGE) continue;
            if (e.onGround && !Float.isFinite(ground.apply(e.position))) continue;
            float score = d;
            if (camera != null) {
                Vector3f direction = e.position.subtract(camera.getLocation());
                if (direction.dot(camera.getDirection()) < 0) score += LOAD_RANGE * LOAD_RANGE;
            }
            if (score < bestScore) {bestScore = score; best = e;}
        }
        if (best != null) {
            pendingEntry = best;
            String path = best.path;
            pending = worker.submit(() -> {
                try { return preparation.prepare(assets.loadModel(path)); }
                finally { assets.deleteFromCache(new ModelKey(path)); }
            });
        }
    }

    private void collectUploads(Spatial model) {
        if (model instanceof Geometry geometry) uploads.add(geometry);
        if (model instanceof Node parent) for (Spatial child : parent.getChildren()) collectUploads(child);
    }

    /** Run once per frame from SimpleApplication.simpleRender, with the OpenGL context active. */
    void prewarm(com.jme3.renderer.RenderManager manager) {
        if (closed || warming == null) return;
        if (warming.getParent() == null) { uploads.clear(); warming = null; return; }
        Geometry next = uploads.poll();
        if (next != null) manager.preloadScene(next);
        if (uploads.isEmpty()) {
            warming.setCullHint(Spatial.CullHint.Inherit);
            warming = null;
        }
    }

    int loadedCount() { return loaded.size(); }
    int registeredCount() { return entries.size(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
        loaded.clear(); entries.clear(); retry.clear(); uploads.clear(); warming = null;
        node.detachAllChildren(); node.removeFromParent();
    }
}
