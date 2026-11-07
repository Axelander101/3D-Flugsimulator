package alxb.flighttraining.game;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Liest ein PNG ohne dauerhaften AssetManager-Cache. Der GPU-Upload erfolgt beim Rendern. */
final class TerrainTextureLoader {
    private TerrainTextureLoader() {
    }

    static Texture2D load(Path path) throws IOException {
        var source = ImageIO.read(path.toFile());
        if (source == null) {
            throw new IOException("Ungültiges Luftbild: " + path);
        }
        int width = source.getWidth();
        int height = source.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 3);
        for (int y = height - 1; y >= 0; y--) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Luftbildvorbereitung abgebrochen");
            }
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                pixels.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb);
            }
        }
        pixels.flip();
        Texture2D texture = new Texture2D(new Image(Image.Format.RGB8, width, height, pixels, ColorSpace.sRGB));
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        return texture;
    }
}
