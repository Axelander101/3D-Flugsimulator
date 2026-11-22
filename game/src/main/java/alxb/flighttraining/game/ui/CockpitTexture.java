package alxb.flighttraining.game.ui;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

/** Reuses CPU image, direct buffer and GPU texture; no per-frame texture allocations/mipmaps. */
final class CockpitTexture {
    final BufferedImage canvas;
    private final Texture2D texture;
    private final ByteBuffer pixels;
    private final int[] argb;
    CockpitTexture(AssetManager assets, Geometry surface, int width, int height, boolean transparent) {
        canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        argb = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        pixels = BufferUtils.createByteBuffer(width * height * 4);
        texture = new Texture2D(new Image(Image.Format.RGBA8, width, height, pixels, ColorSpace.sRGB));
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.setColor("Color", ColorRGBA.White);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        if (transparent) {
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthWrite(false);
            surface.setQueueBucket(RenderQueue.Bucket.Transparent);
        }
        surface.setMaterial(material);
    }
    Graphics2D begin(boolean transparent) {
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(transparent ? new Color(0, 0, 0, 0) : new Color(2, 10, 7));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
    void upload(Graphics2D g) {
        g.dispose(); pixels.clear();
        // glTF display UVs and its imported textures use an upper-left image origin.
        for (int value : argb) {
            pixels.put((byte) (value >> 16)).put((byte) (value >> 8))
                    .put((byte) value).put((byte) (value >>> 24));
        }
        pixels.flip(); texture.getImage().setData(pixels);
    }
}
