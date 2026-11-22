package alxb.flighttraining.game.world;

import com.jme3.renderer.RenderManager;
import com.jme3.scene.*;
import com.jme3.scene.instancing.InstancedGeometry;
import java.util.*;

/** OpenGL work only on the render thread, one material/buffer at a time. */
final class WorldUploadQueue {
    static final class Budget {
        private final long deadline=System.nanoTime()+2_000_000L;
        private int bytes=512*1024,steps=32,materials=2;
        boolean available(){return steps>0&&System.nanoTime()<deadline;}
        boolean material(){if(!available()||materials==0)return false;materials--;steps--;return true;}
        boolean buffer(int size){if(!available()||size>bytes)return false;bytes-=size;steps--;return true;}
    }
    private final ArrayDeque<Object> work=new ArrayDeque<>();
    private final List<Geometry> geometries=new ArrayList<>();
    private final Set<com.jme3.material.Material> materials=Collections.newSetFromMap(new IdentityHashMap<>());
    void add(Spatial model){model.depthFirstTraversal(s->{if(s instanceof Geometry g){geometries.add(g);if(materials.add(g.getMaterial()))work.add(g);addBuffers(g);}});}
    private void addBuffers(Geometry geometry){
        for(VertexBuffer buffer:geometry.getMesh().getBufferList())if(uploadable(buffer))work.add(buffer);
        if(geometry instanceof InstancedGeometry instances){
            VertexBuffer[] buffers=instances.getAllInstanceData();if(buffers!=null)for(VertexBuffer buffer:buffers)if(uploadable(buffer))work.add(buffer);
        }
    }
    private static boolean uploadable(VertexBuffer b){return b!=null&&b.getData()!=null&&b.getUsage()!=VertexBuffer.Usage.CpuOnly;}
    static int bytes(VertexBuffer b){return b.getData().limit()*switch(b.getFormat()){
        case Byte,UnsignedByte->1;case Short,UnsignedShort,Half->2;case Double->8;default->4;};}
    boolean advance(RenderManager manager,Budget budget){
        while(!work.isEmpty()&&budget.available()){
            Object next=work.peek();
            if(next instanceof Geometry g){
                if(!budget.material())break;
                // preloadScene would upload ALL buffers at once; separate the shader/texture step.
                g.getMaterial().preload(manager,g);work.remove();
            }else{
                VertexBuffer buffer=(VertexBuffer)next;
                if(!buffer.isUpdateNeeded()){work.remove();continue;}
                if(!budget.buffer(bytes(buffer)))break;
                manager.getRenderer().updateBufferData(buffer);work.remove();
            }
        }
        if(!work.isEmpty())return false;
        // A floating-origin change may invalidate an instance buffer already processed above.
        for(Geometry g:geometries){
            for(VertexBuffer b:g.getMesh().getBufferList())if(uploadable(b)&&b.isUpdateNeeded())work.add(b);
            if(g instanceof InstancedGeometry instances){VertexBuffer[] bs=instances.getAllInstanceData();if(bs!=null)for(VertexBuffer b:bs)if(uploadable(b)&&b.isUpdateNeeded())work.add(b);}
        }
        return work.isEmpty();
    }
    void clear(){work.clear();geometries.clear();materials.clear();}
}
