package alxb.flighttraining.desktopmodule;

import alxb.flighttraining.game.FlightTraining;
import com.jme3.system.AppSettings;

/**
 * Used to launch a jme application in desktop environment
 *
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        final FlightTraining game = new FlightTraining();

        AppSettings settings = new AppSettings(true);
        settings.setResolution(1600, 900);
        settings.setDepthBits(24);
        settings.setVSync(true);

        settings.setUseJoysticks(true);
        game.setSettings(settings);
        game.setShowSettings(false);
        game.start();
    }
}
