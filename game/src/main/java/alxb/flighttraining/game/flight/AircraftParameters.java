package alxb.flighttraining.game.flight;

/** Grundeinstellungen eines Flugzeugs. Aero-Koeffizienten sind Spielmodell-Annahmen. */
public record AircraftParameters(float massKg, float wingAreaM2, float aerodynamicSpanM,
                                 float dryThrustN, float afterburnerThrustN,
                                 float positiveGLimit, float negativeGLimit) {
    public AircraftParameters {
        if (!Float.isFinite(massKg) || massKg<=0 || !Float.isFinite(wingAreaM2) || wingAreaM2<=0
                || !Float.isFinite(aerodynamicSpanM) || aerodynamicSpanM<=0
                || !Float.isFinite(dryThrustN) || dryThrustN<=0
                || !Float.isFinite(afterburnerThrustN) || afterburnerThrustN<dryThrustN
                || !Float.isFinite(positiveGLimit) || positiveGLimit<=1
                || !Float.isFinite(negativeGLimit) || negativeGLimit>=0)
            throw new IllegalArgumentException("Ungültige Flugzeugparameter");
    }
    public static AircraftParameters hornet() {
        // 14 t ist ein angenommener Flugzustand, nicht das maximale Startgewicht.
        return new AircraftParameters(14000f,37.16f,11.43f,98000f,157470f,7.5f,-3f);
    }
}
