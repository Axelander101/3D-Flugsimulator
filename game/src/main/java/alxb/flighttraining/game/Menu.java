package alxb.flighttraining.game;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;

import java.awt.*;

import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import java.awt.image.BufferedImage;


public class Menu{

    private final Picture picture;

    private boolean menuOffen = true;

    private final FlightTraining app;
    private final InputManager input;

    private boolean freePlayOffen = false;

    private Color hellBlau = new Color(50, 100, 160);

    private final java.util.Map<String, BufferedImage> airportImages =
            new java.util.HashMap<>();

    private static final String[] AIRPORTS = {
            "Zürich", "Genf", "Basel-Mulhouse",
            "Bern-Belp", "St. Gallen-Altenrhein", "Lugano-Agno",
            "Sion", "Grenchen", "Payerne",
            "Meiringen", "Emmen"
    };

    private static final String[] MISSIONS = {
            "Trainingsflug",
            "Navigation",
            "Abfangen",
            "Dogfight",
            "Patrouille",
            "Begleitschutz"
    };

    private static final String[] MISSION_DESCRIPTIONS = {
            "Lerne dein Flugzeug kennen.",
            "Fliege die vorgegebene Route.",
            "Erreiche und identifiziere das Ziel.",
            "Bestehe im direkten Luftkampf.",
            "Überwache den zugewiesenen Luftraum.",
            "Begleite ein verbündetes Flugzeug."
    };

    private static final String[] GRAPHICS_SETTING = {
            "Resolution", "Fullscreen", "VSync",
            "FPS limit", "Anti-aliasing", "Texture quality",
            "Terrain quality", "Render distance", "Shadow quality",
            "Vegetation density", "Building quality", "Cloud quality",
            "Particle quality", "Water quality", "Anisotropic filtering"
    };

    private static final String[] RESOLUTIONS = {
            "1280x720",
            "1600x900",
            "1920x1080",
            "2560x1440",
            "3840x2160"
    };

    private final java.util.List<java.awt.Rectangle> missionBounds =
            new java.util.ArrayList<>();

    private final java.awt.Rectangle fpsBounds =
            new java.awt.Rectangle();

    private final java.util.List<java.awt.Rectangle> airportBounds =
            new java.util.ArrayList<>();

    private final java.util.List<java.awt.Rectangle> settingsBounds =
            new java.util.ArrayList<>();

    private final java.util.List<java.awt.Rectangle> graphicsBounds =
            new java.util.ArrayList<>();

    private final java.awt.Rectangle backBounds =
            new java.awt.Rectangle();

    private boolean showFps = false;

    private float scrollOffset = 0;
    private static final int GRAPHICS_ROW_HEIGHT = 60;
    private static final int GRAPHICS_ROW_GAP = 16;
    private static final int GRAPHICS_TOP = 125;
    private static final int GRAPHICS_BOTTOM = 40;
    private final Rectangle graphicsViewport = new Rectangle();
    private boolean graphicsRedrawPending;


    private String currenRes = "1920x1080";

    private enum Screen {
        MAIN,
        MISSIONS,
        SETTINGS,
        GRAPHICS_SETTINGS,
        STEERING_SETTINGS,
        GAMEPLAY_SETTINGS,
        AUDIO_SETTINGS,
        INTERFACE_SETTING,
        MULTIPLAYER,
        FREE_PLAY
    }

    private enum GraphicsSettings {
        RESOLUTION,
        FULLSCREEN,
        VSYNC,
        FPS_LIMIT,
        ANTIALIASING,
        TEXTURE_QUALITY,
        TERRAIN_QUALITY,
        RENDER_DISTANCE,
        SHADOW_QUALITY,
        VEGETATION_DENSITY,
        BUILDING_QUALITY,
        CLOUD_QUALITY,
        PARTICLE_QUALITY,
        WATER_QUALITY,
        ANISOTOPIC_FILTERING
    }

    private Screen screen = Screen.MAIN;

    public Menu(AssetManager assets, int width, int height, FlightTraining app, InputManager input) {
        this.app = app;
        this.input = input;

        for (String airport : AIRPORTS) {
            airportImages.put(
                    airport,
                    loadMenuImage("/Airports/" + airport + ".png")
            );
        }

        picture = new Picture("MainMenu");
        picture.setWidth(width);
        picture.setHeight(height);
        picture.setPosition(0, 0);

        drawMenu(width, height, assets);


        //Mouse klicks
        app.getInputManager().addMapping(
                "MenuClick",
                new MouseButtonTrigger(MouseInput.BUTTON_LEFT)
        );

        app.getInputManager().addMapping(
                "ScrollUp",
                new MouseAxisTrigger(
                        MouseInput.AXIS_WHEEL,
                        false
                )
        );

        app.getInputManager().addMapping(
                "ScrollDown",
                new MouseAxisTrigger(
                        MouseInput.AXIS_WHEEL,
                        true
                )
        );

        ActionListener clickListener = (name, isPressed, tpf) -> {
            if (!isPressed || !menuOffen) {
                return;
            }

            Vector2f cursor = app.getInputManager().getCursorPosition();

            float mouseX = cursor.x;

            // jME: unten → Graphics2D: oben
            float mouseY = app.getCamera().getHeight() - cursor.y;


            // Mausklicks – innerhalb deines ActionListeners
            switch (screen) {
                case MAIN -> {
                    if (mouseX >= 80 && mouseX <= 380) {
                        if (mouseY >= 180 && mouseY <= 250) {
                            screen = Screen.MISSIONS;
                            drawMenu(width, height, assets);

                        } else if (mouseY >= 270 && mouseY <= 340) {
                            screen = Screen.FREE_PLAY;
                            drawMenu(width, height, assets);

                        } else if (mouseY >= 360 && mouseY <= 430) {
                            screen = Screen.SETTINGS;
                            drawMenu(width, height, assets);

                        } else if (mouseY >= 540 && mouseY <= 610) {
                            app.stop();
                        }
                    }
                }

                case FREE_PLAY -> {
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.MAIN;
                        drawMenu(width, height, assets);
                        return;
                    }

                    for (int i = 0; i < airportBounds.size(); i++) {
                        if (airportBounds.get(i).contains(mouseX, mouseY)) {

                            startGame(AIRPORTS[i]);
                        }
                    }
                }

                case MISSIONS -> {
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.MAIN;
                        drawMenu(width, height, assets);
                        return;
                    }

                    for (int i = 0; i < missionBounds.size(); i++) {
                        if (missionBounds.get(i).contains(mouseX, mouseY)) {
                            System.out.println("Mission: " + MISSIONS[i]);

                            // Hier später die ausgewählte Mission starten.
                            return;
                        }
                    }
                }

                case SETTINGS -> {
                    if (mouseY >= 180 && mouseY <= 250) {
                        screen = Screen.GRAPHICS_SETTINGS;
                        scrollOffset = 0;
                        drawMenu(width, height, assets);

                    } else if (mouseY >= 270 && mouseY <= 340) {
                        screen = Screen.STEERING_SETTINGS;
                        drawMenu(width, height, assets);

                    } else if (mouseY >= 360 && mouseY <= 430) {
                        screen = Screen.GAMEPLAY_SETTINGS;
                        drawMenu(width, height, assets);
                    }
                    else if (mouseY >= 450 && mouseY <= 520) {
                        screen = Screen.AUDIO_SETTINGS;
                        drawMenu(width, height, assets);
                    }
                    else if (mouseY >= 540 && mouseY <= 610) {
                        screen = Screen.INTERFACE_SETTING;
                        drawMenu(width, height, assets);
                    }
                }


                case GRAPHICS_SETTINGS -> {
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.SETTINGS;
                        drawMenu(width, height, assets);
                        return;
                    }
                    if (!graphicsViewport.contains(mouseX, mouseY)) return;
                    for (int i = 0; i < graphicsBounds.size(); i++) {
                        if (graphicsBounds.get(i).contains(mouseX, mouseY)) {
                            System.out.println("Grafikeinstellung: " + GRAPHICS_SETTING[i]);
                            // Hier später die ausgewählte Einstellung ändern.
                            return;
                        }
                    }
                }
            }

        };


        app.getInputManager().addListener(
                clickListener,
                "MenuClick"
        );

        AnalogListener scrollListener = (name, value, tpf) -> {
            if (!menuOffen || screen != Screen.GRAPHICS_SETTINGS) return;
            Vector2f cursor = input.getCursorPosition();
            float mouseY = app.getCamera().getHeight() - cursor.y;
            if (!graphicsViewport.contains(cursor.x, mouseY)) return;
            float direction = name.equals("ScrollDown") ? 1f : -1f;
            float previous = scrollOffset;
            scrollOffset = Math.max(0, Math.min(maxGraphicsScroll(height),
                    scrollOffset + direction * value * 40f));
            if (scrollOffset != previous) graphicsRedrawPending = true;
        };
        input.addListener(scrollListener, "ScrollUp", "ScrollDown");

        // Mehrere Rad-/Trackpad-Ereignisse pro Frame zusammenfassen.
        picture.addControl(new AbstractControl() {
            private float elapsed;
            @Override protected void controlUpdate(float tpf) {
                elapsed += tpf;
                if (graphicsRedrawPending && elapsed >= 1f / 30f) {
                    elapsed = 0;
                    graphicsRedrawPending = false;
                    if (menuOffen && screen == Screen.GRAPHICS_SETTINGS)
                        drawMenu(width, height, assets);
                }
            }
            @Override protected void controlRender(RenderManager rm, ViewPort vp) {}
        });
    }

    private void drawMenu(int width, int height, AssetManager assets) {
        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = image.createGraphics();

        try {
            g.setColor(new Color(20, 30, 45));
            g.fillRect(0, 0, width, height);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));

            switch (screen) {
                case MAIN -> {
                    g.drawString("Flight Training", 80, 120);

                    drawButton(g, "Missionen", 180, 80, hellBlau);
                    drawButton(g, "Free Play", 270, 80, hellBlau);
                    drawButton(g, "Einstellungen", 360, 80, hellBlau);
                    drawButton(g, "Multiplayer", 450, 80, hellBlau);
                    drawButton(g, "Beenden", 540, 80, Color.red);
                }
                case FREE_PLAY -> drawFreePlay(g, width, height);
                case MISSIONS -> drawMissions(g, width, height);
                case SETTINGS -> drawSettings(g);
                case GRAPHICS_SETTINGS -> drawGraphicSetting(g, width, height);

            }
        } finally {
            g.dispose();
        }

        Texture2D texture = new Texture2D(
                new AWTLoader().load(image, true)
        );

        picture.setTexture(assets, texture, true);
    }

    private void drawFreePlay(Graphics2D g, int width, int height) {
        int margin = 40;
        int gap = 16;
        int columns = 3;
        int rows = (AIRPORTS.length + columns - 1) / columns;

        // Kopfbereich
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString("Free Play", margin, 65);

        g.setColor(new Color(165, 180, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString("Wähle deinen Startflugplatz", margin, 95);

        // Zurück oben rechts
        backBounds.setBounds(width - margin - 130, 35, 130, 44);

        g.setColor(new Color(55, 70, 90));
        g.fillRoundRect(
                backBounds.x, backBounds.y,
                backBounds.width, backBounds.height,
                12, 12
        );

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        drawCenteredText(g, "Zurück", backBounds, 0);

        // Kartenraster
        int top = 125;
        int cardWidth = (width - 2 * margin - (columns - 1) * gap) / columns;

        int cardHeight = Math.min(
                155,
                (height - top - margin - (rows - 1) * gap) / rows
        );

        airportBounds.clear();

        for (int i = 0; i < AIRPORTS.length; i++) {
            int x = margin + (i % columns) * (cardWidth + gap);
            int y = top + (i / columns) * (cardHeight + gap);

            java.awt.Rectangle bounds =
                    new java.awt.Rectangle(x, y, cardWidth, cardHeight);

            airportBounds.add(bounds);
            drawAirportCard(g, AIRPORTS[i], bounds);
        }
    }

    private void startGame(String flughafen){

        switch (flughafen){
            case "Zürich" -> app.startFreePlay(new SpawnSettings(62160f, -164460f, 432f));
            case "Genf" -> app.startFreePlay(new SpawnSettings(-124060f, -29110f, 430f));
            case "Basel-Mulhouse" -> app.startFreePlay(new SpawnSettings(-15300f, -179730f, 270f));
            case "Bern-Belp" -> app.startFreePlay(new SpawnSettings(-16970f, -103340, 510f));
            case "St. Gallen-Altenrhein" -> app.startFreePlay(new SpawnSettings(138510f, -169020f, 398f));
            case "Lugano-Agno" -> app.startFreePlay(new SpawnSettings(92500f, -3240f, 279f));
            case "Sion" -> app.startFreePlay(new SpawnSettings(-30120f, -26140f, 482f));
            case "Grenchen" -> app.startFreePlay(new SpawnSettings(-23190f, -133100f, 430f));
            case "Payerne" -> app.startFreePlay(new SpawnSettings(-61550f, -95560f, 447f));
            case "Meiringen" -> app.startFreePlay(new SpawnSettings(29740f, -84500f, 577f));
            case "Emmen" -> app.startFreePlay(new SpawnSettings(44320f, -123520f, 427f));

            default -> throw new IllegalArgumentException(
                    "Unbekannter Flugplatz: " + flughafen
            );
        }
    }

    private void drawMissions(Graphics2D g, int width, int height) {
        drawPageHeader(
                g, "Missionen",
                "Wähle deinen nächsten Einsatz", width
        );

        int margin = 40;
        int gap = 18;
        int columns = 3;
        int cardWidth = (width - 2 * margin - 2 * gap) / columns;
        int cardHeight = Math.min(250, (height - 125 - margin - gap) / 2);

        missionBounds.clear();

        for (int i = 0; i < MISSIONS.length; i++) {
            int x = margin + (i % columns) * (cardWidth + gap);
            int y = 125 + (i / columns) * (cardHeight + gap);

            java.awt.Rectangle bounds = new java.awt.Rectangle(
                    x, y, cardWidth, cardHeight
            );
            missionBounds.add(bounds);

            // Kartenhintergrund
            g.setColor(new Color(35, 49, 68));
            g.fillRoundRect(x, y, cardWidth, cardHeight, 18, 18);

            // Platz für ein späteres Missionsbild
            int imageHeight = cardHeight - 100;

            g.setColor(new Color(48, 68, 91));
            g.fillRoundRect(
                    x + 10, y + 10,
                    cardWidth - 20, imageHeight,
                    12, 12
            );

            g.setColor(new Color(160, 180, 200));
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            drawCenteredText(
                    g, "Missionsvorschau",
                    new java.awt.Rectangle(
                            x + 10, y + 10,
                            cardWidth - 20, imageHeight
                    ),
                    0
            );

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString(MISSIONS[i], x + 18, y + cardHeight - 58);

            g.setColor(new Color(180, 195, 212));
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString(
                    MISSION_DESCRIPTIONS[i],
                    x + 18, y + cardHeight - 30
            );
        }
    }

    private void drawSettings(Graphics2D g) {
        g.drawString("Flight Training", 80, 120);

        drawButton(g, "Graphics", 180, 80, hellBlau);
        drawButton(g, "Steering", 270, 80, hellBlau);
        drawButton(g, "Gameplay", 360, 80, hellBlau);
        drawButton(g, "Audio", 450, 80, hellBlau);
        drawButton(g, "Interface", 540, 80, hellBlau);


    }

    private void drawGraphicSetting(Graphics2D g, int width, int height) {
        drawPageHeader(g, "Grafik", "Mit Mausrad oder Trackpad scrollen", width);
        int margin = 40;
        graphicsViewport.setBounds(margin, GRAPHICS_TOP,
                Math.max(1, width - margin * 2),
                Math.max(1, height - GRAPHICS_TOP - GRAPHICS_BOTTOM));
        scrollOffset = Math.max(0, Math.min(maxGraphicsScroll(height), scrollOffset));
        graphicsBounds.clear();

        Graphics2D content = (Graphics2D) g.create();
        try {
            content.clip(graphicsViewport);
            content.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int cardWidth = Math.max(1, graphicsViewport.width - 24);
            for (int i = 0; i < GRAPHICS_SETTING.length; i++) {
                int y = GRAPHICS_TOP + i * (GRAPHICS_ROW_HEIGHT + GRAPHICS_ROW_GAP)
                        - Math.round(scrollOffset);
                Rectangle row = new Rectangle(margin, y, cardWidth, GRAPHICS_ROW_HEIGHT);
                // Index bleibt gleich; unsichtbare Bereiche sind nicht anklickbar.
                Rectangle hit = row.intersection(graphicsViewport);
                graphicsBounds.add(hit.isEmpty() ? new Rectangle() : hit);

                if (!row.intersects(graphicsViewport)) continue;
                content.setColor(hellBlau);
                content.fillRoundRect(row.x, row.y, row.width, row.height, 16, 16);
                content.setColor(Color.WHITE);
                content.setFont(new Font("SansSerif", Font.PLAIN, 24));
                content.drawString(GRAPHICS_SETTING[i], row.x + 18, row.y + 39);
            }
        } finally {
            content.dispose();
        }

        // Scrollbalken außerhalb der Karten; Kopfbereich bleibt stehen.
        int maximum = maxGraphicsScroll(height);
        if (maximum > 0) {
            int x = graphicsViewport.x + graphicsViewport.width - 8;
            int trackHeight = graphicsViewport.height;
            int thumbHeight = Math.min(trackHeight, Math.max(28,
                    Math.round((float) trackHeight * trackHeight / graphicsContentHeight())));
            int thumbY = graphicsViewport.y + Math.round(
                    scrollOffset / maximum * (trackHeight - thumbHeight));
            g.setColor(new Color(35, 49, 68));
            g.fillRoundRect(x, graphicsViewport.y, 8, trackHeight, 8, 8);
            g.setColor(new Color(130, 185, 220));
            g.fillRoundRect(x, thumbY, 8, thumbHeight, 8, 8);
        }
    }




    private void drawSettingsBox(Graphics2D g, String text, int y, int x, Color color){
        g.setColor(color);
        g.fillRoundRect(x, y, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(text, x+15, y + 45);
    }

    private int graphicsContentHeight() {
        return GRAPHICS_SETTING.length * GRAPHICS_ROW_HEIGHT
                + Math.max(0, GRAPHICS_SETTING.length - 1) * GRAPHICS_ROW_GAP;
    }

    private int maxGraphicsScroll(int height) {
        int viewportHeight = Math.max(1, height - GRAPHICS_TOP - GRAPHICS_BOTTOM);
        return Math.max(0, graphicsContentHeight() - viewportHeight);
    }

    private void drawSettingsButton(Graphics2D g, String text, int y, int x, Color color){
        g.setColor(color);
        g.fillRoundRect(x, y, 1000, 60, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(text, x+15, y + 45);
    }

    private void drawButton(Graphics2D g, String text, int y, int x, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(text, x+15, y + 45);
    }

    private void drawCoverImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        Graphics2D copy = (Graphics2D) g.create();

        try {
            copy.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            copy.clip(new java.awt.geom.RoundRectangle2D.Float(
                    x, y, width, height, 12, 12
            ));

            double scale = Math.max(
                    (double) width / image.getWidth(),
                    (double) height / image.getHeight()
            );

            int drawWidth = (int) Math.ceil(image.getWidth() * scale);
            int drawHeight = (int) Math.ceil(image.getHeight() * scale);

            copy.drawImage(
                    image,
                    x + (width - drawWidth) / 2,
                    y + (height - drawHeight) / 2,
                    drawWidth,
                    drawHeight,
                    null
            );
        } finally {
            copy.dispose();
        }
    }

    private BufferedImage loadMenuImage(String path) {
        try (java.io.InputStream stream =
                     Menu.class.getResourceAsStream(path)) {

            if (stream == null) {
                throw new IllegalArgumentException(
                        "Bild nicht gefunden: " + path
                );
            }

            BufferedImage image = javax.imageio.ImageIO.read(stream);

            if (image == null) {
                throw new IllegalArgumentException(
                        "Bildformat nicht unterstützt: " + path
                );
            }

            return image;

        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "Bild konnte nicht geladen werden: " + path, e
            );
        }
    }

    private void drawAirportCard(Graphics2D g, String name, java.awt.Rectangle bounds) {
        int x = bounds.x;
        int y = bounds.y;
        int w = bounds.width;
        int h = bounds.height;

        g.setColor(new Color(35, 49, 68));
        g.fillRoundRect(x, y, w, h, 16, 16);

        int imageHeight = h - 52;
        BufferedImage image = airportImages.get(name);

        if (image != null) {
            drawCoverImage(
                    g, image,
                    x + 8, y + 8,
                    w - 16, imageHeight
            );
        } else {
            g.setColor(new Color(48, 68, 91));
            g.fillRoundRect(
                    x + 8, y + 8,
                    w - 16, imageHeight,
                    12, 12
            );
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(name, x + 16, y + h - 17);
    }

    private void drawCenteredText(Graphics2D g, String text, java.awt.Rectangle bounds, int offsetY) {
        java.awt.FontMetrics metrics = g.getFontMetrics();

        int x = bounds.x
                + (bounds.width - metrics.stringWidth(text)) / 2;

        int y = bounds.y
                + (bounds.height - metrics.getHeight()) / 2
                + metrics.getAscent()
                + offsetY;

        g.drawString(text, x, y);
    }

    private void drawPageHeader(Graphics2D g, String title, String subtitle, int width) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString(title, 40, 65);

        g.setColor(new Color(165, 180, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString(subtitle, 40, 95);

        backBounds.setBounds(width - 170, 35, 130, 44);

        g.setColor(new Color(55, 70, 90));
        g.fillRoundRect(
                backBounds.x, backBounds.y,
                backBounds.width, backBounds.height,
                12, 12
        );

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        drawCenteredText(g, "Zurück", backBounds, 0);
    }

    public void show(Node guiNode) {
        guiNode.attachChild(picture);
        menuOffen = true;
    }

    public void hide() {
        picture.removeFromParent();
        menuOffen = false;
    }

    public boolean getMenuOffen(){
        return menuOffen;
    }
}