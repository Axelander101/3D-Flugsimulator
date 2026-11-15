package alxb.flighttraining.game;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public final class InfraredMissile{
    private final Spatial model;
    private final Spatial target;

    private final Vector3f position;
    private final Vector3f direction;

    private float speed = 250f;
    private final float acceleration = 100f;
    private final float maxSpeed = 600f;
    private final float turnRate = (float) Math.toRadians(60f);
    private float remainingLife = 12f;



    InfraredMissile(Spatial model, Spatial target, Vector3f position, Vector3f direction){
        this.model = model;
        this.target = target;
        this.position = position;
        this.direction = direction;

        model.setLocalTranslation(position);
    }


    public boolean update(float tpf) {
        if (tpf <= 0f) {
            return false;
        }

        remainingLife -= tpf;

        if (remainingLife <= 0f) {
            model.removeFromParent();
            return true;
        }

        // Bei entferntem Ziel fliegt sie geradeaus weiter.
        if (target != null && target.getParent() != null) {
            Vector3f toTarget = target.getWorldTranslation()
                    .subtract(position);

            if (toTarget.lengthSquared() > 0.001f) {
                steerTowards(toTarget.normalizeLocal(), tpf);
            }
        }

        speed = Math.min(
                maxSpeed,
                speed + acceleration * tpf
        );

        position.addLocal(direction.mult(speed * tpf));
        model.setLocalTranslation(position);

        return false;
    }



    private void steerTowards(Vector3f desired, float tpf) {
        float dot = Math.max(-1f, Math.min(1f, direction.dot(desired)));

        float angle = (float) Math.acos(dot);

        if (angle < 0.0001f) {
            return;
        }

        float turn = Math.min(angle, turnRate * tpf);
        Vector3f axis = direction.cross(desired);


        if (axis.lengthSquared() < 0.000001f) {
            axis = direction.cross(Vector3f.UNIT_Y);

            if (axis.lengthSquared() < 0.000001f) {
                axis = direction.cross(Vector3f.UNIT_X);
            }
        }

        Quaternion rotation = new Quaternion().fromAngleAxis(turn, axis.normalizeLocal());

        rotation.mult(direction, direction);
        direction.normalizeLocal();
    }

}
