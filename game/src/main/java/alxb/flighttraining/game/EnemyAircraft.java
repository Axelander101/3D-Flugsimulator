package alxb.flighttraining.game;

import com.jme3.scene.Node;

public class EnemyAircraft {
    private final Node enemyPlane;
    private final FlightCommandAi flightCommandAi;
    private final EnemyAI enemyAI;
    private final FlightPhysics physics;

    EnemyAircraft(Node enemyPlane, FlightCommandAi flightCommandAi, EnemyAI enemyAI, FlightPhysics physics){
        this.enemyPlane = enemyPlane;
        this.flightCommandAi = flightCommandAi;
        this.enemyAI = enemyAI;
        this.physics = physics;
    }

    public void update(float tpf) {
        enemyAI.update();
        physics.update(tpf);
    }
}
