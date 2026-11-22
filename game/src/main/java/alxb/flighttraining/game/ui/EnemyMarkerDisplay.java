package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.enemy.Enemy;
import com.jme3.asset.AssetManager;
import com.jme3.material.*;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import java.util.*;

/** Small screen-space training aid, projected from actual enemy world positions. */
public final class EnemyMarkerDisplay {
    private final Node display = new Node("EnemyMarkers");
    private final List<Geometry> markers = new ArrayList<>();
    private final Material material;
    private final Enemy enemies;
    private final Camera camera;
    public EnemyMarkerDisplay(AssetManager assets, Node gui, Camera camera, Enemy enemies) {
        this.camera=camera;this.enemies=enemies;
        material=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color",new ColorRGBA(.15f,1,.35f,1));
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false);
        material.getAdditionalRenderState().setDepthWrite(false);
        display.setQueueBucket(RenderQueue.Bucket.Gui);gui.attachChild(display);hide();
    }
    public void hide() {display.setCullHint(Spatial.CullHint.Always);}
    public void update(boolean active) {
        if(!active){hide();return;}
        display.setCullHint(Spatial.CullHint.Inherit);
        while(markers.size()<enemies.getCount()){
            Geometry marker=new Geometry("EnemyDiamond_"+markers.size(),diamond());marker.setMaterial(material);
            display.attachChild(marker);markers.add(marker);
        }
        while(markers.size()>enemies.getCount())markers.remove(markers.size()-1).removeFromParent();
        for(int i=0;i<markers.size();i++){
            Geometry marker=markers.get(i);Vector3f world=enemies.getPosition(i);
            Vector3f offset=world.subtract(camera.getLocation());Vector3f pixel=camera.getScreenCoordinates(world);
            boolean visible=enemies.getPlane(i).getParent()!=null
                    && offset.dot(camera.getDirection())>camera.getFrustumNear()
                    && offset.lengthSquared()<=camera.getFrustumFar()*camera.getFrustumFar()
                    && Float.isFinite(pixel.x)&&Float.isFinite(pixel.y)&&pixel.z>=0&&pixel.z<=1
                    &&pixel.x>=8&&pixel.x<=camera.getWidth()-8&&pixel.y>=8&&pixel.y<=camera.getHeight()-8;
            marker.setCullHint(visible?Spatial.CullHint.Inherit:Spatial.CullHint.Always);
            if(visible)marker.setLocalTranslation(pixel.x,pixel.y,0);
        }
    }
    private static Mesh diamond(){
        float[] points=new float[48];int[] indices=new int[24];
        Vector2f[] corners={new Vector2f(0,7),new Vector2f(7,0),new Vector2f(0,-7),new Vector2f(-7,0)};
        for(int i=0;i<4;i++){
            Vector2f a=corners[i],b=corners[(i+1)%4];Vector2f edge=b.subtract(a).normalizeLocal();
            Vector2f normal=new Vector2f(-edge.y,edge.x).multLocal(.7f);
            Vector2f[] quad={a.add(normal),b.add(normal),b.subtract(normal),a.subtract(normal)};
            for(int j=0;j<4;j++){points[i*12+j*3]=quad[j].x;points[i*12+j*3+1]=quad[j].y;}
            int k=i*4;int[] order={k,k+1,k+2,k,k+2,k+3};System.arraycopy(order,0,indices,i*6,6);
        }
        Mesh mesh=new Mesh();mesh.setBuffer(VertexBuffer.Type.Position,3,points);mesh.setBuffer(VertexBuffer.Type.Index,3,indices);
        mesh.setStatic();mesh.updateBound();return mesh;
    }
}
