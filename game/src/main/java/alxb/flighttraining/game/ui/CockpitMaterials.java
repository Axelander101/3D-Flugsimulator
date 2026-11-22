package alxb.flighttraining.game.ui;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.MatParam;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

final class CockpitMaterials {
    /** jME Lighting compatibility: this cockpit also works without a PBR environment probe. */
    static void prepare(AssetManager assets, Node model) {
        IdentityHashMap<Material, Material> converted = new IdentityHashMap<>();
        IdentityHashMap<Image, Image> images = new IdentityHashMap<>();
        model.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) return;
            Material source = geometry.getMaterial();
            Material ready = converted.get(source);
            if (ready == null) {
                String name = source.getName() == null ? "" : source.getName();
                boolean unlit = name.contains("Live") || name.contains("Labels") || name.contains("Glass");
                ready = new Material(assets, unlit
                        ? "Common/MatDefs/Misc/Unshaded.j3md"
                        : "Common/MatDefs/Light/Lighting.j3md");
                ready.setName(name);
                MatParam parameter = source.getParam("BaseColor");
                ColorRGBA color = parameter != null ? ((ColorRGBA) parameter.getValue()).clone() : ColorRGBA.White.clone();
                parameter = source.getParam("BaseColorMap");
                if (parameter != null) {
                    Texture texture = ((Texture) parameter.getValue()).clone();
                    Image image = texture.getImage();
                    if (image != null) texture.setImage(images.computeIfAbsent(image, CockpitMaterials::rgba));
                    ready.setTexture(unlit ? "ColorMap" : "DiffuseMap", texture);
                }
                if (unlit) ready.setColor("Color", color);
                else {
                    ready.setBoolean("UseMaterialColors", true);
                    ready.setColor("Diffuse", color);
                    ready.setColor("Ambient", color);
                    ready.setColor("Specular", new ColorRGBA(.17f, .18f, .18f, 1));
                    ready.setFloat("Shininess", 24f);
                }
                if (name.contains("Glass") || name.contains("Labels") || name.contains("HUD_Demo")) {
                    ready.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                    ready.getAdditionalRenderState().setDepthWrite(false);
                }
                ready.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
                converted.put(source, ready);
            }
            geometry.setMaterial(ready);
            String name = ready.getName();
            if (name.contains("Glass") || name.contains("Labels") || name.contains("HUD_Demo"))
                geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        });
    }

    private static Image rgba(Image source) {
        if (source.getFormat() != Image.Format.RGB8 || source.hasMipmaps() || source.getData().size() != 1)
            return source;
        ByteBuffer input = source.getData(0).duplicate(); input.rewind();
        if (input.remaining() != source.getWidth() * source.getHeight() * 3) return source;
        ByteBuffer output = BufferUtils.createByteBuffer(source.getWidth() * source.getHeight() * 4);
        while (input.remaining() >= 3) output.put(input.get()).put(input.get()).put(input.get()).put((byte) 255);
        output.flip();
        return new Image(Image.Format.RGBA8, source.getWidth(), source.getHeight(), output, source.getColorSpace());
    }

}
