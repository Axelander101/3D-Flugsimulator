package alxb.flighttraining.game.ui;

import com.jme3.anim.*;
import com.jme3.asset.AssetManager;
import com.jme3.math.*;
import com.jme3.scene.*;

/** Continuous skinned shoulders/arms. Hands are rigidly posed around their actual controls. */
final class PilotFigure {
    private final Node model;
    private final Spatial stick,throttle,pedalLeft,pedalRight;
    private final Spatial[] hands=new Spatial[2],thighs=new Spatial[2],shins=new Spatial[2],boots=new Spatial[2];
    private final Vector3f[] wristOffsets=new Vector3f[2];
    private final SkinningControl skin;
    private final ArmPose[] arms=new ArmPose[2];
    private final Vector3f handPoint=new Vector3f(0,.337f,.015f),throttlePoint=new Vector3f(0,.128f,.05f);
    private static final class ArmPose {
        final Joint upper,lower,wrist;
        final Transform upperBind,lowerBind,wristBind;
        final Quaternion handBind;
        final float upperLength,lowerLength;
        ArmPose(Armature armature,String side,Quaternion handBind){
            upper=requiredJoint(armature,"UpperArm."+side);lower=requiredJoint(armature,"Forearm."+side);wrist=requiredJoint(armature,"Wrist."+side);
            upperBind=upper.getModelTransform().clone();lowerBind=lower.getModelTransform().clone();wristBind=wrist.getModelTransform().clone();
            upperLength=upperBind.getTranslation().distance(lowerBind.getTranslation());lowerLength=lowerBind.getTranslation().distance(wristBind.getTranslation());this.handBind=handBind;
        }
    }
    PilotFigure(AssetManager assets,Node cockpit,Spatial stick,Spatial throttle,Spatial pedalLeft,Spatial pedalRight){
        this.stick=stick;this.throttle=throttle;this.pedalLeft=pedalLeft;this.pedalRight=pedalRight;
        model=(Node)assets.loadModel("Models/FA18C_Pilot/FA18C_Pilot.glb");
        CockpitMaterials.prepare(assets,model);
        model.depthFirstTraversal(part->{
            if(part instanceof Geometry geometry){
                var material=geometry.getMaterial();String name=material.getName();
                if(name.contains("flight suit")||name.contains("cloth")||name.contains("harness")){
                    material.setColor("Specular",new ColorRGBA(.015f,.015f,.015f,1));material.setFloat("Shininess",2f);
                }
            }
        });
        cockpit.attachChild(model);required("Pilot_Head").setCullHint(Spatial.CullHint.Always);
        SkinningControl[] found={null};model.depthFirstTraversal(part->{if(part.getControl(SkinningControl.class)!=null)found[0]=part.getControl(SkinningControl.class);});
        if(found[0]==null)throw new IllegalStateException("Pilot armature missing; install the matching pilot model");
        skin=found[0];skin.getArmature().update();
        Quaternion modelToSkin=skin.getSpatial().getWorldRotation().inverse().mult(model.getWorldRotation());
        for(int i=0;i<2;i++){
            String side=i==0?"L":"R";
            hands[i]=required("Glove."+side);wristOffsets[i]=required("WristSocket."+side).getLocalTranslation().clone();
            thighs[i]=required("Thigh."+side);shins[i]=required("Shin."+side);boots[i]=required("Boot."+side);
            Quaternion neutralHand=new Quaternion().fromAngleAxis(FastMath.PI,Vector3f.UNIT_Y)
                    .mult(new Quaternion().fromAngleAxis(i==0?-.21f:0,Vector3f.UNIT_X));
            arms[i]=new ArmPose(skin.getArmature(),side,modelToSkin.mult(neutralHand));
        }
        update();
    }
    private static Joint requiredJoint(Armature armature,String name){
        Joint joint=armature.getJoint(name);if(joint==null)throw new IllegalStateException("Pilot joint missing: "+name);return joint;
    }
    private Spatial required(String name){
        Spatial found=model.getChild(name);if(found==null)throw new IllegalStateException("Pilot part missing: "+name);return found;
    }
    private Vector3f inSkin(Vector3f inModel){
        return skin.getSpatial().worldToLocal(model.localToWorld(inModel,new Vector3f()),new Vector3f());
    }
    void update(){
        Transform root=requiredJoint(skin.getArmature(),"PilotRoot").getModelTransform();
        for(int i=0;i<2;i++){
            float sign=i==0?-1:1;Spatial control=i==0?throttle:stick;
            Vector3f hand=model.worldToLocal(control.localToWorld(i==0?throttlePoint:handPoint,new Vector3f()),new Vector3f());
            Quaternion handRotation=model.getWorldRotation().inverse().mult(control.getWorldRotation());
            hands[i].setLocalTranslation(hand);hands[i].setLocalRotation(handRotation);hands[i].setLocalScale(1);
            Vector3f wrist=inSkin(hand.add(handRotation.mult(wristOffsets[i])));
            ArmPose arm=arms[i];Vector3f shoulder=arm.upperBind.getTranslation();
            Vector3f elbow=solveJoint(shoulder,wrist,arm.upperLength,arm.lowerLength,inSkin(new Vector3f(sign*.20f,.315f,-1.70f)));
            Quaternion upper=align(arm.lowerBind.getTranslation().subtract(shoulder),elbow.subtract(shoulder)).mult(arm.upperBind.getRotation());
            Quaternion lower=align(arm.wristBind.getTranslation().subtract(arm.lowerBind.getTranslation()),wrist.subtract(elbow)).mult(arm.lowerBind.getRotation());
            Quaternion handInSkin=skin.getSpatial().getWorldRotation().inverse().mult(control.getWorldRotation());
            Quaternion cuff=handInSkin.mult(arm.handBind.inverse()).mult(arm.wristBind.getRotation());
            pose(arm.upper,shoulder,upper,root.getTranslation(),root.getRotation());
            pose(arm.lower,elbow,lower,shoulder,upper);pose(arm.wrist,wrist,cuff,elbow,lower);
            Spatial pedal=i==0?pedalLeft:pedalRight;
            Vector3f foot=model.worldToLocal(pedal.localToWorld(new Vector3f(0,.06f,.012f),new Vector3f()),new Vector3f());
            Quaternion bootRotation=model.getWorldRotation().inverse().mult(pedal.getWorldRotation());
            Vector3f ankle=foot.add(bootRotation.mult(new Vector3f(0,.045f,-.005f)));
            Vector3f hip=new Vector3f(sign*.075f,.275f,-1.70f);
            Vector3f knee=solveJoint(hip,ankle,.32f,.30f,new Vector3f(sign*.11f,.50f,-2.02f));
            segment(thighs[i],hip,knee,.059f);segment(shins[i],knee,ankle,.044f);
            boots[i].setLocalTranslation(foot);boots[i].setLocalRotation(bootRotation);boots[i].setLocalScale(1);
        }
        skin.getArmature().update();
    }
    private static void pose(Joint joint,Vector3f position,Quaternion rotation,Vector3f parentPosition,Quaternion parentRotation){
        Quaternion inverse=parentRotation.inverse();joint.setLocalTranslation(inverse.mult(position.subtract(parentPosition)));
        joint.setLocalRotation(inverse.mult(rotation));joint.setLocalScale(Vector3f.UNIT_XYZ);
    }
    private static Quaternion align(Vector3f from,Vector3f to){
        from=from.normalize();to=to.normalize();float dot=FastMath.clamp(from.dot(to),-1,1);Vector3f axis=from.cross(to);
        if(axis.lengthSquared()<.000001f)return dot>=0?new Quaternion():new Quaternion().fromAngleAxis(FastMath.PI,Vector3f.UNIT_Y);
        return new Quaternion().fromAngleAxis(FastMath.acos(dot),axis.normalizeLocal());
    }
    static Vector3f solveJoint(Vector3f start,Vector3f end,float upper,float lower,Vector3f pole){
        Vector3f axis=end.subtract(start);float actual=axis.length();if(actual<.00001f)return start.add(0,-upper,0);
        axis.divideLocal(actual);float distance=Math.max(Math.abs(upper-lower)+.0001f,Math.min(upper+lower-.0001f,actual));
        float along=(upper*upper-lower*lower+distance*distance)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,upper*upper-along*along));
        Vector3f bend=pole.subtract(start);bend.subtractLocal(axis.mult(bend.dot(axis)));
        if(bend.lengthSquared()<.000001f)bend=axis.cross(Vector3f.UNIT_X);
        if(bend.lengthSquared()<.000001f)bend=axis.cross(Vector3f.UNIT_Y);
        return start.add(axis.mult(along)).addLocal(bend.normalizeLocal().mult(height));
    }
    private static void segment(Spatial part,Vector3f start,Vector3f end,float radius){
        Vector3f direction=end.subtract(start);float length=direction.length();if(length<.00001f)return;
        part.setLocalTranslation(start);part.setLocalScale(radius,radius,length);
        direction.divideLocal(length);Vector3f up=Math.abs(direction.y)>.98f?Vector3f.UNIT_X:Vector3f.UNIT_Y;
        part.setLocalRotation(new Quaternion().lookAt(direction,up));
    }
}
