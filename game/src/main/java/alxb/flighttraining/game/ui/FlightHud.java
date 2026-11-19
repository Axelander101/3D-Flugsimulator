package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.flight.FlightPhysics;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.scene.Node;

public final class FlightHud {
    private final BitmapText hudText;

    public FlightHud(BitmapFont font, Node guiNode, int viewportHeight) {
        hudText = new BitmapText(font);
        hudText.setLocalTranslation(10, viewportHeight - 10, 0);
        guiNode.attachChild(hudText);
    }

    public void showLoading(String status) {
        hudText.setText(status);
    }

    public void update(FlightPhysics physics, float height) {
        String speedText = String.format("%.0f", physics.getAirspeed());
        String heightText = String.format("%.0f", height);
        String aoaText = String.format("%.1f", -physics.getAngleOfAttack());
        String betaText = String.format("%.1f", physics.getSideSlipAngle());
        String flightPathText = String.format("%.1f", physics.getFlightPathAngle());
        String gFaktor = String.format("%.1f", physics.getNormalG());
        String pitchSpeed = String.format("%.3f", physics.getPitchSpeed());
        String verticalSpeed = String.format("%.1f", physics.getVerticalSpeed());

        hudText.setText(
                "TAS: " + speedText + " m/s\n"
                        + "EAS: " + String.format("%.0f", physics.getEquivalentAirspeed()) + " m/s\n"
                        + "Mach: " + String.format("%.2f", physics.getMach()) + "\n"
                        + "Height: " + heightText + " m\n"
                        + "AoA: " + aoaText + "°\n"
                        + "Beta: " + betaText + "°\n"
                        + "Throttle: " + (physics.getThrottle() * 100f) + "%\n"
                        + "Vertical Speed: " + verticalSpeed + " m/s\n"
                        + "Flight Path: " + flightPathText + "°\n"
                        + "Pitch Speed: " + pitchSpeed + " rad/s\n"
                        + "G: " + gFaktor + " g\n"
        );
    }
}
