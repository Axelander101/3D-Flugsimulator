package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.FlightTraining;
import alxb.flighttraining.game.config.SpawnSettings;

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
    private final DogfightUI dogfightUI = new DogfightUI();
    private final AssetManager menuAssets;
    private int menuWidth, menuHeight;


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

    private static final java.util.Map<String, String> AIRPORT_IMAGE_CODES = java.util.Map.ofEntries(
            java.util.Map.entry("Zürich", "LSZH"),
            java.util.Map.entry("Genf", "LSGG"),
            java.util.Map.entry("Basel-Mulhouse", "LFSB"),
            java.util.Map.entry("Bern-Belp", "LSZB"),
            java.util.Map.entry("St. Gallen-Altenrhein", "LSZR"),
            java.util.Map.entry("Lugano-Agno", "LSZA"),
            java.util.Map.entry("Sion", "LSGS"),
            java.util.Map.entry("Grenchen", "LSZG"),
            java.util.Map.entry("Payerne", "LSMP"),
            java.util.Map.entry("Meiringen", "LSMM"),
            java.util.Map.entry("Emmen", "LSME")
    );

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

    private float displayedAudioVolume=-1;
    private final java.awt.Rectangle audioMinus=new java.awt.Rectangle(),audioPlus=new java.awt.Rectangle(),audioMute=new java.awt.Rectangle();
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
        FREE_PLAY,
        DOGFIGHT_AI
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
        menuAssets = assets;
        menuWidth = width; menuHeight = height;

        for (String airport : AIRPORTS) {
            airportImages.put(
                    airport,
                    MenuImages.load(menuAssets, AIRPORT_IMAGE_CODES.get(airport), airport)
            );
        }

        picture = new Picture("MainMenu");
        picture.setWidth(width);
        picture.setHeight(height);
        picture.setPosition(0, 0);

        redraw();


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

            if(app.getGameAudio()!=null)app.getGameAudio().uiClick();
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
                            redraw();

                        } else if (mouseY >= 270 && mouseY <= 340) {
                            screen = Screen.FREE_PLAY;
                            redraw();

                        } else if (mouseY >= 360 && mouseY <= 430) {
                            screen = Screen.SETTINGS;
                            redraw();

                        } else if (mouseY >= 540 && mouseY <= 610) {
                            screen = Screen.DOGFIGHT_AI;
                            redraw();

                        }else if (mouseY >= 620 && mouseY <= 690){
                            app.stop();
                        }

                    }
                }

                case FREE_PLAY -> {
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.MAIN;
                        redraw();
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
                        redraw();
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
                        redraw();

                    } else if (mouseY >= 270 && mouseY <= 340) {
                        screen = Screen.STEERING_SETTINGS;
                        redraw();

                    } else if (mouseY >= 360 && mouseY <= 430) {
                        screen = Screen.GAMEPLAY_SETTINGS;
                        redraw();
                    }
                    else if (mouseY >= 450 && mouseY <= 520) {
                        screen = Screen.AUDIO_SETTINGS;
                        redraw();
                    }
                    else if (mouseY >= 540 && mouseY <= 610) {
                        screen = Screen.INTERFACE_SETTING;
                        redraw();
                    }
                }


                case GRAPHICS_SETTINGS -> {
                    if (backBounds.contains(mouseX, mouseY)) {
                        screen = Screen.SETTINGS;
                        redraw();
                        return;
                    }
                    if (!graphicsViewport.contains(mouseX, mouseY)) return;
                    for (int i = 0; i < graphicsBounds.size(); i++) {
                        if (graphicsBounds.get(i).contains(mouseX, mouseY)) {
                            System.out.println("Grafikeinstellung: " + GRAPHICS_SETTING[i]);
                            if(i==7){alxb.flighttraining.game.world.WorldDetailSettings.cycleRenderDistance();redraw();}
                            return;
                        }
                    }
                }

                case AUDIO_SETTINGS -> {
                    if(backBounds.contains(mouseX,mouseY))screen=Screen.SETTINGS;
                    else if(app.getGameAudio()!=null){
                        var audio=app.getGameAudio();
                        if(audioMinus.contains(mouseX,mouseY))audio.setVolume(audio.getVolume()-.1f);
                        if(audioPlus.contains(mouseX,mouseY))audio.setVolume(audio.getVolume()+.1f);
                        if(audioMute.contains(mouseX,mouseY))audio.toggleMute();
                    }
                    redraw();
                }
                case STEERING_SETTINGS -> {

                }

                case DOGFIGHT_AI -> {
                    String clicked = dogfightUI.hit(mouseX, mouseY);
                    switch (clicked) {
                        case "back" -> screen = Screen.MAIN;
                        case "countMinus" -> dogfightUI.count = Math.max(1, dogfightUI.count - 1);
                        case "countPlus", "count" -> dogfightUI.count = Math.min(8, dogfightUI.count + 1);
                        case "difficulty" -> dogfightUI.difficultyIndex = (dogfightUI.difficultyIndex + 1) % 3;
                        case "region" -> dogfightUI.regionIndex = (dogfightUI.regionIndex + 1) % DogfightSettings.Region.values().length;
                        case "altitude" -> dogfightUI.altitudeIndex = (dogfightUI.altitudeIndex + 1) % DogfightUI.HEIGHTS.length;
                        case "distance" -> dogfightUI.distanceIndex = (dogfightUI.distanceIndex + 1) % DogfightUI.DISTANCES.length;
                        case "weapons" -> dogfightUI.weapons = !dogfightUI.weapons;
                        case "start" -> { app.startDogfight(dogfightUI.settings()); return; }
                        default -> { return; }
                    }
                    redraw();
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
            scrollOffset = Math.max(0, Math.min(maxGraphicsScroll(menuHeight),
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
                        redraw();
                }
            }
            @Override protected void controlRender(RenderManager rm, ViewPort vp) {}
        });
    }

    private void redraw() {
        drawMenu(app.getCamera().getWidth(), app.getCamera().getHeight(), menuAssets);
    }

    public void update(float tpf) {
        if(menuOffen&&screen==Screen.AUDIO_SETTINGS&&app.getGameAudio()!=null&&displayedAudioVolume!=app.getGameAudio().getVolume())redraw();
        if (menuOffen && (menuWidth != app.getCamera().getWidth() || menuHeight != app.getCamera().getHeight())) redraw();
    }

    private void drawMenu(int width, int height, AssetManager assets) {
        menuWidth = width; menuHeight = height;
        picture.setWidth(width); picture.setHeight(height);
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
                    g.drawString(English.text("Flight Training"), 80, 120);

                    drawButton(g, "Missionen", 180, 80, hellBlau);
                    drawButton(g, "Free Play", 270, 80, hellBlau);
                    drawButton(g, "Einstellungen", 360, 80, hellBlau);
                    drawButton(g, "Multiplayer", 450, 80, hellBlau);
                    drawButton(g, "Dogfight AI", 540, 80, hellBlau);
                    drawButton(g, "Beenden", 630, 80, Color.red);
                }
                case FREE_PLAY -> drawFreePlay(g, width, height);
                case MISSIONS -> drawMissions(g, width, height);
                case SETTINGS -> drawSettings(g);
                case GRAPHICS_SETTINGS -> drawGraphicSetting(g, width, height);
                case AUDIO_SETTINGS -> drawAudioSettings(g,width);
                case DOGFIGHT_AI -> dogfightUI.draw(g, width, height);

            }
        } finally {
            g.dispose();
        }

        Texture2D texture = new Texture2D(
                new AWTLoader().load(image, true)
        );

        com.jme3.texture.Texture old = picture.getMaterial() == null ? null
                : picture.getMaterial().getTextureParam("Texture") == null ? null
                : picture.getMaterial().getTextureParam("Texture").getTextureValue();
        picture.setTexture(assets, texture, true);
        if (old != null && old.getImage() != null) old.getImage().dispose();
    }

    private void drawFreePlay(Graphics2D g, int width, int height) {
        int margin = 40;
        int gap = 16;
        int columns = 3;
        int rows = (AIRPORTS.length + columns - 1) / columns;

        // Kopfbereich
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString(English.text("Free Play"), margin, 65);

        g.setColor(new Color(165, 180, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString(English.text("Wähle deinen Startflugplatz"), margin, 95);

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
            case "Zürich" -> app.startFreePlay(new SpawnSettings(62160f, -164460f, 0f, 0f));
            case "Genf" -> app.startFreePlay(new SpawnSettings(-124060f, -29110f, 0f, 0f));
            case "Basel-Mulhouse" -> app.startFreePlay(new SpawnSettings(-15300f, -179730f, 0f, 0f));
            case "Bern-Belp" -> app.startFreePlay(new SpawnSettings(-16970f, -103340, 0f, 0f));
            case "St. Gallen-Altenrhein" -> app.startFreePlay(new SpawnSettings(138510f, -169020f, 398f, 0f));
            case "Lugano-Agno" -> app.startFreePlay(new SpawnSettings(92500f, -3240f, 0f, 0f));
            case "Sion" -> app.startFreePlay(new SpawnSettings(-30120f, -26140f, 0f, 0f));
            case "Grenchen" -> app.startFreePlay(new SpawnSettings(-23190f, -133100f, 0f, 0f));
            case "Payerne" -> app.startFreePlay(new SpawnSettings(-61550f, -95560f, 0f, 0f));
            case "Meiringen" -> app.startFreePlay(new SpawnSettings(29740f, -84500f, 0f, 0f));
            case "Emmen" -> app.startFreePlay(new SpawnSettings(44320f, -123520f, 0f, 0f));

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
            g.drawString(English.text(MISSIONS[i]), x + 18, y + cardHeight - 58);

            g.setColor(new Color(180, 195, 212));
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString(English.text(
                    MISSION_DESCRIPTIONS[i]),
                    x + 18, y + cardHeight - 30
            );
        }
    }

    private void drawSettings(Graphics2D g) {
        g.drawString(English.text("Flight Training"), 80, 120);

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
                content.drawString(English.text(GRAPHICS_SETTING[i]+(i==7?String.format(java.util.Locale.ROOT,"   %.0f km  ›",alxb.flighttraining.game.world.WorldDetailSettings.renderDistance()/1000f):"")), row.x + 18, row.y + 39);
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




    private void drawAudioSettings(Graphics2D g,int width){
        drawPageHeader(g,"Audio","Flight effects, cockpit warnings and airport proximity radio",width);
        float volume=app.getGameAudio()==null?.65f:app.getGameAudio().getVolume();
        displayedAudioVolume=volume;
        g.setFont(new Font("SansSerif",Font.BOLD,28));g.setColor(Color.WHITE);g.drawString("Master volume: "+Math.round(volume*100)+" %",60,185);
        audioMinus.setBounds(60,215,100,60);audioPlus.setBounds(180,215,100,60);audioMute.setBounds(300,215,190,60);
        for(java.awt.Rectangle r:new java.awt.Rectangle[]{audioMinus,audioPlus,audioMute}){g.setColor(new Color(50,100,160));g.fillRoundRect(r.x,r.y,r.width,r.height,12,12);}
        g.setColor(Color.WHITE);drawCenteredText(g,"−",audioMinus,0);drawCenteredText(g,"+",audioPlus,0);drawCenteredText(g,volume>0?"Mute":"Unmute",audioMute,0);
        g.setFont(new Font("SansSerif",Font.PLAIN,19));g.drawString("F8: mute   F9: quieter   F10: louder",60,325);
        g.drawString("Safety warnings take priority over radio messages.",60,370);
        g.drawString("Airport radio announces proximity only, not airspace clearance.",60,405);
    }

    private void drawSettingsBox(Graphics2D g, String text, int y, int x, Color color){
        g.setColor(color);
        g.fillRoundRect(x, y, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(English.text(text), x+15, y + 45);
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
        g.drawString(English.text(text), x+15, y + 45);
    }

    private void drawButton(Graphics2D g, String text, int y, int x, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, 300, 70, 16, 16);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString(English.text(text), x+15, y + 45);
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
        g.drawString(English.text(name), x + 16, y + h - 17);
    }

    private void drawCenteredText(Graphics2D g, String text, java.awt.Rectangle bounds, int offsetY) {
        java.awt.FontMetrics metrics = g.getFontMetrics();

        int x = bounds.x
                + (bounds.width - metrics.stringWidth(English.text(text))) / 2;

        int y = bounds.y
                + (bounds.height - metrics.getHeight()) / 2
                + metrics.getAscent()
                + offsetY;

        g.drawString(English.text(text), x, y);
    }

    private void drawPageHeader(Graphics2D g, String title, String subtitle, int width) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString(English.text(title), 40, 65);

        g.setColor(new Color(165, 180, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString(English.text(subtitle), 40, 95);

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
        redraw();
    }

    public void hide() {
        picture.removeFromParent();
        menuOffen = false;
    }

    public boolean getMenuOffen(){
        return menuOffen;
    }

    private static final class DogfightUI {
        private static final Color WHITE = new Color(232, 240, 250);
        private static final Color MUTED = new Color(144, 163, 187);
        private static final Color ACCENT = new Color(71, 205, 188);
        // Bildschirmkoordinaten der Felder für deine spätere Klickbehandlung.
        private final java.util.Map<String, Rectangle> bounds = new java.util.LinkedHashMap<>();
        private int count=1, difficultyIndex=1, regionIndex=0, altitudeIndex=3, distanceIndex=2;
        private boolean weapons=true;
        private static final float[] HEIGHTS = {600,1000,2000,3000,4000,6000};
        private static final float[] DISTANCES = {1000,3000,5000,8000,10000};
        private DogfightSettings settings() {
            return new DogfightSettings(DogfightSettings.Region.values()[regionIndex],
                    DogfightSettings.Difficulty.values()[difficultyIndex], count,
                    HEIGHTS[altitudeIndex], DISTANCES[distanceIndex], weapons);
        }
        private String hit(float x,float y) {
            for (java.util.Map.Entry<String,Rectangle> e: bounds.entrySet())
                if(e.getValue().contains(x,y)) return e.getKey();
            return "";
        }
        private float scale;
        private int offsetX, offsetY;

        public void draw(Graphics2D original, int width, int height) {
            Graphics2D g = (Graphics2D) original.create();
            try {
                bounds.clear();
                scale = Math.min(width / 1280f, height / 800f);
                offsetX = Math.round((width - 1280 * scale) / 2);
                offsetY = Math.round((height - 800 * scale) / 2);
                g.setPaint(new GradientPaint(0, 0, new Color(12, 21, 35), width, height, new Color(24, 42, 61)));
                g.fillRect(0, 0, width, height);
                g.translate(offsetX, offsetY);
                g.scale(scale, scale);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                text(g, "FLIGHT TRAINING   /   EINZELSPIELER", 48, 44, 12, ACCENT, true);
                text(g, "Dogfight", 48, 100, 44, WHITE, true);
                text(g, "Dein Flugzeug. Dein Gegner. Dein Trainingsszenario.", 50, 130, 16, MUTED, false);
                button(g, "back", "Zurück", 1092, 56, 140, 44, false);

                panel(g, 48, 164, 440, 412);
                text(g, "DEIN FLUGZEUG", 72, 199, 12, ACCENT, true);
                text(g, "F/A-18 Hornet", 72, 267, 32, WHITE, true);
                text(g, "Mehrzweck-Kampfflugzeug", 74, 301, 16, MUTED, false);
                g.setColor(new Color(47, 65, 85));
                g.drawLine(72, 330, 462, 330);
                text(g, "STARTREGION", 74, 371, 12, ACCENT, true);
                text(g, DogfightSettings.Region.values()[regionIndex].label, 74, 401, 20, WHITE, true);
                text(g, "HÖHE ÜBER GELÄNDE", 74, 451, 12, ACCENT, true);
                text(g, String.format(java.util.Locale.ROOT, "%.0f m", HEIGHTS[altitudeIndex]), 74, 481, 22, WHITE, true);
                text(g, "Frontalstart gegen KI-Gegner", 74, 542, 15, MUTED, false);

                panel(g, 512, 164, 720, 220);
                text(g, "01   GEGNER", 540, 199, 13, ACCENT, true);
                label(g, "Anzahl", 540, 236);
                button(g, "countMinus", "−", 540, 252, 42, 46, false);
                button(g, "count", Integer.toString(count), 590, 252, 60, 46, false);
                button(g, "countPlus", "+", 658, 252, 42, 46, false);
                label(g, "Flugzeug", 726, 236);
                text(g, "F/A-18 Hornet", 726, 282, 18, WHITE, true);
                label(g, "Schwierigkeit", 978, 236);
                button(g, "difficulty", DogfightSettings.Difficulty.values()[difficultyIndex].label + "  ›", 978, 252, 226, 46, false);
                text(g, "Klicken: nächster Wert · 1 bis 8 Gegner · Frontalstart", 540, 345, 14, MUTED, false);

                panel(g, 512, 404, 720, 274);
                text(g, "02   STARTBEDINGUNGEN", 540, 440, 13, ACCENT, true);
                field(g, "region", "Region", DogfightSettings.Region.values()[regionIndex].label + "  ›", 540, 474, 316);
                field(g, "altitude", "Höhe über Boden", String.format(java.util.Locale.ROOT, "%.0f m  ›", HEIGHTS[altitudeIndex]), 880, 474, 324);
                field(g, "distance", "Anfangsabstand", String.format(java.util.Locale.ROOT, "%.0f km  ›", DISTANCES[distanceIndex]/1000f), 540, 567, 316);
                field(g, "weapons", "Bewaffnung", weapons ? "Bordkanone  ›" : "Unbewaffnet  ›", 880, 567, 324);

                panel(g, 48, 596, 440, 82);
                text(g, "TRAININGSSZENARIO", 72, 626, 12, ACCENT, true);
                text(g, count + " Gegner · " + DogfightSettings.Difficulty.values()[difficultyIndex].label, 72, 653, 15, WHITE, false);
                text(g, "BEREIT ZUM START", 50, 728, 11, MUTED, true);
                text(g, "Starthöhe über Gelände · Terrain lädt vor dem Flugstart.", 50, 752, 14, MUTED, false);
                button(g, "start", "DOGFIGHT STARTEN   →", 912, 706, 320, 58, true);
            } finally {
                g.dispose();
            }
        }

        private void field(Graphics2D g, String id, String title, String value, int x, int y, int w) {
            label(g, title, x, y);
            button(g, id, value, x, y + 15, w, 46, false);
        }
        private void label(Graphics2D g, String value, int x, int y) {
            text(g, value, x, y, 14, MUTED, false);
        }
        private void panel(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(new Color(25, 39, 57));
            g.fillRoundRect(x, y, w, h, 20, 20);
            g.setColor(new Color(47, 65, 85));
            g.drawRoundRect(x, y, w, h, 20, 20);
        }
        private void button(Graphics2D g, String id, String value, int x, int y, int w, int h, boolean primary) {
            bounds.put(id, new Rectangle(offsetX + Math.round(x * scale), offsetY + Math.round(y * scale),
                    Math.round(w * scale), Math.round(h * scale)));
            g.setColor(primary ? ACCENT : new Color(35, 53, 74));
            g.fillRoundRect(x, y, w, h, 12, 12);
            g.setColor(primary ? ACCENT : new Color(63, 83, 106));
            g.drawRoundRect(x, y, w, h, 12, 12);
            g.setFont(new Font("SansSerif", Font.BOLD, value.length() > 22 ? 13 : 15));
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(primary ? new Color(12, 35, 40) : WHITE);
            g.drawString(English.text(value), x + (w - metrics.stringWidth(English.text(value))) / 2,
                    y + (h - metrics.getHeight()) / 2 + metrics.getAscent());
        }
        private void text(Graphics2D g, String value, int x, int y, int size, Color color, boolean bold) {
            g.setColor(color);
            g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
            g.drawString(English.text(value), x, y);
        }
    }
}