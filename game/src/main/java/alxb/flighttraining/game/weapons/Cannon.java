package alxb.flighttraining.game.weapons;

import com.jme3.scene.Node;

public class Cannon extends Guns {


    public Cannon(Node targets) {
        super(targets);
    }

    @Override
    protected float getMuzzleSpeed() {
        return 1000f;
    }
}
