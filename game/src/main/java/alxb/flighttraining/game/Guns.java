package alxb.flighttraining.game;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class Guns {
    private final Node plane;
    private final FlightInput input;
    private final Node rootNode;
    private final AnimComposer composer;
    private final AssetManager assetManager;

    float timeLastShot = 1f;
    float flashSpeed = 10f;
    float timeLastFlash = 1f;

    Guns(Node plane, FlightInput input, Node rootNode, AnimComposer composer, AssetManager assetManager) {
        this.plane = plane;
        this.input = input;
        this.rootNode = rootNode;
        this.composer = composer;
        this.assetManager = assetManager;
    }

    void shoot(float tpf) {

        timeLastFlash += tpf;

        if (input.shoot && timeLastFlash > 1f/flashSpeed){
            composer.setCurrentAction(
                    "07_Cannon_Burst",
                    AnimComposer.DEFAULT_LAYER,
                    false
            );

            timeLastFlash = 0f;

        }

        float shootSpeed = 100f;
        timeLastShot += tpf;
        if (timeLastShot >= 1f / shootSpeed) {

            if (input.shoot) {
                Vector3f pos = plane.getLocalTranslation();

                Vector3f forward =
                        plane.getLocalRotation()
                                .mult(Vector3f.UNIT_Z.negate())
                                .normalize();

                Ray ray =
                        new Ray(
                                pos,
                                forward
                        );

                ray.setLimit(1500f);

                CollisionResults results =
                        new CollisionResults();

                rootNode.collideWith(
                        ray,
                        results
                );

                timeLastShot = 0f;



                if (results.size() > 0) {

                    for (int i = 0; i < results.size(); i++) {

                        var collision =
                                results.getCollision(i);

                        Spatial hitObject =
                                collision.getGeometry();

                        if (isPartOfPlane(hitObject)) {
                            continue;
                        }

                        Vector3f hitPosition =
                                collision.getContactPoint();

                        partikel(hitPosition);

                        break;
                    }
                }
            }
        }


    }

    private void partikel(Vector3f position){
        ParticleEmitter sparks =
                new ParticleEmitter(
                        "Sparks",
                        ParticleMesh.Type.Triangle,
                        30
                );

        Material mat =
                new Material(
                        assetManager,
                        "Common/MatDefs/Misc/Particle.j3md"
                );

        mat.setTexture(
                "Texture",
                assetManager.loadTexture(
                        "Textures/Particles/spark.png"
                )
        );

        sparks.setMaterial(mat);

        sparks.setStartColor(
                new ColorRGBA(1f, 0.8f, 0.2f, 1f)
        );

        sparks.setEndColor(
                new ColorRGBA(1f, 0.1f, 0f, 0f)
        );

        sparks.setStartSize(0.3f);
        sparks.setEndSize(0.05f);

        sparks.setLowLife(0.2f);
        sparks.setHighLife(0.5f);

        sparks.setParticlesPerSec(0); // Nur ein einzelner Ausstoß

        sparks.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0f, 3f, 0f));
        sparks.getParticleInfluencer()
                .setVelocityVariation(1f);

        // Bei ParticleEmitter wird die Gravitation subtrahiert:
        // positiver Y-Wert zieht die Funken nach unten.
        sparks.setGravity(0f, 5f, 0f);

        rootNode.attachChild(sparks);

        // hitPosition ist eine Weltposition.
        sparks.setLocalTranslation(
                rootNode.worldToLocal(position, new Vector3f())
        );

        // Erst positionieren, dann erzeugen!
        sparks.emitAllParticles();
    }

    private boolean isPartOfPlane(Spatial spatial) {

        Spatial current = spatial;

        while (current != null) {

            if (current == plane) {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }
}
