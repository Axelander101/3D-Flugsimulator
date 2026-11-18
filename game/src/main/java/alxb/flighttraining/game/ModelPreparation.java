package alxb.flighttraining.game;

import com.jme3.material.*;
import com.jme3.scene.*;
import com.jme3.texture.*;
import jme3tools.optimize.GeometryBatchFactory;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/** Background-only preprocessing. No OpenGL calls. Keeps a bounded pool of shared images. */
final class ModelPreparation {
    private record Key(Image.Format format, int width, int height, String colorSpace, String digest) {}
    private final LinkedHashMap<Key, Image> images = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Image> e) { return size() > 32; }
    };
    private final List<Material> materials = new ArrayList<>();

    Spatial prepare(Spatial model) {
        materials.clear();
        canonicalize(model, new IdentityHashMap<>());
        // Batch only one spatial chunk at a time, preserving distance culling between chunks.
        if (model instanceof Node parent) GeometryBatchFactory.optimize(parent, false);
        model.depthFirstTraversal(s -> { if (s instanceof Geometry g) g.getMesh().setStatic(); });
        return model;
    }
    private void canonicalize(Spatial s, IdentityHashMap<Image, Image> seen) {
        if (s instanceof Geometry g) {
            Material material = g.getMaterial();
            for (MatParam parameter : material.getParams()) {
                if (!(parameter instanceof MatParamTexture textureParameter)) continue;
                Texture texture = textureParameter.getTextureValue();
                Image source = texture.getImage();
                if (source == null || source.getData().isEmpty()) continue;
                Image shared = seen.get(source);
                if (shared == null) {
                    Key key = key(source);
                    shared = images.computeIfAbsent(key, ignored -> source);
                    seen.put(source, shared);
                }
                texture.setImage(shared);
            }
            for (Material other : materials) {
                if (material.contentEquals(other)) { g.setMaterial(other); return; }
            }
            materials.add(material);
        }
        if (s instanceof Node parent) for (Spatial child : parent.getChildren()) canonicalize(child, seen);
    }
    private static Key key(Image image) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (ByteBuffer buffer : image.getData()) {
                ByteBuffer data = buffer.asReadOnlyBuffer(); data.rewind(); sha.update(data);
            }
            return new Key(image.getFormat(), image.getWidth(), image.getHeight(),
                    String.valueOf(image.getColorSpace()), HexFormat.of().formatHex(sha.digest()));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
