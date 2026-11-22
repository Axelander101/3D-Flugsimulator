package alxb.flighttraining.game.enemy;

import alxb.flighttraining.game.weapons.Cannon;
import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.flight.FlightCommandAi;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.flight.AircraftRig;
import alxb.flighttraining.game.weapons.Guns;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
public final class EnemyAircraft {
 private final Node enemyPlane;
 private final FlightCommandAi commands=new FlightCommandAi();
 private final FlightPhysics physics;
 private final EnemyAI ai;
 private final Guns cannon;
 private float cooldown;
 private boolean weaponsEnabled=true;
 public void configure(DogfightSettings settings) {
  ai.setDifficulty(settings.difficulty); weaponsEnabled=settings.weaponsEnabled;
 }

 public EnemyAircraft(Node node,Node weaponTargets){
  enemyPlane=node; node.updateGeometricState();
  physics=new FlightPhysics(node,commands);
  physics.reset(node.getLocalRotation().mult(new Vector3f(0,0,-180)), physics.getTrimThrottle(180,1,0));
  AircraftRig.attach(node,physics.getSystems());
  ai=new EnemyAI(commands,node,physics); cannon=new Cannon(weaponTargets);
 }
 public void update(float dt,Node target,Vector3f targetVelocity,float ground){
  if (!Float.isFinite(dt) || dt <= 0) return;
  float remaining=Math.min(dt,0.25f);
  while(remaining>0.000001f){
   float step=Math.min(remaining,1f/120f);
   updateStep(step,target,targetVelocity,ground);
   remaining-=step;
  }
 }
 private void updateStep(float dt,Node target,Vector3f targetVelocity,float ground){
  ai.update(dt,target,targetVelocity,ground); physics.update(dt);
  enemyPlane.updateGeometricState(); cannon.update(dt); cooldown-=dt;
  if(weaponsEnabled && ai.isFiring() && cooldown<=0){
   Vector3f direction=enemyPlane.getWorldRotation().mult(new Vector3f(0,0,-1));
   cannon.fire(getPosition().add(direction.mult(12)),direction,physics.getVelocity());
   cooldown=0.04f;
  }
 }
 public void setSoundEvents(java.util.function.Consumer<Vector3f> fire,java.util.function.Consumer<Vector3f> hit){cannon.setShotListener(fire);cannon.setHitListener(hit);}
 public void rebase(Vector3f delta){ai.rebase(delta);cannon.rebase(delta);}
 public EnemyAI.State getState(){return ai.getState();}
 public Vector3f getPosition(){return enemyPlane.getWorldTranslation().clone();}
 public Node getPlane(){return enemyPlane;}
 public void remove(){enemyPlane.removeFromParent();}
}
