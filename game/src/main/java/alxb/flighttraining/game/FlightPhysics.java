package alxb.flighttraining.game;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

final class FlightPhysics {
    private final Node plane;
    private final FlightInput input;
    private final FlightCommandAi aiInput;
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


    private static final float G = 9.81f;
    private final Vector3f gravity = new Vector3f(0f, -G, 0f);

    private float normalG = 0f;


    FlightPhysics(Node plane, FlightInput input) {
        this.plane = plane;
        this.input = java.util.Objects.requireNonNull(input);
        this.aiInput = null;
    }

    FlightPhysics(Node plane, FlightCommandAi commands) {
        this.plane = java.util.Objects.requireNonNull(plane);
        this.input = null;
        this.aiInput = java.util.Objects.requireNonNull(commands);
    }

    public void reset(Vector3f initialVelocity, float initialThrottle) {
        setVelocity(initialVelocity);
        pitchSpeed = yawSpeed = rollSpeed = 0f;
        pitchCommand = rollCommand = 0f;
        throttle = clamp(initialThrottle, 0f, 1f);
        normalG = 0f;
    }

    public void setVelocity(Vector3f initialVelocity) {
        if (initialVelocity == null || !Float.isFinite(initialVelocity.x)
                || !Float.isFinite(initialVelocity.y) || !Float.isFinite(initialVelocity.z)) {
            throw new IllegalArgumentException("Ungültige Geschwindigkeit");
        }
        velocity.set(initialVelocity);
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : 0f;
    }

    private static float stick(float value) {
        value = clamp(value, -1f, 1f);
        float magnitude = Math.abs(value);
        return magnitude <= 0.05f ? 0f
                : Math.signum(value) * (magnitude - 0.05f) / 0.95f;
    }

    private float requestedPitch() {
        if (aiInput != null) return clamp(aiInput.getPitch(), -1f, 1f);
        float keyboard = (input.pitchUp ? 1f : 0f) - (input.pitchDown ? 1f : 0f);
        return keyboard != 0f ? keyboard : stick(input.pitchInput);
    }

    private float requestedRoll() {
        if (aiInput != null) return clamp(aiInput.getRoll(), -1f, 1f);
        float keyboard = (input.rollLeft ? 1f : 0f) - (input.rollRight ? 1f : 0f);
        return keyboard != 0f ? keyboard : stick(input.rollInput);
    }

    private float requestedYaw() {
        if (aiInput != null) return clamp(aiInput.getYaw(), -1f, 1f);
        float keyboard = (input.yawLeft ? 1f : 0f) - (input.yawRight ? 1f : 0f);
        return keyboard != 0f ? keyboard : stick(input.yawInput);
    }

    float getNormalG() {
        return normalG;
    }

    void update(float tpf) {
        if (tpf <= 0f) {
            return;
        }

        Vector3f previousVelocity = velocity.clone();

        updateRotation(tpf);
        updateSpeed(tpf);
        updateLift(tpf);
        updateDrag(tpf);
        updateSideSlip(tpf);


        Vector3f acceleration = velocity
                .subtract(previousVelocity)
                .divideLocal(tpf);


        Vector3f specificForce = acceleration.subtract(gravity);


        Vector3f aircraftUp = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Y)
                .normalizeLocal();

        normalG = specificForce.dot(aircraftUp) / G;

        plane.move(velocity.mult(tpf));
    }

    Vector3f getVelocity() {
        return velocity.clone();
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

        float throttleForce = 0f;

        if (aiInput != null) {
            throttle = clamp(aiInput.getThrottle(), 0f, 1f);
        } else {
            float change = (input.throttleUp ? 1f : 0f)
                    - (input.throttleDown ? 1f : 0f);
            if (Math.abs(input.throttleUpInput) > 0.05f)
                change += input.throttleUpInput;
            if (Math.abs(input.throttleDownInput) > 0.05f)
                change -= input.throttleDownInput;
            throttle = clamp(throttle + change * tpf, 0f, 1f);
        }

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


        float targetCommand = requestedPitch();


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

        float targetCommand = requestedRoll();

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
        if (Math.abs(requestedYaw()) < 0.0001f) {
            if (yawSpeed > 0) {
                yawSpeed = Math.max(0, yawSpeed - abbremsen * tpf);
            }

            if (yawSpeed < 0) {
                yawSpeed = Math.min(0, yawSpeed + abbremsen * tpf);
            }

            yawSpeed -= yawSpeed * yawDamping * tpf;
        }

        yawSpeed += requestedYaw() * rotationSpeed * tpf * aoaControlFactor;

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

        // Vereinfachte Kurvenkoordination nur für den KI-Autopiloten.
        // Schräglage erzeugt eine Kursänderung statt nur seitlichen Drift.
        if (aiInput != null && speed > 100f) {
            Vector3f right = plane.getLocalRotation().mult(Vector3f.UNIT_X);
            Vector3f up = plane.getLocalRotation().mult(Vector3f.UNIT_Y);
            float bank = (float)Math.atan2(right.y, up.y);
            bank = Math.max(-1.2f, Math.min(1.2f, bank));
            float turnRate = G * (float)Math.tan(bank) / speed;
            com.jme3.math.Quaternion turn = new com.jme3.math.Quaternion()
                    .fromAngleAxis(turnRate * tpf, Vector3f.UNIT_Y);
            plane.setLocalRotation(turn.mult(plane.getLocalRotation()));
        }
        plane.rotate(
                pitchSpeed * tpf,
                yawSpeed * tpf,
                rollSpeed * tpf
        );
    }

}
