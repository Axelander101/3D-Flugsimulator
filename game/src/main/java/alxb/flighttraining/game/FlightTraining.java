package alxb.flighttraining.game;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.asset.AssetManager;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;

public class FlightTraining extends SimpleApplication {
    private Node plane;
    private FlightInput input;
    private FlightPhysics physics;
    private FlightHud hud;
    private TerrainStreaming terrain;
    private Guns guns;
    private AnimComposer composer;
    private Spatial planeModel;
    private FlightSurfaces flightSurfaces;

    public FlightTraining() {
    }

    public FlightTraining(AppState... initialStates) {
        super(initialStates);
    }

    @Override
    public void simpleInitApp() {

        setDisplayFps(false);
        setDisplayStatView(false);

        input = new FlightInput(inputManager);
        hud = new FlightHud(guiFont, guiNode, cam.getHeight());

        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1f, 1f));
        initializePlane();
        configureCamera();
        initializeLighting();

        physics = new FlightPhysics(plane, input);
        terrain = new TerrainStreaming(assetManager, rootNode);

        composer = findAnimComposer(planeModel);

        if (composer == null) {
            System.out.println("Kein AnimComposer gefunden!");
        } else {
            System.out.println(composer.getAnimClipsNames());
        }
        guns = new Guns(plane, input, rootNode, composer, assetManager);

        flightSurfaces = new FlightSurfaces(plane, input);
        composer.setEnabled(false);
    }

    @Override
    public void destroy() {
        if (terrain != null) {
            terrain.close();
        }
        super.destroy();
    }

    @Override
    public void simpleUpdate(float tpf) {
        physics.update(tpf);
        flightSurfaces.update(tpf);
        updateCamera();

        guns.shoot(tpf);

        Vector3f position = plane.getLocalTranslation();
        terrain.update(position);
        physics.resolveGroundCollision(terrain.getGroundHeight(position));
        hud.update(physics, plane.getLocalTranslation().y);
        input.resetControllerInput();
    }

    public float getAngleOfAttack() {
        return physics == null ? 0f : physics.getAngleOfAttack();
    }

    public float sideSlip() {
        return physics.sideSlip();
    }

    public float getSideSlipAngle() {
        return physics.getSideSlipAngle();
    }

    private void initializePlane() {
        plane = new Node("Plane");

        planeModel =
                assetManager.loadModel(
                        "Models/FA18C_Swiss_Hornet_Cannon.glb"
                );

        planeModel.rotate(
                0f,
                (float) Math.PI,
                0f
        );
        plane.attachChild(planeModel);

        plane.setLocalTranslation(2500, 2000, -1000);

        rootNode.attachChild(plane);
    }

    private void configureCamera() {
        cam.setLocation(new Vector3f(0, 5, 10));
        cam.lookAt(new Vector3f(0, 1, 0), Vector3f.UNIT_Y);
        cam.setFrustumFar(20000f);
    }

    private void initializeLighting() {
        // Sonnenlicht von schräg oben
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.6f, -1f, -0.5f).normalizeLocal());
        sun.setColor(new ColorRGBA(1.30f, 1.24f, 1.14f, 1f));
        rootNode.addLight(sun);

        Spatial lightingSky = SkyFactory.createSky(
                assetManager,
                "Lighting/daylight.hdr",
                SkyFactory.EnvMapType.EquirectMap
        );
        lightingSky.updateGeometricState();

        LightProbe daylight = FastLightProbeFactory.makeProbe(
                renderManager, assetManager, 128,
                Vector3f.ZERO, 0.1f, 100f, lightingSky
        );
        daylight.getArea().setRadius(20_000f);
        rootNode.addLight(daylight);
    }

    private void updateCamera() {
        Vector3f forward = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Z.negate());

        Vector3f up = plane.getLocalRotation()
                .mult(Vector3f.UNIT_Y);

        Vector3f planePosition = plane.getLocalTranslation();

        Vector3f cameraPosition = planePosition
                .subtract(forward.mult(18))
                .add(up.mult(6));

        cam.setLocation(cameraPosition);

        cam.lookAt(
                planePosition.add(forward.mult(10)),
                up
        );
    }

    private AnimComposer findAnimComposer(Spatial spatial) {

        AnimComposer composer =
                spatial.getControl(AnimComposer.class);

        if (composer != null) {
            return composer;
        }

        if (spatial instanceof Node node) {

            for (Spatial child : node.getChildren()) {

                AnimComposer found = findAnimComposer(child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}
