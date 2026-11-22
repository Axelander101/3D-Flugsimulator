package alxb.flighttraining.game.weapons;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;


import java.util.ArrayList;
import java.util.List;

public abstract class Guns {
    private final List<Bullet> bullets = new ArrayList<>();

    protected final Node targets;
    private java.util.function.Consumer<Vector3f> hitListener=p->{};
    private java.util.function.Consumer<Vector3f> shotListener=p->{};
    public void setHitListener(java.util.function.Consumer<Vector3f> listener){hitListener=listener;}
    public void setShotListener(java.util.function.Consumer<Vector3f> listener){shotListener=listener;}

    protected Guns(Node targets) {
        this.targets = targets;
    }

    protected abstract float getMuzzleSpeed();

    public void fire(Vector3f muzzleWorldPosition, Vector3f direction, Vector3f planeVelocity) {
        Vector3f velocity = direction.normalize()
                .mult(getMuzzleSpeed())
                .addLocal(planeVelocity);

        bullets.add(new Bullet(muzzleWorldPosition, velocity));
        shotListener.accept(muzzleWorldPosition.clone());
    }

    public void rebase(Vector3f delta){for(Bullet bullet:bullets)bullet.rebase(delta);}

    public void clear() { bullets.clear(); }

    public void update(float tpf) {
        bullets.removeIf(bullet -> bullet.update(tpf, targets, hitListener));
    }
}
