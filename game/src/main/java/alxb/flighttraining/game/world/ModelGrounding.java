package alxb.flighttraining.game.world;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.function.Function;
/** Gebäude als Ganzes versetzen; Bodenflächen pro Vertex an das Terrain anlegen. */
final class ModelGrounding {
 private final ArrayDeque<Spatial> pending=new ArrayDeque<>();
 private final Function<Vector3f,Float> height;
 private int count;
 private float maxError;
 private int pavementVertices;
 ModelGrounding(Spatial model,boolean airport,Function<Vector3f,Float> height){
  this.height=height;
  if(airport)collect(model);else pending.add(model);
 }
 private void collect(Spatial s){
  String name=s.getName()==null?"":s.getName();
  if(name.startsWith("building_")||name.startsWith("pavement_")){pending.add(s);return;}
  if(s instanceof Node n)for(Spatial child:n.getChildren())collect(child);
 }
 boolean step(){
  long end=System.nanoTime()+2_000_000;
  while(!pending.isEmpty() && System.nanoTime()<end){
   Spatial s=pending.peek();s.updateGeometricState();
   boolean ok=s.getName()!=null&&s.getName().startsWith("pavement_")?pavement(s):building(s);
   if(!ok)return false;
   pending.remove();count++;
  }
  return pending.isEmpty();
 }
 private boolean building(Spatial s){
  if(!(s.getWorldBound() instanceof BoundingBox b))return true;
  Vector3f center=b.getCenter();float lowest=Float.POSITIVE_INFINITY;
  for(float dx:new float[]{-b.getXExtent(),0,b.getXExtent()})for(float dz:new float[]{-b.getZExtent(),0,b.getZExtent()}){
   float h=height.apply(new Vector3f(center.x+dx,0,center.z+dz));
   if(!Float.isFinite(h))return false;
   lowest=Math.min(lowest,h);
  }
  float correction=lowest-(center.y-b.getYExtent());
  Vector3f world=s.getWorldTranslation().add(0,correction,0);
  s.setLocalTranslation(s.getParent()==null?world:s.getParent().worldToLocal(world,new Vector3f()));
  s.updateGeometricState();
  if(s.getWorldBound() instanceof BoundingBox updated)maxError=Math.max(maxError,Math.abs(updated.getCenter().y-updated.getYExtent()-lowest));
  return true;
 }
 private boolean pavement(Spatial s){
  List<Geometry> geometries=new ArrayList<>();s.depthFirstTraversal(p->{if(p instanceof Geometry g)geometries.add(g);});
  // Erst alle Messwerte sammeln: bei fehlender Kachel nichts halb verformen.
  Map<Geometry,float[]> updates=new LinkedHashMap<>();
  for(Geometry g:geometries){
   FloatBuffer data=g.getMesh().getFloatBuffer(VertexBuffer.Type.Position);float[] result=new float[data.limit()];
   for(int i=0;i<result.length;i+=3){
    Vector3f world=g.localToWorld(new Vector3f(data.get(i),data.get(i+1),data.get(i+2)),new Vector3f());
    float ground=height.apply(world);if(!Float.isFinite(ground))return false;
    world.y=ground+0.08f;
    Vector3f local=g.worldToLocal(world,new Vector3f());result[i]=local.x;result[i+1]=local.y;result[i+2]=local.z;
    Vector3f reconstructed=g.localToWorld(local,new Vector3f());maxError=Math.max(maxError,Math.abs(reconstructed.y-world.y));pavementVertices++;
   }
   updates.put(g,result);
  }
  updates.forEach((g,positions)->{Mesh copy=g.getMesh().deepClone();copy.setBuffer(VertexBuffer.Type.Position,3,positions);copy.updateBound();copy.setStatic();g.setMesh(copy);});
  s.updateGeometricState();return true;
 }
 float maxError(){return maxError;}
 int pavementVertices(){return pavementVertices;}
 int completed(){return count;}
}
