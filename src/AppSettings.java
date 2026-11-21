import java.awt.Color;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Tiny preference store for the in-app customizations. Preferences are used
 * instead of a config file so the portable builds have somewhere writable to
 * put settings on both Linux and Windows.
 */
final class AppSettings {

    static final Color DEFAULT_MAIN = new Color(211, 211, 211);
    static final Color DEFAULT_CARD = new Color(211, 211, 211);
    static final Color DEFAULT_ACCENT = Color.BLACK;

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    private AppSettings() {
    }

    static final class State {
        Color main = DEFAULT_MAIN;
        Color card = DEFAULT_CARD;
        Color accent = DEFAULT_ACCENT;
        File logo;
        boolean logoEnabled;
    }

    static State load() {
        State state = new State();
        try {
            state.main = readColor("color.main", DEFAULT_MAIN);
            state.card = readColor("color.card", DEFAULT_CARD);
            state.accent = readColor("color.accent", DEFAULT_ACCENT);

            String logoPath = PREFS.get("logo.path", "");
            if (!logoPath.isBlank()) {
                File candidate = new File(logoPath.trim());
                if (candidate.isFile()) {
                    state.logo = candidate;
                }
            }
            state.logoEnabled = PREFS.getBoolean("logo.enabled", false);
        } catch (RuntimeException ex) {
            System.err.println("[Cardelle] Could not load preferences: " + ex.getMessage());
        }
        return state;
    }

    static void save(State state) {
        try {
            writeColor("color.main", state.main);
            writeColor("color.card", state.card);
            writeColor("color.accent", state.accent);

            if (state.logo == null) {
                PREFS.remove("logo.path");
            } else {
                PREFS.put("logo.path", state.logo.getAbsolutePath());
            }
            PREFS.putBoolean("logo.enabled", state.logoEnabled);
        } catch (RuntimeException ex) {
            System.err.println("[Cardelle] Could not save preferences: " + ex.getMessage());
        }
    }

    private static Color readColor(String key, Color fallback) {
        int rgb = PREFS.getInt(key, fallback.getRGB());
        return new Color(rgb);
    }

    private static void writeColor(String key, Color color) {
        if (color == null) {
            PREFS.remove(key);
        } else {
            PREFS.putInt(key, color.getRGB());
        }
    }
}
