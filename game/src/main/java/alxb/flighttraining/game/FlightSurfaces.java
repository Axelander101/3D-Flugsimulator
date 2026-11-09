package alxb.flighttraining.game;

import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public final class FlightSurfaces {

    private final FlightInput input;

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

    public FlightSurfaces(Spatial model, FlightInput input) {
        this.input = input;
        initPitchSurfaces(model);
    }

    public void update(float tpf) {
        updateControlSurfaces(tpf);
    }

    private void initPitchSurfaces(Spatial model) {
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

        neutralAileronLeft = stabilatorLeft.getInitialTransform().getRotation().clone();
        neutralAileronRight = stabilatorRight.getInitialTransform().getRotation().clone();


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

}
