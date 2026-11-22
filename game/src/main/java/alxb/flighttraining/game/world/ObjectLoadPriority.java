package alxb.flighttraining.game.world;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/** One policy for CPU admission, missing-ground resumes, metadata and GPU uploads. */
final class ObjectLoadPriority {
    static final int VISIBLE=0, AHEAD=1, SURROUNDINGS=2, DETAIL=3;
    private Vector3f absolute=new Vector3f(),forward=new Vector3f(0,0,-1),predicted=new Vector3f();
    private Camera camera;
    private WorldCoordinates coordinates;
    private TerrainHeightSnapshot heights;
    private float range=5000,ground;
    void update(Vector3f absolute,Vector3f forward,Vector3f predicted,float range,float ground,Camera camera,WorldCoordinates coordinates,TerrainHeightSnapshot heights){
        this.absolute=absolute;this.forward=forward;this.predicted=predicted;this.range=range;this.ground=ground;
        this.camera=camera;this.coordinates=coordinates;this.heights=heights;
    }
    boolean visible(double x,double z,float halfSize){
        if(camera==null)return true;
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        // Sample the terrain envelope, not just a point: mountain slopes can intersect the view.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            float y=heights==null?ground:heights.sample(x+dx*halfSize,z+dz*halfSize);
            if(Float.isFinite(y)){min=Math.min(min,y);max=Math.max(max,y);}
        }
        if(!Float.isFinite(min)){min=ground-300;max=ground+300;}
        Vector3f center=coordinates.local(x,(min+max)*.5f+70,z);
        BoundingBox box=new BoundingBox(center,halfSize,(max-min)*.5f+150,halfSize);
        int state=camera.getPlaneState();
        try{camera.setPlaneState(0);return camera.contains(box)!=Camera.FrustumIntersect.Outside;}
        finally{camera.setPlaneState(state);}
    }
    float score(double x,double z,float distance,boolean detail){
        int tier;
        if(detail)tier=visible(x,z,500)?DETAIL:DETAIL+1;
        else if(distance<range&&(distance<250||visible(x,z,500)))tier=VISIBLE;
        else{
            double dx=x-absolute.x,dz=z-absolute.z,length=Math.hypot(dx,dz);
            double facing=length==0?1:(dx*forward.x+dz*forward.z)/length;
            tier=facing>.45?AHEAD:SURROUNDINGS;
        }
        double current=Math.hypot(x-absolute.x,z-absolute.z),future=Math.hypot(x-predicted.x,z-predicted.z);
        return tier*100000f+(float)Math.min(90000,Math.min(current,future+500));
    }
    static int tier(float score){return(int)(score/100000);}
}
