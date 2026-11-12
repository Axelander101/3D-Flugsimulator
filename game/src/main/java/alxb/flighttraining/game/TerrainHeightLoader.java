package alxb.flighttraining.game;

import java.awt.image.Raster;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;


final class TerrainHeightLoader {

    private TerrainHeightLoader() {
    }

    static float[] load(TerrainTile center, Map<TerrainTile, Path> paths) throws IOException {
        Map<TerrainTile, Raster> rasters = new HashMap<>();
        for (var entry : paths.entrySet()) {
            var image = ImageIO.read(entry.getValue().toFile());
            if (image == null || image.getWidth() != 500 || image.getHeight() != 500) {
                throw new IOException("Erwartet wird eine 500 × 500 swissALTI3D-Kachel: " + entry.getValue());
            }
            rasters.put(entry.getKey(), image.getRaster());
        }
        if (!rasters.containsKey(center)) {
            throw new IOException("Höhenkachel fehlt: " + center);
        }

        float[] heights = new float[513 * 513];
        for (int z = 0; z <= 512; z++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Höhenvorbereitung abgebrochen");
            }
            double sourceZ = z * 500.0 / 512 - 0.5;
            int z0 = (int) Math.floor(sourceZ);
            float fractionZ = (float) (sourceZ - z0);
            for (int x = 0; x <= 512; x++) {
                double sourceX = x * 500.0 / 512 - 0.5;
                int x0 = (int) Math.floor(sourceX);
                float fractionX = (float) (sourceX - x0);
                float top = interpolate(sample(center, rasters, x0, z0),
                        sample(center, rasters, x0 + 1, z0), fractionX);
                float bottom = interpolate(sample(center, rasters, x0, z0 + 1),
                        sample(center, rasters, x0 + 1, z0 + 1), fractionX);
                heights[z * 513 + x] = interpolate(top, bottom, fractionZ);
            }
        }
        return heights;
    }

    private static float interpolate(float a, float b, float fraction) {
        return a + (b - a) * fraction;
    }

    private static float sample(TerrainTile center, Map<TerrainTile, Raster> rasters,
                                int x, int z) throws IOException {
        TerrainTile owner = center.offset(Math.floorDiv(x, 500), -Math.floorDiv(z, 500));
        Raster raster = rasters.get(owner);
        int sampleX = Math.floorMod(x, 500);
        int sampleZ = Math.floorMod(z, 500);
        if (raster == null) {
            // Am Rand des Datensatzes den letzten vorhandenen Messwert fortsetzen.
            raster = rasters.get(center);
            sampleX = Math.max(0, Math.min(499, x));
            sampleZ = Math.max(0, Math.min(499, z));
        }
        float height = raster.getSampleFloat(sampleX, sampleZ, 0);
        if (!Float.isFinite(height) || height <= -9000f) {
            throw new IOException("Ungültiger Höhenwert in " + owner);
        }
        return height;
    }
}
