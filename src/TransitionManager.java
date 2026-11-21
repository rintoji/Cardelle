import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class TransitionManager {

    public enum EditorTransition {
        SLIDE("Slide", "slideleft"),
        SHAPE_SLIDE("Shape Slide", "smoothleft"),
        PUSH("Push", "slideup"),
        WIPE("Wipe", "wipeleft"),
        DIAGONAL_WIPE("Diagonal Wipe", "diagtl"),
        CIRCLE_REVEAL("Circle Reveal", "circleopen"),
        BOX_REVEAL("Box Reveal", "rectcrop"),
        SPLIT("Split", "squeezeh"),
        ZOOM("Zoom", "zoomin"),
        CROSS_DISSOLVE("Cross Dissolve", "dissolve"),
        BLINDS("Blinds", "hlslice"),
        STRIP_REVEAL("Strip Reveal", "hrslice"),
        VENETIAN_BLINDS("Venetian Blinds", "vuslice"),
        DIAGONAL_SPLIT("Diagonal Split", "diagbr"),
        CORNER_REVEAL("Corner Reveal", "diagtr"),
        SQUARE_REVEAL("Square Reveal", "rectcrop"),
        DIAMOND_REVEAL("Diamond Reveal", "circleclose"),
        RADIAL_WIPE("Radial Wipe", "radial"),
        ZOOM_BLUR("Zoom Blur", "smoothright"),
        SHUTTER("Shutter", "squeezev"),
        MOSAIC_REVEAL("Mosaic Reveal", "pixelize"),
        CURTAIN("Curtain", "wiperight"),
        UPWARD_REVEAL("Upward Reveal", "wipeup"),
        FLIP("Flip", "smoothdown"),
        LINE_REVEAL("Line Reveal", "wipedown"),
        STRIPE_REVEAL("Stripe Reveal", "vdslice");

        private final String label;
        private final String ffmpegXfade;

        EditorTransition(String label, String ffmpegXfade) {
            this.label = label;
            this.ffmpegXfade = ffmpegXfade;
        }

        public String getLabel() {
            return label;
        }

        public String getFfmpegXfade() {
            return ffmpegXfade;
        }

        public EditorTransition next() {
            EditorTransition[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    private final Random random = new Random();
    private EditorTransition lastTransition = null;
    private File customLogoFile;
    private int width = 1280;
    private int height = 720;
    private int fps = 60;
    private double transitionDuration = 0.45;

    public void setCustomLogo(File file) {
        this.customLogoFile = (file != null && file.isFile()) ? file : null;
    }

    public File getCustomLogo() {
        return customLogoFile;
    }

    public void resetHistory() {
        lastTransition = null;
    }

    public EditorTransition nextRandomTransition() {
        EditorTransition[] all = EditorTransition.values();
        if (all.length == 1) {
            return all[0];
        }
        EditorTransition chosen = all[random.nextInt(all.length)];
        int tries = 0;
        while (tries < 12 && chosen == lastTransition) {
            chosen = all[random.nextInt(all.length)];
            tries++;
        }
        if (chosen == lastTransition) {
            chosen = lastTransition.next();
        }
        lastTransition = chosen;
        return chosen;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFps() {
        return fps;
    }

    public double getTransitionDuration() {
        return transitionDuration;
    }

    public String buildImageFilterGraph() {
        return String.format(
                Locale.ROOT,
                "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black,setsar=1,fps=%d",
                width, height, width, height, fps
        );
    }

    public String buildColorCardFilterGraph(Color baseColor, double durationSeconds) {
        int r = baseColor.getRed();
        int g = baseColor.getGreen();
        int b = baseColor.getBlue();

        int rDark = Math.max(0, (int) (r * 0.62));
        int gDark = Math.max(0, (int) (g * 0.62));
        int bDark = Math.max(0, (int) (b * 0.62));

        int rLight = Math.min(255, r + 48);
        int gLight = Math.min(255, g + 48);
        int bLight = Math.min(255, b + 48);

        String botHex = String.format(Locale.ROOT, "0x%02X%02X%02X", rDark, gDark, bDark);
        String topHex = String.format(Locale.ROOT, "0x%02X%02X%02X", rLight, gLight, bLight);

        return String.format(
                Locale.ROOT,
                "color=c=%s:s=%dx%d:r=%d:d=%.3f[bot];"
                        + "color=c=%s:s=%dx%d:r=%d:d=%.3f,format=rgba,"
                        + "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='255*(1.0 - Y/%.1f)'[top];"
                        + "[bot][top]overlay=0:0,setsar=1",
                botHex, width, height, fps, durationSeconds,
                topHex, width, height, fps, durationSeconds,
                (double) height
        );
    }

    public String buildFullTrackBlurFilter() {
        return "boxblur=36:5,setsar=1";
    }

    public File writeCardelleLogoPng(Path workDir) throws IOException {
        File out = workDir.resolve("Cardelle_logo.png").toFile();

        if (customLogoFile != null && customLogoFile.isFile()) {
            BufferedImage custom = ImageIO.read(customLogoFile);
            if (custom != null) {
                ImageIO.write(custom, "PNG", out);
                return out;
            }
        }

        try (var in = TransitionManager.class.getResourceAsStream("/Cardelle_logo.png")) {
            if (in != null) {
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return out;
            }
        }

        int badgeW = 220;
        int badgeH = 56;
        BufferedImage img = new BufferedImage(badgeW, badgeH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        RoundRectangle2D pill = new RoundRectangle2D.Double(2, 2, badgeW - 4, badgeH - 4, 20, 20);
        g2.setPaint(new GradientPaint(0, 0, new Color(20, 22, 28), badgeW, badgeH, new Color(36, 40, 52)));
        g2.fill(pill);

        g2.setColor(new Color(255, 255, 255, 55));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(pill);

        Graphics2D iconG = (Graphics2D) g2.create();
        iconG.translate(28, badgeH / 2.0);
        iconG.rotate(Math.toRadians(-10));
        iconG.setColor(new Color(245, 247, 255));
        iconG.fillRoundRect(-9, -12, 18, 24, 4, 4);
        iconG.setColor(new Color(45, 95, 195));
        iconG.fillRoundRect(-6, -9, 12, 18, 2, 2);
        iconG.dispose();

        g2.setColor(new Color(248, 250, 255));
        Font font = new Font("SansSerif", Font.BOLD, 17);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics(font);
        g2.drawString("CARDELLE", 48, (badgeH - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();

        ImageIO.write(img, "PNG", out);
        return out;
    }

    public String describeTransitions(List<EditorTransition> list) {
        if (list == null || list.isEmpty()) {
            return "Random (26 transitions)";
        }
        int shown = Math.min(list.size(), 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(list.get(i).getLabel());
        }
        if (list.size() > shown) {
            sb.append(" (+").append(list.size() - shown).append(")");
        }
        return sb.toString();
    }
}