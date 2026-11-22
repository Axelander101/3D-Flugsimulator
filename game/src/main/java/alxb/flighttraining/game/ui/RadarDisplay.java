package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.enemy.Enemy;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;

public class RadarDisplay {
    private final Node display = new Node("RadarDisplay");
    private final List<Geometry> dots = new ArrayList<>();

    private final AssetManager assets;
    private final Node plane;
    private final Enemy enemies;

    private final float size = 220f;
    private final float range = 5000f;

    public RadarDisplay(AssetManager assets, Node guiNode, Node plane, Enemy enemies) {
        this.assets = assets;
        this.plane = plane;
        this.enemies = enemies;

        Geometry background = rectangle(
                "RadarBackground", size, size,
                new ColorRGBA(0.02f, 0.08f, 0.05f, 1f)
        );
        display.attachChild(background);

        // Kreuz durch die Mitte.
        Geometry horizontal = rectangle(
                "Horizontal", size, 1f, ColorRGBA.Gray
        );
        horizontal.setLocalTranslation(0, size / 2f, 1);
        display.attachChild(horizontal);

        Geometry vertical = rectangle(
                "Vertical", 1f, size, ColorRGBA.Gray
        );
        vertical.setLocalTranslation(size / 2f, 0, 1);
        display.attachChild(vertical);

        Geometry playerDot = rectangle(
                "Player", 8f, 8f, ColorRGBA.Green
        );
        playerDot.setLocalTranslation(
                size / 2f - 4f,
                size / 2f - 4f,
                2
        );
        display.attachChild(playerDot);

        // Convert the existing pixel layout to a physical cockpit display.
        display.setLocalScale(0.0007f);
        display.setLocalTranslation(0.065f, 0.285f, -2.32f);
        display.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Translucent);
        guiNode.attachChild(display);
    }

    public void update(int screenWidth, int screenHeight) {

            // Position stays fixed to the aircraft, independently of viewport size.

            // Anzahl der Punkte an die Gegnerzahl anpassen.
            while (dots.size() < enemies.getCount()) {
                Geometry dot = rectangle(
                        "EnemyDot", 6f, 6f, ColorRGBA.Red
                );
                dots.add(dot);
                display.attachChild(dot);
            }

            while (dots.size() > enemies.getCount()) {
                dots.remove(dots.size() - 1).removeFromParent();
            }

            Vector3f playerPosition = plane.getWorldTranslation();

            // Flugrichtung auf die horizontale Ebene projizieren.
            Vector3f forward = plane.getWorldRotation()
                    .mult(new Vector3f(0, 0, -1));
            forward.y = 0;

            if (forward.lengthSquared() < 0.0001f) {
                forward.set(0, 0, -1);
            }
            forward.normalizeLocal();

            Vector3f right = forward.cross(Vector3f.UNIT_Y)
                    .normalizeLocal();

            for (int i = 0; i < dots.size(); i++) {
                Vector3f offset = enemies.getPosition(i)
                        .subtract(playerPosition);

                Geometry dot = dots.get(i);

                if (offset.lengthSquared() > range * range) {
                    dot.setCullHint(
                            com.jme3.scene.Spatial.CullHint.Always
                    );
                    continue;
                }

                dot.setCullHint(
                        com.jme3.scene.Spatial.CullHint.Inherit
                );

                float radarX = offset.dot(right);
                float radarY = offset.dot(forward);

                float scale = (size / 2f - 6f) / range;

                dot.setLocalTranslation(
                        size / 2f + radarX * scale - 3f,
                        size / 2f + radarY * scale - 3f,
                        2
                );
            }

    }

    private Geometry rectangle(String name, float width, float height, ColorRGBA color) {
        Geometry geometry = new Geometry(
                name, new Quad(width, height)
        );

        Material material = new Material(
                assets, "Common/MatDefs/Misc/Unshaded.j3md"
        );
        material.setColor("Color", color);
        geometry.setMaterial(material);

        return geometry;
    }

    public void setVisible(boolean visible) {
        display.setCullHint(
                visible
                        ? com.jme3.scene.Spatial.CullHint.Inherit
                        : com.jme3.scene.Spatial.CullHint.Always
        );
    }
}
