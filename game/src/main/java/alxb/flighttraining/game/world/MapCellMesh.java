package alxb.flighttraining.game.world;

import com.google.gson.*;
import com.jme3.asset.AssetManager;
import com.jme3.material.*;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.texture.Texture;
import java.util.*;

/** Background construction of natural foliage and footprint-sized buildings. */
final class MapCellMesh {
    private final AssetManager assets;
    private final Map<String,List<Geometry>> prototypes=new HashMap<>();
    private final Map<String,Material> foliage=new HashMap<>();
    private final Material facade,roof;
    private final Map<Material,Material> instanceMaterials=new HashMap<>();
    record Built(Node node,int trees,int buildings,int lod) {}
    MapCellMesh(AssetManager assets){
        this.assets=assets;
        facade=lit();Texture texture=assets.loadTexture("Textures/SwissNature/facade.png");texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMinFilter(Texture.MinFilter.Trilinear);facade.setTexture("DiffuseMap",texture);
        roof=lit();
    }
    private Material lit(){Material m=new Material(assets,"MatDefs/WorldObjects/Lighting.j3md");
        m.setBoolean("UseMaterialColors",true);m.setBoolean("UseVertexColor",true);
        m.setColor("Diffuse",ColorRGBA.White);m.setColor("Ambient",ColorRGBA.White);m.setColor("Specular",ColorRGBA.Black);return m;}
    private Material foliage(String name){return foliage.computeIfAbsent(name,key->{
        Material m=name.startsWith("Impostor_")?new Material(assets,"MatDefs/WorldObjects/Unshaded.j3md"):lit();
        if(name.startsWith("Impostor_"))m.setColor("Color",new ColorRGBA(1.8f,1.8f,1.8f,1));
        if(!name.equals("Bark")){
            m.setTexture(name.startsWith("Impostor_")?"ColorMap":"DiffuseMap",assets.loadTexture("Textures/SwissNature/"+(name.startsWith("Impostor_")?name.substring(9):(name.equals("Leaves")?"beech":"spruce"))+".png"));
            m.setFloat("AlphaDiscardThreshold",.45f);
        }else{m.setColor("Diffuse",new ColorRGBA(.27f,.21f,.14f,1));m.setColor("Ambient",new ColorRGBA(.27f,.21f,.14f,1));}
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);return m;
    });}
    private List<Geometry> prototype(String name){return prototypes.computeIfAbsent(name,key->{
        Spatial s=assets.loadModel("Textures/SwissNature/Models/"+name+".glb");s.updateGeometricState();
        List<Geometry> result=new ArrayList<>();s.depthFirstTraversal(part->{if(part instanceof Geometry g){
            boolean oversized=false;for(VertexBuffer buffer:g.getMesh().getBufferList())if(buffer.getData()!=null&&WorldUploadQueue.bytes(buffer)>98304)oversized=true;
            if(oversized)for(Geometry group:WorldRenderGroups.split(g)){
                group.setLocalTransform(g.getWorldTransform());group.updateGeometricState();result.add(group);
            }else result.add(g);
        }});return result;
    });}
    Built build(MapCellPlan plan){
        Node result=new Node("MapCell_"+plan.cell.key());Map<Material,WorldMeshBuilder> meshes=new LinkedHashMap<>();
        record InstanceGroup(Geometry geometry,int x,int z) {}
        Map<InstanceGroup,List<Matrix4f>> instances=new LinkedHashMap<>();
        Map<Geometry,Material> instanceTypes=new HashMap<>();
        for(MapCellPlan.Tree t:plan.trees){
            float y=plan.heights.get(t.heightIndex());if(!Float.isFinite(y))continue;
            long seed=MapCell.hash(Math.round(t.x()),Math.round(t.z()));
            String species=t.species()==0?"Beech":((seed&3)==0?"Fir":(seed&15)==1?"Larch":"Spruce");
            String name=species+"_"+(seed&1)+"_LOD"+t.lod();
            float verticalScale=t.scale()/(plan.level==3?2.9f:1f);
            Transform transform=new Transform(new Vector3f(t.x(),y,t.z()),new Quaternion().fromAngleAxis(t.angle(),Vector3f.UNIT_Y),new Vector3f(t.scale(),verticalScale,t.scale()));
            Matrix4f model=transform.toTransformMatrix();
            for(Geometry geometry:prototype(name)){
                String mat=geometry.getMaterial().getName();
                if(mat!=null)mat=mat.replaceFirst("\\.\\d+$", "");
                String kind=mat!=null&&mat.startsWith("Impostor_")?mat:mat!=null&&mat.contains("Bark")?"Bark":species.equals("Beech")?"Leaves":"Needles";
                Material material=foliage(kind);WorldMeshBuilder mesh=meshes.computeIfAbsent(material,k->new WorldMeshBuilder());
                if(t.lod()<2){
                    InstanceGroup group=new InstanceGroup(geometry,(int)Math.floor(t.x()/WorldRenderGroups.GROUP_SIZE),(int)Math.floor(t.z()/WorldRenderGroups.GROUP_SIZE));
                    instances.computeIfAbsent(group,k->new ArrayList<>()).add(model.mult(geometry.getWorldTransform().toTransformMatrix()));
                    instanceTypes.put(geometry,material);continue;
                }
                float tint=.88f+MapCell.random(seed,8)*.18f;
                // Exported node transforms are included (Blender Z-up -> glTF Y-up).
                mesh.append(geometry,model.mult(geometry.getWorldTransform().toTransformMatrix()),new ColorRGBA(tint,tint,tint,1));
            }
        }
        for(var e:instances.entrySet()){
            Material material=instanceMaterials.computeIfAbsent(instanceTypes.get(e.getKey().geometry()),m->{Material copy=m.clone();copy.setBoolean("UseInstancing",true);copy.setBoolean("UseVertexColor",false);return copy;});
            for(int start=0;start<e.getValue().size();start+=WorldRenderGroups.MAX_INSTANCES){
                List<Matrix4f> group=e.getValue().subList(start,Math.min(start+WorldRenderGroups.MAX_INSTANCES,e.getValue().size()));
                result.attachChild(new StaticTreeInstances("TreeInstances",e.getKey().geometry(),material,group));
            }
        }
        WorldMeshBuilder walls=meshes.computeIfAbsent(facade,k->new WorldMeshBuilder());
        WorldMeshBuilder roofs=meshes.computeIfAbsent(roof,k->new WorldMeshBuilder());
        for(MapCellPlan.Building b:plan.buildings)building(plan,b,walls,roofs);
        int i=0;for(var e:meshes.entrySet()){Geometry g=e.getValue().finish("MapMesh_"+i++,e.getKey());if(g!=null)for(Geometry part:WorldRenderGroups.split(g))result.attachChild(part);}
        result.updateGeometricState();return new Built(result,plan.trees.size(),plan.buildings.size(),plan.level);
    }
    private static List<Vector3f> ring(JsonArray p,float y){
        List<Vector3f> points=new ArrayList<>();for(JsonElement value:p){JsonArray a=value.getAsJsonArray();points.add(new Vector3f(a.get(0).getAsFloat(),y,a.get(1).getAsFloat()));}
        float area=0;for(int i=0;i<points.size();i++){Vector3f a=points.get(i),b=points.get((i+1)%points.size());area+=a.x*b.z-b.x*a.z;}
        if(area>0)Collections.reverse(points);return points;
    }
    private void building(MapCellPlan plan,MapCellPlan.Building b,WorldMeshBuilder walls,WorldMeshBuilder roofs){
        float ground=Float.POSITIVE_INFINITY;for(int i:b.groundIndices()){float y=plan.heights.get(i);if(!Float.isFinite(y))return;ground=Math.min(ground,y);}
        JsonObject f=b.feature();float minimum=f.has("min")?f.get("min").getAsFloat():0;
        float height=f.get("h").getAsFloat();float bottom=ground+minimum-.3f,top=ground+Math.max(minimum+2,height);
        long seed=MapCell.hash(f.get("id").getAsString().hashCode(),13);
        ColorRGBA tint=new ColorRGBA(.83f+MapCell.random(seed,0)*.17f,.82f+MapCell.random(seed,12)*.18f,.78f+MapCell.random(seed,24)*.2f,1);
        List<Vector3f> outline=ring(f.getAsJsonArray("p"),bottom);
        List<List<Vector3f>> rings=new ArrayList<>();rings.add(outline);
        if(f.has("holes"))for(JsonElement h:f.getAsJsonArray("holes")){List<Vector3f> hole=ring(h.getAsJsonArray(),bottom);Collections.reverse(hole);rings.add(hole);}
        for(List<Vector3f> points:rings)for(int i=0;i<points.size();i++){
            Vector3f a=points.get(i),d=points.get((i+1)%points.size());
            walls.quad(a,d,new Vector3f(d.x,top,d.z),new Vector3f(a.x,top,a.z),a.distance(d)/4,(top-bottom)/4,tint);
        }
        ColorRGBA roofColor=new ColorRGBA(.25f+MapCell.random(seed,0)*.16f,.17f+MapCell.random(seed,12)*.12f,.13f+MapCell.random(seed,24)*.10f,1);
        float area=0;for(int i=0;i<outline.size();i++){Vector3f a=outline.get(i),d=outline.get((i+1)%outline.size());area+=a.x*d.z-d.x*a.z;}
        // Large industrial roofs use metal/bitumen tones rather than small-house roof tiles.
        if(Math.abs(area)>1000 || f.get("roof").getAsString().equals("flat"))
            roofColor=new ColorRGBA(.30f+MapCell.random(seed,0)*.1f,.32f+MapCell.random(seed,12)*.1f,.33f+MapCell.random(seed,24)*.1f,1);
        // Rectangular houses get a pitched roof; complex mapped footprints keep a fitted flat roof.
        if(outline.size()==4 && !f.get("roof").getAsString().equals("flat") && rings.size()==1 && plan.level<3){
            Vector3f a=outline.get(0).clone(),d=outline.get(1).clone(),e=outline.get(2).clone(),g=outline.get(3).clone();
            if(a.distance(d)<d.distance(e)){Vector3f temp=a;a=d;d=e;e=g;g=temp;}
            float rise=Math.min(3.5f,Math.max(1.1f,a.distance(g)*.3f));a.y=d.y=e.y=g.y=top;
            Vector3f r1=a.add(g).multLocal(.5f).addLocal(0,rise,0),r2=d.add(e).multLocal(.5f).addLocal(0,rise,0);
            roofs.quad(a,d,r2,r1,1,1,roofColor);roofs.quad(r1,r2,e,g,1,1,roofColor);
            roofs.triangle(a,r1,g,1,1,tint);roofs.triangle(d,e,r2,1,1,tint);
            if(plan.level==0){
                float x=(a.x+d.x+e.x+g.x)*.25f,z=(a.z+d.z+e.z+g.z)*.25f;
                box(roofs,x+.7f,top+rise*.55f,z+.5f,.65f,2,.65f,roofColor);
            }
        }else if(f.has("tri")){
            for(JsonElement tr:f.getAsJsonArray("tri")){JsonArray t=tr.getAsJsonArray();Vector3f[] v=new Vector3f[3];for(int k=0;k<3;k++){JsonArray p=t.get(k).getAsJsonArray();v[k]=new Vector3f(p.get(0).getAsFloat(),top,p.get(1).getAsFloat());}roofs.triangle(v[0],v[1],v[2],1,1,roofColor);}
        }else triangulate(roofs,ring(f.getAsJsonArray("p"),top),roofColor);
    }
    private static void box(WorldMeshBuilder b,float x,float y,float z,float sx,float sy,float sz,ColorRGBA color){
        Vector3f[] p={new Vector3f(x,y,z),new Vector3f(x+sx,y,z),new Vector3f(x+sx,y,z+sz),new Vector3f(x,y,z+sz),new Vector3f(x,y+sy,z),new Vector3f(x+sx,y+sy,z),new Vector3f(x+sx,y+sy,z+sz),new Vector3f(x,y+sy,z+sz)};
        for(int[] f:new int[][]{{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{4,5,6,7}})b.quad(p[f[0]],p[f[1]],p[f[2]],p[f[3]],1,1,color);
    }
    // Ear clipping follows the actual concave footprint instead of a bounding-box roof.
    private static void triangulate(WorldMeshBuilder mesh,List<Vector3f> p,ColorRGBA color){
        int guard=p.size()*p.size();
        while(p.size()>2&&guard-->0){boolean clipped=false;
            for(int i=0;i<p.size();i++){
                Vector3f a=p.get((i+p.size()-1)%p.size()),b=p.get(i),c=p.get((i+1)%p.size());
                if(cross(a,b,c)>=-.0001f)continue;
                boolean contains=false;for(Vector3f q:p)if(q!=a&&q!=b&&q!=c&&cross(a,b,q)<=0&&cross(b,c,q)<=0&&cross(c,a,q)<=0){contains=true;break;}
                if(!contains){mesh.triangle(a,b,c,1,1,color);p.remove(i);clipped=true;break;}
            }if(!clipped)break;
        }
    }
    private static float cross(Vector3f a,Vector3f b,Vector3f c){return(b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);}
}
