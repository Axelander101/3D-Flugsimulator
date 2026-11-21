package alxb.flighttraining.game.enemy;

import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.flight.FlightCommandAi;
import alxb.flighttraining.game.flight.FlightPhysics;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

/** Autopilot und vereinfachte Sensoren, passend zu FlightPhysics (-Z vorwärts). */
public final class EnemyAI {
    public enum State { PATROL, INTERCEPT, ATTACK, SEARCH, EVADE }
    private final FlightCommandAi commands;
    private final Node plane;
    private final FlightPhysics physics;
    private final Vector3f home;
    private final Vector3f observation = new Vector3f();
    private final Vector3f observedVelocity = new Vector3f();
    private State state = State.PATROL;
    private boolean hasContact, firing;
    private Node trackedTarget;
    private float age = Float.POSITIVE_INFINITY;
    private float scanTimer, elapsed, recoveryTimer, speedIntegral;
    private float targetAltitude, targetSpeed = 220f, lastBank;
    private int waypoint;
    private float turnDirection = 1f;
    private DogfightSettings.Difficulty difficulty = DogfightSettings.Difficulty.NORMAL;
    public void setDifficulty(DogfightSettings.Difficulty value) { difficulty = value; }


    public EnemyAI(FlightCommandAi commands, Node plane, FlightPhysics physics) {
        this.commands = commands;
        this.plane = plane;
        this.physics = physics;
        home = plane.getWorldTranslation().clone();
        targetAltitude = home.y;
    }

    public void rebase(Vector3f delta){home.subtractLocal(delta);observation.subtractLocal(delta);}

    public State getState() { return state; }
    public boolean isFiring() { return firing; }
    public float getContactAge() { return age; }
    public float getTargetAltitude() { return targetAltitude; }
    public float getTargetSpeed() { return targetSpeed; }
    public Vector3f getEstimatedTargetPosition() {
        return hasContact ? observation.add(observedVelocity.mult(Math.min(age, 60f))) : null;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
    private static float wrap(float angle) {
        return (float) Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    public void update(float dt, Node target, Vector3f targetVelocity, float ground) {
        if (!Float.isFinite(dt) || dt <= 0) return;
        elapsed += dt;
        age += dt;
        firing = false;
        Vector3f position = plane.getWorldTranslation();
        Vector3f forward = plane.getWorldRotation().mult(new Vector3f(0, 0, -1));
        scan(dt, target, targetVelocity, position, forward);

        Vector3f estimate = getEstimatedTargetPosition();
        float distance = estimate == null ? Float.POSITIVE_INFINITY : position.distance(estimate);
        float clearance = Float.isFinite(ground) ? position.y - ground : Float.POSITIVE_INFINITY;
        float speed = physics.getAirspeed();
        float aoa = Math.abs(physics.getAngleOfAttack());
        // Bei Sinkflug früher abfangen; noch keine vorausschauende Geländeabfrage.
        float safeHeight = 220f + Math.max(0, -physics.getVerticalSpeed()) * 5f;
        if (clearance < safeHeight || physics.getEquivalentAirspeed() < 90 || aoa > 30 || distance < 160) {
            recoveryTimer = 2.5f;
        }
        Vector3f aim;
        boolean recovering = recoveryTimer > 0;
        if (recovering) {
            state = State.EVADE;
            recoveryTimer = Math.max(0, recoveryTimer - dt);
            Vector3f levelForward = new Vector3f(forward.x, 0, forward.z);
            if (levelForward.lengthSquared() < 0.01f) levelForward.set(0, 0, -1);
            aim = position.add(levelForward.normalizeLocal().mult(1800));
            aim.y = clearance < safeHeight ? Math.max(position.y + 300, ground + 500) : position.y;
            targetSpeed = 240;
        } else if (hasContact && age < 60) {
            state = State.INTERCEPT;
            float lead = clamp(distance / 900f, 0, 2f) * difficulty.aimFactor;
            aim = estimate.add(observedVelocity.mult(lead));
            float targetMotion = observedVelocity.length();
            boolean following = targetMotion > 1 && forward.dot(observedVelocity.normalize()) > 0.5f;
            targetSpeed = following && distance < 2000
                    ? clamp(targetMotion + (distance - 550f) * 0.06f, 150, 310) : 310;
            Vector3f offset = aim.subtract(position);
            float alignment = offset.lengthSquared() > 1 ? forward.dot(offset.normalize()) : -1;
            if (age < 0.6f && distance > 200 && distance < 1000 && alignment > 0.97f) {
                state = State.ATTACK;
                firing = alignment > (difficulty == DogfightSettings.Difficulty.EASY ? 0.9998f : 0.999f)
                        && elapsed % 1.6f < 0.35f * difficulty.aimFactor;
            }
        } else if (hasContact && age < 120) {
            state = State.SEARCH;
            float angle = (age - 60) * 0.16f;
            aim = estimate.add((float)Math.cos(angle)*1200, 150, (float)Math.sin(angle)*1200);
            targetSpeed = 210;
        } else {
            state = State.PATROL;
            float angle = waypoint * (float)Math.PI / 2;
            aim = home.add((float)Math.cos(angle)*2200, 150, (float)Math.sin(angle)*2200);
            if (position.distance(aim) < 400) waypoint = (waypoint + 1) % 4;
            targetSpeed = 220;
        }
        targetAltitude = aim.y;
        if (Float.isFinite(ground)) targetAltitude = Math.max(targetAltitude, ground + 400);
        flyInstruments(dt, aim, forward, position, recovering, clearance < safeHeight);
    }

    private void scan(float dt, Node target, Vector3f velocity, Vector3f position, Vector3f forward) {
        if (target != trackedTarget || target == null || target.getParent() == null) {
            hasContact = false;
            age = Float.POSITIVE_INFINITY;
            trackedTarget = target;
        }
        scanTimer -= dt;
        if (scanTimer > 0) return;
        scanTimer = difficulty.scanInterval;
        if (target == null || target.getParent() == null) return;
        Vector3f offset = target.getWorldTranslation().subtract(position);
        float distance = offset.length();
        // Nahbereich rundum; vorwärts gerichteter Radarkegel darüber hinaus.
        boolean detected = distance < 3000
                || (distance < 12000 && distance > 0.01f && forward.dot(offset.normalize()) > 0.5f);
        if (!detected) return;
        observation.set(target.getWorldTranslation());
        observedVelocity.set(velocity);
        age = 0;
        hasContact = true;
    }

    private void flyInstruments(float dt, Vector3f aim, Vector3f forward,
                                Vector3f position, boolean recovering, boolean low) {
        Vector3f delta = aim.subtract(position);
        float heading = (float)Math.atan2(-forward.x, -forward.z);
        float desiredHeading = (float)Math.atan2(-delta.x, -delta.z);
        float error = wrap(desiredHeading - heading);
        // Hinter dem Heck nicht bei +/-180 Grad ständig die Kurvenseite wechseln.
        if (Math.abs(error) < 2.3f && Math.abs(error) > 0.15f) turnDirection = Math.signum(error);
        if (Math.abs(error) > 2.6f) error = turnDirection * Math.abs(error);
        if (!recovering && hasContact && age < 60 && Math.abs(error) > 1.1f) targetSpeed = 195f;
        Vector3f right = plane.getWorldRotation().mult(Vector3f.UNIT_X);
        Vector3f up = plane.getWorldRotation().mult(Vector3f.UNIT_Y);
        float bank = (float)Math.atan2(right.y, up.y);
        float bankRate = wrap(bank - lastBank) / Math.max(dt, 0.001f);
        lastBank = bank;
        float targetBank = recovering ? 0 : clamp(error * 1.6f, -1.13f * difficulty.turnFactor, 1.13f * difficulty.turnFactor);
        commands.setRoll((targetBank - bank) * 2.2f - bankRate * 0.5f);
        // Höhenmesser -> Soll-Steigrate -> Soll-Nicklage -> Höhenruder.
        float climb = clamp((targetAltitude - position.y) * 0.08f, -20, low ? 40 : 25);
        float speed = Math.max(physics.getAirspeed(), 1);
        float bankSupport = Math.max(0.3f, (float)Math.cos(bank));
        float verticalAcceleration = clamp((climb - physics.getVerticalSpeed()) * 0.65f, -12f, 18f);
        float desiredLoad = (1f + verticalAcceleration / 9.80665f) / bankSupport;
        desiredLoad = clamp(desiredLoad, -2f, physics.getParameters().positiveGLimit());
        if (physics.getAngleOfAttack() < -26 || (physics.getEquivalentAirspeed() < 85 && !low))
            desiredLoad = Math.min(desiredLoad, 0.6f);
        commands.setPitch(physics.pitchCommandForLoadFactor(desiredLoad));
        // Physik enthält Schiebestabilisierung; Seitenruder nur als geringe Unterstützung.
        commands.setYaw(clamp(error * 0.03f, -0.1f, 0.1f));
        float speedError = targetSpeed - speed;
        speedIntegral = clamp(speedIntegral + speedError * dt, -100, 100);
        float trim = physics.getTrimThrottle(targetSpeed, desiredLoad, climb);
        commands.setThrottle(recovering ? 1 : clamp(trim + speedError * 0.008f + speedIntegral * 0.0006f, 0, 1));
    }
}
