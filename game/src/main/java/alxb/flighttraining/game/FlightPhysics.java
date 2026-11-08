package alxb.flighttraining.game;

import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

final class FlightPhysics {
    private final Node plane;
    private final FlightInput input;
    private Vector3f velocity = new Vector3f();

    private float pitchSpeed;
    private float yawSpeed;
    private float rollSpeed;

    private float throttle;
    private final float maxThrust = 100000f;
    private final float rho = 1.225f;
    private final float wingArea = 20f;
    private final float mass = 10000f;
    private final float sideArea = 10f;

    private float rollCommand = 0f;

    private float pitchCommand = 0f;
    private float trimAoADeg = 3f;

    FlightPhysics(Node plane, FlightInput input) {
        this.plane = plane;
        this.input = input;
    }

    void update(float tpf) {
        updateRotation(tpf);
        updateSpeed(tpf);
        updateLift(tpf);
        updateDrag(tpf);
        updateSideSlip(tpf);
        plane.move(velocity.mult(tpf));
    }

    float getSpeed() {
        return velocity.length();
    }

    float getVerticalSpeed() {
        return velocity.y;
    }

    float getFlightPathAngle() {
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        return (float) Math.toDegrees((float) Math.atan2(velocity.y, horizontalSpeed));
    }

    float getThrottle() {
        return throttle;
    }

    float getPitchSpeed() {
        return pitchSpeed;
    }

    void resolveGroundCollision(float groundHeight) {
        Vector3f position = plane.getLocalTranslation();
        if (!Float.isNaN(groundHeight) && position.y < groundHeight + 0.5f) {
            plane.setLocalTranslation(position.x, groundHeight + 0.5f, position.z);
            if (velocity.y < 0) {
                velocity.y = 0;
            }
        }
    }

    float getAngleOfAttack() {
        if (velocity.length() == 0) return 0;

        Vector3f localVelocity = plane.getLocalRotation().inverse().mult(velocity);

        float angle = (float) Math.atan2(
                localVelocity.y,
                -localVelocity.z
        );

        float angleDeg = (float) Math.toDegrees(angle);

        return angleDeg;
    }

    float sideSlip() {
        Vector3f right = plane.getLocalRotation().mult(Vector3f.UNIT_X);

        float sideSpeed = velocity.dot(right);

        return sideSpeed;
    }

    float getSideSlipAngle() {
        Vector3f localVelocity = plane.getLocalRotation().inverse().mult(velocity);

        float angle = (float) Math.atan2(localVelocity.x, -localVelocity.z);

        float angleDeg = (float) Math.toDegrees(angle);

        return angleDeg;
    }

    private void updateSideSlip(float tpf) {
        float sideDragAmount = 0f;

        float betaDeg = getSideSlipAngle();
        float cy = 0f;

        float absBeta = Math.abs(betaDeg);

        if (absBeta > 5) cy = -betaDeg / 20f;
        if (absBeta < 5) cy = -betaDeg / 10f;

        cy = Math.max(-1f, Math.min(cy, 1f));

        Vector3f right = plane.getLocalRotation().mult(Vector3f.UNIT_X);

        float speed = velocity.length();

        sideDragAmount = 0.5f * rho * speed * speed * sideArea * cy;

        Vector3f sideDragForce = right.mult(sideDragAmount / mass * tpf);

        velocity = velocity.add(sideDragForce);
    }

    private float calculateDrag() {
        float geschwindigkeit = velocity.length();

        if (geschwindigkeit == 0) return 0;

        float angleDeg = getAngleOfAttack();
        float absAngle = Math.abs(angleDeg);
        float cd;

        float stallAngle = 15f;
        float excessAoA = absAngle - stallAngle;

        if (absAngle <= 15) cd = 0.02f + absAngle / 200f;
        else cd = 0.1f + excessAoA / 50f;

        float dragForce = geschwindigkeit * geschwindigkeit * 0.5f * wingArea * rho * cd;

        return dragForce;
    }

    private void updateDrag(float tpf) {
        if (velocity.length() == 0) return;

        Vector3f dragDirection = velocity.normalize().negate();

        float drag = calculateDrag();

        drag /= mass;

        Vector3f dragAcceleration = dragDirection.mult(drag * tpf);

        velocity = velocity.add(dragAcceleration);
    }

    private void updateLift(float tpf) {
        float angleDeg = getAngleOfAttack();
        float absAngle = Math.abs(angleDeg);

        float cl;
        float stallCl;

        if (absAngle < 15) {
            stallCl = absAngle / 15f;
        } else {
            stallCl = (15f / absAngle) * (15f / absAngle);
        }

        if (angleDeg < 0) {
            cl = stallCl;
        } else {
            cl = -stallCl;
        }

        float speed = velocity.length();

        if (speed == 0) {
            return;
        }

        Vector3f right = plane.getLocalRotation().mult(Vector3f.UNIT_X);

        Vector3f velocityDirection = velocity.normalize();

        Vector3f liftDirection = right.cross(velocityDirection).normalize();

        float liftAmount = 0.5f * rho
                * speed * speed
                * wingArea
                * cl;

        Vector3f lift = liftDirection.mult(liftAmount);

        Vector3f liftAcceleration = lift.mult(1f / mass);

        velocity = velocity.add(liftAcceleration.mult(tpf));
    }

    private void updateSpeed(float tpf) {
        Vector3f forward = plane.getLocalRotation().mult(Vector3f.UNIT_Z.negate());
        Vector3f gravity = new Vector3f(0, -9.81f, 0);

        float throttleForce = 0f;

        if (input.throttleUp) {
            throttle += tpf;
        }

        if (input.throttleDown) {
            throttle -= tpf;
        }

        if (Math.abs(input.throttleDownInput) > 0.05f) {
            throttle -= input.throttleDownInput * tpf;
        }

        if (Math.abs(input.throttleUpInput) > 0.05f) {
            throttle += input.throttleUpInput * tpf;
        }

        if (throttle > 1) throttle = 1;
        if (throttle < 0) throttle = 0;

        throttleForce = maxThrust * throttle;

        Vector3f thrustForce = forward.mult(throttleForce);
        Vector3f thrustAcceleration = thrustForce.mult(1f / mass);

        velocity = velocity.add(thrustAcceleration.mult(tpf));

        velocity = velocity.add(gravity.mult(tpf));
    }

    private float getRollAngle() {
        float[] angles = plane.getLocalRotation().toAngles(null);

        float rollRad = angles[2];
        float rollDeg = (float) Math.toDegrees(rollRad);

        return rollDeg;
    }

    private float calculateAoAControlFactor(float aoa) {
        if (aoa <= 15f) {
            return 1f;
        }

        if (aoa <= 30f) {
            return 1f - (aoa - 15f) / 20f;
        }

        return 0.05f;
    }

    private float calculateQFactor(float speed) {
        float referenceSpeed = 150f;

        float referencePressure = 0.5f * rho * referenceSpeed * referenceSpeed;

        float dynamicPressure = 0.5f * rho * speed * speed;

        float qFactor = dynamicPressure / referencePressure;

        return Math.max(0f, Math.min(qFactor, 1f));
    }

    private float calculateRequiredCl(float forwardSpeed) {
        float speed = Math.max(forwardSpeed, 0f);

        if (speed < 30f) {
            return 0f;
        }

        float weightForce = mass * 9.81f;

        float dynamicPart =
                0.5f
                        * rho
                        * speed
                        * speed
                        * wingArea;

        float requiredCl = weightForce / dynamicPart;

        return requiredCl;
    }

    private void updatePitch(float tpf, float qFactor, float aoaControlFactor, float forwardSpeed) {
        if (tpf <= 0f) {
            return;
        }


        float targetCommand = (input.pitchUp ? 1f : 0f) - (input.pitchDown ? 1f : 0f);


        float controlResponse = 7f;
        float blend = 1f - (float) Math.exp(-controlResponse * tpf);

        pitchCommand += (targetCommand - pitchCommand) * blend;


        float pressure = Math.max(0f, Math.min(3f, qFactor));

        float controlAuthority = Math.max(0f, Math.min(1f, aoaControlFactor));


        float currentAoA = -(float) Math.toRadians(getAngleOfAttack());

        float trimAoA = (float) Math.toRadians(trimAoADeg);


        float aoaError = trimAoA - currentAoA;

        float controlGain = 2.5f;
        float stabilityGain = 1.2f;

        float controlAcceleration = pitchCommand
                        * controlGain
                        * pressure
                        * controlAuthority;

        float stabilityAcceleration = aoaError
                        * stabilityGain
                        * pressure;

        float acceleration =
                controlAcceleration + stabilityAcceleration;


        float damping = 0.2f + 1.5f * (float) Math.sqrt(pressure);


        float decay = (float) Math.exp(-damping * tpf);

        float equilibriumRate = acceleration / damping;

        pitchSpeed = equilibriumRate + (pitchSpeed - equilibriumRate) * decay;


        float maxPitchSpeed = (float) Math.toRadians(40f);

        pitchSpeed = Math.max(-maxPitchSpeed, Math.min(maxPitchSpeed, pitchSpeed));
    }

    private void updateRoll(float tpf, float aoaControlFactor) {
        if (tpf <= 0f) {
            return;
        }

        float deadzone = 0.05f;
        float stick = input.rollInput;


        if (Math.abs(stick) <= deadzone) {
            stick = 0f;
        } else {
            stick = Math.signum(stick)
                    * (Math.abs(stick) - deadzone)
                    / (1f - deadzone);
        }


        float keyboard = (input.rollLeft ? 1f : 0f) - (input.rollRight ? 1f : 0f);

        // Tastatur hat bei Betätigung Vorrang.
        float targetCommand = keyboard != 0f ? keyboard : stick;

        targetCommand = Math.max(-1f, Math.min(1f, targetCommand));


        float controlResponse = 8f;
        float blend = 1f - (float) Math.exp(-controlResponse * tpf);

        rollCommand += (targetCommand - rollCommand) * blend;

        float controlAuthority = Math.max(0f, Math.min(1f, aoaControlFactor));


        float rollAcceleration = 6f;
        float rollDamping = 2f;

        float acceleration = rollCommand
                        * rollAcceleration
                        * controlAuthority;


        float decay = (float) Math.exp(-rollDamping * tpf);

        float equilibriumRate = acceleration / rollDamping;

        rollSpeed = equilibriumRate + (rollSpeed - equilibriumRate) * decay;

        float maxRollSpeed = (float) Math.toRadians(150f);

        rollSpeed = Math.max(-maxRollSpeed, Math.min(maxRollSpeed, rollSpeed));
    }

    private void updateYaw(float tpf, float qFactor, float aoaControlFactor) {
        float betaStability = 0.03f;

        float currentBeta = getSideSlipAngle();
        float targetBeta = 0f;
        float betaError = targetBeta-currentBeta;

        betaError = Math.max(-15f, Math.min(betaError, 15f));

        float restoringYawAcceleration  = betaError
                * betaStability
                * qFactor;

        float rotationSpeed = 1.5f;
        float abbremsen = 1f;

        float yawDamping = 1.5f;

        float speed = velocity.length();

        float maxAerodynamicTurnRate;

        if (speed > 1f) {
            maxAerodynamicTurnRate = 25f / speed;
        } else {
            maxAerodynamicTurnRate = 0f;
        }

        float maxYawSpeed = Math.min(0.15f, maxAerodynamicTurnRate * 0.3f);

        // Dämpfung
        if (!input.yawLeft && !input.yawRight && Math.abs(input.yawInput) < 0.05f) {
            if (yawSpeed > 0) {
                yawSpeed = Math.max(0, yawSpeed - abbremsen * tpf);
            }

            if (yawSpeed < 0) {
                yawSpeed = Math.min(0, yawSpeed + abbremsen * tpf);
            }

            yawSpeed -= yawSpeed * yawDamping * tpf;
        }

        // Tastatur
        if (input.yawLeft) {
            yawSpeed += rotationSpeed * tpf * aoaControlFactor;
        }

        if (input.yawRight) {
            yawSpeed -= rotationSpeed * tpf * aoaControlFactor;
        }

        // Controller
        if (Math.abs(input.yawInput) > 0.05f) {
            yawSpeed += input.yawInput
                            * rotationSpeed
                            * tpf
                            * aoaControlFactor;
        }

        yawSpeed += restoringYawAcceleration * tpf;
        // Begrenzen
        yawSpeed = Math.max(-maxYawSpeed, Math.min(yawSpeed, maxYawSpeed));
    }

    private void updateRotation(float tpf) {
        Vector3f forward = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Z.negate());

        float forwardSpeed = velocity.dot(forward);

        float speed = velocity.length();
        float aoa = Math.abs(getAngleOfAttack());

        float qFactor = calculateQFactor(speed);
        float aoaControlFactor = calculateAoAControlFactor(aoa);

        updatePitch(tpf, qFactor, aoaControlFactor, forwardSpeed);
        updateRoll(tpf, aoaControlFactor);
        updateYaw(tpf, qFactor, aoaControlFactor);

        plane.rotate(
                pitchSpeed * tpf,
                yawSpeed * tpf,
                rollSpeed * tpf
        );
    }

}
