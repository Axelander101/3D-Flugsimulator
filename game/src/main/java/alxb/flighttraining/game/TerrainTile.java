package alxb.flighttraining.game;

/** Kilometer-Kachel in LV95. Die Weltposition bezeichnet die Kachelmitte. */
public record TerrainTile(int x, int z) {
    static final float SIZE = 1000f;
    static final int ORIGIN_X = 2621;
    static final int ORIGIN_Z = 1092;

    static TerrainTile at(float worldX, float worldZ) {
        return new TerrainTile(
                ORIGIN_X + (int) Math.floor(worldX / SIZE + 0.5f),
                ORIGIN_Z - (int) Math.floor(worldZ / SIZE + 0.5f)
        );
    }

    TerrainTile offset(int dx, int dz) {
        return new TerrainTile(x + dx, z + dz);
    }

    String fileName() {
        return "terrain_" + x + "-" + z + ".tif";
    }
}
