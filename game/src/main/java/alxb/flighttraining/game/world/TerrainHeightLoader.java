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
        Raster loaded=repairNoData(image.getRaster(),absolute);
        synchronized(RASTERS){Raster existing=RASTERS.get(key);if(existing!=null)return existing;RASTERS.put(key,loaded);}
        return loaded;
    }
    private static boolean valid(float height){return Float.isFinite(height) && height>-9000f;}
    private static Raster repairNoData(Raster source,Path path)throws IOException{
        int width=source.getWidth(),height=source.getHeight(),count=width*height;
        float[] data=source.getSamples(0,0,width,height,0,(float[])null);int validCount=0;
        for(float value:data)if(valid(value))validCount++;
        if(validCount==count)return source;
        if(validCount==0)throw new IOException("Keine gültigen Höhenwerte in "+path);
        // Fill raster-edge voids once per decoded tile, from the nearest measured sample.
        // Multi-source breadth-first propagation is linear, unlike a search for every missing pixel.
        int[] queue=new int[count];int head=0,tail=0;
        for(int i=0;i<count;i++)if(valid(data[i]))queue[tail++]=i;
        while(head<tail){
            if((head&16383)==0 && Thread.currentThread().isInterrupted())
                throw new InterruptedIOException("Höhenvorbereitung abgebrochen");
            int i=queue[head++],x=i%width,y=i/width;
            if(x>0&&!valid(data[i-1])){data[i-1]=data[i];queue[tail++]=i-1;}
            if(x+1<width&&!valid(data[i+1])){data[i+1]=data[i];queue[tail++]=i+1;}
            if(y>0&&!valid(data[i-width])){data[i-width]=data[i];queue[tail++]=i-width;}
            if(y+1<height&&!valid(data[i+width])){data[i+width]=data[i];queue[tail++]=i+width;}
        }
        var sampleModel=new java.awt.image.ComponentSampleModel(java.awt.image.DataBuffer.TYPE_FLOAT,width,height,1,width,new int[]{0});
        return Raster.createWritableRaster(sampleModel,new java.awt.image.DataBufferFloat(data,count),null);
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
            try{rasters.put(entry.getKey(),raster(entry.getValue()));}
            catch(IOException error){if(entry.getKey().equals(center))throw error;}
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
