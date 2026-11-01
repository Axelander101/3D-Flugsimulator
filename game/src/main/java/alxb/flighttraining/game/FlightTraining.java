package alxb.flighttraining.game;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapText;
import com.jme3.input.Joystick;
import com.jme3.input.JoystickAxis;
import com.jme3.input.JoystickConnectionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.JoyAxisTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.app.state.AppState;

import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;

import java.util.ArrayList;


public class FlightTraining extends SimpleApplication{

    //Joystick
    private Joystick xboxController;

    private float rollInput = 0f;
    private float yawInput = 0f;
    private float pitchInput = 0f;

    private float throttleUpInput = 0f;
    private float throttleDownInput = 0f;


    //Flugrichtung
    private boolean pitchUp;
    private boolean pitchDown;
    private boolean rollLeft;
    private boolean rollRight;
    private boolean yawLeft;
    private boolean yawRight;
    private float pitchSpeed;
    private float yawSpeed;
    private float rollSpeed;

    //Fluggeschwindigkeit
    private float throttle;
    private float maxThrust = 100000f;

    private boolean throttleUp;
    private boolean throttleDown;

    //Flugzeugdaten
    private float rho = 1.225f;
    private float wingArea = 20f;
    private float mass = 10000f;
    private float sideArea = 10f;


    //Map
    TerrainQuad terrain = new TerrainQuad();

    private ArrayList<TerrainQuad> terrainTiles = new ArrayList<>();


    //Hud
    private BitmapText hudText;

    private Vector3f velocity = new Vector3f();

    private Geometry plane;

    public FlightTraining() {

    }

    public FlightTraining(AppState... initialStates) {
        super(initialStates);
    }


    public float getAngleOfAttack() {

        if (velocity.length() == 0) return 0;

        float liftFaktor = 0f;

        Vector3f localVelocity = plane.getLocalRotation().inverse().mult(velocity);

        float angle = (float) Math.atan2(
                localVelocity.y,
                -localVelocity.z
        );

        float angleDeg = (float) Math.toDegrees(angle);

        return angleDeg;
    }

    public float sideSlip(){
        Vector3f right = plane.getLocalRotation().mult(Vector3f.UNIT_X);

        float sideSpeed = velocity.dot(right);

        return sideSpeed;
    }

    public float getSideSlipAngle(){
        Vector3f localVelocity = plane.getLocalRotation().inverse().mult(velocity);

        float angle = (float) Math.atan2(localVelocity.x, -localVelocity.z);

        float angleDeg = (float) Math.toDegrees(angle);

        return angleDeg;
    }

    private void updateSideSlip(float tpf){
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

    private float dragBerechnen(){
        float geschwindigkeit = velocity.length();

        if (geschwindigkeit == 0) return 0;

        float angleDeg = getAngleOfAttack();
        float absAngle = Math.abs(angleDeg);
        float cd;

        if (absAngle <= 15) cd = 0.02f + absAngle / 200f;
        else cd = 0.1f + (absAngle - 15f) / 50f;

        float dragForce = geschwindigkeit * geschwindigkeit * 0.5f * wingArea * rho * cd;

        return dragForce;
    }

    private void updateDrag(float tpf){
        if (velocity.length() == 0) return;

        Vector3f dragDirection = velocity.normalize().negate();

        float drag = dragBerechnen();

        drag /= mass;

        Vector3f dragAcceleration = dragDirection.mult(drag * tpf);

        velocity = velocity.add(dragAcceleration);

    }

    private void auftrieb(float tpf) {

        float angleDeg = getAngleOfAttack();
        float absAngle = Math.abs(angleDeg);

        float cl;

        if (absAngle < 15) {
            cl = -(angleDeg / 15f);
        } else {
            cl = -(15f / angleDeg);
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

        if (throttleUp) {
            throttle += tpf;
        }

        if (throttleDown) {
            throttle -= tpf;
        }

        if (Math.abs(throttleDownInput) > 0.05f){
            throttle -= throttleDownInput * tpf;
        }

        if (Math.abs(throttleUpInput) > 0.05f){
            throttle += throttleUpInput * tpf;
        }


        if (throttle > 1) throttle = 1;
        if (throttle < 0) throttle = 0;

        throttleForce = maxThrust * throttle;

        Vector3f thrustForce = forward.mult(throttleForce);
        Vector3f thrustAcceleration = thrustForce.mult(1f / mass);

        velocity = velocity.add(thrustAcceleration.mult(tpf));

        velocity = velocity.add(gravity.mult(tpf));

    }

    private void updateCamera() {

        Vector3f forward = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Z.negate());

        Vector3f up = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Y);

        Vector3f planePosition = plane.getLocalTranslation();

        Vector3f cameraPosition = planePosition
                .subtract(forward.mult(10))
                .add(up.mult(4));

        cam.setLocation(cameraPosition);

        cam.lookAt(
                planePosition.add(forward.mult(10)),
                up
        );
    }

    private float rollStabelize(){
        float[] angles = plane.getLocalRotation().toAngles(null);

        float rollRad = angles[2];
        float rollDeg = (float) Math.toDegrees(rollRad);

        return rollDeg;
    }

    private float pitchStabelize(){
        float[] angles = plane.getLocalRotation().toAngles(null);

        float rollRad = angles[0];
        float rollDeg = (float) Math.toDegrees(rollRad);

        return rollDeg;
    }

    private void updateRotation(float tpf){
        Vector3f forward = plane.getLocalRotation().mult(Vector3f.UNIT_Z.negate());

        float forwardSpeed = velocity.dot(forward);

        float rotationSpeed = 1.5f;
        float abbremsen = 1f;
        float aoa = Math.abs(getAngleOfAttack());

        float aoaControlFactor = 1f;

        if (aoa <= 15f) {
            aoaControlFactor = 1f;
        } else if (aoa <= 30f) {
            aoaControlFactor = 1f - (aoa - 15f) / 20f;
        } else {
            aoaControlFactor = 0.05f;
        }

        float effectiveSpeed = Math.max(0f, forwardSpeed);

        float controlFactor =
                0.2f + effectiveSpeed / 200f;

        controlFactor =
                Math.max(0.2f, Math.min(controlFactor, 1f));


        //Stabilisation
        float pitchDamping = 1f;
        float pitchStabilisationStrength = 0.2f;
        float pitchDeg = pitchStabelize();

        float yawDamping = 1.5f;

        float rollDeg = rollStabelize();
        float stabilisationStrength = 0.2f;
        float damping = 2.0f;



        float speed = velocity.length();

        float maxAerodynamicTurnRate;

        if (speed > 1f) {
            maxAerodynamicTurnRate = 25f / speed;
        } else {
            maxAerodynamicTurnRate = 0f;
        }

        float maxPitchSpeed = Math.min(0.5f, maxAerodynamicTurnRate);

        float maxRollSpeed = 1.5f;

        float maxYawSpeed = Math.min(0.15f, maxAerodynamicTurnRate * 0.3f);




        if (!pitchUp && !pitchDown && Math.abs(pitchInput) < 0.05f) {
            if (pitchSpeed > 0) pitchSpeed = Math.max(0, pitchSpeed - abbremsen * tpf);
            if (pitchSpeed < 0) pitchSpeed = Math.min(0, pitchSpeed + abbremsen * tpf);

            //pitchSpeed += (-pitchDeg * pitchStabilisationStrength) * tpf;
            pitchSpeed -= pitchSpeed * pitchDamping * tpf;
        }

        if (!rollLeft && !rollRight && Math.abs(rollInput) < 0.05f)  {
            if (rollSpeed > 0) rollSpeed = Math.max(0, rollSpeed - abbremsen * tpf);

            if (rollSpeed < 0) rollSpeed = Math.min(0, rollSpeed + abbremsen * tpf);

            rollSpeed += (-rollDeg * stabilisationStrength) * tpf;
            rollSpeed -= rollSpeed * damping * tpf;

        }

        if (!yawRight && !yawLeft && Math.abs(yawInput) < 0.05f) {
            if (yawSpeed > 0) yawSpeed = Math.max(0, yawSpeed - abbremsen * tpf);
            if (yawSpeed < 0) yawSpeed = Math.min(0, yawSpeed + abbremsen * tpf);

            //yawSpeed += (-betaDeg * yawStabilisationStrength) * tpf;
            yawSpeed -= yawSpeed * yawDamping * tpf;
        }


        if (pitchUp) {
            pitchSpeed = Math.min(
                    maxPitchSpeed,
                    pitchSpeed
                            + rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }

        if (pitchDown) {
            pitchSpeed = Math.max(
                    -maxPitchSpeed,
                    pitchSpeed
                            - rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }

        if (rollLeft) {
            rollSpeed = Math.min(
                    maxRollSpeed,
                    rollSpeed
                            + rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }

        if (rollRight) {
            rollSpeed = Math.max(
                    -maxRollSpeed,
                    rollSpeed
                            - rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }

        if (yawLeft) {
            yawSpeed = Math.min(
                    maxYawSpeed,
                    yawSpeed
                            + rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }

        if (yawRight) {
            yawSpeed = Math.max(
                    -maxYawSpeed,
                    yawSpeed
                            - rotationSpeed
                            * tpf
                            * controlFactor
                            * aoaControlFactor
            );
        }


        if (Math.abs(rollInput) > 0.05f) {

            rollSpeed += rollInput
                    * rotationSpeed
                    * tpf
                    * controlFactor
                    * aoaControlFactor;

            rollSpeed = Math.max(-maxRollSpeed, Math.min(rollSpeed, maxRollSpeed));
        }
        if (Math.abs(pitchInput) > 0.05f) {

            pitchSpeed += pitchInput
                    * rotationSpeed
                    * tpf
                    * controlFactor
                    * aoaControlFactor;

            pitchSpeed = Math.max(-maxPitchSpeed, Math.min(pitchSpeed, maxPitchSpeed));
        }
        if (Math.abs(yawInput) > 0.05f) {

            yawSpeed += yawInput
                    * rotationSpeed
                    * tpf
                    * controlFactor
                    * aoaControlFactor;

            yawSpeed = Math.max(-maxYawSpeed, Math.min(yawSpeed, maxYawSpeed));
        }

        plane.rotate(pitchSpeed * tpf, yawSpeed * tpf, rollSpeed * tpf);
    }

    private void updateGroundCollision() {

        float planeX = plane.getLocalTranslation().x;
        float planeZ = plane.getLocalTranslation().z;

        for (TerrainQuad tile : terrainTiles) {

            float groundHeight = tile.getHeight(
                    new Vector2f(planeX, planeZ)
            );
            if (!Float.isNaN(groundHeight)) {

                    if (plane.getLocalTranslation().y < groundHeight + 0.5f) {

                        Vector3f pos = plane.getLocalTranslation();

                        plane.setLocalTranslation(
                                pos.x,
                                groundHeight + 0.5f,
                                pos.z
                        );

                        if (velocity.y < 0) {
                            velocity.y = 0;
                        }
                    }

                    break;
                }
        }


    }

    private void loadTerrainTile(String fileName, int tileX, int tileZ) {

        Texture heightMapTexture =
                assetManager.loadTexture("TerrainData/" + fileName);

        ImageBasedHeightMap heightMap =
                new ImageBasedHeightMap(heightMapTexture.getImage());

        heightMap.load();

        TerrainQuad tile = new TerrainQuad(
                "Terrain_" + tileX + "_" + tileZ,
                65,
                513,
                heightMap.getHeightMap()
        );

        tile.setLocalScale(
                1.953125f,
                42f,
                1.953125f
        );

        float worldX = (tileX - 2621) * 1000f;
        float worldZ = -(tileZ - 1092) * 1000f;

        tile.setLocalTranslation(
                worldX,
                0,
                worldZ
        );

        Material terrainMat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );

        String textureFile =
                "TerrainData/texture_"
                        + tileX + "-" + tileZ
                        + ".png";

        try {
            Texture terrainTexture =
                    assetManager.loadTexture(textureFile);

            terrainMat.setTexture("ColorMap", terrainTexture);

        } catch (AssetNotFoundException e) {

            terrainMat.setColor("Color", ColorRGBA.Green);
        }

        tile.setMaterial(terrainMat);

        terrainTiles.add(tile);
        rootNode.attachChild(tile);
    }

    @Override
    public void simpleInitApp() {
        //Stats aus
        setDisplayFps(false);
        setDisplayStatView(false);

        //Joystick
        Joystick[] joysticks = inputManager.getJoysticks();


        inputManager.addJoystickConnectionListener(
                new JoystickConnectionListener() {
                    @Override
                    public void onConnected(Joystick joystick) {
                        System.out.println("VERBUNDEN: " + joystick.getName());
                        xboxController = joystick;

                        JoystickAxis rollAxis = null;
                        JoystickAxis pitchAxis = null;
                        JoystickAxis yawAxis = null;
                        JoystickAxis leftTriggerAxis = null;
                        JoystickAxis rightTriggerAxis = null;

                        for (JoystickAxis axis : joystick.getAxes()) {
                            if (axis.getLogicalId().equals("x")) {
                                rollAxis = axis;
                            }
                            if (axis.getLogicalId().equals("y")){
                                pitchAxis = axis;
                            }
                            if (axis.getLogicalId().equals("z")){
                                yawAxis = axis;
                            }

                            if (axis.getLogicalId().equals("rx")) {
                                leftTriggerAxis = axis;
                            }

                            if (axis.getLogicalId().equals("ry")) {
                                rightTriggerAxis = axis;
                            }
                        }


                        if (rollAxis != null && pitchAxis != null && yawAxis != null && leftTriggerAxis != null && rightTriggerAxis != null) {


                            inputManager.addMapping(
                                    "RollLeftController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            rollAxis.getAxisId(),
                                            true
                                    )
                            );

                            inputManager.addMapping(
                                    "RollRightController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            rollAxis.getAxisId(),
                                            false
                                    )
                            );

                            inputManager.addMapping(
                                    "YawLeftController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            yawAxis.getAxisId(),
                                            true
                                    )
                            );

                            inputManager.addMapping(
                                    "YawRightController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            yawAxis.getAxisId(),
                                            false
                                    )
                            );

                            inputManager.addMapping(
                                    "PitchUpController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            pitchAxis.getAxisId(),
                                            true
                                    )
                            );

                            inputManager.addMapping(
                                    "PitchDownController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            pitchAxis.getAxisId(),
                                            false
                                    )
                            );

                            inputManager.addMapping(
                                    "ThrottleUpController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            leftTriggerAxis.getAxisId(),
                                            true
                                    )
                            );

                            inputManager.addMapping(
                                    "ThrottleDownController",
                                    new JoyAxisTrigger(
                                            joystick.getJoyId(),
                                            rightTriggerAxis.getAxisId(),
                                            false
                                    )
                            );

                        }

                        inputManager.addListener(
                                controllerListener,
                                "RollLeftController",
                                "RollRightController",
                                "YawLeftController",
                                "YawRightController",
                                "PitchUpController",
                                "PitchDownController",
                                "ThrottleUpController",
                                "ThrottleDownController"

                        );





                    }

                    @Override
                    public void onDisconnected(Joystick joystick) {
                        System.out.println("GETRENNT: " + joystick.getName());

                        xboxController = null;
                    }
                }
        );


        inputManager.addListener(
                controllerListener,
                "RollLeftController",
                "RollRightController"
        );



        //Hud
        hudText = new BitmapText(guiFont);

        hudText.setLocalTranslation(10, cam.getHeight() - 10, 0);

        guiNode.attachChild(hudText);

        //Inputs Keyboard
        inputManager.addMapping("PitchUp", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("PitchDown", new KeyTrigger(KeyInput.KEY_S));

        inputManager.addMapping("RollLeft", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("RollRight", new KeyTrigger(KeyInput.KEY_D));

        inputManager.addMapping("YawLeft", new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("YawRight", new KeyTrigger(KeyInput.KEY_E));

        inputManager.addMapping("ThrottleUp", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("ThrottleDown", new KeyTrigger(KeyInput.KEY_F));


        inputManager.addListener(actionListener,
                "PitchUp",
                "PitchDown",
                "RollLeft",
                "RollRight",
                "YawLeft",
                "YawRight",
                "ThrottleUp",
                "ThrottleDown"
        );


        // Map
        for (int tileX = 2621; tileX <= 2624; tileX++) {

            for (int tileZ = 1092; tileZ <= 1098; tileZ++) {

                String fileName =
                        "terrain_" + tileX + "-" + tileZ + ".png";

                loadTerrainTile(fileName, tileX, tileZ);
            }
        }

        // unvollständiger Bereich 2625 bis 2626
        for (int tileX = 2625; tileX <= 2626; tileX++) {

            for (int tileZ = 1093; tileZ <= 1097; tileZ++) {

                String fileName =
                        "terrain_" + tileX + "-" + tileZ + ".png";

                loadTerrainTile(fileName, tileX, tileZ);
            }
        }

        //Himmel
        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1f, 1f));

        // Platzhalter-Flugzeug
        Box planeBox = new Box(1, 0.3f, 2);
        plane = new Geometry("Plane", planeBox);

        Material planeMat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );
        planeMat.setColor("Color", ColorRGBA.Blue);
        plane.setMaterial(planeMat);

        plane.setLocalTranslation(2500, 2000, 1000);
        rootNode.attachChild(plane);


        // Kamera
        cam.setLocation(new Vector3f(0, 5, 10));
        cam.lookAt(new Vector3f(0, 1, 0), Vector3f.UNIT_Y);
        cam.setFrustumFar(20000f);


    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {

        switch (name) {
            case "PitchUp":
                pitchUp = isPressed;
                break;

            case "PitchDown":
                pitchDown = isPressed;
                break;

            case "RollLeft":
                rollLeft = isPressed;
                break;

            case "RollRight":
                rollRight = isPressed;
                break;

            case "YawLeft":
                yawLeft = isPressed;
                break;

            case "YawRight":
                yawRight = isPressed;
                break;

            case "ThrottleUp":
                throttleUp = isPressed;
                break;

            case "ThrottleDown":
                throttleDown = isPressed;
                break;
        }
    };

    private final AnalogListener controllerListener = (name, value, tpf) -> {

        float stickValue = value / tpf;

        if (stickValue < 0.05f) {
            stickValue = 0f;
        }

        switch (name) {

            case "RollLeftController":
                rollInput = -stickValue;
                break;

            case "RollRightController":
                rollInput = stickValue;
                break;

            case "YawLeftController":
                yawInput = -stickValue;
                break;

            case "YawRightController":
                yawInput = stickValue;
                break;

            case "PitchUpController":
                pitchInput = -stickValue;
                break;

            case "PitchDownController":
                pitchInput = stickValue;

            case "ThrottleUpController":
                throttleUpInput = stickValue;
                break;

            case "ThrottleDownController":
                throttleDownInput = stickValue;
                break;
        }
    };

    @Override
    public void simpleUpdate(float tpf) {

        updateRotation(tpf);

        // Physik
        updateSpeed(tpf);
        auftrieb(tpf);
        updateDrag(tpf);
        sideSlip();
        updateSideSlip(tpf);

        //Position
        plane.move(velocity.mult(tpf));

        updateGroundCollision();
        updateCamera();



        //Hud text
        float speed = velocity.length();
        float height = plane.getLocalTranslation().y;
        float aoa = getAngleOfAttack();
        float beta = getSideSlipAngle();

        String speedText = String.format("%.0f", speed);
        String heightText = String.format("%.0f", height);
        String aoaText = String.format("%.1f", aoa);
        String betaText = String.format("%.1f", beta);

        hudText.setText(
                "Speed: " + speedText + " m/s\n"
                        + "Height: " + heightText + " m\n"
                        + "AoA: " + aoaText + "°\n"
                        + "Beta: " + betaText + "°\n"
                        + "Throttle: " + (throttle * 100f) + "%"
                + "Vertical Speed: " + String.format("%.1f", velocity.y) + " m/s\n"
        );

    }


}
