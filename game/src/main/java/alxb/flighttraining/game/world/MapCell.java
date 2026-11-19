package alxb.flighttraining.game.world;

import com.google.gson.*;
import java.util.*;

/** Map geometry, read-only by convention, in metres relative to this LV95 cell's southwest corner. */
record MapCell(int east, int north, JsonArray features) {
    static final float SIZE = 1000;
    String key() { return east + "_" + north; }
    double worldX() { return east * 1000.0 - 2621500.0; }
    double worldZ() { return 1092500.0 - north * 1000.0; }
    static boolean inside(float x, float z, JsonArray polygon) {
        boolean in = false;
        for (int i = 0, j = polygon.size()-1; i < polygon.size(); j = i++) {
            JsonArray a=polygon.get(i).getAsJsonArray(), b=polygon.get(j).getAsJsonArray();
            float ax=a.get(0).getAsFloat(), az=a.get(1).getAsFloat();
            float bx=b.get(0).getAsFloat(), bz=b.get(1).getAsFloat();
            if ((az>z)!=(bz>z) && x<(bx-ax)*(z-az)/(bz-az)+ax) in=!in;
        }
        return in;
    }
    static boolean contains(float x,float z,JsonObject feature) {
        if (!inside(x,z,feature.getAsJsonArray("p"))) return false;
        if(feature.has("holes")) for(JsonElement hole:feature.getAsJsonArray("holes"))
            if(inside(x,z,hole.getAsJsonArray())) return false;
        return true;
    }
    static long hash(long x,long z) {
        long h=x*0x9e3779b97f4a7c15L+z*0xc2b2ae3d27d4eb4fL;
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;h=(h^(h>>>27))*0x94d049bb133111ebL;
        return h^(h>>>31);
    }
    static float random(long hash,int offset) { return ((hash >>> offset)&65535)/65535f; }
}
