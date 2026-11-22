package alxb.flighttraining.game.world;

import java.util.*;

/** Immutable terrain samples in absolute map coordinates; safe to use on mesh workers. */
final class TerrainHeightSnapshot {
    static final class Grid {
        final int size,patchSpan;
        private final float[] values;
        Grid(int size,float[] values){this.size=size;this.patchSpan=Math.min(64,(size-1)/2);this.values=values;}
        float sample(double x,double z){
            float gx=(float)(x*(size-1)/1000),gz=(float)(z*(size-1)/1000);
            int ix=Math.min(size-2,Math.max(0,(int)gx)),iz=Math.min(size-2,Math.max(0,(int)gz));
            float fx=gx-ix,fz=gz-iz;
            int index=iz*size+ix;float a=values[index],b=values[index+1],c=values[index+size],d=values[index+size+1];
            int px=ix%patchSpan,pz=iz%patchSpan;
            // Match jME LODGeomap's triangle split, including each patch's two special corners.
            if((px==0&&pz==0)||(px==patchSpan-1&&pz==patchSpan-1))
                return fx<fz?a+fx*(d-c)+fz*(c-a):a+fx*(b-a)+fz*(d-b);
            return fx<1-fz?c+fx*(b-a)+(1-fz)*(a-c):c+fx*(d-c)+(1-fz)*(b-d);
        }
    }
    private final Map<Long,Grid> grids;
    private final Set<Long> unavailable;
    TerrainHeightSnapshot(Map<Long,Grid> grids){this(grids,Set.of());}
    TerrainHeightSnapshot(Map<Long,Grid> grids,Set<Long> unavailable){this.grids=Map.copyOf(grids);this.unavailable=Set.copyOf(unavailable);}
    boolean isUnavailable(int east,int north){long key=key(east,north);return !grids.containsKey(key)&&unavailable.contains(key);}
    boolean isUnavailable(double worldX,double worldZ){return isUnavailable((int)Math.floor((2621500+worldX)/1000),(int)Math.floor((1092500-worldZ)/1000));}
    boolean covers(Set<Long> required){for(long key:required)if(!grids.containsKey(key)&&!unavailable.contains(key))return false;return true;}
    static long key(int east,int north){return((long)east<<32)^(north&0xffffffffL);}
    boolean contains(int east,int north){return grids.containsKey(key(east,north));}
    float sample(double worldX,double worldZ){
        double east=2621500+worldX,north=1092500-worldZ;
        int e=(int)Math.floor(east/1000),n=(int)Math.floor(north/1000);
        Grid grid=grids.get(key(e,n));
        if(grid==null)return Float.NaN;
        return grid.sample(east-e*1000,n*1000+1000-north);
    }
}
