package alxb.flighttraining.game;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;


public final class Enemy {
    private final AssetManager assets;
    private final Node targets;
    private EnemyAI enemyAI;
    private FlightCommandAi flightCommandAi;
    private FlightPhysics physics;
    private EnemyAircraft enemyAircraft;
    private final List<Node> enemies = new ArrayList<>();



    public Enemy(AssetManager assets, Node targets) {
        this.assets = assets;
        this.targets = targets;
    }


    public void spawn(int count, Node player) {
        if (count < 0) {
            throw new IllegalArgumentException("Gegnerzahl muss mindestens 0 sein.");
        }
        clear();
        Vector3f forward = player.getWorldRotation().mult(new Vector3f(0, 0, -1));
        Vector3f right = player.getWorldRotation().mult(Vector3f.UNIT_X);
        for (int i = 0; i < count; i++) {
            Node enemy = new Node("Enemy_" + i);
            Spatial model = assets.loadModel("Models/FA18C_Swiss_Hornet_Cannon.glb");
            model.rotate(0f, (float) Math.PI, 0f);
            model.setLocalScale(0.5f);
            enemy.attachChild(model);
            targets.attachChild(enemy);
            Vector3f position = player.getWorldTranslation()
                    .add(forward.mult(300f + (i / 5) * 150f))
                    .add(right.mult(((i % 5) - Math.min(count, 5) / 2f + 0.5f) * 70f));
            enemy.setLocalTranslation(targets.worldToLocal(position, new Vector3f()));
            enemy.setLocalRotation(targets.getWorldRotation().inverse()
                    .mult(player.getWorldRotation()));


            FlightCommandAi commands = new FlightCommandAi();
            FlightPhysics enemyPhysics = new FlightPhysics(enemy, commands);

            enemyPhysics.setVelocity(forward.mult(150f));
            commands.setThrottle(0.6f);
            EnemyAI enemyAI = new EnemyAI(commands);
            enemyAircraft = new EnemyAircraft(enemy, flightCommandAi, enemyAI, physics);


            enemies.add(enemy);
        }
    }

    public Vector3f getPosition(int index) {
        return enemies.get(index).getWorldTranslation().clone();
    }

    public int getCount() {
        return enemies.size();
    }

    public void clear() {
        for (Node enemy : enemies) {
            enemy.removeFromParent();
        }
        enemies.clear();
    }
}
