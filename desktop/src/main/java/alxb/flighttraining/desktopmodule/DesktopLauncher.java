package alxb.flighttraining.desktopmodule;

import alxb.flighttraining.game.FlightTraining;
import com.jme3.system.AppSettings;

/**
 * Used to launch a jme application in desktop environment
 *
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        final FlightTraining game = new FlightTraining();

        final AppSettings appSettings = new AppSettings(true);

        AppSettings settings = new AppSettings(true);
        settings.setResolution(1600, 900);

        settings.setUseJoysticks(true);
        game.setSettings(settings);
        game.setShowSettings(false);
        game.start();
    }
}
