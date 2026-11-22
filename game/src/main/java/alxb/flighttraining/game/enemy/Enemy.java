package alxb.flighttraining.game.enemy;

import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.world.TerrainStreaming;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

public final class Enemy {
    private final AssetManager assets;
    private final Node targets;
    private java.util.function.Consumer<Vector3f> soundFire=p->{},soundHit=p->{};
    public void setSoundEvents(java.util.function.Consumer<Vector3f> fire,java.util.function.Consumer<Vector3f> hit){soundFire=fire;soundHit=hit;for(EnemyAircraft e:enemies)e.setSoundEvents(fire,hit);}
    private final List<EnemyAircraft> enemies = new ArrayList<>();

    public Enemy(AssetManager assets, Node targets) {
        this.assets = assets;
        this.targets = targets;
    }

    private Node player;
    private FlightPhysics playerPhysics;
    private TerrainStreaming terrain;

    public void configure(Node player, FlightPhysics physics, TerrainStreaming terrain) {
        this.player=player; this.playerPhysics=physics; this.terrain=terrain;
    }

    public void spawn(int count, Node player) { spawn(count, player, null); }

    public void spawn(int count, Node player, DogfightSettings settings) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "Gegnerzahl muss mindestens 0 sein."
            );
        }

        clear();

        player.updateGeometricState();
        targets.updateGeometricState();

        Vector3f forward = player.getWorldRotation()
                .mult(new Vector3f(0f, 0f, -1f));

        Vector3f right = player.getWorldRotation()
                .mult(Vector3f.UNIT_X);

        for (int i = 0; i < count; i++) {
            Node enemy = new Node("Enemy_" + i);

            Spatial model = assets.loadModel(
                    "Models/FA18C_Swiss_Hornet_Cannon.glb"
            );

            model.rotate(0f, (float) Math.PI, 0f);
            model.setLocalScale(0.5f);

            enemy.attachChild(model);
            targets.attachChild(enemy);

            Vector3f position = player.getWorldTranslation()
                    .add(forward.mult((settings == null ? 300f : settings.initialDistance) + (i / 5) * 250f))
                    .add(right.mult(
                            ((i % 5) - Math.min(count, 5) / 2f + 0.5f)
                                    * 70f
                    ));

            enemy.setLocalTranslation(
                    targets.worldToLocal(position, new Vector3f())
            );

            enemy.setLocalRotation(
                    targets.getWorldRotation().inverse()
                            .mult(player.getWorldRotation())
            );

            if (settings != null) {
                // Gegner schauen dem Spieler entgegen: nachvollziehbarer Frontalstart.
                enemy.rotate(0f, (float)Math.PI, 0f);
            }
            EnemyAircraft aircraft = new EnemyAircraft(enemy, player);
            if (settings != null) aircraft.configure(settings);
            aircraft.setSoundEvents(soundFire,soundHit);
            enemies.add(aircraft);
        }
    }

    public void update(float tpf) {
        for (EnemyAircraft enemy : enemies) {
            float ground=terrain==null?Float.NaN:terrain.getGroundHeight(enemy.getPosition());
            enemy.update(tpf, player, playerPhysics==null?new Vector3f():playerPhysics.getVelocity(),ground);
        }
    }

    public Vector3f getPosition(int index) {
        return enemies.get(index).getPosition();
    }

    public void rebase(Vector3f delta){for(EnemyAircraft enemy:enemies)enemy.rebase(delta);}

    public int getCount() {
        return enemies.size();
    }

    public void clear() {
        for (EnemyAircraft enemy : enemies) {
            enemy.remove();
        }

        enemies.clear();
    }

    public Node getPlane(int index) {
        return enemies.get(index).getPlane();
    }
}
