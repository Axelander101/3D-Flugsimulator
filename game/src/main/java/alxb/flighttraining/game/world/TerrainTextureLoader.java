package alxb.flighttraining.game.world;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Worker-only image decode and mip generation. No per-frame/driver-side mipmap construction. */
final class TerrainTextureLoader {
    private static final float[] LINEAR = new float[256];
    private static final int[] SRGB = new int[65536];
    static {
        for (int i=0;i<LINEAR.length;i++) {
            float c=i/255f; LINEAR[i]=c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);
        }
        for (int i=0;i<SRGB.length;i++) {
            float c=i/65535f; SRGB[i]=Math.round(255*(c<=.0031308f?12.92f*c:1.055f*(float)Math.pow(c,1/2.4f)-.055f));
        }
    }
    private TerrainTextureLoader() {}
    static Texture2D load(Path path) throws IOException { return load(path,1024); }
    static Texture2D load(Path path,int maximum) throws IOException {
        if(maximum<1)throw new IllegalArgumentException("Texture size must be positive");
        var source=ImageIO.read(path.toFile());
        if(source==null)throw new IOException("Ungültiges Luftbild: "+path);
        if(source.getWidth()>maximum){
            int height=Math.max(1,Math.round(source.getHeight()*(maximum/(float)source.getWidth())));
            var smaller=new java.awt.image.BufferedImage(maximum,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=smaller.createGraphics();
            try{
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source,0,0,maximum,height,null);
            }finally{graphics.dispose();}
            source=smaller;
        }
        int width=source.getWidth(),height=source.getHeight(),total=0;
        ArrayList<Integer> sizes=new ArrayList<>();
        for(int w=width,h=height;;w=Math.max(1,w/2),h=Math.max(1,h/2)){
            int bytes=Math.multiplyExact(Math.multiplyExact(w,h),4);sizes.add(bytes);total=Math.addExact(total,bytes);
            if(w==1&&h==1)break;
        }
        ByteBuffer pixels=BufferUtils.createByteBuffer(total);
        int[] level=source.getRGB(0,0,width,height,null,0,width);
        int w=width,h=height;
        for(int mip=0;mip<sizes.size();mip++){
            for(int y=h-1;y>=0;y--){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Luftbildvorbereitung abgebrochen");
                for(int x=0;x<w;x++){
                    int rgb=level[y*w+x];pixels.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)255);
                }
            }
            if(w==1&&h==1)break;
            int nextWidth=Math.max(1,w/2),nextHeight=Math.max(1,h/2);int[] next=new int[nextWidth*nextHeight];
            for(int y=0;y<nextHeight;y++)for(int x=0;x<nextWidth;x++){
                int x0=x*2,x1=Math.min(w-1,x0+1),y0=y*2,y1=Math.min(h-1,y0+1);
                int a=level[y0*w+x0],b=level[y0*w+x1],c=level[y1*w+x0],d=level[y1*w+x1];
                next[y*nextWidth+x]=0xff000000|average(a,b,c,d,16)<<16|average(a,b,c,d,8)<<8|average(a,b,c,d,0);
            }
            level=next;w=nextWidth;h=nextHeight;
        }
        pixels.flip();int[] mipSizes=sizes.stream().mapToInt(Integer::intValue).toArray();
        Image image=new Image(Image.Format.RGBA8,width,height,pixels,mipSizes,ColorSpace.sRGB);
        Texture2D texture=new Texture2D(image);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(8);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        return texture;
    }
    private static int average(int a,int b,int c,int d,int shift){
        float value=(LINEAR[(a>>>shift)&255]+LINEAR[(b>>>shift)&255]+LINEAR[(c>>>shift)&255]+LINEAR[(d>>>shift)&255])*.25f;
        return SRGB[Math.max(0,Math.min(65535,Math.round(value*65535)))];
    }
}
