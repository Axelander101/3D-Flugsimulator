package alxb.flighttraining.game.world;

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
    private static final float LOAD_RANGE = 5000;
    private final NaturalObjectStreaming landscape;
    private float activeRange=5000, currentAgl;
    private Vector3f lastAbsolute=new Vector3f();
    private final Set<Entry> displayedEntries=new HashSet<>();
    private final WorldCoordinates coordinates;
    private ModelGrounding grounding;
    private Spatial groundingModel;
    private Entry groundingEntry;
    private long groundingStarted;
    private final AssetManager assets;
    private final Node node = new Node("WorldObjects");
    private final Node originals = new Node("ObjectSources");
    private final Map<String,Node> batches=new HashMap<>();
    private final Map<String,Long> dirtyBatches=new LinkedHashMap<>();
    private Entry warmingEntry;
    private Future<Spatial> preparing;
    private Entry preparingEntry;
    private Spatial preparingSource;
    private Future<Node> batchBuild;
    private Node batchWarming;
    private String batchKey;
    private double batchOriginX,batchOriginZ;
    private final WorldUploadQueue batchUploads=new WorldUploadQueue();
    private final ObjectLoadPriority priority=new ObjectLoadPriority();
    private Vector3f flightDirection=new Vector3f(0,0,-1),predicted=new Vector3f();
    private long previousPositionTime;
    private int uploadFrames;
    private static String cell(Entry e){return (int)Math.floor(e.position.x/400)+":"+(int)Math.floor(e.position.z/400);}
    private void dirty(Entry e){dirtyBatches.putIfAbsent(cell(e),System.nanoTime());}
    private final Function<Vector3f, Float> ground;
    private final OsmMapStreaming osm=new OsmMapStreaming();
    private final Set<String> sourceIds=new HashSet<>();
    private final List<Vector3f> airportBuildings=new ArrayList<>();
    private final Map<Entry,Integer> loadedLods=new HashMap<>();
    private int pendingLod;
    private int groundedParts;
    private float maxGroundError;
    private int pavementVertices;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Entry, Spatial> loaded = new HashMap<>();
    private final Map<Entry, Long> retry = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("world-model-loader").factory());
    private final ModelPreparation preparation = new ModelPreparation();
    private Spatial warming;
    private final WorldUploadQueue uploads = new WorldUploadQueue();
    private Entry pendingEntry;
    private Future<Spatial> pending;
    private boolean closed;
    private record Entry(String path, Vector3f position, boolean onGround,
                         float offset, float heading, float scale) {}

    WorldObjectStreaming(AssetManager assets, Node root, Function<Vector3f, Float> ground, WorldCoordinates coordinates) {
        this(assets,root,ground,coordinates,()->null);
    }
    WorldObjectStreaming(AssetManager assets, Node root, Function<Vector3f, Float> ground, WorldCoordinates coordinates,
                         java.util.function.Supplier<TerrainHeightSnapshot> snapshots) {
        this.coordinates=coordinates;
        this.assets = assets;
        this.ground = ground;
        root.attachChild(node);
        originals.setCullHint(Spatial.CullHint.Always);node.attachChild(originals);
        loadAirports();
        loadPlacements();
        landscape=new NaturalObjectStreaming(assets,root,ground,coordinates,airportBuildings,snapshots);
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
                    JsonArray originArray=airport.getAsJsonArray("origin_lv95_ln02");
                    double worldX=originArray.get(0).getAsDouble()-2621500.0;
                    double worldZ=1092500.0-originArray.get(1).getAsDouble();
                    float originHeight=originArray.get(2).getAsFloat();
                    try {
                        for(JsonElement v:json(base+id+"/objects.json").getAsJsonArray()){
                            JsonObject b=v.getAsJsonObject();
                            if(b.has("east")&&b.has("north")&&b.has("height"))airportBuildings.add(new Vector3f(
                                (float)(b.get("east").getAsDouble()-2621500.0),0,(float)(1092500.0-b.get("north").getAsDouble())));
                        }
                    }catch(Exception ignored){}
                    for (JsonElement c : airport.getAsJsonArray("chunks")) {
                        JsonObject chunk = c.getAsJsonObject();
                        Vector3f offset = vector(chunk.getAsJsonArray("localTranslation_jme"));
                        add(base + id + "/" + chunk.get("file").getAsString(),
                                new Vector3f((float)(worldX+offset.x),originHeight+offset.y,(float)(worldZ+offset.z)),
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
                JsonObject placement=value.getAsJsonObject();
                String source=placement.has("sourceId")?placement.get("sourceId").getAsString():"";
                // Old generated OSM placeholders are replaced by the nationwide footprint/forest data.
                if(source.startsWith("way/")||source.startsWith("node/"))continue;
                registerPlacement(placement);
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("WorldPlacements ungültig: " + ex.getMessage());
        }
    }

    private void registerPlacement(JsonObject o){
        if(o.has("enabled")&&!o.get("enabled").getAsBoolean())return;
        String id=o.has("sourceId")?o.get("sourceId").getAsString():o.toString();
        if(!sourceIds.add(id))return;
        float x=(float)(o.get("east").getAsDouble()-2621500.0),z=(float)(1092500.0-o.get("north").getAsDouble());
        boolean tree=o.get("model").getAsString().contains("Tree_");
        if(!tree && id.startsWith("way/"))for(Vector3f airport:airportBuildings)
            if(Math.hypot(airport.x-x,airport.z-z)<25)return;
        boolean onGround=!o.has("heightAboveSea");
        add(o.get("model").getAsString(),new Vector3f(x,onGround?0:o.get("heightAboveSea").getAsFloat(),z),onGround,
            number(o,"groundOffset",0),number(o,"headingDeg",0),number(o,"scale",1));
    }
    private static int lod(Entry e,Vector3f player){
        if(e.path.startsWith("Textures/SwissAirports/"))return 0;
        float d=distance(e,player);return d<400*400?0:d<1000*1000?1:2;
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
        landscape.update(player,camera);
        float groundY=ground.apply(player);currentAgl=Float.isFinite(groundY)?Math.max(0,player.y-groundY):Math.max(0,player.y);
        activeRange=Math.min(LOAD_RANGE,Math.min(WorldDetailSettings.renderDistance(),camera==null?LOAD_RANGE:camera.getFrustumFar()));
        Vector3f absolutePlayer=coordinates.absolute(player);
        long positionTime=System.nanoTime();float elapsed=(positionTime-previousPositionTime)*1e-9f;
        Vector3f movement=absolutePlayer.subtract(lastAbsolute);movement.y=0;
        if(previousPositionTime!=0&&elapsed>0&&elapsed<1&&movement.length()/elapsed<1200&&movement.lengthSquared()>.001f){
            flightDirection=movement.normalize();predicted=absolutePlayer.add(flightDirection.mult(Math.min(3000,movement.length()/elapsed*20)));
        }else{if(previousPositionTime==0&&camera!=null)flightDirection=new Vector3f(camera.getDirection().x,0,camera.getDirection().z).normalizeLocal();predicted=absolutePlayer;}
        previousPositionTime=positionTime;lastAbsolute=absolutePlayer;
        priority.update(absolutePlayer,flightDirection,predicted,activeRange,Float.isFinite(groundY)?groundY:0,camera,coordinates,null);
        Set<Entry> shown=new HashSet<>();
        for(Entry entry:loaded.keySet())if(distance(entry,absolutePlayer)<=activeRange*activeRange)shown.add(entry);
        for(Entry entry:shown)if(!displayedEntries.contains(entry))dirty(entry);
        for(Entry entry:displayedEntries)if(!shown.contains(entry))dirty(entry);
        displayedEntries.clear();displayedEntries.addAll(shown);

        loaded.entrySet().removeIf(e -> {
            float d=distance(e.getKey(),absolutePlayer);
            boolean change=!e.getKey().path.startsWith("Textures/SwissAirports/")
                && lod(e.getKey(),absolutePlayer)!=loadedLods.getOrDefault(e.getKey(),-1)
                && e.getValue()!=warming && e.getValue()!=groundingModel && e.getValue()!=preparingSource;
            if(d<=(activeRange+2500)*(activeRange+2500)&&currentAgl<3000&&!change)return false;
            e.getValue().removeFromParent();dirty(e.getKey());loadedLods.remove(e.getKey());return true;
        });
        if (pending != null && pending.isDone()) {
            Entry e = pendingEntry;
            try {
                Spatial model = pending.get();
                if (distance(e, absolutePlayer) <= (activeRange+2500)*(activeRange+2500)) {
                    float y = e.position.y;
                    if (e.onGround) y = ground.apply(coordinates.local(e.position.x,0,e.position.z));
                    if (Float.isFinite(y)) {
                        model.setLocalTranslation(coordinates.local(e.position.x,y+e.offset,e.position.z));
                        model.setLocalScale(e.scale);
                        // GLBs face +Z; geographic heading 0 points north (-Z).
                        model.rotate(0, (float) Math.PI - (float) Math.toRadians(e.heading), 0);
                        // Airport chunks already contain geographic orientation.
                        if (!e.onGround && e.path.startsWith("Textures/SwissAirports/"))
                            model.setLocalRotation(com.jme3.math.Quaternion.IDENTITY);
                        model.setCullHint(Spatial.CullHint.Always);
                        node.attachChild(model); loaded.put(e, model);loadedLods.put(e,pendingLod);
                        groundingModel=model;groundingEntry=e;
                        grounding=new ModelGrounding(model,e.path.startsWith("Textures/SwissAirports/"),ground);
                        groundingStarted=System.nanoTime();
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
        if(grounding!=null){
            if(groundingModel.getParent()==null){grounding=null;groundingModel=null;}
            else if(grounding.step()){
                // Erst nach Bodenanpassung zusammenfassen, sonst gehen Gebäudepivots verloren.
                groundingModel.updateGeometricState();
                groundedParts+=grounding.completed();
                maxGroundError=Math.max(maxGroundError,grounding.maxError());pavementVertices+=grounding.pavementVertices();
                preparingSource=groundingModel;preparingEntry=groundingEntry;
                Spatial detached=groundingModel.clone(false);
                preparing=worker.submit(()->WorldRenderGroups.prepare(detached));
                grounding=null;groundingModel=null;
            }else if(System.nanoTime()-groundingStarted>10_000_000_000L){
                groundingModel.removeFromParent();loaded.remove(groundingEntry);
                retry.put(groundingEntry,System.nanoTime()+2_000_000_000L);grounding=null;groundingModel=null;
            }
        }
        if(preparing!=null&&preparing.isDone()){
            try{
                Spatial result=preparing.get();
                if(preparingSource.getParent()!=null){
                    result.setLocalTransform(preparingSource.getLocalTransform());preparingSource.removeFromParent();
                    node.attachChild(result);loaded.put(preparingEntry,result);
                    warming=result;warmingEntry=preparingEntry;uploads.add(result);
                }
            }catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            catch(ExecutionException ex){
                System.err.println("Objektgruppen konnten nicht erstellt werden: "+ex.getCause());
                preparingSource.removeFromParent();loaded.remove(preparingEntry);retry.put(preparingEntry,System.nanoTime()+10_000_000_000L);
            }finally{preparing=null;preparingEntry=null;preparingSource=null;}
        }
        if (pending != null || warming != null || grounding!=null || preparing!=null) return;
        long now = System.nanoTime();
        Entry best = null; float bestScore = Float.POSITIVE_INFINITY;
        for (Entry e : entries) {
            if (loaded.containsKey(e) || (retry.containsKey(e) && now - retry.get(e) < 0)) continue;
            float d = distance(e, absolutePlayer);
            if (d > (activeRange+2000)*(activeRange+2000) || currentAgl>=3000) continue;
            if (e.onGround && !Float.isFinite(ground.apply(coordinates.local(e.position.x,0,e.position.z)))) continue;
            float score=priority.score(e.position.x,e.position.z,(float)Math.sqrt(d),false);
            if (score < bestScore) {bestScore = score; best = e;}
        }
        if (best != null) {
            pendingEntry = best;
            pendingLod=lod(best,absolutePlayer);
            String path = best.path.replaceAll("_LOD[0-2]", "_LOD"+pendingLod);
            pending = worker.submit(() -> {
                try { return preparation.prepare(assets.loadModel(path)); }
                finally { if(path.startsWith("Textures/SwissAirports/"))assets.deleteFromCache(new ModelKey(path)); }
            });
        }
    }

    /** Shared per-frame upload budget for landscape AND airport geometry. */
    void prewarm(com.jme3.renderer.RenderManager manager) {
        if(closed)return;
        WorldUploadQueue.Budget budget=new WorldUploadQueue.Budget();
        // Airport uploads receive a regular turn even during sustained landscape streaming.
        boolean airportTurn=++uploadFrames%4==0;
        if(airportTurn)prewarmModels(manager,budget);
        landscape.prewarm(manager,budget);
        if(!airportTurn)prewarmModels(manager,budget);
    }
    private void prewarmModels(com.jme3.renderer.RenderManager manager,WorldUploadQueue.Budget budget){
        refreshBatch(manager,budget);
        if(warming==null)return;
        if(warming.getParent()==null){uploads.clear();warming=null;warmingEntry=null;return;}
        if(uploads.advance(manager,budget)){
            warming.setCullHint(Spatial.CullHint.Inherit);originals.attachChild(warming);
            warming.updateGeometricState();dirty(warmingEntry);warming=null;warmingEntry=null;
        }
    }
    private void refreshBatch(com.jme3.renderer.RenderManager manager){refreshBatch(manager,new WorldUploadQueue.Budget());}
    private void refreshBatch(com.jme3.renderer.RenderManager manager,WorldUploadQueue.Budget budget){
        if(batchBuild!=null&&batchBuild.isDone()){
            try{batchWarming=batchBuild.get();batchUploads.add(batchWarming);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            catch(ExecutionException ex){System.err.println("Flughafen-Rendergruppe: "+ex.getCause());dirtyBatches.put(batchKey,System.nanoTime());batchKey=null;}
            finally{batchBuild=null;}
        }
        if(batchWarming!=null){
            if(!batchUploads.advance(manager,budget))return;
            // Worker snapshots are in the origin that was current when the task started.
            batchWarming.setLocalTranslation((float)(batchOriginX-coordinates.x()),0,(float)(batchOriginZ-coordinates.z()));
            Node previous=batches.remove(batchKey);if(previous!=null)previous.removeFromParent();
            if(batchWarming.getQuantity()>0){node.attachChild(batchWarming);batches.put(batchKey,batchWarming);}
            batchUploads.clear();batchWarming=null;batchKey=null;
            if(node.getParent()!=null)node.getParent().updateGeometricState();
        }
        if(batchBuild!=null)return;
        String selected=null;long now=System.nanoTime();float best=Float.POSITIVE_INFINITY;
        for(var e:dirtyBatches.entrySet())if(now-e.getValue()>100_000_000L){
            String[] parts=e.getKey().split(":");double x=Integer.parseInt(parts[0])*400.0+200,z=Integer.parseInt(parts[1])*400.0+200;
            float score=priority.score(x,z,Math.max(0,(float)Math.hypot(x-lastAbsolute.x,z-lastAbsolute.z)-300),false);
            if(score<best){selected=e.getKey();best=score;}
        }
        if(selected==null)return;
        dirtyBatches.remove(selected);
        Node combined=new Node("ObjectCell_"+selected);List<com.jme3.material.Material> materials=new ArrayList<>();
        for(var e:loaded.entrySet())if(cell(e.getKey()).equals(selected)&&e.getValue().getParent()==originals&&distance(e.getKey(),lastAbsolute)<=activeRange*activeRange){
            Spatial source=e.getValue();source.updateGeometricState();Spatial copy=source.clone(false);
            copy.setCullHint(Spatial.CullHint.Dynamic);copy.setLocalTransform(source.getWorldTransform());
            copy.depthFirstTraversal(part->{if(part instanceof Geometry g){
                for(var m:materials)if(g.getMaterial().contentEquals(m)){g.setMaterial(m);return;}materials.add(g.getMaterial());
            }});combined.attachChild(copy);
        }
        batchKey=selected;batchOriginX=coordinates.x();batchOriginZ=coordinates.z();
        // Neither merging nor mesh partitioning runs in simpleUpdate/simpleRender anymore.
        batchBuild=worker.submit(()->{combined.updateGeometricState();return(Node)WorldRenderGroups.prepare(combined);});
    }

    void normalizeRoot(){
        landscape.normalizeRoot();
        Vector3f shift=node.getLocalTranslation().clone();
        for(Spatial child:node.getChildren())child.move(shift);
        node.setLocalTranslation(0,0,0);
        Vector3f sourceShift=originals.getLocalTranslation().clone();
        for(Spatial child:originals.getChildren())child.move(sourceShift);
        originals.setLocalTranslation(0,0,0);
        for(Entry entry:loaded.keySet())dirty(entry);
    }
    int visibleCount(){int n=landscape.visibleCount();for(var e:loaded.entrySet())if(e.getValue().getParent()==originals&&batches.containsKey(cell(e.getKey()))&&distance(e.getKey(),lastAbsolute)<=activeRange*activeRange)n++;return n;}
    float maxGroundError(){return maxGroundError;}
    int pavementVertices(){return pavementVertices;}
    int groundedParts(){return groundedParts;}
    void groundChanged(TerrainTile tile){landscape.groundChanged(tile.x(),tile.z());}
    boolean spawnReady(Vector3f local){return landscape.spawnReady(local);}
    int spawnTotal(Vector3f local){return landscape.spawnTotal(local);}
    int spawnProgress(Vector3f local){return landscape.spawnProgress(local);}
    int mapCellCount(){return landscape.loadedCount();}
    int mappedTreeCount(){return landscape.treeCount();}
    int mappedBuildingCount(){return landscape.buildingCount();}
    int loadedCount() { return loaded.size()+landscape.loadedCount(); }
    int registeredCount() { return entries.size(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (pending != null) pending.cancel(true);
        if(preparing!=null)preparing.cancel(true);if(batchBuild!=null)batchBuild.cancel(true);batchUploads.clear();batchWarming=null;
        worker.shutdownNow();osm.close();landscape.close();
        loaded.clear(); entries.clear(); retry.clear(); uploads.clear(); warming = null;
        originals.detachAllChildren();batches.clear();dirtyBatches.clear();displayedEntries.clear();
        node.detachAllChildren(); node.removeFromParent();
    }
}
