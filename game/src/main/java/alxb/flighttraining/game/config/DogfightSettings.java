package alxb.flighttraining.game.config;

/** Unveränderliche Startkonfiguration. Höhen sind Meter über Terrain, keine Flughafenhöhe. */
public final class DogfightSettings {
    public enum Difficulty {
        EASY("Einfach", 1f, 0.7f, 0.3f), NORMAL("Mittel", 0.5f, 1f, 0.6f), HARD("Schwer", 0.25f, 1.2f, 1f);
        public final String label;
        public final float scanInterval, turnFactor, aimFactor;
        Difficulty(String label, float scan, float turn, float aim) {
            this.label=label; scanInterval=scan; turnFactor=turn; aimFactor=aim;
        }
        @Override public String toString() { return label; }
    }
    public enum Region {
        MEIRINGEN("Meiringen",29740,-84500), EMMEN("Emmen",44320,-123520),
        PAYERNE("Payerne",-61550,-95560), ZURICH("Zürich",62160,-164460),
        GENEVA("Genf",-124060,-29110), BASEL("Basel-Mulhouse",-15300,-179730),
        BERN("Bern-Belp",-16970,-103340), ALTENRHEIN("St. Gallen-Altenrhein",138510,-169020),
        LUGANO("Lugano-Agno",92500,-3240), SION("Sion",-30120,-26140), GRENCHEN("Grenchen",-23190,-133100);
        public final String label;
        public final float x,z;
        Region(String label,float x,float z){this.label=label;this.x=x;this.z=z;}
        public SpawnSettings spawn(float height){return new SpawnSettings(x,z,height, 1);}
        @Override public String toString(){return label;}
    }
    public final Region region;
    public final Difficulty difficulty;
    public final int enemyCount;
    public final float heightAboveGround, initialDistance;
    public final boolean weaponsEnabled;
    public DogfightSettings(Region region,Difficulty difficulty,int count,float height,float distance,boolean weapons){
        if(region==null || difficulty==null || count<1 || count>8 || !Float.isFinite(height)
                || height<600 || height>6000 || !Float.isFinite(distance) || distance<1000 || distance>10000)
            throw new IllegalArgumentException("Ungültige Dogfight-Einstellungen");
        this.region=region;this.difficulty=difficulty;enemyCount=count;
        heightAboveGround=height;initialDistance=distance;weaponsEnabled=weapons;
    }
    public SpawnSettings spawn(){return region.spawn(heightAboveGround);}
}
