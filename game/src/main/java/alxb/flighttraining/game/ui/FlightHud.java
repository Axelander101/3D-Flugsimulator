package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.flight.FlightPhysics;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.awt.*;
import java.util.Locale;

/** Live flight instruments drawn onto the actual 3D display surfaces, not a screen overlay. */
public final class FlightHud {
    private static final Color GREEN = new Color(96, 255, 140), DIM = new Color(22, 89, 53);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 28);
    private final CockpitTexture hud, radar, flight, navigation, scratchpad;
    private final Node plane;
    private final Enemy enemies;
    private boolean visible, weaponsEnabled = true;
    private int missiles, page;
    private long nextHud, nextPage;

    public FlightHud(AssetManager assets, CockpitView cockpit, Node plane, Enemy enemies) {
        this.plane = plane; this.enemies = enemies;
        hud = new CockpitTexture(assets, cockpit.surface("HUD_Symbols_Surface"), 512, 512, true);
        radar = new CockpitTexture(assets, cockpit.surface("DDI_Left_Surface"), 512, 512, false);
        flight = new CockpitTexture(assets, cockpit.surface("DDI_Right_Surface"), 512, 512, false);
        navigation = new CockpitTexture(assets, cockpit.surface("AMPCD_Surface"), 512, 512, false);
        scratchpad = new CockpitTexture(assets, cockpit.surface("UFC_Scratchpad_Surface"), 512, 64, false);
        showLoading("");
    }
    public void setVisible(boolean value) { if (value && !visible) nextHud = nextPage = 0; visible = value; }
    public void setWeapons(int missiles, boolean enabled) { this.missiles = missiles; weaponsEnabled = enabled; }
    public void showLoading(String status) {
        for (CockpitTexture screen : new CockpitTexture[]{radar, flight, navigation, scratchpad}) {
            Graphics2D g = screen.begin(false); g.setColor(GREEN); g.setFont(FONT);
            if (!status.isEmpty()) g.drawString("LOADING", 25, 45); screen.upload(g);
        }
        hud.upload(hud.begin(true)); nextHud = nextPage = 0;
    }
    public void update(FlightPhysics physics, float altitude) {
        if (!visible) return;
        long now = System.nanoTime();
        if (now >= nextHud) { paintHud(physics, altitude); nextHud = now + 50_000_000L; }
        // Stagger the large textures; never regenerate all instrument pages in one frame.
        if (now >= nextPage) {
            switch (page++ % 4) {
                case 0 -> paintRadar();
                case 1 -> paintFlight(physics, altitude);
                case 2 -> paintNavigation(physics);
                case 3 -> paintScratchpad();
            }
            nextPage = now + 25_000_000L;
        }
    }
    private static String number(float value, String format) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, format, value) : "---";
    }
    private float heading() {
        Vector3f f = plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        return ((float) Math.toDegrees(Math.atan2(f.x, -f.z)) + 360) % 360;
    }
    private static void heading(Graphics2D g, String title) {
        g.setFont(FONT.deriveFont(Font.BOLD, 29)); g.setColor(GREEN); g.drawString(title, 22, 42);
        g.setStroke(new BasicStroke(2)); g.drawLine(20, 57, 492, 57); g.setFont(FONT);
    }
    private static void row(Graphics2D g, int y, String key, String value) {
        g.setColor(GREEN); g.drawString(key, 25, y);
        g.drawString(value, 485 - g.getFontMetrics().stringWidth(value), y);
    }
    private void paintHud(FlightPhysics p, float altitude) {
        Graphics2D g = hud.begin(true); g.setColor(GREEN); g.setStroke(new BasicStroke(2.4f));
        g.setFont(FONT.deriveFont(Font.BOLD, 35));
        g.drawRect(10, 130, 147, 49); g.drawRect(334, 130, 168, 49);
        g.drawString(number(p.getAirspeed(), "%3.0f"), 25, 167);
        String alt = number(altitude, "%5.0f"); g.drawString(alt, 492 - g.getFontMetrics().stringWidth(alt), 167);
        g.setFont(FONT.deriveFont(21f)); g.drawString("TAS m/s", 18, 119); g.drawString("ALT m", 364, 119);
        g.setFont(FONT.deriveFont(26f));
        String hdg = number(heading(), "%03.0f"); g.drawString(hdg, 232, 64);
        for (int i = -3; i <= 3; i++) g.drawLine(256 + i * 31, 75, 256 + i * 31, i == 0 ? 94 : 86);
        g.drawOval(243, 239, 26, 26); g.drawLine(222, 252, 243, 252);
        g.drawLine(269, 252, 290, 252); g.drawLine(256, 222, 256, 239);
        // A real attitude horizon in the physical HUD frame.
        Vector3f forward = plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        Vector3f right = plane.getWorldRotation().mult(Vector3f.UNIT_X);
        Vector3f up = plane.getWorldRotation().mult(Vector3f.UNIT_Y);
        float pitch = (float) Math.asin(Math.max(-1, Math.min(1, forward.y)));
        float bank = (float) Math.atan2(right.y, up.y);
        Graphics2D horizon = (Graphics2D) g.create(); horizon.translate(256, 252 + Math.max(-120, Math.min(120, pitch * 190)));
        horizon.rotate(-bank); horizon.drawLine(-112, 0, -30, 0); horizon.drawLine(30, 0, 112, 0); horizon.dispose();
        g.drawString("M " + number(p.getMach(), "%.2f"), 16, 400);
        g.drawString("G " + number(p.getNormalG(), "%+.1f"), 333, 400);
        g.setFont(FONT.deriveFont(23f));
        g.drawString("VS " + number(p.getVerticalSpeed(), "%+.0f") + " m/s", 135, 455);
        if (p.isStalling()) { g.setColor(new Color(255, 195, 75)); g.drawString("HIGH AOA", 196, 345); }
        hud.upload(g);
    }
    private void paintFlight(FlightPhysics p, float altitude) {
        Graphics2D g = flight.begin(false); heading(g, "FLIGHT / AIRDATA");
        row(g, 103, "TAS", number(p.getAirspeed(), "%.0f") + " m/s");
        row(g, 148, "EAS", number(p.getEquivalentAirspeed(), "%.0f") + " m/s");
        row(g, 193, "MACH", number(p.getMach(), "%.2f"));
        row(g, 238, "ALT", number(altitude, "%.0f") + " m");
        row(g, 283, "AOA", number(-p.getAngleOfAttack(), "%+.1f") + " deg");
        row(g, 328, "G LOAD", number(p.getNormalG(), "%+.1f") + " g");
        row(g, 373, "V/S", number(p.getVerticalSpeed(), "%+.0f") + " m/s");
        row(g, 420, "THR", number(p.getThrottle() * 100, "%.0f") + " %");
        g.setColor(DIM); g.fillRect(26, 453, 458, 18); g.setColor(GREEN);
        g.fillRect(26, 453, (int) (458 * Math.max(0, Math.min(1, p.getThrottle()))), 18);
        flight.upload(g);
    }
    private void paintNavigation(FlightPhysics p) {
        Graphics2D g = navigation.begin(false); heading(g, "NAV / FLIGHT / STORES");
        row(g, 103, "HDG", number(heading(), "%03.0f") + " deg");
        row(g, 150, "BETA", number(p.getSideSlipAngle(), "%+.1f") + " deg");
        row(g, 197, "PATH", number(p.getFlightPathAngle(), "%+.1f") + " deg");
        row(g, 244, "PITCH RATE", number(p.getPitchSpeed(), "%+.3f"));
        g.setFont(FONT.deriveFont(20f)); g.drawString("rad/s", 412, 270); g.setFont(FONT);
        row(g, 312, "IR MISSILES", missiles + " / 4");
        row(g, 359, "RELEASE", !weaponsEnabled ? "SAFE" : missiles == 0 ? "IR EMPTY" : "IR READY");
        row(g, 406, "THROTTLE", number(p.getThrottle() * 100, "%.0f") + " %");
        g.setFont(FONT.deriveFont(19f)); g.drawString("ARROWS / RMB: LOOK   HOME: CENTER", 22, 462);
        g.drawString("SPACE: CANNON    M: MISSILE", 22, 490);
        navigation.upload(g);
    }
    private void paintRadar() {
        Graphics2D g = radar.begin(false); heading(g, "RADAR / 5 km");
        int centerX = 256, centerY = 270, radius = 190;
        g.setColor(DIM); for (int r : new int[]{radius / 2, radius}) g.drawOval(centerX-r, centerY-r, r*2, r*2);
        g.drawLine(centerX-radius, centerY, centerX+radius, centerY); g.drawLine(centerX, centerY-radius, centerX, centerY+radius);
        Vector3f position = plane.getWorldTranslation();
        Vector3f forward = plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate()); forward.y=0;
        if (forward.lengthSquared() < .0001f) forward.set(0,0,-1); forward.normalizeLocal();
        Vector3f right = forward.cross(Vector3f.UNIT_Y).normalizeLocal();
        int contacts=0; float nearest=Float.POSITIVE_INFINITY;
        g.setColor(GREEN); g.fillPolygon(new int[]{256,249,256,263},new int[]{260,280,275,280},4);
        for(int i=0;i<enemies.getCount();i++) {
            Vector3f offset=enemies.getPosition(i).subtract(position); float distance=offset.length();
            if(distance>5000 || enemies.getPlane(i).getParent()==null) continue;
            contacts++; nearest=Math.min(nearest,distance);
            int x=Math.round(centerX+offset.dot(right)*radius/5000f), y=Math.round(centerY-offset.dot(forward)*radius/5000f);
            g.drawPolygon(new int[]{x,x+6,x,x-6},new int[]{y-6,y,y+6,y},4);
        }
        g.setFont(FONT.deriveFont(24f));g.drawString("CONTACTS " + contacts,22,491);
        if(Float.isFinite(nearest))g.drawString(number(nearest/1000,"%.1f")+" km",360,491);
        radar.upload(g);
    }
    private void paintScratchpad() {
        Graphics2D g=scratchpad.begin(false);g.setColor(GREEN);g.setFont(FONT.deriveFont(Font.BOLD,38));
        g.drawString(!weaponsEnabled ? "WEAPONS SAFE" : "IR " + missiles + "   " + (missiles>0 ? "READY" : "EMPTY"),18,46);
        scratchpad.upload(g);
    }
}
