package alxb.flighttraining.game;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.effect.Particle;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Plane;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public final class FlightSurfaces {

    private final FlightInput input;
    private final FlightPhysics physics;
    private final Node plane;
    private final AssetManager assetManager;

    private Joint stabilatorLeft;
    private Joint stabilatorRight;

    private Joint aileronLeft;
    private Joint aileronRight;

    private Joint leadingEdgeFlapLeft;
    private Joint leadingEdgeFlapRight;

    private Joint rudderLeft;
    private Joint rudderRight;

    private Quaternion neutralLeft;
    private Quaternion neutralRight;

    private Quaternion neutralAileronLeft;
    private Quaternion neutralAileronRight;

    private Quaternion neutralLeadingEdgeFlapLeft;
    private Quaternion neutralLeadingEdgeFlapRight;

    private Quaternion neutralRudderLeft;
    private Quaternion neutralRudderRight;


    private ParticleEmitter wingVaporLeft;
    private ParticleEmitter wingVaporRight;


    private float stabilatorAngle;
    private float aileronAngle = 0f;
    private float leadingEdgeFlapAngle = 0f;
    private float rudderAngle = 0f;

    private final Vector3f rudderAxisLeft =
            new Vector3f(-0.78f, -0.38f, 2.74f)
                    .normalizeLocal();

    private final Vector3f rudderAxisRight =
            new Vector3f(0.78f, -0.38f, 2.74f)
                    .normalizeLocal();


    private AnimComposer composer;
    private boolean wasSupersonic;
    private boolean wasStalling;

    public FlightSurfaces(Spatial model, FlightInput input, AnimComposer composer, FlightPhysics physics,
                            AssetManager assetManager, Node plane) {
        this.input = input;
        this.composer = composer;
        this.physics = physics;
        this.assetManager = assetManager;
        this.plane = plane;
        initSurfaces(model);
        wingVaporLeft = createWingVapor(plane, assetManager, "WingVapor.L",
                new Vector3f(-0.6f, 0.3f, -1f));
        wingVaporRight = createWingVapor(plane, assetManager, "WingVapor.R",
                new Vector3f(0.6f, 0.3f, -1f));
    }

    public void update(float tpf) {
        updateControlSurfaces(tpf);
        updateWingVapor();
    }

    private void initSurfaces(Spatial model) {
        SkinningControl skin = findSkinningControl(model);

        if (skin == null) {
            throw new IllegalStateException(
                    "Kein SkinningControl im Flugzeug gefunden"
            );
        }

        stabilatorLeft = skin.getArmature().getJoint("Stabilator.L");
        stabilatorRight = skin.getArmature().getJoint("Stabilator.R");

        aileronLeft = skin.getArmature().getJoint("Aileron.L");
        aileronRight = skin.getArmature().getJoint("Aileron.R");

        leadingEdgeFlapLeft = skin.getArmature().getJoint("LeadingFlap.L");
        leadingEdgeFlapRight = skin.getArmature().getJoint("LeadingFlap.R");

        rudderLeft = skin.getArmature().getJoint("Rudder.L");
        rudderRight = skin.getArmature().getJoint("Rudder.R");


        if (stabilatorLeft == null || stabilatorRight == null) {
            throw new IllegalStateException(
                    "Stabilator-Bones fehlen im geladenen Modell"
            );
        }

        neutralLeft = stabilatorLeft.getInitialTransform().getRotation().clone();
        neutralRight = stabilatorRight.getInitialTransform().getRotation().clone();


        if (aileronLeft == null || aileronRight == null) {
            throw new IllegalStateException(
                    "Stabilator-Bones fehlen im geladenen Modell"
            );
        }

        neutralAileronLeft = aileronLeft.getInitialTransform().getRotation().clone();
        neutralAileronRight = aileronRight.getInitialTransform().getRotation().clone();


        if (leadingEdgeFlapLeft == null || leadingEdgeFlapRight == null) {
            throw new IllegalStateException(
                    "Stabilator-Bones fehlen im geladenen Modell"
            );
        }

        neutralLeadingEdgeFlapLeft = leadingEdgeFlapLeft.getInitialTransform().getRotation().clone();
        neutralLeadingEdgeFlapRight = leadingEdgeFlapRight.getInitialTransform().getRotation().clone();

        if (rudderLeft == null || rudderRight == null) {
            throw new IllegalStateException(
                    "Stabilator-Bones fehlen im geladenen Modell"
            );
        }

        neutralRudderLeft = rudderLeft.getInitialTransform().getRotation().clone();
        neutralRudderRight = rudderRight.getInitialTransform().getRotation().clone();

    }

    private ParticleEmitter createWingVapor(
            Node plane, AssetManager assets,
            String name, Vector3f position
    ) {
        ParticleEmitter vapor = new ParticleEmitter(
                name, ParticleMesh.Type.Triangle, 64
        );

        Material material = new Material(
                assets, "Common/MatDefs/Misc/Particle.j3md"
        );
        material.setTexture("Texture",
                assets.loadTexture("Textures/Particles/vapor.png"));
        vapor.setMaterial(material);

        vapor.setParticlesPerSec(0f);
        vapor.setInWorldSpace(false);
        vapor.setGravity(0f, 0f, 0f);
        vapor.setLowLife(0.25f);
        vapor.setHighLife(0.45f);
        vapor.setStartSize(0.35f);
        vapor.setEndSize(1.3f);
        vapor.setStartColor(new ColorRGBA(1f, 1f, 1f, 0.3f));
        vapor.setEndColor(new ColorRGBA(1f, 1f, 1f, 0f));
        vapor.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0f, 0f, 9f));
        vapor.getParticleInfluencer().setVelocityVariation(0.2f);

        vapor.setLocalTranslation(position);
        plane.attachChild(vapor);
        return vapor;
    }

    private void stallEffect(){
        composer.setCurrentAction(
                "05_Stall_Buffet_Recovery",
                AnimComposer.DEFAULT_LAYER,
                false
        );

    }

    private void superSonicEffect(){
        composer.setCurrentAction(
                "06_Transonic_FX_Preview",
                AnimComposer.DEFAULT_LAYER,
                false
        );
    }

    private SkinningControl findSkinningControl(Spatial spatial) {
        SkinningControl skin = spatial.getControl(SkinningControl.class);

        if (skin != null) {
            return skin;
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                skin = findSkinningControl(child);

                if (skin != null) {
                    return skin;
                }
            }
        }

        return null;
    }

    private void updateControlSurfaces(float tpf) {
        float pitchCommand =
                (input.pitchUp ? 1f : 0f)
                        - (input.pitchDown ? 1f : 0f);

        float rollCommand =
                (input.rollLeft ? 1f : 0f)
                        - (input.rollRight ? 1f : 0f);

        float yawCommand =
                (input.yawLeft ? 1f : 0f)
                        - (input.yawRight ? 1f : 0f);

        float maxPitchAngle = (float) Math.toRadians(20f);
        float maxRollAngle = (float) Math.toRadians(20f);
        float maxRudderAngle = (float) Math.toRadians(25f);

        // Höherer Wert = schnellere sichtbare Reaktion.
        float response = 25f;
        float blend = 1f - (float) Math.exp(-response * tpf);

        stabilatorAngle +=
                (pitchCommand * maxPitchAngle - stabilatorAngle)
                        * blend;

        aileronAngle +=
                (rollCommand * maxRollAngle - aileronAngle)
                        * blend;

        rudderAngle +=
                (yawCommand * maxRudderAngle - rudderAngle)
                        * blend;

        // Auch den gespeicherten Winkel begrenzen.
        rudderAngle = Math.max(-maxRudderAngle, Math.min(maxRudderAngle, rudderAngle));



        Quaternion pitchDeflection = new Quaternion()
                .fromAngleAxis(stabilatorAngle, Vector3f.UNIT_X);

        stabilatorLeft.setLocalRotation(
                neutralLeft.mult(pitchDeflection)
        );

        stabilatorRight.setLocalRotation(
                neutralRight.mult(pitchDeflection)
        );


        float pitchMix = stabilatorAngle * 0.3f;

        float leftAngle = pitchMix + aileronAngle;
        float rightAngle = pitchMix - aileronAngle;

        // Gesamtausschlag begrenzen.
        float maxAileronAngle = (float) Math.toRadians(25f);

        leftAngle = Math.max(-maxAileronAngle, Math.min(maxAileronAngle, leftAngle));

        rightAngle = Math.max(-maxAileronAngle, Math.min(maxAileronAngle, rightAngle));

        Quaternion leftDeflection = new Quaternion()
                .fromAngleAxis(leftAngle, Vector3f.UNIT_X);

        Quaternion rightDeflection = new Quaternion()
                .fromAngleAxis(rightAngle, Vector3f.UNIT_X);

        aileronLeft.setLocalRotation(
                neutralAileronLeft.mult(leftDeflection)
        );

        aileronRight.setLocalRotation(
                neutralAileronRight.mult(rightDeflection)
        );



        Quaternion leftRudderDeflection = new Quaternion()
                .fromAngleAxis(-rudderAngle, rudderAxisLeft);

        Quaternion rightRudderDeflection = new Quaternion()
                .fromAngleAxis(-rudderAngle, rudderAxisRight);

        rudderLeft.setLocalRotation(
                neutralRudderLeft.mult(leftRudderDeflection)
        );

        rudderRight.setLocalRotation(
                neutralRudderRight.mult(rightRudderDeflection)
        );
    }

    public void updateEffects() {
        boolean supersonic = physics.getSpeed() >= 343f;
        boolean stalling = Math.abs(physics.getAngleOfAttack()) > 15f;

        if (stalling && !wasStalling) {
            stallEffect();
        } else if (supersonic && !wasSupersonic) {
            superSonicEffect();
        }

        wasStalling = stalling;
        wasSupersonic = supersonic;
    }

    private void updateWingVapor() {
        float aoa = -physics.getAngleOfAttack();
        float intensity = Math.max(0f, Math.min(1f, (aoa - 10f) / 12f));
        float rate = physics.getSpeed() > 50f ? 70f * intensity : 0f;
        wingVaporLeft.setParticlesPerSec(rate);
        wingVaporRight.setParticlesPerSec(rate);
    }


}




