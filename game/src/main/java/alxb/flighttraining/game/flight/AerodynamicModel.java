package alxb.flighttraining.game.flight;

/** Stetige Näherungen für ein Spiel, keine vermessenen Hornet-Kennfelder. Winkel in rad. */
public final class AerodynamicModel {
    public static final float STALL_ONSET_DEG=24f;
    private AerodynamicModel() {}
    private static float smooth(float a,float b,float x){
        float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);
    }
    public static float liftCoefficient(float alpha,float mach) {
        float attached=1.55f*(float)Math.tanh(4.5f*alpha/1.55f);
        float separated=1.1f*(float)Math.sin(2*alpha);
        float blend=smooth((float)Math.toRadians(24),(float)Math.toRadians(50),Math.abs(alpha));
        float compressibility=1+0.08f*smooth(0.5f,0.85f,mach)-0.15f*smooth(0.95f,1.5f,mach);
        return (attached+(separated-attached)*blend)*compressibility;
    }
    public static float dragCoefficient(float alpha,float beta,float cl,float mach,AircraftParameters p){
        float ar=p.aerodynamicSpanM()*p.aerodynamicSpanM()/p.wingAreaM2();
        float induced=cl*cl/((float)Math.PI*ar*0.8f);
        float wave=0.045f*smooth(0.8f,1.08f,mach)-0.023f*smooth(1.1f,1.8f,mach);
        float sa=(float)Math.sin(alpha),sb=(float)Math.sin(beta);
        return 0.022f+induced+1.1f*sa*sa+0.25f*sb*sb+wave;
    }
    public static float alphaForLift(float desiredCl,float mach){
        float lo=-(float)Math.toRadians(24),hi=-lo;
        for(int i=0;i<18;i++){
            float mid=(lo+hi)*0.5f;
            if(liftCoefficient(mid,mach)<desiredCl)lo=mid;else hi=mid;
        }
        return (lo+hi)*0.5f;
    }
}
