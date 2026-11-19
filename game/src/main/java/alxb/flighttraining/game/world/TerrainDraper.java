package alxb.flighttraining.game.world;
import com.jme3.scene.*;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.*;
/** Lange Boden-Dreiecke vor der Terrain-Anpassung in höchstens 25-m-Kanten zerlegen. */
final class TerrainDraper {
 private record Attribute(VertexBuffer.Type type,int size,int offset){}
 private final List<Attribute> attributes=new ArrayList<>();
 private final ArrayList<float[]> vertices=new ArrayList<>();
 private int stride;
 private float[] vertex(Mesh mesh,int index){float[] v=new float[stride];for(Attribute a:attributes){FloatBuffer b=mesh.getFloatBuffer(a.type);for(int i=0;i<a.size;i++)v[a.offset+i]=b.get(index*a.size+i);}return v;}
 private static float edge(float[] a,float[] b){float x=a[0]-b[0],z=a[2]-b[2];return x*x+z*z;}
 private float[] mid(float[] a,float[] b){float[] m=new float[stride];for(int i=0;i<stride;i++)m[i]=(a[i]+b[i])*0.5f;return m;}
 private void split(float[] a,float[] b,float[] c,int depth){
  if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
  if(depth<9&&Math.max(edge(a,b),Math.max(edge(b,c),edge(c,a)))>625&&vertices.size()<250000){
   float[] ab=mid(a,b),bc=mid(b,c),ca=mid(c,a);split(a,ab,ca,depth+1);split(ab,b,bc,depth+1);split(ca,bc,c,depth+1);split(ab,bc,ca,depth+1);
  }else{vertices.add(a);vertices.add(b);vertices.add(c);}
 }
 static Mesh subdivide(Mesh source){
  if(source.getMode()!=Mesh.Mode.Triangles)return source;
  TerrainDraper d=new TerrainDraper();
  for(VertexBuffer.Type type:new VertexBuffer.Type[]{VertexBuffer.Type.Position,VertexBuffer.Type.Normal,VertexBuffer.Type.TexCoord,VertexBuffer.Type.Color,VertexBuffer.Type.Tangent}){
   VertexBuffer b=source.getBuffer(type);if(b!=null&&b.getData() instanceof FloatBuffer){d.attributes.add(new Attribute(type,b.getNumComponents(),d.stride));d.stride+=b.getNumComponents();}
  }
  IndexBuffer indices=source.getIndexBuffer();for(int i=0;i<indices.size();i+=3)d.split(d.vertex(source,indices.get(i)),d.vertex(source,indices.get(i+1)),d.vertex(source,indices.get(i+2)),0);
  Mesh result=new Mesh();for(Attribute a:d.attributes){FloatBuffer data=BufferUtils.createFloatBuffer(d.vertices.size()*a.size);for(float[] v:d.vertices)for(int i=0;i<a.size;i++)data.put(v[a.offset+i]);data.flip();result.setBuffer(a.type,a.size,data);}result.updateBound();return result;
 }
}
