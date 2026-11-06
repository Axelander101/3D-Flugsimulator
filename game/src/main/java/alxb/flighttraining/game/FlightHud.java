package alxb.flighttraining.game;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.scene.Node;

final class FlightHud {
    private final BitmapText hudText;

    FlightHud(BitmapFont font, Node guiNode, int viewportHeight) {
        hudText = new BitmapText(font);
        hudText.setLocalTranslation(10, viewportHeight - 10, 0);
        guiNode.attachChild(hudText);
    }

    void update(FlightPhysics physics, float height) {
        String speedText = String.format("%.0f", physics.getSpeed());
        String heightText = String.format("%.0f", height);
        String aoaText = String.format("%.1f", -physics.getAngleOfAttack());
        String betaText = String.format("%.1f", physics.getSideSlipAngle());
        String flightPathText = String.format("%.1f", physics.getFlightPathAngle());

        hudText.setText(
                "Speed: " + speedText + " m/s\n"
                        + "Height: " + heightText + " m\n"
                        + "AoA: " + aoaText + "°\n"
                        + "Beta: " + betaText + "°\n"
                        + "Throttle: " + (physics.getThrottle() * 100f) + "%\n"
                        + "Vertical Speed: " + String.format("%.1f", physics.getVerticalSpeed()) + " m/s\n"
                        + "Flight Path: " + flightPathText + "°\n"
                        + "Pitch Speed: " + String.format("%.3f", physics.getPitchSpeed()) + " rad/s\n"
        );
    }
}
