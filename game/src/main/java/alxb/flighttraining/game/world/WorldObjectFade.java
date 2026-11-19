package alxb.flighttraining.game.world;

import com.jme3.material.Material;
import com.jme3.scene.*;
import java.util.*;

/** Opaque dither transitions keep depth writes, including for alpha-tested tree foliage. */
final class WorldObjectFade {
    private WorldObjectFade() {}
    static void prepare(Node node){
        Map<Material,Material> copies=new IdentityHashMap<>();
        node.depthFirstTraversal(part->{if(part instanceof Geometry g && g.getMaterial().getMaterialDef().getMaterialParam("WorldFade")!=null)
            g.setMaterial(copies.computeIfAbsent(g.getMaterial(),Material::clone));});
        set(node,0,false);
    }
    static boolean supported(Spatial scene){
        if(scene instanceof Geometry geometry)return geometry.getMaterial().getMaterialDef().getMaterialParam("WorldFade")!=null;
        if(scene instanceof Node node)for(Spatial child:node.getChildren())if(supported(child))return true;
        return false;
    }
    static void fallback(Spatial scene,com.jme3.asset.AssetManager assets){
        scene.depthFirstTraversal(part->{if(part instanceof Geometry geometry&&supported(geometry)){
            Material old=geometry.getMaterial();
            String definition=old.getMaterialDef().getAssetName().contains("Unshaded")?"Common/MatDefs/Misc/Unshaded.j3md":"Common/MatDefs/Light/Lighting.j3md";
            Material plain=new Material(assets,definition);
            for(com.jme3.material.MatParam parameter:old.getParams())if(plain.getMaterialDef().getMaterialParam(parameter.getName())!=null)
                plain.setParam(parameter.getName(),parameter.getVarType(),parameter.getValue());
            plain.getAdditionalRenderState().setFaceCullMode(old.getAdditionalRenderState().getFaceCullMode());
            geometry.setMaterial(plain);
        }});
    }
    static void set(Node node,float progress,boolean outgoing){
        node.depthFirstTraversal(part->{if(part instanceof Geometry g && g.getMaterial().getMaterialDef().getMaterialParam("WorldFade")!=null){
            g.getMaterial().setFloat("WorldFade",progress);g.getMaterial().setBoolean("WorldFadeOut",outgoing);
        }});
    }
}
