package alxb.flighttraining.game.world;

import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Worker-only partitioning: small spatial groups, bounded individual GPU buffers. */
final class WorldRenderGroups {
    static final int MAX_VERTICES=6144, MAX_INSTANCES=256;
    static final float GROUP_SIZE=250f;
    private record Cell(int x,int z) {}
    static List<Geometry> split(Geometry source){
        Mesh mesh=source.getMesh();
        if(mesh.getMode()!=Mesh.Mode.Triangles)throw new IllegalArgumentException("Static world geometry must be triangles: "+source.getName());
        FloatBuffer positions=mesh.getFloatBuffer(VertexBuffer.Type.Position);
        Map<Cell,List<Integer>> groups=new LinkedHashMap<>();int[] tri=new int[3];
        for(int t=0;t<mesh.getTriangleCount();t++){
            mesh.getTriangle(t,tri);
            float x=0,z=0;for(int i:tri){x+=positions.get(i*3);z+=positions.get(i*3+2);}
            Cell cell=new Cell((int)Math.floor(x/(3*GROUP_SIZE)),(int)Math.floor(z/(3*GROUP_SIZE)));
            groups.computeIfAbsent(cell,k->new ArrayList<>()).add(t);
        }
        List<Geometry> result=new ArrayList<>();int part=0;
        for(List<Integer> triangles:groups.values())for(int start=0;start<triangles.size();start+=MAX_VERTICES/3){
            int length=Math.min(MAX_VERTICES/3,triangles.size()-start),count=length*3;Mesh output=new Mesh();
            List<VertexBuffer> inputs=new ArrayList<>(),outputs=new ArrayList<>();
            for(VertexBuffer input:mesh.getBufferList()){
                if(input.getBufferType()==VertexBuffer.Type.Index||input.getData()==null)continue;
                if(input.getStride()!=0)throw new IllegalArgumentException("Interleaved world mesh: "+source.getName());
                VertexBuffer buffer=new VertexBuffer(input.getBufferType());
                buffer.setupData(VertexBuffer.Usage.Static,input.getNumComponents(),input.getFormat(),VertexBuffer.createBuffer(input.getFormat(),input.getNumComponents(),count));
                buffer.setNormalized(input.isNormalized());output.setBuffer(buffer);inputs.add(input);outputs.add(buffer);
            }
            int destination=0;
            for(int t=start;t<start+length;t++){
                mesh.getTriangle(triangles.get(t),tri);
                for(int index:tri){for(int b=0;b<inputs.size();b++)inputs.get(b).copyElement(index,outputs.get(b),destination);destination++;}
            }
            output.updateCounts();output.updateBound();
            Geometry geometry=new Geometry(source.getName()+"_part"+part++,output);
            geometry.setMaterial(source.getMaterial());geometry.setLocalTransform(source.getLocalTransform());
            geometry.setQueueBucket(source.getQueueBucket());geometry.setShadowMode(source.getShadowMode());
            result.add(geometry);
        }
        return result;
    }
    static Spatial prepare(Spatial model){
        if(model instanceof Node parent)jme3tools.optimize.GeometryBatchFactory.optimize(parent,false);
        List<Geometry> geometries=new ArrayList<>();model.depthFirstTraversal(s->{if(s instanceof Geometry g)geometries.add(g);});
        if(model instanceof Geometry g){Node result=new Node(g.getName());for(Geometry part:split(g))result.attachChild(part);result.updateGeometricState();return result;}
        for(Geometry geometry:geometries){Node parent=geometry.getParent();List<Geometry> parts=split(geometry);geometry.removeFromParent();for(Geometry part:parts)parent.attachChild(part);}
        model.updateGeometricState();return model;
    }
}
