import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

/**
 * Small in-panel settings surface. It deliberately reuses the translucent dark
 * look of the help overlay so the two feel like the same part of the app.
 */
public class CustomizeOverlay extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] ROW_NAMES = {"Main panel", "Card panel", "Accent"};
    private static final Color[] DEFAULTS = {
            AppSettings.DEFAULT_MAIN,
            AppSettings.DEFAULT_CARD,
            AppSettings.DEFAULT_ACCENT
    };

    private final Consumer<Color> mainSetter;
    private final Consumer<Color> cardSetter;
    private final Consumer<Color> accentSetter;
    private final AppSettings.State settings;
    private final TransitionManager transitionManager;
    private final JLabel statusLabel;
    private final Consumer<Boolean> logoModeSetter;
    private final Runnable saveSettings;

    private final Color[] current = new Color[3];
    private final JButton[] swatches = new JButton[3];
    private final JLabel logoName;

    CustomizeOverlay(Consumer<Color> mainSetter,
                     Consumer<Color> cardSetter,
                     Consumer<Color> accentSetter,
                     AppSettings.State settings,
                     TransitionManager transitionManager,
                     JLabel statusLabel,
                     Consumer<Boolean> logoModeSetter,
                     Runnable saveSettings) {
        this.mainSetter = mainSetter;
        this.cardSetter = cardSetter;
        this.accentSetter = accentSetter;
        this.settings = settings;
        this.transitionManager = transitionManager;
        this.statusLabel = statusLabel;
        this.logoModeSetter = logoModeSetter;
        this.saveSettings = saveSettings;

        current[0] = settings.main;
        current[1] = settings.card;
        current[2] = settings.accent;

        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setVisible(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("CARDELLE  /  CUSTOMIZE");
        title.setFont(new Font("Monospaced", Font.BOLD, 12));
        title.setForeground(new Color(235, 238, 245));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        add(title, c);
        c.gridwidth = 1;

        for (int i = 0; i < ROW_NAMES.length; i++) {
            JLabel name = new JLabel(ROW_NAMES[i]);
            name.setFont(new Font("Arial", Font.PLAIN, 11));
            name.setForeground(new Color(225, 230, 240));
            c.gridx = 0;
            c.gridy = i + 1;
            add(name, c);

            swatches[i] = new JButton();
            swatches[i].setPreferredSize(new Dimension(90, 22));
            swatches[i].setOpaque(true);
            swatches[i].setBackground(current[i]);
            swatches[i].setFocusPainted(false);
            swatches[i].setBorder(BorderFactory.createLineBorder(new Color(110, 120, 145)));
            final int index = i;
            swatches[i].addActionListener(e -> pickColor(index));
            c.gridx = 1;
            add(swatches[i], c);

            JButton reset = smallButton("reset");
            reset.addActionListener(e -> setColor(index, DEFAULTS[index]));
            c.gridx = 2;
            add(reset, c);
        }

        JLabel logoLabel = new JLabel("Logo");
        logoLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        logoLabel.setForeground(new Color(225, 230, 240));
        c.gridx = 0;
        c.gridy = 4;
        add(logoLabel, c);

        logoName = new JLabel();
        logoName.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logoName.setForeground(new Color(180, 190, 210));
        c.gridx = 1;
        add(logoName, c);

        JButton choose = smallButton("Choose...");
        choose.addActionListener(e -> chooseLogo());
        c.gridx = 2;
        add(choose, c);

        JButton resetLogo = smallButton("Reset logo");
        resetLogo.addActionListener(e -> resetLogo());
        c.gridx = 1;
        c.gridy = 5;
        add(resetLogo, c);

        JButton resetAll = smallButton("Reset all");
        resetAll.addActionListener(e -> resetAll());
        c.gridx = 2;
        add(resetAll, c);

        JLabel hint = new JLabel("Ctrl+P  close  •  changes are saved");
        hint.setFont(new Font("Arial", Font.ITALIC, 10));
        hint.setForeground(new Color(165, 175, 195));
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 3;
        add(hint, c);

        updateLogoName();
    }

    private JButton smallButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Monospaced", Font.PLAIN, 10));
        button.setBackground(new Color(38, 40, 48));
        button.setForeground(new Color(230, 234, 242));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(110, 120, 145)));
        return button;
    }

    private void pickColor(int index) {
        Color chosen = JColorChooser.showDialog(this, "Choose " + ROW_NAMES[index] + " color", current[index]);
        if (chosen != null) {
            setColor(index, chosen);
        }
    }

    private void setColor(int index, Color color) {
        current[index] = color;
        switch (index) {
            case 0 -> {
                settings.main = color;
                mainSetter.accept(color);
            }
            case 1 -> {
                settings.card = color;
                cardSetter.accept(color);
            }
            default -> {
                settings.accent = color;
                accentSetter.accept(color);
            }
        }
        swatches[index].setBackground(color);
        saveSettings.run();
        repaint();
    }

    private void chooseLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a logo image");
        chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            if (ImageIO.read(file) == null) {
                throw new IllegalArgumentException("unsupported image");
            }
        } catch (Exception ex) {
            setStatus("Logo error: not a valid image");
            return;
        }
        settings.logo = file;
        transitionManager.setCustomLogo(file);
        logoModeSetter.accept(true);
        saveSettings.run();
        updateLogoName();
        setStatus("Custom logo: " + file.getName() + " (logo mode ON)");
    }

    private void resetLogo() {
        settings.logo = null;
        transitionManager.setCustomLogo(null);
        logoModeSetter.accept(true);
        saveSettings.run();
        updateLogoName();
        setStatus("Default Cardelle logo restored (logo mode ON)");
    }

    private void resetAll() {
        for (int i = 0; i < DEFAULTS.length; i++) {
            setColor(i, DEFAULTS[i]);
        }
        resetLogo();
    }

    private void updateLogoName() {
        File logo = transitionManager.getCustomLogo();
        logoName.setText(logo == null ? "Cardelle default" : logo.getName());
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    void onCtrlP() {
        setVisible(!isVisible());
        repaint();
    }

    void hideOverlay() {
        setVisible(false);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(24, 24, 28, 235));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.setColor(new Color(110, 120, 145, 220));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }
}
