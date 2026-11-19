package alxb.flighttraining.game.config;


public final class SpawnSettings {
    public final float worldX;
    public final float worldZ;
    public final float heightAboveGround;
    public final float enemies;

    public SpawnSettings(
            float worldX,
            float worldZ,
            float heightAboveGround,
            float enemies
    ) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.heightAboveGround = heightAboveGround;
        this.enemies = enemies;
    }
}
