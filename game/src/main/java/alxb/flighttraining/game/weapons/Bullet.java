package alxb.flighttraining.game.weapons;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public final class Bullet {
    private final Vector3f position;
    private final Vector3f velocity;
    private float life = 3f;

    public Bullet(Vector3f position, Vector3f velocity) {
        this.position = position.clone();
        this.velocity = velocity.clone();
    }

    public void rebase(Vector3f delta){position.subtractLocal(delta);}

    public boolean update(float tpf,Node targets){return update(tpf,targets,p->{});}
    public boolean update(float tpf, Node targets, java.util.function.Consumer<Vector3f> hitListener) {
        if (tpf <= 0f) return false;
        life -= tpf;

        if (life <= 0f) return true;
        Vector3f movement = velocity.mult(tpf);
        float distance = movement.length();

        if (distance > 0f) {
            Ray ray = new Ray(position.clone(), movement.normalize());
            ray.setLimit(distance);
            CollisionResults results = new CollisionResults();
            targets.collideWith(ray, results);

            for (CollisionResult hit : results) {
                if (hit.getDistance() <= distance) {
                    hitListener.accept(hit.getContactPoint().clone());
                    return true;
                }
            }
        }
        position.addLocal(movement);
        return false;
    }
}
