package alxb.flighttraining.game.flight;

/** Per-aircraft mechanical state. Positions are 0..1, angles radians, time seconds. */
public final class AircraftSystems {
    public static final int GEAR=1, AIRBRAKE=2, FLAPS=4, PARKING_BRAKE=8,
            CANOPY=16, HOOK=32, WING_FOLD=64, ENGINE=128, DISPLAY_PAGE=256, DISPLAY_DETAIL=512;
    public enum Flaps { AUTO, HALF, FULL }
    private boolean gearDown, airbrakeOut, parkingBrake, canopyOpen, hookDown, wingsFolded;
    private boolean onGround;
    private boolean systemsPage=true, airframePage;
    private float gear, airbrake, flap, canopy, hook, fold, brake, steering, wheelAngle, noseWheelAngle;
    private Flaps flapMode=Flaps.AUTO;
    private String notice="";
    private float noticeTime;

    public void reset(boolean airborne) {
        gearDown=!airborne; gear=gearDown?1:0;
        airbrakeOut=parkingBrake=canopyOpen=hookDown=wingsFolded=onGround=false;
        airbrake=flap=canopy=hook=fold=brake=steering=wheelAngle=noseWheelAngle=noticeTime=0;
        flapMode=Flaps.AUTO; notice="";systemsPage=true;airframePage=false;
    }
    public void command(int commands, float speed) {
        if((commands&DISPLAY_DETAIL)!=0)airframePage=!airframePage;
        if((commands&DISPLAY_PAGE)!=0)systemsPage=!systemsPage;
        if ((commands&GEAR)!=0) {
            if(onGround && gearDown) warn("GEAR: WEIGHT ON WHEELS"); else gearDown=!gearDown;
        }
        if ((commands&AIRBRAKE)!=0) airbrakeOut=!airbrakeOut;
        if ((commands&FLAPS)!=0) flapMode=Flaps.values()[(flapMode.ordinal()+1)%3];
        if ((commands&PARKING_BRAKE)!=0) parkingBrake=!parkingBrake;
        if ((commands&HOOK)!=0) hookDown=!hookDown;
        if ((commands&CANOPY)!=0) {
            if(canopyOpen || onGround && speed<10) canopyOpen=!canopyOpen;
            else warn("CANOPY: STOP ON GROUND");
        }
        if ((commands&WING_FOLD)!=0) {
            if(onGround && speed<2 && gear>.99f) wingsFolded=!wingsFolded;
            else warn("FOLD: STOP, GEAR DOWN");
        }
    }
    private void warn(String text){notice=text;noticeTime=3;}
    public void update(float dt, float airspeed, float alphaDegrees, float yaw, boolean brakeHeld) {
        gear=approach(gear,gearDown?1:0,dt/3f);
        airbrake=approach(airbrake,airbrakeOut?1:0,dt/1.2f);
        canopy=approach(canopy,canopyOpen?1:0,dt/2.5f);
        hook=approach(hook,hookDown?1:0,dt/1.5f);
        fold=approach(fold,wingsFolded?1:0,dt/4f);
        // A game approximation: retract landing flaps progressively at high airspeed.
        float speedLimit=clamp((170-airspeed)/50f,0,1);
        float target=flapMode==Flaps.AUTO?clamp((alphaDegrees-2)/24f,0,.35f)*speedLimit
                :(flapMode==Flaps.HALF?.5f:1)*speedLimit;
        flap=approach(flap,target,dt*.6f);
        brake=parkingBrake||brakeHeld?1:0;
        float targetSteer=onGround && gear>.99f ? yaw*(float)Math.toRadians(30)
                *clamp(1-airspeed/55f,0,1):0;
        steering=approach(steering,targetSteer,dt*1.5f);
        noticeTime=Math.max(0,noticeTime-dt);
    }
    public void groundContact(boolean grounded,float forwardSpeed,float dt) {
        onGround=grounded;
        if(grounded && gear>.99f){
            wheelAngle=(wheelAngle+forwardSpeed*dt/.175f)%(float)(2*Math.PI);
            noseWheelAngle=(noseWheelAngle+forwardSpeed*dt/.125f)%(float)(2*Math.PI);
        }
    }
    public float getGear(){return gear;} public boolean isGearDownSelected(){return gearDown;}
    public float getAirbrake(){return airbrake;} public float getFlaps(){return flap;}
    public Flaps getFlapMode(){return flapMode;} public float getCanopy(){return canopy;}
    public float getHook(){return hook;} public float getWingFold(){return fold;}
    public float getBrake(){return brake;} public boolean isParkingBrake(){return parkingBrake;}
    public boolean isOnGround(){return onGround;} public float getSteering(){return steering;}
    public float getNoseWheelAngle(){return noseWheelAngle;}
    public float getWheelAngle(){return wheelAngle;}
    public boolean isAirframePage(){return airframePage;}
    public boolean isSystemsPage(){return systemsPage;}
    public String getNotice(){return noticeTime>0?notice:"";}
    public String getGearStatus(){return gear>.99f?"DOWN / LOCKED":gear<.01f?"UP":"TRANSIT";}
    private static float approach(float value,float target,float step){return value+clamp(target-value,-step,step);}
    private static float clamp(float value,float low,float high){return Math.max(low,Math.min(high,value));}
}
