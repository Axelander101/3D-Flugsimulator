package alxb.flighttraining.game;

import com.jme3.app.SimpleApplication;
import com.jme3.input.controls.InputListener;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.app.state.AppState;

import java.awt.event.KeyEvent;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;


/**
 * The JMonkeyEngine game entry, you should only do initializations for your game here, game logic is handled by
 * Custom states {@link com.jme3.app.state.BaseAppState}, Custom controls {@link com.jme3.scene.control.AbstractControl}
 * and your custom entities implementations of the previous.
 *
 */
public class FlightTraining extends SimpleApplication{

    private boolean pitchUp;
    private boolean pitchDown;
    private boolean rollLeft;
    private boolean rollRight;
    private boolean yawLeft;
    private boolean yawRight;
    private float pitchSpeed;
    private float yawSpeed;
    private float rollSpeed;

    private Geometry plane;

    public FlightTraining() {

    }

    public FlightTraining(AppState... initialStates) {
        super(initialStates);
    }

    @Override
    public void simpleInitApp() {

        //Inputs
        inputManager.addMapping("PitchUp", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("PitchDown", new KeyTrigger(KeyInput.KEY_S));

        inputManager.addMapping("RollLeft", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("RollRight", new KeyTrigger(KeyInput.KEY_D));

        inputManager.addMapping("YawLeft", new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("YawRight", new KeyTrigger(KeyInput.KEY_E));


        inputManager.addListener(actionListener,
                "PitchUp",
                "PitchDown",
                "RollLeft",
                "RollRight",
                "YawLeft",
                "YawRight"
        );




        // Boden
        Box groundBox = new Box(2000, 0.1f, 2000);
        Geometry ground = new Geometry("Ground", groundBox);

        Material groundMat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );
        groundMat.setColor("Color", ColorRGBA.Gray);
        ground.setMaterial(groundMat);

        ground.setLocalTranslation(0, -1, 0);
        rootNode.attachChild(ground);


        // Platzhalter-Flugzeug
        Box planeBox = new Box(1, 0.3f, 2);
        plane = new Geometry("Plane", planeBox);

        Material planeMat = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );
        planeMat.setColor("Color", ColorRGBA.Blue);
        plane.setMaterial(planeMat);

        plane.setLocalTranslation(5, 10, -10);
        rootNode.attachChild(plane);


        // Kamera
        cam.setLocation(new Vector3f(0, 5, 10));
        cam.lookAt(new Vector3f(0, 1, 0), Vector3f.UNIT_Y);
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
        }
    };

    @Override
    public void simpleUpdate(float tpf) {

        Vector3f forward = plane.getLocalRotation().mult(Vector3f.UNIT_Z.negate());
        float speed = 5f;

        plane.move(forward.mult(speed * tpf));

        //Rotation
        float rotationSpeed = 1.5f;
        float maxRotationSpeed = 1.5f;
        float abbremsen = 1f;

        if (pitchSpeed > 0) pitchSpeed = Math.max(0, pitchSpeed - abbremsen * tpf);
        if (pitchSpeed < 0) pitchSpeed = Math.min(0, pitchSpeed + abbremsen * tpf);
        if (rollSpeed > 0) rollSpeed = Math.max(0, rollSpeed - abbremsen * tpf);
        if (rollSpeed < 0) rollSpeed = Math.min(0, rollSpeed + abbremsen * tpf);
        if (yawSpeed > 0) yawSpeed = Math.max(0, yawSpeed - abbremsen * tpf);
        if (yawSpeed < 0) yawSpeed = Math.min(0, yawSpeed + abbremsen * tpf);

        if (pitchUp) {
            pitchSpeed = Math.min(maxRotationSpeed, pitchSpeed + rotationSpeed * tpf);
        }

        if (pitchDown) {
            pitchSpeed = Math.max(-maxRotationSpeed, pitchSpeed - rotationSpeed * tpf);
        }

        if (rollLeft) {
            rollSpeed = Math.min(maxRotationSpeed, rollSpeed + rotationSpeed * tpf);
        }

        if (rollRight) {
            rollSpeed = Math.max(-maxRotationSpeed, rollSpeed - rotationSpeed * tpf);
        }

        if (yawLeft) {
            yawSpeed = Math.min(maxRotationSpeed, yawSpeed + rotationSpeed * tpf);
        }

        if (yawRight) {
            yawSpeed = Math.max(-maxRotationSpeed, yawSpeed - rotationSpeed * tpf);
        }


        plane.rotate(pitchSpeed * tpf, yawSpeed * tpf, rollSpeed * tpf);



        Vector3f planePosition = plane.getLocalTranslation();

        Vector3f up = plane.getLocalRotation().mult(Vector3f.UNIT_Y);

        Vector3f cameraPosition = planePosition.subtract(forward.mult(10)).add(up.mult(4));

        cam.setLocation(cameraPosition);

        cam.lookAt(planePosition.add(forward.mult(10)), up);
    }


}
