package alxb.flighttraining.game;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;


import java.util.ArrayList;
import java.util.List;

public abstract class Guns {
    private final List<Bullet> bullets = new ArrayList<>();

    protected final Node targets;

    protected Guns(Node targets) {
        this.targets = targets;
    }

    protected abstract float getMuzzleSpeed();

    public void fire(
            Vector3f muzzleWorldPosition,
            Vector3f direction,
            Vector3f planeVelocity
    ) {
        Vector3f velocity = direction.normalize()
                .mult(getMuzzleSpeed())
                .addLocal(planeVelocity);

        bullets.add(new Bullet(muzzleWorldPosition, velocity));
    }

    public void update(float tpf) {
        bullets.removeIf(bullet -> bullet.update(tpf, targets));
    }
}
