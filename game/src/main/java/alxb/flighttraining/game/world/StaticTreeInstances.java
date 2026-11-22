package alxb.flighttraining.game.world;

import com.jme3.bounding.*;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.List;

/** Static hardware instances: one shared tree mesh, no Geometry object per tree. */
final class StaticTreeInstances extends InstancedGeometry {
    private final int count;
    private final float[] localData;
    private final BoundingVolume localBounds;
    private final Vector3f origin=new Vector3f();
    private boolean originSet;
    private final FloatBuffer data;
    StaticTreeInstances(String name,Geometry source,Material material,List<Matrix4f> transforms){
        super(name);setMesh(source.getMesh());setMaterial(material);count=transforms.size();localData=new float[count*16];
        BoundingVolume bounds=null;int offset=0;
        for(Matrix4f m:transforms){
            Quaternion q=new Quaternion().fromRotationMatrix(m.toRotationMatrix().invertLocal());
            float[] values={m.m00,m.m10,m.m20,q.getX(),m.m01,m.m11,m.m21,q.getY(),m.m02,m.m12,m.m22,q.getZ(),m.m03,m.m13,m.m23,q.getW()};
            System.arraycopy(values,0,localData,offset,16);offset+=16;
            BoundingVolume b=source.getMesh().getBound().transform(m,null);bounds=bounds==null?b:bounds.mergeLocal(b);
        }
        localBounds=bounds;data=BufferUtils.createFloatBuffer(localData.length);data.put(localData).flip();
        VertexBuffer buffer=new VertexBuffer(VertexBuffer.Type.InstanceData);buffer.setInstanced(true);
        buffer.setupData(VertexBuffer.Usage.Dynamic,16,VertexBuffer.Format.Float,data);setTransformUserInstanceData(buffer);
    }
    void setOrigin(Vector3f origin){
        if(originSet&&this.origin.equals(origin))return;
        originSet=true;this.origin.set(origin);data.clear();data.put(localData);
        for(int i=0;i<count;i++){int offset=i*16;data.put(offset+12,localData[offset+12]+origin.x);data.put(offset+13,localData[offset+13]+origin.y);data.put(offset+14,localData[offset+14]+origin.z);}
        data.flip();getTransformUserInstanceData().updateData(data);setBoundRefresh();
    }
    @Override public int getNumVisibleInstances(){return count;}
    @Override public int getNumInstances(){return count;}
    @Override public void updateInstances(){}
    @Override public void updateInstances(Camera camera){}
    @Override protected void updateWorldBound(){
        refreshFlags &= ~RF_BOUND;
        worldBound=localBounds.transform(new Transform(origin),worldBound);
    }
}
