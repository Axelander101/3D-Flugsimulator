package alxb.flighttraining.game.flight;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.Objects;

/**
 * Aerodynamische Kräfte mit vereinfachtem Fly-by-Wire und gedämpften Drehraten.
 * Welt: +Y oben; Flugzeug: +X rechts, -Z vorne. SI-Einheiten.
 * Spieler und KI verwenden dieselbe Physik. Keine KI-spezifische Kursdrehung.
 */
public final class FlightPhysics {
    private static final float G=9.80665f, STEP=1f/120f;
    private double positionX,positionY,positionZ;
    private final Vector3f publishedPosition=new Vector3f(Float.NaN,0,0);
    private final Node plane;
    private final FlightInput input;
    private final FlightCommandAi aiInput;
    private final AircraftParameters parameters;
    private final Vector3f velocity=new Vector3f(),wind=new Vector3f();
    private float pitchSpeed,yawSpeed,rollSpeed,pitchCommand,rollCommand,yawCommand;
    private float throttle,enginePower,normalG,lastThrust,lastLift,lastDrag,lastFrameDt;
    private final AircraftSystems systems=new AircraftSystems();
    private boolean engineRunning=true,flightAssistEnabled=true;

    public FlightPhysics(Node plane,FlightInput input){this(plane,input,null,AircraftParameters.hornet());}
    public FlightPhysics(Node plane,FlightCommandAi commands){this(plane,null,Objects.requireNonNull(commands),AircraftParameters.hornet());}
    public FlightPhysics(Node plane,FlightInput input,AircraftParameters parameters){this(plane,Objects.requireNonNull(input),null,parameters);}
    public FlightPhysics(Node plane,FlightCommandAi commands,AircraftParameters parameters){this(plane,null,Objects.requireNonNull(commands),parameters);}
    private FlightPhysics(Node plane,FlightInput input,FlightCommandAi commands,AircraftParameters parameters){
        this.plane=Objects.requireNonNull(plane);this.input=input;aiInput=commands;
        this.parameters=Objects.requireNonNull(parameters);
        systems.reset(true);
        if(input==null && commands==null)throw new IllegalArgumentException("Steuerquelle fehlt");
    }
    public AircraftSystems getSystems(){return systems;}
    public boolean isEngineRunning(){return engineRunning;}
    public AircraftParameters getParameters(){return parameters;}
    public void reset(Vector3f initialVelocity,float initialThrottle){
        publishedPosition.x=Float.NaN;
        setVelocity(initialVelocity);throttle=clamp(initialThrottle,0,1);enginePower=engineRunning?throttle:0;
        pitchSpeed=yawSpeed=rollSpeed=pitchCommand=rollCommand=yawCommand=normalG=0;
        lastThrust=lastLift=lastDrag=0;
    }
    public void setVelocity(Vector3f value){checkVector(value);velocity.set(value);}
    public void setWindVelocity(Vector3f value){checkVector(value);wind.set(value);}
    public Vector3f getWindVelocity(){return wind.clone();}
    public void setEngineRunning(boolean value){engineRunning=value;}
    public void setFlightAssistEnabled(boolean value){flightAssistEnabled=value;}
    private static void checkVector(Vector3f v){
        if(v==null || !Float.isFinite(v.x)||!Float.isFinite(v.y)||!Float.isFinite(v.z))
            throw new IllegalArgumentException("Ungültiger Vektor");
    }
    private static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):0;}
    private static float blend(float response,float dt){return 1-(float)Math.exp(-response*dt);}
    private static float stick(float value){
        value=clamp(value,-1,1);float a=Math.abs(value);
        return a<=0.05f?0:Math.signum(value)*(a-0.05f)/0.95f;
    }
    private float requestedPitch(){
        if(aiInput!=null)return clamp(aiInput.getPitch(),-1,1);
        float key=(input.pitchUp?1:0)-(input.pitchDown?1:0);return key!=0?key:stick(input.pitchInput);
    }
    private float requestedRoll(){
        if(aiInput!=null)return clamp(aiInput.getRoll(),-1,1);
        float key=(input.rollLeft?1:0)-(input.rollRight?1:0);return key!=0?key:stick(input.rollInput);
    }
    private float requestedYaw(){
        if(aiInput!=null)return clamp(aiInput.getYaw(),-1,1);
        float key=(input.yawLeft?1:0)-(input.yawRight?1:0);return key!=0?key:stick(input.yawInput);
    }
    public float pitchCommandForLoadFactor(float g){
        return g>=1?clamp((g-1)/(parameters.positiveGLimit()-1),0,1)
                :clamp((g-1)/(1-parameters.negativeGLimit()),-1,0);
    }
    private float requestedLoad(){
        return pitchCommand>=0?1+pitchCommand*(parameters.positiveGLimit()-1)
                :1+pitchCommand*(1-parameters.negativeGLimit());
    }
    public void update(float tpf){
        if(!Float.isFinite(tpf)||tpf<=0)return;
        if(input!=null){
            int commands=input.drainSystemCommands();
            systems.command(commands,getSpeed());
            if((commands&AircraftSystems.ENGINE)!=0)engineRunning=!engineRunning;
        }
        lastFrameDt=Math.min(tpf,0.25f);
        float remaining=lastFrameDt;
        while(remaining>0.000001f){float dt=Math.min(STEP,remaining);step(dt);remaining-=dt;}
    }
    private void step(float dt){
        updateCommands(dt);
        systems.update(dt,getAirspeed(),-getAngleOfAttack(),requestedYaw(),input!=null && input.wheelBrake);
        Vector3f air=velocity.subtract(wind);
        float speed=air.length();
        Quaternion rotation=plane.getWorldRotation().clone();
        Vector3f forward=rotation.mult(new Vector3f(0,0,-1));
        Vector3f right=rotation.mult(Vector3f.UNIT_X);
        Vector3f up=rotation.mult(Vector3f.UNIT_Y);
        FlightAtmosphere atmosphere=getAtmosphere();
        float mach=speed/atmosphere.speedOfSoundMps();
        float q=0.5f*atmosphere.densityKgM3()*speed*speed;
        Vector3f local=rotation.inverse().mult(air);
        float alpha=speed>0.1f?(float)Math.atan2(-local.y,-local.z):0;
        float beta=speed>0.1f?(float)Math.asin(clamp(local.x/speed,-1,1)):0;
        float cl=AerodynamicModel.liftCoefficient(alpha,mach);
        cl+=systems.getFlaps()*.55f;
        float cd=AerodynamicModel.dragCoefficient(alpha,beta,cl,mach,parameters)
                +systemsDragCoefficient();
        lastThrust=thrustForPower(enginePower,mach,atmosphere.densityKgM3())
                * (engineRunning?1:clamp(enginePower/0.03f,0,1));
        Vector3f nonGravity=forward.mult(lastThrust);
        lastLift=q*parameters.wingAreaM2()*cl*(1-.35f*systems.getWingFold());lastDrag=q*parameters.wingAreaM2()*cd;
        if(speed>0.1f){
            Vector3f direction=air.divide(speed);
            Vector3f liftDirection=right.cross(direction);
            if(liftDirection.lengthSquared()>0.0001f)nonGravity.addLocal(liftDirection.normalizeLocal().mult(lastLift));
            nonGravity.addLocal(direction.mult(-lastDrag));
            // Rumpf-/Leitwerks-Seitenkraft; zusätzliche Energieverluste stehen im C_D(beta).
            nonGravity.addLocal(right.mult(-q*parameters.wingAreaM2()*0.45f*beta));
        }
        Vector3f specificForce=nonGravity.divide(parameters.massKg());
        Vector3f acceleration=specificForce.add(new Vector3f(0,-G,0));
        normalG=specificForce.dot(up)/G;
        updateRotation(dt,rotation,air,alpha,beta,q,mach,acceleration);
        velocity.addLocal(acceleration.mult(dt));
        Vector3f current=plane.getWorldTranslation();
        if(!current.equals(publishedPosition)) {positionX=current.x;positionY=current.y;positionZ=current.z;}
        positionX+=(double)velocity.x*dt;positionY+=(double)velocity.y*dt;positionZ+=(double)velocity.z*dt;
        Vector3f worldPosition=new Vector3f((float)positionX,(float)positionY,(float)positionZ);
        publishedPosition.set(worldPosition);
        plane.setLocalTranslation(plane.getParent()==null?worldPosition:plane.getParent().worldToLocal(worldPosition,new Vector3f()));
    }
    private void updateCommands(float dt){
        pitchCommand+=(requestedPitch()-pitchCommand)*blend(12,dt);
        rollCommand+=(requestedRoll()-rollCommand)*blend(16,dt);
        yawCommand+=(requestedYaw()-yawCommand)*blend(10,dt);
        if(aiInput!=null)throttle=clamp(aiInput.getThrottle(),0,1);
        else{
            float change=(input.throttleUp?1:0)-(input.throttleDown?1:0);
            if(Math.abs(input.throttleUpInput)>0.05f)change+=input.throttleUpInput;
            if(Math.abs(input.throttleDownInput)>0.05f)change-=input.throttleDownInput;
            throttle=clamp(throttle+change*dt*0.4f,0,1);
        }
        float target=engineRunning?throttle:0;
        enginePower+=(target-enginePower)*blend(target>enginePower?1.2f:2.5f,dt);
    }
    private void updateRotation(float dt,Quaternion rotation,Vector3f air,float alpha,float beta,
                                float q,float mach,Vector3f acceleration){
        float speed=air.length();
        float authority=clamp((float)Math.sqrt(q/12000f),0,1);
        float separated=clamp((Math.abs(alpha)-(float)Math.toRadians(24))/(float)Math.toRadians(35),0,1);
        float controlAuthority=authority*(1-0.75f*separated);
        // Winkelgeschwindigkeit des Geschwindigkeitsvektors: folgt der durch Kräfte entstehenden Kurve.
        // Vereinfachte aerodynamische Stabilisierung, nicht die volle Momentengleichung eines Jets.
        Vector3f pathRate=speed>30?air.cross(acceleration).divideLocal(speed*speed):new Vector3f();
        Vector3f localPathRate=rotation.inverse().mult(pathRate);
        float targetPitch;
        if(systems.isOnGround() && speed<65){
            // Nosewheel steering is independent of aerodynamic rudder authority.
            float signedSpeed=velocity.dot(rotation.mult(new Vector3f(0,0,-1)));
            float turn=Math.max(-.65f,Math.min(.65f,(float)Math.tan(systems.getSteering())*signedSpeed/3.7f));
            plane.setLocalRotation(plane.getLocalRotation().mult(new Quaternion().fromAngleAxis(turn*dt,Vector3f.UNIT_Y)).normalizeLocal());
            float headingChange=turn*dt;
            Vector3f rolling=new Quaternion().fromAngleAxis(headingChange,Vector3f.UNIT_Y).mult(velocity);
            velocity.x=rolling.x;velocity.z=rolling.z;
            pitchSpeed=yawSpeed=rollSpeed=0;
            return;
        }
        if(flightAssistEnabled){
            float desiredCl=requestedLoad()*parameters.massKg()*G/Math.max(100,q*parameters.wingAreaM2()*(1-.35f*systems.getWingFold()))
                    -systems.getFlaps()*.55f;
            float desiredAlpha=AerodynamicModel.alphaForLift(desiredCl,mach);
            targetPitch=localPathRate.x+(desiredAlpha-alpha)*3.2f*controlAuthority;
        }else targetPitch=localPathRate.x+pitchCommand*0.65f*controlAuthority-alpha*0.2f*controlAuthority;
        targetPitch=clamp(targetPitch,-0.7f,0.7f);
        float targetYaw=localPathRate.y-beta*2.5f*authority+yawCommand*0.25f*controlAuthority;
        float targetRoll=rollCommand*(float)Math.toRadians(150)*controlAuthority;
        pitchSpeed+=(targetPitch-pitchSpeed)*blend(8,dt);
        yawSpeed+=(clamp(targetYaw,-0.6f,0.6f)-yawSpeed)*blend(7,dt);
        rollSpeed+=(targetRoll-rollSpeed)*blend(10,dt);
        Vector3f rates=new Vector3f(pitchSpeed,yawSpeed,rollSpeed);
        float magnitude=rates.length();
        if(magnitude>0.000001f){
            Quaternion increment=new Quaternion().fromAngleAxis(magnitude*dt,rates.divide(magnitude));
            plane.setLocalRotation(plane.getLocalRotation().mult(increment).normalizeLocal());
        }
    }
    private float thrustForPower(float power,float mach,float density){
        float lapse=(float)Math.pow(Math.max(0.08f,density/1.225f),0.65f);
        float inlet=1-0.12f*clamp(mach/2,0,1);
        float dry=parameters.dryThrustN()*(0.03f+0.97f*clamp(power/0.85f,0,1));
        float afterburner=(parameters.afterburnerThrustN()-parameters.dryThrustN())*clamp((power-0.85f)/0.15f,0,1);
        return (dry+afterburner)*lapse*inlet;
    }
    private float systemsDragCoefficient(){
        return systems.getGear()*.025f+systems.getAirbrake()*.14f+systems.getFlaps()*.055f
                +systems.getCanopy()*.03f+systems.getWingFold()*.08f;
    }
    public float getTrimThrottle(float airspeed,float loadFactor,float climbRate){
        float speed=Math.max(50,airspeed);FlightAtmosphere a=getAtmosphere();
        float q=0.5f*a.densityKgM3()*speed*speed,mach=speed/a.speedOfSoundMps();
        float cl=parameters.massKg()*G*loadFactor/(q*parameters.wingAreaM2());
        float alpha=AerodynamicModel.alphaForLift(cl-systems.getFlaps()*.55f,mach);
        float drag=q*parameters.wingAreaM2()*(AerodynamicModel.dragCoefficient(alpha,0,cl,mach,parameters)+systemsDragCoefficient());
        float required=Math.max(0,drag+parameters.massKg()*G*climbRate/speed);
        float dry=thrustForPower(0.85f,mach,a.densityKgM3()),max=thrustForPower(1,mach,a.densityKgM3());
        return required<=dry?clamp(0.85f*(required/dry-0.03f)/0.97f,0,0.85f)
                :clamp(0.85f+0.15f*(required-dry)/Math.max(1,max-dry),0.85f,1);
    }
    public void resolveGroundCollision(float groundHeight){
        if(!Float.isFinite(groundHeight)){systems.groundContact(false,0,0);return;}
        Vector3f world=plane.getWorldTranslation();
        Vector3f up=plane.getWorldRotation().mult(Vector3f.UNIT_Y);
        // Asset is displayed at half scale: main wheel bottom is 1.155 m below its root.
        float clearance=systems.getGear()>.99f?1.155f:.5f;
        boolean contact=world.y<=groundHeight+clearance;
        float forwardSpeed=velocity.dot(plane.getWorldRotation().mult(new Vector3f(0,0,-1)));
        systems.groundContact(contact,forwardSpeed,lastFrameDt);
        if(contact){
            world=world.clone();world.y=groundHeight+clearance;
            plane.setLocalTranslation(plane.getParent()==null?world:plane.getParent().worldToLocal(world,new Vector3f()));
            positionX=world.x;positionY=world.y;positionZ=world.z;publishedPosition.set(world);
            if(velocity.y<0)velocity.y=0;
            float horizontal=(float)Math.hypot(velocity.x,velocity.z);
            // Braking scales with supported weight; airborne brakes never slow the aircraft.
            float supported=Math.max(0,Math.min(1,1-lastLift*up.y/(parameters.massKg()*G)));
            float friction=systems.getGear()>.99f?.02f+.55f*systems.getBrake():.4f;
            float deceleration=friction*G*supported;
            float factor=horizontal>0?Math.max(0,1-deceleration*lastFrameDt/horizontal):0;
            velocity.x*=factor;velocity.z*=factor;
            // Static brake holds against idle thrust, without reversing velocity.
            if(horizontal<.5f && systems.getBrake()>.5f && lastThrust<deceleration*parameters.massKg()){
                velocity.x=velocity.z=0;
            }
            normalG=up.dot(Vector3f.UNIT_Y);
            pitchSpeed*=.9f;yawSpeed*=.9f;
        }
    }
    public Vector3f getVelocity(){return velocity.clone();}
    public float getSpeed(){return velocity.length();}
    public float getAirspeed(){return velocity.subtract(wind).length();}
    public float getEquivalentAirspeed(){return getAirspeed()*(float)Math.sqrt(getAirDensity()/1.225f);}
    public FlightAtmosphere getAtmosphere(){return FlightAtmosphere.atAltitude(plane.getWorldTranslation().y);}
    public float getAirDensity(){return getAtmosphere().densityKgM3();}
    public float getMach(){return getAirspeed()/getAtmosphere().speedOfSoundMps();}
    public float getVerticalSpeed(){return velocity.y;}
    public float getFlightPathAngle(){return (float)Math.toDegrees(Math.atan2(velocity.y,Math.hypot(velocity.x,velocity.z)));}
    public float getThrottle(){return throttle;}
    public float getEnginePower(){return enginePower;}
    public float getPitchSpeed(){return pitchSpeed;}
    public float getNormalG(){return normalG;}
    public float getThrustN(){return lastThrust;}
    public float getLiftN(){return lastLift;}
    public float getDragN(){return lastDrag;}

    public float getAngleOfAttack(){
        Vector3f air=velocity.subtract(wind);if(air.lengthSquared()<0.01f)return 0;
        Vector3f local=plane.getWorldRotation().inverse().mult(air);
        return (float)Math.toDegrees(Math.atan2(local.y,-local.z));
    }
    public float sideSlip(){return velocity.subtract(wind).dot(plane.getWorldRotation().mult(Vector3f.UNIT_X));}
    public float getSideSlipAngle(){return (float)Math.toDegrees(Math.asin(clamp(sideSlip()/Math.max(0.1f,getAirspeed()),-1,1)));}
    public boolean isStalling(){return Math.abs(getAngleOfAttack())>AerodynamicModel.STALL_ONSET_DEG && getAirspeed()>15;}
}
