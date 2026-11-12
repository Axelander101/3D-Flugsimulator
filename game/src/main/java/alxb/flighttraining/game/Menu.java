package alxb.flighttraining.game;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;

import java.awt.*;

import com.jme3.input.controls.ActionListener;
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

    private final java.util.List<java.awt.Rectangle> missionBounds =
            new java.util.ArrayList<>();

    private final java.awt.Rectangle fpsBounds =
            new java.awt.Rectangle();

    private final java.util.List<java.awt.Rectangle> airportBounds =
            new java.util.ArrayList<>();

    private final java.awt.Rectangle backBounds =
            new java.awt.Rectangle();

    private boolean showFps = false;

    private enum Screen {
        MAIN,
        MISSIONS,
        SETTINGS,
        FREE_PLAY
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
                            System.out.println("Flugplatz: " + AIRPORTS[i]);

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
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.MAIN;
                        drawMenu(width, height, assets);
                        return;
                    }

                    if (fpsBounds.contains(mouseX, mouseY)) {
                        showFps = !showFps;
                        app.setDisplayFps(showFps);
                        drawMenu(width, height, assets);
                    }
                }
            }

        };

        app.getInputManager().addListener(
                clickListener,
                "MenuClick"
        );
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
                case SETTINGS -> drawSettings(g, width, height);

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
            case "Zürich" -> app.startFreePlay(new SpawnSettings(62160f, -164460f, 600f));
            case "Genf" -> app.startFreePlay(new SpawnSettings(-124060f, -29110f, 600f));
            case "Basel-Mulhouse" -> app.startFreePlay(new SpawnSettings(-15300f, -179730f, 600f));
            case "Bern-Belp" -> app.startFreePlay(new SpawnSettings(-16970f, -103340, 600f));
            case "St. Gallen-Altenrhein" -> app.startFreePlay(new SpawnSettings(138510f, -169020f, 600f));
            case "Lugano-Agno" -> app.startFreePlay(new SpawnSettings(92500f, -3240f, 600f));
            case "Sion" -> app.startFreePlay(new SpawnSettings(-30120f, -26140f, 600f));
            case "Grenchen" -> app.startFreePlay(new SpawnSettings(-23190f, -133100f, 600f));
            case "Payerne" -> app.startFreePlay(new SpawnSettings(-61550f, -95560f, 600f));
            case "Meiringen" -> app.startFreePlay(new SpawnSettings(29740f, -84500f, 600f));
            case "Emmen" -> app.startFreePlay(new SpawnSettings(44320f, -123520f, 600f));
        }
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

    private void drawSettings(Graphics2D g, int width, int height) {
        drawPageHeader(
                g, "Einstellungen",
                "Anzeige und Steuerung", width
        );

        int panelWidth = Math.min(900, width - 80);
        int x = (width - panelWidth) / 2;

        g.setColor(new Color(35, 49, 68));
        g.fillRoundRect(x, 135, panelWidth, 400, 18, 18);

        g.setColor(new Color(110, 190, 240));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString("ANZEIGE", x + 24, 172);

        drawSettingRow(
                g, x + 24, 195, panelWidth - 48,
                "Fenstergröße",
                width + " × " + height
        );

        drawSettingRow(
                g, x + 24, 275, panelWidth - 48,
                "FPS-Anzeige",
                showFps ? "AN" : "AUS"
        );

        // Gesamte FPS-Zeile ist anklickbar.
        fpsBounds.setBounds(x + 24, 275, panelWidth - 48, 64);

        g.setColor(new Color(110, 190, 240));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString("STEUERUNG", x + 24, 380);

        drawSettingRow(
                g, x + 24, 400, panelWidth - 48,
                "Eingabegeräte",
                "Tastatur / Controller"
        );

        g.setColor(new Color(165, 180, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(
                "FPS-Anzeige anklicken zum Umschalten.",
                x + 24, 505
        );
    }

    private void drawSettingRow(Graphics2D g, int x, int y, int width, String label, String value) {
        g.setColor(new Color(44, 61, 82));
        g.fillRoundRect(x, y, width, 64, 12, 12);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString(label, x + 18, y + 39);

        g.setColor(new Color(130, 205, 250));
        g.setFont(new Font("SansSerif", Font.BOLD, 17));

        int textWidth = g.getFontMetrics().stringWidth(value);
        g.drawString(value, x + width - textWidth - 18, y + 39);
    }

    private void drawButton(Graphics2D g, String text, int y, int x, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(text, x+15, y + 45);
    }

    void freePlayMenu(Graphics2D g){
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        g.drawString("Free Play", 80, 120);


        g.setColor(new Color(50, 100, 160));
        g.fillRoundRect(80, 270, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString("Zürich", 115, 315);
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
