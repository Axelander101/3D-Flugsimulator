package alxb.flighttraining.game.world;

import java.awt.image.Raster;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;


final class TerrainHeightLoader {

    // Immutable decoded DEM rasters are reused between the nine overlapping tile neighbourhoods.
    private record RasterKey(Path path,java.nio.file.attribute.FileTime modified,long length) {}
    private static final Map<RasterKey,Raster> RASTERS=new java.util.LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<RasterKey,Raster> entry){return size()>64;}
    };
    private static Raster raster(Path path)throws IOException {
        Path absolute=path.toAbsolutePath().normalize();
        RasterKey key=new RasterKey(absolute,java.nio.file.Files.getLastModifiedTime(absolute),java.nio.file.Files.size(absolute));
        synchronized(RASTERS){Raster found=RASTERS.get(key);if(found!=null)return found;}
        var image=ImageIO.read(path.toFile());
        if(image==null||image.getWidth()!=500||image.getHeight()!=500)
            throw new IOException("Erwartet wird eine 500 × 500 swissALTI3D-Kachel: "+path);
        Raster loaded=image.getRaster();
        synchronized(RASTERS){Raster existing=RASTERS.get(key);if(existing!=null)return existing;RASTERS.put(key,loaded);}
        return loaded;
    }
    private TerrainHeightLoader() {
    }

    static float[] load(TerrainTile center, Map<TerrainTile, Path> paths) throws IOException {
        return load(center,paths,513);
    }
    static float[] load(TerrainTile center, Map<TerrainTile, Path> paths,int size) throws IOException {
        int last=size-1;
        Map<TerrainTile, Raster> rasters = new HashMap<>();
        for (var entry : paths.entrySet()) {
            rasters.put(entry.getKey(), raster(entry.getValue()));
        }
        if (!rasters.containsKey(center)) {
            throw new IOException("Höhenkachel fehlt: " + center);
        }

        float[] heights = new float[size * size];
        for (int z = 0; z <= last; z++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Höhenvorbereitung abgebrochen");
            }
            double sourceZ = z * 500.0 / last - 0.5;
            int z0 = (int) Math.floor(sourceZ);
            float fractionZ = (float) (sourceZ - z0);
            for (int x = 0; x <= last; x++) {
                double sourceX = x * 500.0 / last - 0.5;
                int x0 = (int) Math.floor(sourceX);
                float fractionX = (float) (sourceX - x0);
                float top = interpolate(sample(center, rasters, x0, z0),
                        sample(center, rasters, x0 + 1, z0), fractionX);
                float bottom = interpolate(sample(center, rasters, x0, z0 + 1),
                        sample(center, rasters, x0 + 1, z0 + 1), fractionX);
                heights[z * size + x] = interpolate(top, bottom, fractionZ);
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
