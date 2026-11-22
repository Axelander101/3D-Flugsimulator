package alxb.flighttraining.game.flight;
public final class FlightCommandAi {
 private float pitch, roll, yaw, throttle;
 private static float limit(float v,float lo,float hi) {return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):0;}
 public float getPitch(){return pitch;} public float getRoll(){return roll;}
 public float getYaw(){return yaw;} public float getThrottle(){return throttle;}
 public void setPitch(float v){pitch=limit(v,-1,1);} public void setRoll(float v){roll=limit(v,-1,1);}
 public void setYaw(float v){yaw=limit(v,-1,1);} public void setThrottle(float v){throttle=limit(v,0,1);}
}
