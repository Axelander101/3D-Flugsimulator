package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.flight.FlightInput;
import alxb.flighttraining.game.flight.AircraftSystems;
import com.jme3.asset.AssetManager;
import com.jme3.input.*;
import com.jme3.input.controls.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.*;
import com.jme3.renderer.*;
import com.jme3.scene.*;
import java.util.*;

/** Aircraft-space 3D cockpit; its own depth pass prevents the old filled interior from covering it. */
public final class CockpitView {
    public static final Vector3f EYE = new Vector3f(0f, .62f, -1.8f);
    private final Node plane, instruments = new Node("CockpitInstruments"), model;
    private static final float FOV = 65f, WORLD_NEAR = 1f, COCKPIT_NEAR = .01f, COCKPIT_FAR = 10f;
    private final Camera camera, cockpitCamera;
    private final Vector3f eyeInAircraft;
    private final DirectionalLight cockpitSun;
    private final Vector3f worldSun = new Vector3f(-.6f, -1, -.5f).normalizeLocal();
    private int projectionWidth = -1, projectionHeight = -1;
    private float projectionRange = -1;
    private final ViewPort viewport;
    private final boolean separatePass;
    private final Spatial pilotEye, stick, throttleLeft, throttleRight, pedalLeft, pedalRight;
    private final Spatial gearLever, hookLever, canopyPivot;
    private final Quaternion gearNeutral, hookNeutral, canopyNeutral;
    private final Quaternion stickNeutral, leftThrottleNeutral, rightThrottleNeutral;
    private final Vector3f leftPedalNeutral, rightPedalNeutral;
    private final Map<Spatial, Spatial.CullHint> oldWindows = new IdentityHashMap<>();
    private boolean left, right, up, down, mouseLook, enabled, instrumentZoom;
    private float projectionFov=-1;
    private final PilotFigure pilot;
    private float yaw, pitch, stickPitch, stickRoll;

    public CockpitView(AssetManager assets, InputManager input, Camera camera, Node plane,
                       RenderManager renderManager) {
        this.plane = plane; this.camera = camera;
        separatePass = renderManager != null;
        model = (Node) assets.loadModel("Models/FA18C_Cockpit/FA18C_Cockpit.glb");
        model.rotate(0, FastMath.PI, 0); model.setLocalScale(.5f);
        CockpitMaterials.prepare(assets, model);
        instruments.attachChild(model);
        instruments.addLight(new AmbientLight(new ColorRGBA(.58f, .62f, .66f, 1)));
        cockpitSun = new DirectionalLight(worldSun.clone(), new ColorRGBA(1.15f, 1.1f, 1.04f, 1));
        instruments.addLight(cockpitSun);
        pilotEye = required("PilotEye");
        eyeInAircraft = pilotEye.getWorldTranslation().clone();
        cockpitCamera = camera.clone();
        stick = required("Stick_Pivot");
        gearLever=required("Gear_Lever_Pivot");hookLever=required("Hook_Lever_Pivot");canopyPivot=required("Canopy_Pivot");
        gearNeutral=gearLever.getLocalRotation().clone();hookNeutral=hookLever.getLocalRotation().clone();
        canopyNeutral=canopyPivot.getLocalRotation().clone();
        throttleLeft = required("Throttle_Left_Pivot"); throttleRight = required("Throttle_Right_Pivot");
        pedalLeft = required("Rudder_Pedal_Left"); pedalRight = required("Rudder_Pedal_Right");
        stickNeutral = stick.getLocalRotation().clone();
        leftThrottleNeutral = throttleLeft.getLocalRotation().clone();
        rightThrottleNeutral = throttleRight.getLocalRotation().clone();
        leftPedalNeutral = pedalLeft.getLocalTranslation().clone(); rightPedalNeutral = pedalRight.getLocalTranslation().clone();
        plane.depthFirstTraversal(part -> {
            if (part instanceof Geometry && part.getName().toLowerCase(Locale.ROOT).contains("canopy glass"))
                oldWindows.put(part, part.getLocalCullHint());
        });
        if (separatePass) {
            viewport = renderManager.createPostView("Cockpit", cockpitCamera);
            viewport.setClearFlags(false, true, false); viewport.attachScene(instruments);
        } else { viewport = null; plane.attachChild(instruments); }
        pilot=new PilotFigure(assets,instruments,stick,throttleLeft,pedalLeft,pedalRight);
        setEnabled(false);
        input.addMapping("LookLeft", new KeyTrigger(KeyInput.KEY_LEFT));
        input.addMapping("LookRight", new KeyTrigger(KeyInput.KEY_RIGHT));
        input.addMapping("LookUp", new KeyTrigger(KeyInput.KEY_UP));
        input.addMapping("LookDown", new KeyTrigger(KeyInput.KEY_DOWN));
        input.addMapping("InstrumentZoom",new KeyTrigger(KeyInput.KEY_Z));
        input.addMapping("LookCenter", new KeyTrigger(KeyInput.KEY_HOME));
        input.addMapping("CockpitMouseLook", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        ActionListener actions = (name, pressed, tpf) -> {
            switch (name) {
                case "LookLeft" -> left = enabled && pressed;
                case "LookRight" -> right = enabled && pressed;
                case "LookUp" -> up = enabled && pressed;
                case "LookDown" -> down = enabled && pressed;
                case "InstrumentZoom" -> instrumentZoom=enabled && pressed;
                case "LookCenter" -> { if (enabled && pressed) reset(); }
                case "CockpitMouseLook" -> mouseLook = enabled && pressed;
            }
        };
        input.addListener(actions, "LookLeft", "LookRight", "LookUp", "LookDown", "LookCenter", "CockpitMouseLook", "InstrumentZoom");
        input.addMapping("HeadLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping("HeadRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping("HeadUp", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addMapping("HeadDown", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        AnalogListener motion = (name, value, tpf) -> {
            if (!enabled || !mouseLook) return;
            switch (name) {
                case "HeadLeft" -> yaw += value * 1.6f;
                case "HeadRight" -> yaw -= value * 1.6f;
                case "HeadUp" -> pitch += value * 1.6f;
                case "HeadDown" -> pitch -= value * 1.6f;
            }
            clampLook();
        };
        input.addListener(motion, "HeadLeft", "HeadRight", "HeadUp", "HeadDown");
    }
    private Spatial required(String name) {
        Spatial spatial = model.getChild(name);
        if (spatial == null) throw new IllegalStateException("Cockpit node missing: " + name);
        return spatial;
    }
    Geometry surface(String name) {
        Spatial spatial = required(name);
        if (spatial instanceof Geometry geometry) return geometry;
        List<Geometry> found = new ArrayList<>();
        spatial.depthFirstTraversal(child -> { if (child instanceof Geometry geometry) found.add(geometry); });
        if (found.size() != 1) throw new IllegalStateException("Expected one mesh on cockpit screen: " + name);
        return found.get(0);
    }
    public Node instruments() { return instruments; }
    public void setEnabled(boolean value) {
        enabled = value;
        instruments.setCullHint(value ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (viewport != null) viewport.setEnabled(value);
        oldWindows.forEach((window, previous) -> window.setCullHint(value ? Spatial.CullHint.Always : previous));
        if (!value) left = right = up = down = mouseLook = instrumentZoom = false;
    }
    public void reset() { yaw = pitch = 0;instrumentZoom=false; }
    public void update(float dt) {
        if (enabled && Float.isFinite(dt) && dt > 0) {
            yaw += ((left ? 1 : 0) - (right ? 1 : 0)) * 1.35f * dt;
            pitch += ((up ? 1 : 0) - (down ? 1 : 0)) * 1.05f * dt;
        }
        clampLook();
        // World rendering needs metre-scale depth precision; close cockpit geometry has its own camera.
        float range = alxb.flighttraining.game.world.WorldDetailSettings.renderDistance();
        float fov=instrumentZoom?34f:FOV;
        if (projectionFov!=fov || projectionWidth != camera.getWidth() || projectionHeight != camera.getHeight()
                || projectionRange != range) {
            projectionFov=fov;
            projectionWidth = camera.getWidth(); projectionHeight = camera.getHeight(); projectionRange = range;
            float aspect = (float) projectionWidth / Math.max(1, projectionHeight);
            camera.setFrustumPerspective(fov, aspect, WORLD_NEAR, range);
            cockpitCamera.resize(projectionWidth, projectionHeight, false);
            cockpitCamera.setFrustumPerspective(fov, aspect, COCKPIT_NEAR, COCKPIT_FAR);
        }
        Quaternion head = new Quaternion().fromAngles(pitch + (instrumentZoom?-.32f:-.08f), yaw, 0);
        camera.setLocation(plane.localToWorld(eyeInAircraft, new Vector3f()));
        Quaternion worldHead = plane.getWorldRotation().mult(head);
        camera.lookAtDirection(worldHead.mult(Vector3f.UNIT_Z.negate()), worldHead.mult(Vector3f.UNIT_Y));
        if (separatePass) {
            // Keep cockpit vertices/camera near zero, including at high altitude or during a rebase.
            // Translating both by kilometres loses the sub-millimetre separation of labels/screens.
            cockpitCamera.setLocation(eyeInAircraft);
            cockpitCamera.lookAtDirection(head.mult(Vector3f.UNIT_Z.negate()), head.mult(Vector3f.UNIT_Y));
            cockpitSun.setDirection(plane.getWorldRotation().inverse().mult(worldSun));
        }
    }
    public void updateControls(FlightInput input, float throttle, float dt) {
        float p = command(input.pitchUp, input.pitchDown, input.pitchInput);
        float r = command(input.rollLeft, input.rollRight, input.rollInput);
        float y = command(input.yawLeft, input.yawRight, input.yawInput);
        float blend = 1 - (float) Math.exp(-18 * Math.max(0, dt));
        stickPitch += (p - stickPitch) * blend; stickRoll += (r - stickRoll) * blend;
        stick.setLocalRotation(stickNeutral.mult(new Quaternion().fromAngles(-stickPitch * .20f, 0, -stickRoll * .20f)));
        float lever = (1 - Math.max(0, Math.min(1, throttle))) * -.42f;
        Quaternion angle = new Quaternion().fromAngleAxis(lever, Vector3f.UNIT_X);
        throttleLeft.setLocalRotation(leftThrottleNeutral.mult(angle)); throttleRight.setLocalRotation(rightThrottleNeutral.mult(angle));
        pedalLeft.setLocalTranslation(leftPedalNeutral.add(0, 0, y * .025f));
        pedalRight.setLocalTranslation(rightPedalNeutral.add(0, 0, -y * .025f));
        pilot.update();
    }
    public void updateSystems(AircraftSystems state){
        gearLever.setLocalRotation(gearNeutral.mult(new Quaternion().fromAngleAxis(state.isGearDownSelected()?0:-.7f,Vector3f.UNIT_X)));
        hookLever.setLocalRotation(hookNeutral.mult(new Quaternion().fromAngleAxis(state.getHook()*.65f,Vector3f.UNIT_X)));
        canopyPivot.setLocalRotation(canopyNeutral.mult(new Quaternion().fromAngleAxis(-state.getCanopy()*.61f,Vector3f.UNIT_X)));
    }
    private static float command(boolean positive, boolean negative, float analog) {
        float key = (positive ? 1 : 0) - (negative ? 1 : 0);
        return key != 0 ? key : Math.max(-1, Math.min(1, analog));
    }
    private void clampLook() {
        yaw = Math.max(-1.92f, Math.min(1.92f, yaw)); pitch = Math.max(-1.15f, Math.min(.85f, pitch));
    }
    public void prepareRender() {
        if (separatePass) { instruments.updateLogicalState(0); instruments.updateGeometricState(); }
    }
}
