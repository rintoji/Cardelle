import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MediaManager {

    public record SlideItem(File imageFile, Color solidColor) {
        public boolean isColorCard() {
            return imageFile == null && solidColor != null;
        }

        public String displayName() {
            if (imageFile != null) {
                return imageFile.getName();
            }
            if (solidColor != null) {
                return String.format(Locale.ROOT, "#%02X%02X%02X",
                        solidColor.getRed(), solidColor.getGreen(), solidColor.getBlue());
            }
            return "Empty Card";
        }
    }

    @FunctionalInterface
    public interface MediaChangeListener {
        void onMediaChanged(MediaManager manager);
    }

    private static final double GOLDEN_ANGLE = 0.6180339887498949;

    private final List<SlideItem> slides = new ArrayList<>();
    private final List<MediaChangeListener> listeners = new ArrayList<>();
    private final Random random = new Random();
    private double hueCursor = random.nextDouble();
    private File mp3File;

    public void addChangeListener(MediaChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public List<SlideItem> getSlides() {
        return Collections.unmodifiableList(slides);
    }

    public File getMp3File() {
        return mp3File;
    }

    public boolean hasAudio() {
        return mp3File != null && mp3File.exists() && mp3File.isFile();
    }

    public boolean hasMedia() {
        return !slides.isEmpty() && hasAudio();
    }

    /**
     * Picks a color well separated from existing/neighboring colors so you
     * don't get repetitive pinks or close shades next to each other.
     */
    public Color addRandomColorCard() {
        Color best = null;
        double bestMinDist = -1.0;

        for (int attempt = 0; attempt < 24; attempt++) {
            hueCursor = (hueCursor + GOLDEN_ANGLE + (random.nextDouble() - 0.5) * 0.08) % 1.0;
            if (hueCursor < 0) {
                hueCursor += 1.0;
            }
            float sat = 0.72f + random.nextFloat() * 0.22f;
            float val = 0.72f + random.nextFloat() * 0.22f;
            Color candidate = Color.getHSBColor((float) hueCursor, sat, val);

            double minDist = minColorDistance(candidate);
            if (minDist >= 155.0) {
                best = candidate;
                break;
            }
            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                best = candidate;
            }
        }

        Color chosen = (best != null) ? best : Color.getHSBColor((float) hueCursor, 0.8f, 0.85f);
        slides.add(new SlideItem(null, chosen));
        notifyListeners();
        return chosen;
    }

    private double minColorDistance(Color candidate) {
        double min = Double.MAX_VALUE;
        int checkCount = Math.min(slides.size(), 6);
        for (int i = slides.size() - 1; i >= slides.size() - checkCount; i--) {
            SlideItem item = slides.get(i);
            if (!item.isColorCard()) {
                continue;
            }
            Color existing = item.solidColor();
            double dr = candidate.getRed() - existing.getRed();
            double dg = candidate.getGreen() - existing.getGreen();
            double db = candidate.getBlue() - existing.getBlue();
            double dist = Math.sqrt(dr * dr + dg * dg + db * db);
            if (dist < min) {
                min = dist;
            }
        }
        return min;
    }

    public boolean removeSlideAt(int index) {
        if (index < 0 || index >= slides.size()) {
            return false;
        }
        slides.remove(index);
        notifyListeners();
        return true;
    }

    public boolean moveSlide(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= slides.size() || toIndex < 0 || toIndex >= slides.size()) {
            return false;
        }
        if (fromIndex == toIndex) {
            return false;
        }
        SlideItem moving = slides.remove(fromIndex);
        slides.add(toIndex, moving);
        notifyListeners();
        return true;
    }

    public void resetAll() {
        slides.clear();
        mp3File = null;
        notifyListeners();
    }

    public String acceptFiles(List<File> droppedFiles) {
        if (droppedFiles == null || droppedFiles.isEmpty()) {
            return "No valid media files dropped.";
        }

        int addedImages = 0;
        File updatedMp3 = null;
        int ignored = 0;

        for (File file : droppedFiles) {
            if (file == null || !file.exists() || !file.isFile()) {
                if (file != null) {
                    System.err.println("[Cardelle] Missing/not a file: " + file.getAbsolutePath());
                }
                ignored++;
                continue;
            }

            String name = file.getName().toLowerCase(Locale.ROOT);
            if (isImageFile(name)) {
                slides.add(new SlideItem(file, null));
                addedImages++;
            } else if (isMp3File(name)) {
                mp3File = file;
                updatedMp3 = file;
            } else {
                System.err.println("[Cardelle] Unsupported extension: " + file.getName());
                ignored++;
            }
        }

        if (addedImages > 0 || updatedMp3 != null) {
            notifyListeners();
        }

        return buildSummary(addedImages, updatedMp3, ignored);
    }

    private void notifyListeners() {
        for (MediaChangeListener listener : listeners) {
            listener.onMediaChanged(this);
        }
    }

    private String buildSummary(int addedImages, File updatedMp3, int ignored) {
        List<String> parts = new ArrayList<>();
        if (addedImages > 0) {
            parts.add("+" + addedImages + " image(s) (slides: " + slides.size() + ")");
        }
        if (updatedMp3 != null) {
            parts.add("MP3: " + updatedMp3.getName());
        }
        if (parts.isEmpty()) {
            return ignored > 0
                    ? "Unsupported file(s). Drop images or .mp3 (or Ctrl+N for color card)."
                    : "Drop images & .mp3, or press Ctrl+N for color card.";
        }
        if (hasMedia()) {
            parts.add("Ready (Ctrl+E to render)");
        } else if (slides.isEmpty()) {
            parts.add("Need image/color card (Ctrl+N)");
        } else {
            parts.add("Need .mp3");
        }
        if (ignored > 0) {
            parts.add(ignored + " skipped");
        }
        return String.join(" • ", parts);
    }

    private boolean isImageFile(String lowerName) {
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".gif");
    }

    private boolean isMp3File(String lowerName) {
        return lowerName.endsWith(".mp3");
    }
}