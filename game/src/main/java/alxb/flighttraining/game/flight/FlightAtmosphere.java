package alxb.flighttraining.game.flight;

/** Trockene Standardatmosphäre, ISA-Troposphäre und isotherme Schicht bis 20 km. */
public record FlightAtmosphere(float densityKgM3, float temperatureK, float speedOfSoundMps) {
    public static FlightAtmosphere atAltitude(float altitudeM) {
        float h=Float.isFinite(altitudeM)?Math.max(-500f,Math.min(20000f,altitudeM)):0;
        double temperature,pressure;
        if(h<=11000){
            temperature=288.15-0.0065*h;
            pressure=101325*Math.pow(temperature/288.15,5.25588);
        }else{
            temperature=216.65;
            pressure=22632.06*Math.exp(-9.80665*(h-11000)/(287.05287*temperature));
        }
        return new FlightAtmosphere((float)(pressure/(287.05287*temperature)),
                (float)temperature,(float)Math.sqrt(1.4*287.05287*temperature));
    }
}
