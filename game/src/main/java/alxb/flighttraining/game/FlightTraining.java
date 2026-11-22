package alxb.flighttraining.game;

import alxb.flighttraining.game.weapons.Cannon;
import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.ui.FlightHud;
import alxb.flighttraining.game.ui.CockpitView;
import alxb.flighttraining.game.flight.FlightInput;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.flight.FlightSurfaces;
import alxb.flighttraining.game.weapons.Guns;
import alxb.flighttraining.game.weapons.InfraredMissile;
import alxb.flighttraining.game.ui.Menu;
import alxb.flighttraining.game.sensors.Radar;
import alxb.flighttraining.game.ui.EnemyMarkerDisplay;
import alxb.flighttraining.game.config.SpawnSettings;
import alxb.flighttraining.game.world.TerrainStreaming;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.anim.SkinningControl;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.app.state.AppState;
import com.jme3.app.state.AbstractAppState;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;

import java.util.ArrayList;
import java.util.List;

public class FlightTraining extends SimpleApplication {
    private alxb.flighttraining.game.audio.GameAudio gameAudio;
    public alxb.flighttraining.game.audio.GameAudio getGameAudio(){return gameAudio;}
    private boolean spawnReady;
    private Node plane;
    private FlightInput input;
    private FlightPhysics physics;
    private FlightHud hud;
    private alxb.flighttraining.game.ui.LoadingScreen loadingScreen;
    private TerrainStreaming terrain;
    private Guns guns;
    private float shotCooldown;
    private float cannonFlashTimer = 1f;
    private Node targets;
    private AnimComposer composer;
    private Spatial planeModel;
    private FlightSurfaces surfaces;
    private CockpitView cockpit;
    private static final int MISSILE_CAPACITY = 4;
    private final List<Spatial> mountedMissiles = new ArrayList<>();
    private float missileCooldown;
    private Radar radar;
    private EnemyMarkerDisplay enemyMarkers;


    private Enemy enemies;
    private int enemyCount = 0;
    private DogfightSettings dogfightSettings;

    private boolean missileLaunchWasPressed;

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
        // C and M are aircraft controls; disable the engine's overlapping debug shortcuts.
        if(inputManager.hasMapping(INPUT_MAPPING_CAMERA_POS))inputManager.deleteMapping(INPUT_MAPPING_CAMERA_POS);
        if(inputManager.hasMapping(INPUT_MAPPING_MEMORY))inputManager.deleteMapping(INPUT_MAPPING_MEMORY);

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
        gameAudio=new alxb.flighttraining.game.audio.GameAudio(assetManager,rootNode,listener,audioRenderer,inputManager);
        loadingScreen = new alxb.flighttraining.game.ui.LoadingScreen(assetManager, guiNode, guiFont);


        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1f, 1f));
        initializePlane();
        cockpit = new CockpitView(assetManager, inputManager, cam, plane, renderManager);
        stateManager.attach(new AbstractAppState() {
            @Override
            public void render(RenderManager manager) {
                // The cockpit is a separate viewport scene, outside rootNode/guiNode.
                // Refresh it every frame, including the menu and the first startup frame,
                // after all logical updates and before RenderManager checks culling.
                cockpit.prepareRender();
            }
        });
        cockpit.setEnabled(false);
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
        enemies.setSoundEvents(p->gameAudio.at("cannon",p),p->gameAudio.at("impact",p));
        guns.setHitListener(p->gameAudio.at("impact",p));
        radar = new Radar(5000f, plane, enemies);
        hud = new FlightHud(assetManager, cockpit, plane, enemies);
        enemyMarkers = new EnemyMarkerDisplay(assetManager, guiNode, cam, enemies);


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
        if(gameAudio!=null)gameAudio.close();
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
        gameAudio.setActive(!menu.getMenuOffen() && spawnReady);
        input.setFlightControlsEnabled(!menu.getMenuOffen() && spawnReady);
        loadingScreen.update(tpf,cam.getWidth(),cam.getHeight());
        cockpit.setEnabled(!menu.getMenuOffen() && spawnReady);
        hud.setVisible(!menu.getMenuOffen() && spawnReady);
        if (menu.getMenuOffen() || !spawnReady || dogfightSettings == null) enemyMarkers.hide();
        if (!menu.getMenuOffen()) {

            if (!spawnReady) {
                updateSpawn();
                input.resetControllerInput();
                return;
            }

            physics.update(tpf);
            Vector3f delta=terrain.recenter(plane.getLocalTranslation(),cam);
            if(delta.lengthSquared()>0){
                guns.rebase(delta);enemies.rebase(delta);gameAudio.rebase(delta);
                for (InfraredMissile missile : missiles) missile.rebase(delta);
            }
            // Streaming/LOD must inspect this frame's camera, after physics and any origin shift.
            cockpit.update(tpf);
            Vector3f position = plane.getLocalTranslation();
            terrain.update(position, cam);
            physics.resolveGroundCollision(terrain.getGroundHeight(position));
            plane.updateGeometricState();
            enemies.update(tpf);

            plane.updateGeometricState();
            targets.updateGeometricState();

            radar.update();
            cockpit.updateControls(input, physics.getThrottle(), tpf);
            cockpit.updateSystems(physics.getSystems());
            // Ground correction may have moved the plane; do not integrate head input twice.
            cockpit.update(0f);
            enemyMarkers.update(dogfightSettings != null);


            updateGuns(tpf);
            missiles.removeIf(missile -> {
                if (missile.update(tpf)) return true;
                Vector3f p = missile.getModel().getLocalTranslation();
                float ground = terrain.getGroundHeight(p);
                if (Float.isFinite(ground) && p.y <= ground) {
                    gameAudio.at("explosion",p);
                    missile.cancel();
                    return true;
                }
                return false;
            });

            gameAudio.update(tpf,physics,enemies,plane,cam,getMapPosition(),terrain.getGroundHeight(plane.getLocalTranslation()),mountedMissiles.size(),weaponsEnabled());
            hud.setWeapons(mountedMissiles.size(), weaponsEnabled());
            hud.update(physics, plane.getLocalTranslation().y);
        }
    }

    private void launchInfraredMissile() {
        if (!weaponsEnabled() || mountedMissiles.isEmpty() || missileCooldown > 0f) return;
        Vector3f direction = plane.getWorldRotation()
                .mult(Vector3f.UNIT_Z.negate())
                .normalizeLocal();
        Spatial target = null;
        float bestAlignment = 0.7f;
        for (int i = 0; i < enemies.getCount(); i++) {
            Spatial candidate = enemies.getPlane(i);
            if (candidate.getParent() == null) continue;
            Vector3f offset = candidate.getWorldTranslation().subtract(plane.getWorldTranslation());
            if (offset.lengthSquared() < 1f || offset.lengthSquared() > 5000f * 5000f) continue;
            float alignment = direction.dot(offset.normalizeLocal());
            if (alignment > bestAlignment) {
                target = candidate;
                bestAlignment = alignment;
            }
        }
        // Release the actual wing-mounted model, preserving its world position and scale.
        Spatial model = mountedMissiles.remove(0);
        plane.updateGeometricState();
        Vector3f position = model.getWorldTranslation().clone();
        Vector3f scale = model.getWorldScale().clone();
        model.removeFromParent();
        rootNode.attachChild(model);
        model.setLocalScale(scale);
        InfraredMissile missile = new InfraredMissile(
                model, target, position, direction, physics.getVelocity(),
                plane.getWorldRotation().mult(Vector3f.UNIT_Y)
        );
        missiles.add(missile);
        if(gameAudio!=null)gameAudio.at("launch",position);
        missileCooldown = 0.75f;
    }

    private boolean weaponsEnabled() {
        return dogfightSettings == null || dogfightSettings.weaponsEnabled;
    }

    private void resetMissileStores() {
        for (Spatial model : mountedMissiles) model.removeFromParent();
        mountedMissiles.clear();
        if (weaponsEnabled()) {
            for (int i = 0; i < MISSILE_CAPACITY; i++) {
                Spatial model = assetManager.loadModel("Models/InfraredMissile/FT_IR_Missile_LOD0.glb");
                model.setLocalScale(0.5f);
                float side = i % 2 == 0 ? -1f : 1f;
                // Centres of the existing inboard pylons, in aircraft space at model scale 0.5.
                // Pylon underside y=-0.1575; missile body radius=0.04 -> centre y=-0.20.
                model.setLocalTranslation(side * (i < 2 ? 1.175f : 1.8f), -0.20f, i < 2 ? 1.0f : 1.175f);
                model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(
                        (float) Math.PI, Vector3f.UNIT_Y));
                plane.attachChild(model);
                mountedMissiles.add(model);
            }
        }
        missileCooldown = 0f;
        missileLaunchWasPressed = false;
        hud.setWeapons(mountedMissiles.size(), weaponsEnabled());
    }

    private void updateGuns(float tpf) {
        // Auch nach dem Loslassen fliegen bestehende Geschosse weiter.
        guns.update(tpf);
        cannonFlashTimer += tpf;
        missileCooldown = Math.max(0f, missileCooldown - tpf);
        //Missile
        if (input.infraredMissile && !missileLaunchWasPressed) {
            launchInfraredMissile();
        }

        missileLaunchWasPressed = input.infraredMissile;

        //BordCwannon
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
            gameAudio.cannonFire();
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
        enemyCount = 0;
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
        if(gameAudio!=null)gameAudio.reset();
        spawnSettings = settings;
        spawnReady = false;
        cockpit.reset();
        enemies.clear();
        enemyMarkers.hide();
        for (InfraredMissile missile : missiles) missile.cancel();
        missiles.clear();
        resetMissileStores();
        guns.clear();
        shotCooldown = 0f;
        cannonFlashTimer = 1f;
        plane.setLocalRotation(new com.jme3.math.Quaternion());
        terrain.close();
        terrain = new TerrainStreaming(assetManager,rootNode,settings.worldX,settings.worldZ);
        enemies.configure(plane,physics,terrain);
        plane.setLocalTranslation(0f, settings.heightAboveGround, 0f);
        physics.reset(new Vector3f(), 0f);
        physics.setEngineRunning(true);
        physics.getSystems().reset(settings.heightAboveGround > 3f);
        input.shoot = input.pitchUp = input.pitchDown = input.rollLeft = input.rollRight = false;
        input.infraredMissile = false;
        input.yawLeft = input.yawRight = input.throttleUp = input.throttleDown = false;
        input.resetControllerInput();
        menu.hide();
        inputManager.setCursorVisible(false);
        hud.showLoading("");
        loadingScreen.begin(cam.getWidth(),cam.getHeight());
        updateCamera();
    }

    private void updateSpawn() {
        Vector3f position = plane.getLocalTranslation();
        updateCamera();
        terrain.update(position, cam);

        float groundHeight = terrain.getGroundHeight(position);
        if (!Float.isFinite(groundHeight)) {
            loadingScreen.progress(terrain.getGroundLoadStatus(position),0);
            return;
        }

        plane.setLocalTranslation(
                position.x, groundHeight + Math.max(spawnSettings.heightAboveGround, 1.155f), position.z
        );
        plane.updateGeometricState();
        int ready=terrain.spawnObjectProgress(plane.getLocalTranslation());
        int total=terrain.spawnObjectTotal(plane.getLocalTranslation());
        if(!terrain.isSpawnAreaReady(plane.getLocalTranslation())){
            loadingScreen.progress("Umgebung wird vorbereitet: "+ready+" / "+total+" Kacheln",.1f+.9f*ready/Math.max(1,total));return;
        }
        spawnOpponents();
        targets.updateGeometricState();
        spawnReady = true;
        loadingScreen.finish();
        updateCamera();
        hud.update(physics, plane.getLocalTranslation().y);
    }

    /** Opponents belong exclusively to a dogfight, independent of any old count setting. */
    private void spawnOpponents() {
        enemies.clear();
        if (dogfightSettings == null) return;
        Vector3f forward = plane.getLocalRotation().mult(new Vector3f(0, 0, -1));
        physics.reset(forward.mult(220f), .6f);
        physics.getSystems().reset(true);
        enemies.spawn(dogfightSettings.enemyCount, plane, dogfightSettings);
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
        flyCam.setEnabled(false);
        updateCamera();
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
        cockpit.update(0f);
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
