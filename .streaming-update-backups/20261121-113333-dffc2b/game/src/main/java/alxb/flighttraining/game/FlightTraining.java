package alxb.flighttraining.game;

import alxb.flighttraining.game.weapons.Cannon;
import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.ui.FlightHud;
import alxb.flighttraining.game.flight.FlightInput;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.flight.FlightSurfaces;
import alxb.flighttraining.game.weapons.Guns;
import alxb.flighttraining.game.weapons.InfraredMissile;
import alxb.flighttraining.game.ui.Menu;
import alxb.flighttraining.game.sensors.Radar;
import alxb.flighttraining.game.ui.RadarDisplay;
import alxb.flighttraining.game.config.SpawnSettings;
import alxb.flighttraining.game.world.TerrainStreaming;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.anim.SkinningControl;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.app.state.AppState;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class FlightTraining extends SimpleApplication {
    private boolean spawnReady;
    private Node plane;
    private FlightInput input;
    private FlightPhysics physics;
    private FlightHud hud;
    private TerrainStreaming terrain;
    private Guns guns;
    private float shotCooldown;
    private float cannonFlashTimer = 1f;
    private Node targets;
    private AnimComposer composer;
    private Spatial planeModel;
    private FlightSurfaces surfaces;
    private InfraredMissile infraredMissile;
    private Radar radar;
    private RadarDisplay radarDisplay;


    private Enemy enemies;
    private int enemyCount = 1;
    private boolean enemyCamera = false;
    private DogfightSettings dogfightSettings;

    private final List<InfraredMissile> missiles = new ArrayList<>();


    private Menu menu;
    private SpawnSettings spawnSettings = new SpawnSettings(-6548f, -9563f, 0f, enemyCount);

    public FlightTraining() {
    }

    public FlightTraining(AppState... initialStates) {
        super(initialStates);
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        flyCam.setEnabled(false);

        menu = new Menu(
                assetManager,
                cam.getWidth(),
                cam.getHeight(),
                this,
                inputManager
        );

        menu.show(guiNode);
        inputManager.setCursorVisible(true);

        System.out.println("Menü erstellt und angehängt");



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
            throw new IllegalStateException("Das Flugzeugmodell enthält keinen AnimComposer.");
        }
        targets = new Node("Targets");
        rootNode.attachChild(targets);
        guns = new Cannon(targets);
        enemies = new Enemy(assetManager, targets);
        enemies.configure(plane, physics, terrain);
        radar = new Radar(5000f, plane, enemies);
        radarDisplay = new RadarDisplay(assetManager, guiNode, plane, enemies);
        radarDisplay.setVisible(false);


        surfaces = new FlightSurfaces(plane, input, composer, physics, assetManager, plane);
        // Procedural surfaces must run after clips have set the pose.
        SkinningControl skin = findSkinningControl(planeModel);
        if (skin == null) {
            throw new IllegalStateException("Das Flugzeugmodell enthält kein SkinningControl.");
        }
        composer.getSpatial().addControl(new AbstractControl() {
            @Override
            protected void controlUpdate(float tpf) {
                if (spawnReady && !menu.getMenuOffen()) {
                    surfaces.update(tpf);
                    skin.getArmature().update();
                }
                input.resetControllerInput();
            }

            @Override
            protected void controlRender(RenderManager rm, ViewPort vp) {
            }
        });
    }

    @Override
    public void simpleRender(RenderManager manager) {
        if (terrain != null) terrain.prewarm(manager);
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
        float renderDistance=alxb.flighttraining.game.world.WorldDetailSettings.renderDistance();
        if(cam.getFrustumFar()!=renderDistance)cam.setFrustumFar(renderDistance);
        menu.update(tpf);
        radarDisplay.setVisible(
                !menu.getMenuOffen() && spawnReady
        );
        if (!menu.getMenuOffen()) {

            if (!spawnReady) {
                updateSpawn();
                input.resetControllerInput();
                return;
            }

            physics.update(tpf);
            Vector3f delta=terrain.recenter(plane.getLocalTranslation(),cam);
            if(delta.lengthSquared()>0){
                guns.rebase(delta);enemies.rebase(delta);
                for(InfraredMissile missile:missiles)missile.rebase(delta);
            }
            Vector3f position = plane.getLocalTranslation();
            terrain.update(position, cam);
            physics.resolveGroundCollision(terrain.getGroundHeight(position));
            plane.updateGeometricState();
            enemies.update(tpf);

            plane.updateGeometricState();
            targets.updateGeometricState();

            radar.update();
            radarDisplay.update(cam.getWidth(), cam.getHeight());
            updateCamera();


            updateGuns(tpf);
            missiles.removeIf(missile -> missile.update(tpf));

            hud.update(physics, plane.getLocalTranslation().y);
        }
    }

    private void updateGuns(float tpf) {
        // Auch nach dem Loslassen fliegen bestehende Geschosse weiter.
        guns.update(tpf);
        cannonFlashTimer += tpf;
        if (!input.shoot || (dogfightSettings != null && !dogfightSettings.weaponsEnabled)) {
            shotCooldown = 0f;
            return;
        }

        shotCooldown -= tpf;
        Vector3f direction = plane.getWorldRotation()
                .mult(Vector3f.UNIT_Z.negate()).normalizeLocal();
        // Vorläufiger Mündungspunkt vor der Nase; später durch den echten Mündungsknoten ersetzen.
        Vector3f muzzle = plane.getWorldTranslation().add(direction.mult(8f));
        int shots = 0;
        while (shotCooldown <= 0f && shots < 16) {
            guns.fire(muzzle, direction, physics.getVelocity());
            shotCooldown += 1f / 100f;
            shots++;
        }
        // Nach einem langen Hänger keine aufgestauten Salven nachholen.
        if (shotCooldown < 0f) shotCooldown = 0f;

        if (cannonFlashTimer >= 0.1f && composer != null
                && composer.hasAnimClip("07_Cannon_Burst")) {
            composer.setCurrentAction("07_Cannon_Burst", AnimComposer.DEFAULT_LAYER, false);
            cannonFlashTimer = 0f;
        }
    }

    public void startFreePlay(SpawnSettings settings) {
        dogfightSettings = null;
        beginFlight(settings);
    }

    public void startDogfight(DogfightSettings settings) {
        java.util.Objects.requireNonNull(settings);
        dogfightSettings = settings;
        enemyCount = settings.enemyCount;
        beginFlight(settings.spawn());
    }

    private void beginFlight(SpawnSettings settings) {
        if (settings == null || !Float.isFinite(settings.worldX) || !Float.isFinite(settings.worldZ)
                || !Float.isFinite(settings.heightAboveGround) || settings.heightAboveGround < 0)
            throw new IllegalArgumentException("Ungültige Startposition");
        spawnSettings = settings;
        spawnReady = false;
        enemyCamera = false;
        enemies.clear();
        for (InfraredMissile missile : missiles) missile.cancel();
        missiles.clear();
        guns.clear();
        shotCooldown = 0f;
        cannonFlashTimer = 1f;
        plane.setLocalRotation(new com.jme3.math.Quaternion());
        terrain.close();
        terrain = new TerrainStreaming(assetManager,rootNode,settings.worldX,settings.worldZ);
        enemies.configure(plane,physics,terrain);
        plane.setLocalTranslation(0f, settings.heightAboveGround, 0f);
        physics.reset(new Vector3f(), 0f);
        input.shoot = input.pitchUp = input.pitchDown = input.rollLeft = input.rollRight = false;
        input.yawLeft = input.yawRight = input.throttleUp = input.throttleDown = false;
        input.resetControllerInput();
        menu.hide();
        inputManager.setCursorVisible(false);
        hud.showLoading("Starthöhe und Terrain werden geladen …");
        updateCamera();
    }

    private void updateSpawn() {
        Vector3f position = plane.getLocalTranslation();
        updateCamera();
        terrain.update(position, cam);

        float groundHeight = terrain.getGroundHeight(position);
        if (!Float.isFinite(groundHeight)) {
            hud.showLoading(terrain.getGroundLoadStatus(position));
            return;
        }

        plane.setLocalTranslation(
                position.x, groundHeight + spawnSettings.heightAboveGround, position.z
        );
        plane.updateGeometricState();
        int ready=terrain.spawnObjectProgress(plane.getLocalTranslation());
        int total=terrain.spawnObjectTotal(plane.getLocalTranslation());
        if(!terrain.isSpawnAreaReady(plane.getLocalTranslation())){
            hud.showLoading("Startumgebung wird vorgeladen: "+ready+" / "+total+" Kacheln");return;
        }
        if (dogfightSettings != null) {
            Vector3f forward = plane.getLocalRotation().mult(new Vector3f(0, 0, -1));
            physics.reset(forward.mult(220f), 0.6f);
            enemies.spawn(enemyCount, plane, dogfightSettings);
        } else {
            enemies.spawn(enemyCount, plane);
        }
        targets.updateGeometricState();
        spawnReady = true;
        updateCamera();
        hud.update(physics, plane.getLocalTranslation().y);
    }

    /** Kartenkoordinaten im bisherigen globalen Projektbezug, trotz Floating Origin. */
    public Vector3f getMapPosition() {
        return terrain.coordinates().absolute(plane.getLocalTranslation());
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

        planeModel.setLocalScale(0.5f);

        plane.attachChild(planeModel);

        plane.setLocalTranslation(spawnSettings.worldX, 0f, spawnSettings.worldZ);

        rootNode.attachChild(plane);
    }

    private void configureCamera() {
        cam.setLocation(new Vector3f(0, 5, 10));
        cam.lookAt(new Vector3f(0, 1, 0), Vector3f.UNIT_Y);
        cam.setFrustumPerspective(55f, (float)cam.getWidth()/cam.getHeight(), 0.7f, alxb.flighttraining.game.world.WorldDetailSettings.renderDistance());
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
        daylight.getArea().setRadius(2_000_000f);
        rootNode.addLight(daylight);
    }

    private void updateCamera() {
        Node cameraTarget = plane;
        if (enemyCamera && spawnReady && enemies != null && enemies.getCount() > 0)
            cameraTarget = enemies.getPlane(0);
        cameraTarget.updateGeometricState();
        Vector3f position = cameraTarget.getWorldTranslation();
        Vector3f forward = cameraTarget.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        Vector3f up = cameraTarget.getWorldRotation().mult(Vector3f.UNIT_Y);
        // Ursprüngliche direkte Flugkamera, etwas näher: zuvor 10 m / 3 m.
        cam.setLocation(position.subtract(forward.mult(8f)).add(up.mult(2.5f)));
        cam.lookAt(position.add(forward.mult(10f)), up);
    }

    private SkinningControl findSkinningControl(Spatial spatial) {
        SkinningControl skin = spatial.getControl(SkinningControl.class);
        if (skin != null) {
            return skin;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                skin = findSkinningControl(child);
                if (skin != null) {
                    return skin;
                }
            }
        }
        return null;
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
