package alxb.flighttraining.game.world;

import com.google.gson.*;
import com.jme3.math.Vector3f;
import java.util.*;

/** CPU-only map planning. Numeric polygon bounds and a building grid keep forests inexpensive. */
final class MapCellPlan {
    record Tree(float x,float z,int species,float scale,float angle,int lod,int heightIndex) {}
    record Building(JsonObject feature,int[] groundIndices) {}
    final MapCell cell;
    final int level;
    final List<Tree> trees=new ArrayList<>();
    final List<Building> buildings=new ArrayList<>();
    final List<float[]> queries=new ArrayList<>();
    final List<Float> heights=new ArrayList<>();
    final Set<Long> requiredGrids=new HashSet<>();
    int sampled;
    private int sample(float x,float z){
        int east=(int)Math.floor((cell.east()*1000.0+x)/1000),north=(int)Math.floor((cell.north()*1000.0-z)/1000);
        requiredGrids.add(TerrainHeightSnapshot.key(east,north));
        int index=queries.size();queries.add(new float[]{x,z});heights.add(Float.NaN);return index;}
    private MapCellPlan(MapCell cell,int level){this.cell=cell;this.level=level;}

    private static final class Ring {
        final float[] x,z;
        float minX=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        Ring(JsonArray source){
            x=new float[source.size()];z=new float[source.size()];
            for(int i=0;i<x.length;i++){JsonArray point=source.get(i).getAsJsonArray();x[i]=point.get(0).getAsFloat();z[i]=point.get(1).getAsFloat();minX=Math.min(minX,x[i]);maxX=Math.max(maxX,x[i]);minZ=Math.min(minZ,z[i]);maxZ=Math.max(maxZ,z[i]);}
        }
        boolean contains(float px,float pz){
            if(px<minX||px>maxX||pz<minZ||pz>maxZ)return false;
            boolean inside=false;
            for(int i=0,j=x.length-1;i<x.length;j=i++)if((z[i]>pz)!=(z[j]>pz)&&px<(x[j]-x[i])*(pz-z[i])/(z[j]-z[i])+x[i])inside=!inside;
            return inside;
        }
    }
    private static final class Footprint {
        final Ring outer;
        final List<Ring> holes=new ArrayList<>();
        Footprint(JsonObject feature){outer=new Ring(feature.getAsJsonArray("p"));if(feature.has("holes"))for(JsonElement hole:feature.getAsJsonArray("holes"))holes.add(new Ring(hole.getAsJsonArray()));}
        boolean contains(float x,float z){if(!outer.contains(x,z))return false;for(Ring hole:holes)if(hole.contains(x,z))return false;return true;}
    }
    private static int bin(float x,float z){return Math.min(31,Math.max(0,(int)(x/32)))+32*Math.min(31,Math.max(0,(int)(-z/32)));}

    static MapCellPlan create(MapCell cell,int level,float playerX,float playerZ,float agl,float far,List<Vector3f> airports){
        MapCellPlan plan=new MapCellPlan(cell,level);
        Map<Integer,List<Footprint>> occupied=new HashMap<>();
        List<Vector3f> localAirports=new ArrayList<>();
        for(Vector3f airport:airports){float x=(float)(airport.x-cell.worldX()),z=(float)(airport.z-cell.worldZ());if(x>-700&&x<1700&&z>-1700&&z<700)localAirports.add(new Vector3f(x,0,z));}
        for(JsonElement element:cell.features()){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            JsonObject feature=element.getAsJsonObject();if(!feature.get("k").getAsString().equals("b"))continue;
            Footprint footprint=new Footprint(feature);Ring ring=footprint.outer;
            int x0=Math.max(0,(int)(ring.minX/32)),x1=Math.min(31,(int)(ring.maxX/32));
            int z0=Math.max(0,(int)(-ring.maxZ/32)),z1=Math.min(31,(int)(-ring.minZ/32));
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)occupied.computeIfAbsent(x+32*z,k->new ArrayList<>()).add(footprint);
            if(level<3){
                float cx=0,cz=0;for(int i=0;i<ring.x.length;i++){cx+=ring.x[i];cz+=ring.z[i];}cx/=ring.x.length;cz/=ring.z.length;
                boolean airport=false;
                for(Vector3f point:localAirports)if((point.x-cx)*(point.x-cx)+(point.z-cz)*(point.z-cz)<625||footprint.contains(point.x,point.z)){airport=true;break;}
                if(airport)continue;
            }
            int[] indices=new int[ring.x.length];for(int i=0;i<indices.length;i++)indices[i]=plan.sample(ring.x[i],ring.z[i]);
            plan.buildings.add(new Building(feature,indices));
        }
        Set<Long> treeGrid=new HashSet<>();
        for(JsonElement element:cell.features()){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            JsonObject feature=element.getAsJsonObject();String kind=feature.get("k").getAsString();
            if(kind.equals("t")){
                JsonArray point=feature.getAsJsonArray("p");float x=point.get(0).getAsFloat(),z=point.get(1).getAsFloat();
                long seed=MapCell.hash(Math.round((cell.east()*1000L+x)*10),Math.round((cell.north()*1000L-z)*10));
                plan.tree(x,z,feature.get("s").getAsInt(),seed,level,1);
            }else if(kind.equals("f")){
                Footprint forest=new Footprint(feature);Ring ring=forest.outer;int stride=level==3?3:1;
                // Iterate only this polygon's bounds; tiny wooded patches no longer scan a full kilometre.
                int ex0=(int)Math.floor((cell.east()*1000.0+Math.max(0,ring.minX)-4)/12)-1;
                int ex1=(int)Math.ceil((cell.east()*1000.0+Math.min(1000,ring.maxX)+4)/12)+1;
                int nz0=(int)Math.floor((cell.north()*1000.0-Math.min(0,ring.maxZ)-4)/12)-1;
                int nz1=(int)Math.ceil((cell.north()*1000.0-Math.max(-1000,ring.minZ)+4)/12)+1;
                for(int ex=ex0;ex<ex1;ex++)for(int nz=nz0;nz<nz1;nz++){
                    if(Math.floorMod(ex,stride)!=0||Math.floorMod(nz,stride)!=0)continue;
                    long seed=MapCell.hash(ex,nz);
                    float x=(float)(ex*12.0-cell.east()*1000.0)+(MapCell.random(seed,0)-.5f)*7;
                    float z=(float)(cell.north()*1000.0-nz*12.0)+(MapCell.random(seed,16)-.5f)*7;
                    if(x<0||x>=1000||z>0||z<=-1000||!forest.contains(x,z))continue;
                    long key=((long)ex<<32)^(nz&0xffffffffL);if(treeGrid.contains(key))continue;
                    boolean blocked=false;List<Footprint> candidates=occupied.get(bin(x,z));
                    if(candidates!=null)for(Footprint building:candidates)if(building.contains(x,z)){blocked=true;break;}
                    if(blocked)continue;treeGrid.add(key);
                    int species=feature.get("s").getAsInt();if(species==2)species=MapCell.random(seed,24)<.45f?1:0;
                    int actual=Math.max(level,WorldDetailSettings.lod((float)Math.hypot(cell.worldX()+x-playerX,cell.worldZ()+z-playerZ),agl,far));
                    plan.tree(x,z,species,seed,actual,level==3?2.9f:1);
                }
            }
        }
        return plan;
    }
    boolean sampleHeights(TerrainHeightSnapshot snapshot){
        sampled=0;
        for(int i=0;i<queries.size();i++){
            float[] point=queries.get(i);float value=snapshot.sample(cell.worldX()+point[0],cell.worldZ()+point[1]);
            if(!Float.isFinite(value)&&!snapshot.isUnavailable(cell.worldX()+point[0],cell.worldZ()+point[1]))return false;
            heights.set(i,value);sampled++;
        }
        return true;
    }
    private void tree(float x,float z,int species,long seed,int lod,float canopyScale){
        float scale=.72f+MapCell.random(seed,8)*.56f;
        trees.add(new Tree(x,z,species,scale*canopyScale,MapCell.random(seed,32)*(float)Math.PI*2,lod,sample(x,z)));
    }
}
