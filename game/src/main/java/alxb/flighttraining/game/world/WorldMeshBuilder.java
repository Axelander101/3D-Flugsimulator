package alxb.flighttraining.game.world;

import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.util.BufferUtils;
import java.nio.*;
import java.util.*;

/** Compact static cell meshes. No per-tree scene-graph nodes or per-house draw calls. */
final class WorldMeshBuilder {
    private float[] p=new float[4096],n=new float[4096],uv=new float[4096],c=new float[4096];
    private int count;
    private static float[] grow(float[] a,int needed){return needed>a.length?Arrays.copyOf(a,Math.max(needed,a.length*2)):a;}
    void vertex(float x,float y,float z,float nx,float ny,float nz,float u,float v,ColorRGBA color){
        p=grow(p,(count+1)*3);n=grow(n,(count+1)*3);uv=grow(uv,(count+1)*2);c=grow(c,(count+1)*4);
        int i=count*3;p[i]=x;p[i+1]=y;p[i+2]=z;n[i]=nx;n[i+1]=ny;n[i+2]=nz;
        uv[count*2]=u;uv[count*2+1]=v;i=count*4;c[i]=color.r;c[i+1]=color.g;c[i+2]=color.b;c[i+3]=color.a;count++;
    }
    void triangle(Vector3f a,Vector3f b,Vector3f d,float u,float v,ColorRGBA color){
        Vector3f normal=b.subtract(a).cross(d.subtract(a)).normalizeLocal();
        vertex(a.x,a.y,a.z,normal.x,normal.y,normal.z,0,0,color);
        vertex(b.x,b.y,b.z,normal.x,normal.y,normal.z,u,0,color);
        vertex(d.x,d.y,d.z,normal.x,normal.y,normal.z,u,v,color);
    }
    void quad(Vector3f a,Vector3f b,Vector3f d,Vector3f e,float u,float v,ColorRGBA color){
        Vector3f normal=b.subtract(a).cross(d.subtract(a)).normalizeLocal();
        Vector3f[] vertices={a,b,d,a,d,e};float[] us={0,u,u,0,u,0},vs={0,0,v,0,v,v};
        for(int i=0;i<6;i++){Vector3f q=vertices[i];vertex(q.x,q.y,q.z,normal.x,normal.y,normal.z,us[i],vs[i],color);}
    }
    void append(Geometry geometry,Matrix4f matrix,ColorRGBA tint){
        Mesh mesh=geometry.getMesh();FloatBuffer ps=mesh.getFloatBuffer(VertexBuffer.Type.Position);
        FloatBuffer ns=mesh.getFloatBuffer(VertexBuffer.Type.Normal),ts=mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
        FloatBuffer cs=mesh.getFloatBuffer(VertexBuffer.Type.Color);
        int[] tri=new int[3];Vector3f point=new Vector3f(),normal=new Vector3f();
        Matrix3f basis=matrix.toRotationMatrix();
        for(int t=0;t<mesh.getTriangleCount();t++){
            mesh.getTriangle(t,tri);
            for(int index:tri){
                point.set(ps.get(index*3),ps.get(index*3+1),ps.get(index*3+2));matrix.mult(point,point);
                if(ns!=null){normal.set(ns.get(index*3),ns.get(index*3+1),ns.get(index*3+2));basis.mult(normal,normal).normalizeLocal();}else normal.set(0,1,0);
                ColorRGBA color=tint;
                if(cs!=null)color=new ColorRGBA(cs.get(index*4)*tint.r,cs.get(index*4+1)*tint.g,cs.get(index*4+2)*tint.b,cs.get(index*4+3));
                vertex(point.x,point.y,point.z,normal.x,normal.y,normal.z,ts==null?0:ts.get(index*2),ts==null?0:ts.get(index*2+1),color);
            }
        }
    }
    Geometry finish(String name,com.jme3.material.Material material){
        if(count==0)return null;
        Mesh mesh=new Mesh();mesh.setBuffer(VertexBuffer.Type.Position,3,Arrays.copyOf(p,count*3));
        mesh.setBuffer(VertexBuffer.Type.Normal,3,Arrays.copyOf(n,count*3));mesh.setBuffer(VertexBuffer.Type.TexCoord,2,Arrays.copyOf(uv,count*2));
        mesh.setBuffer(VertexBuffer.Type.Color,4,Arrays.copyOf(c,count*4));mesh.setStatic();mesh.updateBound();
        Geometry geometry=new Geometry(name,mesh);geometry.setMaterial(material);return geometry;
    }
}
