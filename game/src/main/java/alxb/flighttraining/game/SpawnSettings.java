package alxb.flighttraining.game;


public final class SpawnSettings {
    public final float worldX;
    public final float worldZ;
    public final float heightAboveGround;

    public SpawnSettings(
            float worldX,
            float worldZ,
            float heightAboveGround
    ) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.heightAboveGround = heightAboveGround;
    }
}
