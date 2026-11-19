package alxb.flighttraining.game.ui;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetInfo;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/** Optional menu photographs: ASCII asset keys, compatibility with older Unicode names. */
final class MenuImages {
    private static final Logger LOG = Logger.getLogger(MenuImages.class.getName());
    private MenuImages() {}

    static BufferedImage load(AssetManager assets, String code, String displayName) {
        Set<String> paths = new LinkedHashSet<>();
        if (code != null) paths.add("Airports/" + code + ".png");
        String legacy = "Airports/" + displayName + ".png";
        paths.add(Normalizer.normalize(legacy, Normalizer.Form.NFC));
        paths.add(Normalizer.normalize(legacy, Normalizer.Form.NFD));
        for (String path : paths) {
            try {
                AssetInfo info = assets == null ? null : assets.locateAsset(new AssetKey<>(path));
                try (InputStream stream = info == null
                        ? MenuImages.class.getResourceAsStream("/" + path) : info.openStream()) {
                    if (stream == null) continue;
                    BufferedImage image = ImageIO.read(stream);
                    if (image != null) return image;
                    LOG.warning("Vorschaubild hat kein lesbares Bildformat: " + path
                            + "; das Menü verwendet einen Platzhalter.");
                    return null;
                }
            } catch (IOException | RuntimeException ex) {
                LOG.warning("Vorschaubild konnte nicht gelesen werden: " + path + " ("
                        + ex.getMessage() + "); das Menü verwendet einen Platzhalter.");
                return null;
            }
        }
        LOG.warning("Kein Vorschaubild für " + displayName + " gefunden (" + paths
                + "); das Menü verwendet einen Platzhalter.");
        return null;
    }
}
