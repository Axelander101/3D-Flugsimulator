package alxb.flighttraining.game.world;

/** Metres; altitude is height above terrain, never elevation above sea level. */
public final class WorldDetailSettings {
    public static final float MAX_DETAIL_DISTANCE = 5000f;
    public static final float ALTITUDE_SIMPLIFY_START = 2500f;
    public static final float ALTITUDE_SIMPLIFY_END = 3000f;
    private static final float[] DISTANCES = {3000, 5000, 8000, 12000, 20000};
    private static volatile float renderDistance = initialDistance();
    private static float initialDistance(){
        try{
            float value=Float.parseFloat(System.getProperty("flighttraining.renderDistance","8000"));
            return Float.isFinite(value)?Math.max(1000,Math.min(20000,value)):8000;
        }catch(NumberFormatException ignored){return 8000;}
    }
    private WorldDetailSettings() {}
    public static float renderDistance() { return renderDistance; }
    public static void setRenderDistance(float metres) {
        if (!Float.isFinite(metres) || metres < 1000 || metres > 20000)
            throw new IllegalArgumentException("Render distance: 1000..20000 m");
        renderDistance = metres;
    }
    public static void cycleRenderDistance() {
        for (float d : DISTANCES) if (d > renderDistance + 1) { renderDistance = d; return; }
        renderDistance = DISTANCES[0];
    }
    static int stableLod(float distance,float agl,float far,int previous){
        int next=lod(distance,agl,far);
        if(next==previous || distance>=Math.min(MAX_DETAIL_DISTANCE,far) || agl>=ALTITUDE_SIMPLIFY_END)return next;
        int held=lod(Math.max(0,distance+(next>previous?-140:140)),agl,far);
        return held==previous?previous:next;
    }
    static int lod(float distance, float agl, float far) {
        if (distance >= Math.min(MAX_DETAIL_DISTANCE, far) || agl >= ALTITUDE_SIMPLIFY_END) return 3;
        float slant=(float)Math.hypot(distance,agl);
        int level = slant < 350 ? 0 : slant < 1000 ? 1 : 2;
        return agl > ALTITUDE_SIMPLIFY_START ? Math.max(2, level) : level;
    }
}
