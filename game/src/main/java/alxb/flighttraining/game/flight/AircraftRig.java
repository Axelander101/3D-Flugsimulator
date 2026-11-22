package alxb.flighttraining.game.flight;

import com.jme3.anim.*;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;
import java.util.*;

/** Mechanical poses sampled from the model's existing clips, independently of the cannon action. */
public final class AircraftRig {
    private final AircraftSystems systems;
    private final SkinningControl skin;
    private final Map<String,Joint> joints=new HashMap<>();
    private final Map<String,Transform> base=new HashMap<>(), extended=new HashMap<>(), open=new HashMap<>(), folded=new HashMap<>();
    private final Transform interpolated=new Transform();
    public AircraftRig(Spatial model,AircraftSystems systems){
        this.systems=systems;
        SkinningControl[] found={null};AnimComposer[] composer={null};
        model.depthFirstTraversal(s->{
            if(s.getControl(SkinningControl.class)!=null)found[0]=s.getControl(SkinningControl.class);
            if(s.getControl(AnimComposer.class)!=null)composer[0]=s.getControl(AnimComposer.class);
        });
        skin=Objects.requireNonNull(found[0],"Aircraft skin missing");
        for(Joint joint:skin.getArmature().getJointList()){
            joints.put(joint.getName(),joint);base.put(joint.getName(),joint.getInitialTransform().clone());
        }
        if(composer[0]==null)throw new IllegalStateException("Aircraft animation clips missing");
        sample(composer[0],"03_Gear_Retract_Extend",0,extended);
        sample(composer[0],"04_Canopy_Airbrake_Hook",44d/30,open);
        sample(composer[0],"02_Wing_Fold",39d/30,folded);
        // Start poses of these clips are closed/unfolded even if another clip last changed them.
        sample(composer[0],"04_Canopy_Airbrake_Hook",0,base);
        sample(composer[0],"02_Wing_Fold",0,base);
    }
    private void sample(AnimComposer composer,String clipName,double time,Map<String,Transform> poses){
        AnimClip clip=composer.getAnimClip(clipName);
        if(clip==null)throw new IllegalStateException("Missing aircraft clip: "+clipName);
        for(AnimTrack<?> track:clip.getTracks())if(track instanceof TransformTrack transform && transform.getTarget() instanceof Joint joint){
            String name=joint.getName();
            boolean relevant=clipName.startsWith("03_")?name.startsWith("Gear")
                    :clipName.startsWith("02_")?name.startsWith("WingFold.")
                    :name.equals("Canopy")||name.equals("Airbrake")||name.equals("Hook");
            if(!relevant)continue;
            Transform pose=joint.getInitialTransform().clone();transform.getDataAtTime(time,pose);poses.put(joint.getName(),pose);
        }
    }
    private void pose(String name,Map<String,Transform> from,Map<String,Transform> to,float amount){
        Joint joint=joints.get(name);Transform a=from.get(name),b=to.get(name);
        if(joint==null||a==null||b==null)return;
        interpolated.interpolateTransforms(a,b,amount);joint.setLocalTransform(interpolated);
    }
    private void rotate(String name,Vector3f axis,float angle){
        Joint joint=joints.get(name);if(joint==null)return;
        joint.setLocalTransform(base.get(name));
        joint.setLocalRotation(base.get(name).getRotation().mult(new Quaternion().fromAngleAxis(angle,axis)));
    }
    public void update(){
        for(String side:new String[]{"N","L","R"}){
            // Imported initial gear pose is UP. Authored clip time zero is DOWN, with correct strut translations.
            pose("Gear."+side,base,extended,systems.getGear());
            pose("GearDoor."+side,base,extended,systems.getGear());
            rotate("Wheel."+side,Vector3f.UNIT_X,side.equals("N")?systems.getNoseWheelAngle():systems.getWheelAngle());
        }
        rotate("Steering.N",Vector3f.UNIT_Z,systems.getSteering());
        pose("Airbrake",base,open,systems.getAirbrake());
        pose("Canopy",base,open,systems.getCanopy());pose("Hook",base,open,systems.getHook());
        for(String side:new String[]{"L","R"}){
            pose("WingFold."+side,base,folded,systems.getWingFold());
            rotate("Flap."+side,Vector3f.UNIT_X,-systems.getFlaps()*(float)Math.toRadians(30));
            rotate("LeadingFlap."+side,Vector3f.UNIT_X,systems.getFlaps()*(float)Math.toRadians(18));
        }
    }
    public static void attach(Spatial model,AircraftSystems systems){
        AircraftRig rig=new AircraftRig(model,systems);
        rig.skin.getSpatial().addControl(new AbstractControl(){
            protected void controlUpdate(float dt){rig.update();rig.skin.getArmature().update();}
            protected void controlRender(com.jme3.renderer.RenderManager manager,com.jme3.renderer.ViewPort viewport){}
        });
    }
}
