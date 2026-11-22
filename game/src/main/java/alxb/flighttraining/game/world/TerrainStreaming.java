package alxb.flighttraining.game.world;

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


public final class TerrainStreaming implements AutoCloseable {
    private static final int MAX_PENDING_HEIGHTS = 6;
    private static final int MAX_PENDING_TEXTURES = 2;
    private static final float MIN_TILE_HEIGHT = -500f;
    private static final float MAX_TILE_HEIGHT = 9000f;
    private static final long RETRY_DELAY = Duration.ofSeconds(30).toNanos();

    private final WorldCoordinates coordinates;
    private final Map<Long,TerrainHeightSnapshot.Grid> heightGrids=new HashMap<>();
    private Set<Long> unavailableHeights=Set.of();
    private TerrainHeightSnapshot snapshot=new TerrainHeightSnapshot(Map.of());
    private Camera activeCamera;
    private TerrainTile lastPrediction;
    private long nextWindowRefresh;

    private Vector3f currentPosition=new Vector3f(), lastPosition;
    private Vector3f velocity=new Vector3f();
    private long lastUpdate;
    private int displayRadius=8;
    private float renderDistance=8000;
    private final Map<TerrainTile,Integer> builtNeighborhoods=new HashMap<>(), preparingNeighborhoods=new HashMap<>();
    private final Map<TerrainTile,Integer> tileResolution=new HashMap<>();
    private final AssetManager assetManager;
    private final Node rootNode;
    private final TerrainDownloader downloader;
    private final WorldObjectStreaming worldObjects;
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
    private final Map<TerrainTile,Integer> textureResolution=new HashMap<>();
    private Set<TerrainTile> visibleTiles = Set.of();
    private Set<TerrainTile> wantedDownloads = Set.of();
    private TerrainTile lastCenter;
    private long nextDownloadCheck;
    private boolean closed;


    public TerrainStreaming(AssetManager assetManager, Node rootNode) {
        this(assetManager, rootNode, new TerrainDownloader(), new WorldCoordinates(0,0));
    }

    public TerrainStreaming(AssetManager assets, Node root, double worldX, double worldZ) {
        this(assets,root,new TerrainDownloader(),new WorldCoordinates(worldX,worldZ));
    }
    TerrainStreaming(AssetManager assetManager, Node rootNode, TerrainDownloader downloader) {
        this(assetManager,rootNode,downloader,new WorldCoordinates(0,0));
    }
    TerrainStreaming(AssetManager assetManager, Node rootNode, TerrainDownloader downloader, WorldCoordinates coordinates) {
        this.coordinates=coordinates;
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        this.downloader = downloader;
        this.worldObjects = new WorldObjectStreaming(assetManager, rootNode, this::getGroundHeight, coordinates, ()->snapshot);
    }

    public float getGroundHeight(Vector3f position) {
        Vector2f location = new Vector2f(position.x, position.z);
        TerrainQuad owner=loadedTiles.get(coordinates.tile(position));
        if(owner!=null){float height=owner.getHeight(location);if(Float.isFinite(height))return height;}
        for (TerrainQuad tile : loadedTiles.values()) {
            float height = tile.getHeight(location);
            if (!Float.isNaN(height)) {
                return height;
            }
        }
        return Float.NaN;
    }

    public String getGroundLoadStatus(Vector3f position) {
        TerrainTile tile = coordinates.tile(position);
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

    public void update(Vector3f position, Camera cam) {
        activeCamera=cam;
        if (closed) {
            return;
        }
        currentPosition=position.clone();
        long tick=System.nanoTime();
        if(lastPosition!=null && lastUpdate!=0){float dt=(tick-lastUpdate)*1e-9f;
            if(dt>0&&dt<1){Vector3f v=position.subtract(lastPosition).divideLocal(dt);if(v.length()<1200)velocity.interpolateLocal(v,1-(float)Math.exp(-dt*2));}}
        lastPosition=position.clone();lastUpdate=tick;
        renderDistance=Math.min(WorldDetailSettings.renderDistance(),cam==null?WorldDetailSettings.renderDistance():cam.getFrustumFar());
        int radius=(int)Math.ceil(Math.max(renderDistance,Math.min(5000,renderDistance)+2000)/1000f);
        TerrainTile center = coordinates.tile(position);
        Vector3f ahead=velocity.mult(20);ahead.y=0;if(ahead.length()>3000)ahead.normalizeLocal().multLocal(3000);
        TerrainTile prediction=coordinates.tile(position.add(ahead));
        if (!center.equals(lastCenter)||radius!=displayRadius||(tick>=nextWindowRefresh&&!prediction.equals(lastPrediction))) {
            displayRadius=radius;lastPrediction=prediction;nextWindowRefresh=tick+500_000_000L;
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
        worldObjects.update(position, cam);
    }

    private void changeWindow(TerrainTile center) {
        lastCenter = center;
        visibleTiles = window(center, displayRadius);
        Set<TerrainTile> ahead=new LinkedHashSet<>(visibleTiles);
        Vector3f lookahead=currentPosition.add(velocity.mult(20));
        if(lookahead.distance(currentPosition)>3000)lookahead=currentPosition.add(velocity.normalize().mult(3000));
        ahead.addAll(window(coordinates.tile(lookahead),displayRadius));
        visibleTiles=ahead;
        Set<TerrainTile> wanted=new LinkedHashSet<>();
        for(TerrainTile t:visibleTiles)wanted.addAll(window(t,1));
        wantedDownloads=wanted;
        for (TerrainTile tile : new ArrayList<>(loadedTiles.keySet())) {
            if (!visibleTiles.contains(tile)) {
                loadedTiles.remove(tile).removeFromParent();tileResolution.remove(tile);textureResolution.remove(tile);
                heightGrids.remove(TerrainHeightSnapshot.key(tile.x(),tile.z()));builtNeighborhoods.remove(tile);
            }
        }
        for (TerrainTile tile : new ArrayList<>(prepared.keySet())) {
            if (!visibleTiles.contains(tile)) {
                prepared.remove(tile).cancel(true);preparingNeighborhoods.remove(tile);
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
        snapshot=new TerrainHeightSnapshot(heightGrids,unavailableHeights);
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
        for (TerrainTile tile : visibleTiles) {
            Vector3f position = tilePosition(tile);
            int ring = Math.max(Math.abs(tile.x() - center.x()),
                    Math.abs(tile.z() - center.z()));
            float dx=position.x-currentPosition.x,dz=position.z-currentPosition.z;
            float nearest=(float)Math.hypot(Math.max(0,Math.abs(dx)-halfSize),Math.max(0,Math.abs(dz)-halfSize));
            float display=Math.min(WorldDetailSettings.MAX_DETAIL_DISTANCE,renderDistance);
            Vector3f ahead=velocity.mult(20);ahead.y=0;if(ahead.length()>3000)ahead.normalizeLocal().multLocal(3000);
            Vector3f predicted=currentPosition.add(ahead);
            float predictedDistance=(float)Math.hypot(Math.max(0,Math.abs(position.x-predicted.x)-halfSize),Math.max(0,Math.abs(position.z-predicted.z)-halfSize));
            // Complete the object's display circle before spending bandwidth on far terrain or photographs.
            int group=ring<=1?0:nearest<=display+750?1:predictedDistance<=display+1000?2:nearest<=renderDistance+750?3:4;
            float priority=dx*dx+dz*dz;
            if(group>0){
                BoundingBox box=new BoundingBox(new Vector3f(position.x,centerY,position.z),halfSize,halfHeight,halfSize);
                if(!isInView(cam,box))priority+=250000;
            }
            ordered.add(new TilePriority(tile, group, priority));
        }
        ordered.sort(Comparator.comparingInt(TilePriority::group)
                .thenComparingDouble(TilePriority::distanceSquared));
        return ordered.stream().map(TilePriority::tile).toList();
    }

    private Vector3f tilePosition(TerrainTile tile) {
        return coordinates.local((tile.x()-TerrainTile.ORIGIN_X)*1000.0,0,
                -(tile.z()-TerrainTile.ORIGIN_Z)*1000.0);
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
        if(retry){
            Set<Long> unavailable=new HashSet<>();
            for(var entry:downloads.entrySet())if(entry.getValue().isDone()&&!entry.getValue().isCompletedExceptionally()&&completedPath(entry.getValue())==null)
                unavailable.add(TerrainHeightSnapshot.key(entry.getKey().x(),entry.getKey().z()));
            if(!unavailable.equals(unavailableHeights)){unavailableHeights=Set.copyOf(unavailable);snapshot=new TerrainHeightSnapshot(heightGrids,unavailableHeights);}
        }

        // Übertrage Luftbilder bereits während die Geometrie vorbereitet wird.
        List<TerrainTile> images = ordered.stream()
                .filter(tile -> tilePosition(tile).distance(currentPosition)<renderDistance+1000)
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
            if (current == null || (retry && (current.isCompletedExceptionally()
                    || current.isDone() && current.getNow(null)!=null && !java.nio.file.Files.isRegularFile(current.getNow(null))))) {
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
                    TerrainQuad previous=loadedTiles.put(coordinate, tile);
                    if(previous!=null){previous.removeFromParent();worldObjects.groundChanged(coordinate);}
                    tileResolution.put(coordinate,tile.getTotalSize());
                    builtNeighborhoods.put(coordinate,preparingNeighborhoods.getOrDefault(coordinate,0));
                    heightGrids.put(TerrainHeightSnapshot.key(coordinate.x(),coordinate.z()),new TerrainHeightSnapshot.Grid(tile.getTotalSize(),tile.getHeightMap()));
                    snapshot=new TerrainHeightSnapshot(heightGrids,unavailableHeights);
                    // Vorbereitung kann vor einem Rebase begonnen haben: aktuelle lokale Position verwenden.
                    tile.setLocalTranslation(tilePosition(coordinate));
                    if(activeCamera!=null){
                        com.jme3.terrain.geomipmap.TerrainLodControl lod=new com.jme3.terrain.geomipmap.TerrainLodControl(tile,activeCamera);
                        lod.setLodCalculator(new com.jme3.terrain.geomipmap.lodcalc.DistanceLodCalculator(65,2.7f));
                        tile.addControl(lod);
                    }
                    if(previous!=null){tile.setMaterial(previous.getMaterial());}
                    rootNode.attachChild(tile);
                    tile.updateGeometricState();
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
            prepared.remove(coordinate);preparingNeighborhoods.remove(coordinate);
            // Höchstens eine neue Kachel pro Frame einhängen.
            return;
        }
    }

    private void prepareNextTile(List<TerrainTile> ordered) {
        for (TerrainTile tile : ordered) {
            if (prepared.size() + preparedTextures.size() >= PREPARATION_THREADS) {
                return;
            }
            int desired=resolution(tile);
            Integer existing=tileResolution.get(tile);
            if (prepared.containsKey(tile)
                    || (retryAt.containsKey(tile) && System.nanoTime() - retryAt.get(tile) < 0)) {
                continue;
            }
            Map<TerrainTile, Path> paths = completedNeighborhood(tile);
            if(paths!=null){
                int signature=neighborhoodSignature(tile,paths);
                if(existing!=null && existing==desired && (builtNeighborhoods.getOrDefault(tile,0)==signature || !neighborhoodSettled(tile)))continue;
                preparingNeighborhoods.put(tile,signature);
                prepared.put(tile,preparation.submit(()->buildTile(tile,paths,desired)));
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
            if(future==null || isPending(future))continue;
            // Fehlgeschlagene Nachbarhöhen am Rand aus der eigenen Tile ergänzen.
            Path path = completedPath(future);
            if (path != null) {
                result.put(tile, path);
            }
        }
        return result;
    }

    private boolean neighborhoodSettled(TerrainTile center){
        for(TerrainTile tile:window(center,1)){
            CompletableFuture<Path> future=downloads.get(tile);
            if(future==null || !future.isDone())return false;
        }
        return true;
    }
    private static int neighborhoodSignature(TerrainTile center,Map<TerrainTile,Path> paths){
        int mask=0,index=0;
        for(TerrainTile tile:window(center,1)){if(paths.containsKey(tile))mask|=1<<index;index++;}
        return mask;
    }
    private void prepareNextTexture(List<TerrainTile> ordered) {
        if (!preparedTextures.isEmpty() || prepared.size() >= PREPARATION_THREADS) {
            return;
        }
        for (TerrainTile tile : ordered) {
            if (!loadedTiles.containsKey(tile) || (finishedTextures.contains(tile)&&textureResolution.getOrDefault(tile,0)==textureSize(tile))) {
                continue;
            }
            Path path = completedPath(textures.get(tile));
            if (path != null) {
                int size=textureSize(tile);
                preparedTextures.put(tile, preparation.submit(() -> TerrainTextureLoader.load(path,size)));
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
                    textureResolution.put(coordinate,texture.getImage().getWidth());
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

    private TerrainQuad buildTile(TerrainTile coordinate, Map<TerrainTile, Path> paths,int size)
            throws IOException {
        float[] heights = TerrainHeightLoader.load(coordinate, paths,size);
        TerrainQuad tile = new TerrainQuad(
                "Terrain_" + coordinate.x() + "_" + coordinate.z(), Math.min(65,size), size, heights
        );
        float spacing = TerrainTile.SIZE / (size-1f);
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


    private int textureSize(TerrainTile tile){
        int resolution=resolution(tile);return resolution==513?1024:resolution==129?512:128;
    }
    private int resolution(TerrainTile tile){
        float distance=tilePosition(tile).distance(currentPosition);
        int old=tileResolution.getOrDefault(tile,0);
        if(old==513 && distance<2500)return 513;
        if(old==129 && distance>1900 && distance<5800)return 129;
        if(old==65 && distance>5100)return 65;
        return distance<2200?513:distance<5500?129:65;
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

    public WorldCoordinates coordinates(){return coordinates;}
    public float maxGroundError(){return worldObjects.maxGroundError();}
    public int pavementVertexCount(){return worldObjects.pavementVertices();}
    public int visibleObjectCount(){return worldObjects.visibleCount();}
    public int groundedPartCount(){return worldObjects.groundedParts();}
    public boolean isSpawnAreaReady(Vector3f position){return worldObjects.spawnReady(position);}
    public int spawnObjectTotal(Vector3f position){return worldObjects.spawnTotal(position);}
    public int spawnObjectProgress(Vector3f position){return worldObjects.spawnProgress(position);}
    public int loadedMapCellCount(){return worldObjects.mapCellCount();}
    public int mappedTreeCount(){return worldObjects.mappedTreeCount();}
    public int mappedBuildingCount(){return worldObjects.mappedBuildingCount();}
    public int loadedObjectCount(){return worldObjects.loadedCount();}
    public int registeredObjectCount(){return worldObjects.registeredCount();}
    public Vector3f recenter(Vector3f player, Camera camera) {
        if(Math.abs(player.x)<4000 && Math.abs(player.z)<4000)return new Vector3f();
        Vector3f delta=new Vector3f(Math.round(player.x/1000f)*1000f,0,Math.round(player.z/1000f)*1000f);
        for(com.jme3.scene.Spatial child:rootNode.getChildren())child.move(delta.negate());
        worldObjects.normalizeRoot();
        coordinates.shift(delta);
        if(lastPosition!=null)lastPosition.subtractLocal(delta);
        currentPosition.subtractLocal(delta);
        camera.setLocation(camera.getLocation().subtract(delta));
        rootNode.updateGeometricState();
        return delta;
    }

    public void prewarm(com.jme3.renderer.RenderManager manager) {
        worldObjects.prewarm(manager);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        worldObjects.close();
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
            loadedTiles.clear();heightGrids.clear();snapshot=new TerrainHeightSnapshot(Map.of());
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}