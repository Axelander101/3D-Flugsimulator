package alxb.flighttraining.game.world;

import com.google.gson.*;
import com.jme3.asset.*;
import com.jme3.material.MatParamOverride;
import com.jme3.math.*;
import com.jme3.renderer.*;
import com.jme3.scene.*;
import com.jme3.shader.VarType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.zip.GZIPInputStream;

/** Coarse coverage first, bounded parallel CPU jobs, hidden prefetch and opaque LOD transitions. */
final class NaturalObjectStreaming implements AutoCloseable {
    private static final int MAX_JOBS=8,MAX_DETAIL_JOBS=2;
    private static final float PREFETCH_MARGIN=2000,FADE_SECONDS=.55f;
    private record Key(int east,int north) {
        double x(){return east*1000.0-2621500;}
        double z(){return 1092500.0-north*1000;}
        float distance(Vector3f p){double dx=Math.max(0,Math.max(x()-p.x,p.x-x()-1000));double dz=Math.max(0,Math.max(z()-1000-p.z,p.z-z()));return(float)Math.hypot(dx,dz);}
        float centerDistance(Vector3f p){return(float)Math.hypot(x()+500-p.x,z()-500-p.z);}
    }
    private record Resident(Node node,int lod,int trees,int buildings,long revision) {}
    private record Transition(Node incoming,Node outgoing,long start) {}
    private final class Job {
        final Key key;
        final int lod;
        long revision;
        Future<MapCellPlan> planning;
        Future<MapCellMesh.Built> building;
        MapCellPlan plan;
        MapCellMesh.Built ready;
        final WorldUploadQueue uploads=new WorldUploadQueue();
        long retryAfter;
        Job(Key key,int lod,Vector3f player){
            this.key=key;this.lod=lod;revision=groundRevisions.getOrDefault(key,0L);
            float height=agl,range=far;Vector3f captured=player.clone();
            planning=worker.submit(()->{MapCell cell=readCell(key);return cell==null?null:MapCellPlan.create(cell,lod,captured.x,captured.z,height,range,airportBuildings);});
        }
        void cancel(){if(planning!=null)planning.cancel(true);if(building!=null)building.cancel(true);}
    }
    private final AssetManager assets;
    private final WorldCoordinates coordinates;
    private final Function<Vector3f,Float> ground;
    private final Supplier<TerrainHeightSnapshot> snapshots;
    private final List<Vector3f> airportBuildings;
    private final Node root=new Node("MappedLandscape");
    private final Map<Key,Resident> loaded=new HashMap<>();
    private final Map<Key,Job> jobs=new LinkedHashMap<>();
    private final Map<Key,Job> waitingForGround=new LinkedHashMap<>();
    private final Map<Key,Transition> transitions=new HashMap<>();
    private final Set<Key> absent=new HashSet<>();
    private final Map<Key,Long> retry=new HashMap<>(),groundRevisions=new HashMap<>();
    private final Map<Key,Future<MapCell>> metadataLoads=new HashMap<>();
    private final ExecutorService worker=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("map-cell-mesh-",0).factory());
    private final ThreadLocal<MapCellMesh> builders;
    private final Map<Key,MapCell> cache=Collections.synchronizedMap(new LinkedHashMap<Key,MapCell>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Key,MapCell> entry){return size()>192;}
    });
    private final MatParamOverride objectCenter=new MatParamOverride(VarType.Vector3,"ObjectCenter",new Vector3f());
    private final MatParamOverride objectRange=new MatParamOverride(VarType.Float,"ObjectRange",5000f);
    private float far=5000,agl;
    private Vector3f lastAbsolute,absolute=new Vector3f(),velocity=new Vector3f();
    private long lastTime,nextSchedule;
    private boolean closed,fadeDisabled;
    private final ObjectLoadPriority priority=new ObjectLoadPriority();
    private int coarseAdmissions,uploadFrames;
    private float score(Key key,boolean detail){return priority.score(key.x()+500,key.z()-500,key.distance(absolute),detail);}

    NaturalObjectStreaming(AssetManager assets,Node parent,Function<Vector3f,Float> ground,WorldCoordinates coordinates,List<Vector3f> airports){this(assets,parent,ground,coordinates,airports,()->null);}
    NaturalObjectStreaming(AssetManager assets,Node parent,Function<Vector3f,Float> ground,WorldCoordinates coordinates,List<Vector3f> airports,Supplier<TerrainHeightSnapshot> snapshots){
        this.assets=assets;this.ground=ground;this.coordinates=coordinates;this.airportBuildings=List.copyOf(airports);this.snapshots=snapshots;
        builders=ThreadLocal.withInitial(()->new MapCellMesh(assets));
        parent.attachChild(root);root.addLight(new com.jme3.light.AmbientLight(new ColorRGBA(.42f,.45f,.5f,1)));
        root.addMatParamOverride(objectCenter);root.addMatParamOverride(objectRange);
    }
    private MapCell readCell(Key key)throws IOException{
        MapCell cached=cache.get(key);if(cached!=null)return cached;
        String path="MapData/cells/"+key.east+"_"+key.north+".json.gz";
        AssetInfo info=assets.locateAsset(new AssetKey<>(path));if(info==null)return null;
        try(Reader reader=new InputStreamReader(new GZIPInputStream(info.openStream()),StandardCharsets.UTF_8)){
            MapCell cell=new MapCell(key.east,key.north,JsonParser.parseReader(reader).getAsJsonArray());cache.put(key,cell);return cell;
        }
    }
    void update(Vector3f local,Camera camera){
        if(closed)return;
        long now=System.nanoTime();
        far=Math.min(WorldDetailSettings.MAX_DETAIL_DISTANCE,Math.min(WorldDetailSettings.renderDistance(),camera==null?Float.POSITIVE_INFINITY:camera.getFrustumFar()));
        absolute=coordinates.absolute(local);
        objectCenter.setValue(local.clone());objectRange.setValue(far);
        float floor=ground.apply(local);agl=Float.isFinite(floor)?Math.max(0,local.y-floor):Math.max(0,local.y);
        if(lastAbsolute!=null){float dt=(now-lastTime)*1e-9f;if(dt>0&&dt<1){Vector3f current=absolute.subtract(lastAbsolute).divideLocal(dt);if(current.length()<1200)velocity.interpolateLocal(current,1-(float)Math.exp(-dt*3));}}
        lastAbsolute=absolute.clone();lastTime=now;
        Vector3f horizontal=new Vector3f(velocity.x,0,velocity.z);
        float ahead=Math.min(3000,horizontal.length()*20);
        Vector3f forward=horizontal.lengthSquared()>25?horizontal.normalize():camera==null?new Vector3f(0,0,-1):new Vector3f(camera.getDirection().x,0,camera.getDirection().z).normalizeLocal();
        Vector3f predicted=absolute.add(forward.mult(ahead));
        TerrainHeightSnapshot snapshot=snapshots.get();
        priority.update(absolute,forward,predicted,far,Float.isFinite(floor)?floor:0,camera,coordinates,snapshot);
        advanceJobs(snapshot,now);
        updateVisibility(now);
        loaded.entrySet().removeIf(e->{
            if(e.getKey().distance(absolute)<=far+PREFETCH_MARGIN+1000||e.getKey().distance(predicted)<=far+1000)return false;
            e.getValue().node.removeFromParent();Transition t=transitions.remove(e.getKey());if(t!=null&&t.outgoing!=null)t.outgoing.removeFromParent();return true;
        });
        waitingForGround.entrySet().removeIf(e->e.getKey().distance(absolute)>far+PREFETCH_MARGIN+1000&&e.getKey().distance(predicted)>far+1000);
        jobs.entrySet().removeIf(e->{if(e.getKey().distance(absolute)<=far+PREFETCH_MARGIN+1000||e.getKey().distance(predicted)<=far+1000)return false;e.getValue().cancel();return true;});
        absent.removeIf(key->key.distance(absolute)>far+7000);retry.keySet().removeIf(key->key.distance(absolute)>far+7000);groundRevisions.keySet().removeIf(key->key.distance(absolute)>far+7000);
        drainMetadata(now);
        if(now<nextSchedule||jobs.size()>=MAX_JOBS)return;
        nextSchedule=now+30_000_000L;
        int east=(int)Math.floor((absolute.x+2621500)/1000),north=(int)Math.floor((1092500-absolute.z)/1000);
        int radius=(int)Math.ceil((far+PREFETCH_MARGIN+ahead)/1000);
        record Candidate(Key key,int lod,float score,Job waiting){}
        List<Candidate> candidates=new ArrayList<>();
        List<Key> metadata=new ArrayList<>();
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            Key key=new Key(east+dx,north+dz);if(absent.contains(key)||jobs.containsKey(key)||waitingForGround.containsKey(key)||retry.getOrDefault(key,0L)>now)continue;
            float distance=key.distance(absolute),futureDistance=key.distance(predicted);
            if(distance>far+PREFETCH_MARGIN&&futureDistance>far+800)continue;
            if(snapshot!=null&&snapshot.isUnavailable(key.east,key.north))continue;
            if(snapshot!=null&&!snapshot.contains(key.east,key.north)){
                if(!cache.containsKey(key)&&!metadataLoads.containsKey(key))metadata.add(key);
                continue;
            }
            Resident resident=loaded.get(key);
            int wanted=WorldDetailSettings.lod(distance,agl,far);
            if(resident!=null)wanted=WorldDetailSettings.stableLod(distance,agl,far,resident.lod);
            if(resident==null){wanted=3;}
            else{
                if(distance>far||transitions.containsKey(key))continue;
                if(wanted==resident.lod&&resident.revision>=groundRevisions.getOrDefault(key,0L))continue;
                if(wanted<resident.lod&&!priority.visible(key.x()+500,key.z()-500,500))continue;
            }
            candidates.add(new Candidate(key,wanted,score(key,resident!=null),null));
        }
        // Resumable jobs compete with fresh work; neither queue can bypass visibility priority.
        if(snapshot!=null)for(Job job:waitingForGround.values()){
            if(now<job.retryAfter||!snapshot.covers(job.plan.requiredGrids))continue;
            candidates.add(new Candidate(job.key,job.lod,score(job.key,loaded.containsKey(job.key)),job));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score));
        long detailJobs=jobs.values().stream().filter(job->job.lod<3).count();
        while(!candidates.isEmpty()&&jobs.size()<MAX_JOBS){
            int index=0;
            // Reserve every eighth coarse admission for the sides/rear: continuous turns must not starve them.
            if(coarseAdmissions%8==7)for(int i=0;i<candidates.size();i++){
                if(ObjectLoadPriority.tier(candidates.get(i).score)==ObjectLoadPriority.SURROUNDINGS){index=i;break;}
            }
            Candidate candidate=candidates.remove(index);
            if(candidate.lod<3&&detailJobs>=MAX_DETAIL_JOBS)continue;
            Job job=candidate.waiting;
            if(job==null)job=new Job(candidate.key,candidate.lod,absolute);
            else waitingForGround.remove(candidate.key);
            jobs.put(candidate.key,job);
            if(candidate.lod<3)detailJobs++;else coarseAdmissions++;
        }
        metadata.sort(Comparator.comparingDouble(key->score(key,false)));
        for(Key key:metadata){if(metadataLoads.size()>=2)break;metadataLoads.put(key,worker.submit(()->readCell(key)));}
    }
    private void advanceJobs(TerrainHeightSnapshot snapshot,long now){
        Iterator<Job> iterator=jobs.values().iterator();long fallbackBudget=System.nanoTime()+1_000_000;
        while(iterator.hasNext()){
            Job job=iterator.next();
            try{
                if(job.planning!=null&&job.planning.isDone()){
                    job.plan=job.planning.get();job.planning=null;
                    if(job.plan==null){absent.add(job.key);iterator.remove();continue;}
                }
                if(job.building!=null&&job.building.isDone()){
                    job.ready=job.building.get();job.building=null;
                    if(job.ready!=null){
                        if(fadeDisabled)WorldObjectFade.fallback(job.ready.node(),assets);
                        WorldObjectFade.prepare(job.ready.node());
                        job.ready.node().setLocalTranslation(coordinates.local(job.key.x(),0,job.key.z()));
                        updateInstanceOrigin(job.ready.node());
                        job.uploads.add(job.ready.node());job.plan=null;
                    }else{job.retryAfter=now+500_000_000L;park(job);iterator.remove();continue;}
                }
                if(job.plan!=null&&job.building==null&&now>=job.retryAfter){
                    if(snapshot!=null){
                        if(!snapshot.covers(job.plan.requiredGrids)){park(job);iterator.remove();continue;}
                        MapCellPlan plan=job.plan;
                        job.revision=groundRevisions.getOrDefault(job.key,0L);
                        job.building=worker.submit(()->plan.sampleHeights(snapshot)?builders.get().build(plan):null);
                    }else{
                        // Compatibility for callers that provide only a game-thread height function.
                        while(job.plan.sampled<job.plan.queries.size()&&System.nanoTime()<fallbackBudget){
                            float[] p=job.plan.queries.get(job.plan.sampled);float value=ground.apply(coordinates.local(job.key.x()+p[0],0,job.key.z()+p[1]));
                            if(!Float.isFinite(value)){job.plan.sampled=0;job.retryAfter=now+500_000_000L;break;}
                            job.plan.heights.set(job.plan.sampled++,value);
                        }
                        if(job.plan.sampled==job.plan.queries.size()){MapCellPlan plan=job.plan;job.building=worker.submit(()->builders.get().build(plan));}
                    }
                }
            }catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            catch(ExecutionException|CancellationException ex){System.err.println("Map cell "+job.key+": "+ex.getMessage());retry.put(job.key,now+10_000_000_000L);iterator.remove();}
        }
    }
    private void park(Job job){
        waitingForGround.put(job.key,job);
        if(waitingForGround.size()>32){
            Key farthest=waitingForGround.keySet().stream().max(Comparator.comparingDouble(key->key.centerDistance(absolute))).orElseThrow();
            waitingForGround.remove(farthest);
        }
    }
    private void drainMetadata(long now){
        var iterator=metadataLoads.entrySet().iterator();
        while(iterator.hasNext()){
            var item=iterator.next();if(!item.getValue().isDone())continue;
            try{if(item.getValue().get()==null)absent.add(item.getKey());}
            catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            catch(ExecutionException|CancellationException ex){retry.put(item.getKey(),now+10_000_000_000L);}
            iterator.remove();
        }
    }
    private void updateVisibility(long now){
        for(var entry:loaded.entrySet()){
            Key key=entry.getKey();Node node=entry.getValue().node;
            if(key.distance(absolute)<far&&node.getParent()==null){
                root.attachChild(node);WorldObjectFade.set(node,0,false);transitions.put(key,new Transition(node,null,now));
            }
        }
        var iterator=transitions.entrySet().iterator();
        while(iterator.hasNext()){
            var item=iterator.next();Transition transition=item.getValue();
            float t=Math.min(1,(now-transition.start)/1e9f/FADE_SECONDS);float blend=t*t*(3-2*t);
            WorldObjectFade.set(transition.incoming,blend,false);if(transition.outgoing!=null)WorldObjectFade.set(transition.outgoing,blend,true);
            if(t>=1){if(transition.outgoing!=null)transition.outgoing.removeFromParent();iterator.remove();}
        }
        // Prepared meshes outside the display circle remain resident without being rendered.
        for(var entry:loaded.entrySet())if(entry.getKey().distance(absolute)>far+100&&!transitions.containsKey(entry.getKey()))entry.getValue().node.removeFromParent();
    }
    void prewarm(RenderManager manager){prewarm(manager,new WorldUploadQueue.Budget());}
    void prewarm(RenderManager manager,WorldUploadQueue.Budget budget){
        if(closed)return;
        int installed=0;
        List<Job> ready=jobs.values().stream().filter(job->job.ready!=null)
                .sorted(Comparator.comparingDouble(job->score(job.key,loaded.containsKey(job.key)))).toList();
        if(++uploadFrames%8==0){
            List<Job> fair=new ArrayList<>(ready);
            for(int i=0;i<fair.size();i++)if(ObjectLoadPriority.tier(score(fair.get(i).key,loaded.containsKey(fair.get(i).key)))==ObjectLoadPriority.SURROUNDINGS){
                Job background=fair.remove(i);fair.add(0,background);break;
            }
            ready=fair;
        }
        for(Job job:ready){
            if(!budget.available())break;
            Node node=job.ready.node();node.setLocalTranslation(coordinates.local(job.key.x(),0,job.key.z()));updateInstanceOrigin(node);
            boolean complete;
            try{complete=job.uploads.advance(manager,budget);}
            catch(RuntimeException ex){
                if(!WorldObjectFade.supported(node))throw ex;
                fadeDisabled=true;System.err.println("Objekt-Überblendung deaktiviert: "+ex.getMessage());
                for(Resident resident:loaded.values())WorldObjectFade.fallback(resident.node,assets);
                for(Transition transition:transitions.values())if(transition.outgoing!=null)transition.outgoing.removeFromParent();
                transitions.clear();
                for(Job waiting:jobs.values())if(waiting.ready!=null){
                    WorldObjectFade.fallback(waiting.ready.node(),assets);waiting.uploads.clear();waiting.uploads.add(waiting.ready.node());
                }
                break; // Retry the replacement material on the next frame, within the same budget.
            }
            if(!complete)continue;
            install(job);jobs.remove(job.key);if(++installed>=2)break;
        }
        if(installed>0&&root.getParent()!=null)root.getParent().updateGeometricState();
    }
    private void install(Job job){
        Node node=job.ready.node();node.setLocalTranslation(coordinates.local(job.key.x(),0,job.key.z()));updateInstanceOrigin(node);
        node.setShadowMode(job.lod==0?com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive:com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        Resident old=loaded.put(job.key,new Resident(node,job.lod,job.ready.trees(),job.ready.buildings(),job.revision));
        if(job.key.distance(absolute)<far){
            root.attachChild(node);Node outgoing=old!=null&&old.node.getParent()!=null?old.node:null;
            if(outgoing!=null&&!WorldObjectFade.supported(node)){outgoing.removeFromParent();outgoing=null;}
            if(outgoing!=null)WorldObjectFade.set(outgoing,0,true);
            transitions.put(job.key,new Transition(node,outgoing,System.nanoTime()));
        }else if(old!=null)old.node.removeFromParent();
    }
    void normalizeRoot(){
        // Some prepared nodes are detached, so move all residents, plus outgoing transition nodes.
        Vector3f shift=root.getLocalTranslation().clone();
        Set<Node> moved=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Resident resident:loaded.values())moved.add(resident.node);
        for(Transition transition:transitions.values())if(transition.outgoing!=null)moved.add(transition.outgoing);
        for(Node node:moved){node.move(shift);updateInstanceOrigin(node);}
        root.setLocalTranslation(0,0,0);
    }
    void groundChanged(int east,int north){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){Key key=new Key(east+dx,north+dz);groundRevisions.merge(key,1L,Long::sum);}
    }
    private int[] coverage(Vector3f local){
        Vector3f point=coordinates.absolute(local);int east=(int)Math.floor((point.x+2621500)/1000),north=(int)Math.floor((1092500-point.z)/1000);
        int ready=0,total=0,radius=(int)Math.ceil(far/1000)+1;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            Key key=new Key(east+dx,north+dz);if(key.distance(point)>=far)continue;total++;if(loaded.containsKey(key)||absent.contains(key)||knownMissing(key))ready++;
        }
        return new int[]{ready,total};
    }
    int spawnProgress(Vector3f local){return coverage(local)[0];}
    int spawnTotal(Vector3f local){return coverage(local)[1];}
    boolean spawnReady(Vector3f local){
        int[] coverage=coverage(local);
        return coverage[0]>=coverage[1]*.97f&&nearestMissingDistance()>=Math.min(2500,far);
    }
    private boolean knownMissing(Key key){TerrainHeightSnapshot snapshot=snapshots.get();return snapshot!=null&&snapshot.isUnavailable(key.east,key.north);}
    float nearestMissingDistance(){
        int east=(int)Math.floor((absolute.x+2621500)/1000),north=(int)Math.floor((1092500-absolute.z)/1000);float nearest=Float.POSITIVE_INFINITY;
        int radius=(int)Math.ceil(far/1000)+1;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            Key key=new Key(east+dx,north+dz);float distance=key.distance(absolute);
            if(distance<far&&!loaded.containsKey(key)&&!absent.contains(key)&&!knownMissing(key))nearest=Math.min(nearest,distance);
        }
        return nearest;
    }
    String debug(){int[] coverage=coverage(coordinates.local(absolute.x,absolute.y,absolute.z));return "jobs="+jobs.size()+" waiting="+waitingForGround.size()+" staged="+loaded.values().stream().filter(r->r.node.getParent()==null).count()+" coverage="+coverage[0]+"/"+coverage[1]+" nearestGap="+nearestMissingDistance()+" transitions="+transitions.size()+" cache="+cache.size();}
    private static void updateInstanceOrigin(Node cell){for(Spatial child:cell.getChildren())if(child instanceof StaticTreeInstances instances)instances.setOrigin(cell.getLocalTranslation());}
    int loadedCount(){return loaded.size();}
    int visibleCount(){return loaded.values().stream().filter(r->r.node.getParent()!=null).mapToInt(r->r.trees+r.buildings).sum();}
    int treeCount(){return loaded.values().stream().mapToInt(Resident::trees).sum();}
    int buildingCount(){return loaded.values().stream().mapToInt(Resident::buildings).sum();}
    @Override public void close(){
        closed=true;for(Job job:jobs.values())job.cancel();for(Future<MapCell> future:metadataLoads.values())future.cancel(true);worker.shutdownNow();
        jobs.clear();waitingForGround.clear();metadataLoads.clear();loaded.clear();transitions.clear();root.detachAllChildren();root.removeFromParent();
    }
}
