package alxb.flighttraining.game;

public class FlightCommandAi {
    float pitch = 0f;
    float yaw = 0f;
    float roll = 0f;
    float throttle = 0f;


    public float getPitch(){
        return pitch;
    }

    public float getYaw(){
        return yaw;
    }

    public float getRoll(){
        return roll;
    }

    public float getThrottle(){
        return throttle;
    }

    public void setPitch(float pitch){
        this.pitch = pitch;
    }

    public void setYaw(float yaw){
        this.yaw = yaw;
    }

    public void setRoll(float roll){
        this.roll = roll;
    }

    public void setThrottle(float throttle){
        this.throttle = throttle;
    }

}
