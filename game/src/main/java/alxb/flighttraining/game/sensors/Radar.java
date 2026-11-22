package alxb.flighttraining.game.sensors;

import alxb.flighttraining.game.enemy.Enemy;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.ArrayList;

public class Radar {
    private final float radius;
    private final Node plane;
    private final Enemy enemies;

    ArrayList <Boolean> isInRadius = new ArrayList<>();

    public Radar (float radius, Node plane, Enemy enemies) {
        this.radius = radius;
        this.plane = plane;
        this.enemies = enemies;
    }


    public void update(){
        isInRadius.clear();
        for (int i = 0; i < enemies.getCount(); i++) {
            Vector3f enemyPosition = enemies.getPosition(i);
            Vector3f planePosition = plane.getWorldTranslation();

            float distance = planePosition.distance(enemyPosition);

            if (distance <= radius){
                isInRadius.add(true);
            }
            else {isInRadius.add(false);}

        }

    }

}
