import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Visuals {

    private static final double SECONDS_PER_SLIDE = 2.5;
    private static final int HOLD_BEFORE_FADE_MS = 3000;
    private static final int FADE_TICK_MS = 30;
    private static final float FADE_STEP = 0.05f;
    private static final int EDGE_SCROLL_ZONE_PX = 55;
    private static final int EDGE_SCROLL_STEP_PX = 12;
    private static final int REORDER_ANIM_MS = 180;
    private static final int REORDER_ANIM_TICK_MS = 15;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cardelle");

            JLayeredPane layeredPane = new JLayeredPane();

            MediaManager mediaManager = new MediaManager();
            TransitionManager transitionManager = new TransitionManager();
            VideoRenderer videoRenderer = new VideoRenderer(transitionManager);
            AtomicBoolean isRendering = new AtomicBoolean(false);
            AtomicBoolean includeLogo = new AtomicBoolean(false);

            AppSettings.State settings = AppSettings.load();
            includeLogo.set(settings.logoEnabled);
            if (settings.logo != null) {
                transitionManager.setCustomLogo(settings.logo);
            }
            Consumer<Boolean> setLogoMode = enabled -> {
                includeLogo.set(enabled);
                settings.logoEnabled = enabled;
            };
            Runnable saveSettings = () -> AppSettings.save(settings);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(settings.main);

            BlackProgressBar progressBar = new BlackProgressBar();
            progressBar.setColor(settings.accent);

            JLabel statusLabel = new JLabel(
                    "Cardelle • Ctrl+P theme • Ctrl+O open • Ctrl+N color • Ctrl+V paste • Ctrl+L logo:"
                            + (settings.logoEnabled ? "ON" : "OFF")
                            + " • Ctrl+E render • Ctrl+B blur • Ctrl+H help",
                    SwingConstants.CENTER
            );
            statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
            statusLabel.setForeground(settings.accent);
            statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));

            JPanel bottomBar = new JPanel(new BorderLayout());
            bottomBar.setOpaque(false);
            bottomBar.add(progressBar, BorderLayout.NORTH);
            bottomBar.add(statusLabel, BorderLayout.SOUTH);

            DropSystem dropSystem = new DropSystem(mediaManager, statusLabel);

            JPanel cardContainer = new JPanel();
            cardContainer.setLayout(new CenteredCardLayout());
            cardContainer.setBackground(settings.card);

            JScrollPane scrollPane = new JScrollPane(cardContainer);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(40);

            scrollPane.addMouseWheelListener(e -> {
                JScrollBar bar = scrollPane.getHorizontalScrollBar();
                bar.setValue(bar.getValue() + e.getWheelRotation() * 40);
            });

            InPanelHelpOverlay helpOverlay = new InPanelHelpOverlay();
            InPanelNameOverlay nameOverlay = new InPanelNameOverlay();

            layeredPane.add(mainPanel, JLayeredPane.DEFAULT_LAYER);
            layeredPane.add(helpOverlay, JLayeredPane.PALETTE_LAYER);
            layeredPane.add(nameOverlay, JLayeredPane.MODAL_LAYER);

            final Cards[] floatingCard = {null};
            final int[] draggedIndex = {-1};
            final int[] grabOffsetX = {0};
            final int[] grabOffsetY = {0};
            final int[] lastDragScreenX = {0};
            final int[] lastEdgeScrollDelta = {0};
            final Timer[] reorderAnimTimer = {null};

            Runnable positionFloatingAtCurrentMouse = () -> {
                if (draggedIndex[0] < 0 || floatingCard[0] == null || !layeredPane.isShowing()) {
                    return;
                }
                PointerInfo info = MouseInfo.getPointerInfo();
                if (info == null) {
                    return;
                }
                Point p = new Point(info.getLocation());
                SwingUtilities.convertPointFromScreen(p, layeredPane);
                floatingCard[0].setLocation(p.x - grabOffsetX[0], p.y - grabOffsetY[0]);
                floatingCard[0].setDraggingState(true, p.x - lastDragScreenX[0]);
            };

            Timer edgeScrollTimer = new Timer(20, ev -> {
                if (draggedIndex[0] < 0 || floatingCard[0] == null || lastEdgeScrollDelta[0] == 0) {
                    return;
                }
                JScrollBar bar = scrollPane.getHorizontalScrollBar();
                int maxScroll = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
                int before = bar.getValue();
                int after = Math.max(bar.getMinimum(), Math.min(maxScroll, before + lastEdgeScrollDelta[0]));
                if (after != before) {
                    bar.setValue(after);
                    positionFloatingAtCurrentMouse.run();
                    layeredPane.repaint();
                } else {
                    lastEdgeScrollDelta[0] = 0;
                }
            });

            Runnable updateEdgeScrollFromPointer = () -> {
                if (draggedIndex[0] < 0 || floatingCard[0] == null) {
                    lastEdgeScrollDelta[0] = 0;
                    edgeScrollTimer.stop();
                    return;
                }
                PointerInfo info = MouseInfo.getPointerInfo();
                if (info == null || !scrollPane.isShowing()) {
                    lastEdgeScrollDelta[0] = 0;
                    edgeScrollTimer.stop();
                    return;
                }
                Point inScroll = new Point(info.getLocation());
                SwingUtilities.convertPointFromScreen(inScroll, scrollPane);

                JScrollBar bar = scrollPane.getHorizontalScrollBar();
                int maxScroll = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());

                int delta = 0;
                if (inScroll.x >= 0 && inScroll.x < EDGE_SCROLL_ZONE_PX && bar.getValue() > bar.getMinimum()) {
                    delta = -EDGE_SCROLL_STEP_PX;
                } else if (inScroll.x > scrollPane.getWidth() - EDGE_SCROLL_ZONE_PX
                        && inScroll.x <= scrollPane.getWidth()
                        && bar.getValue() < maxScroll) {
                    delta = EDGE_SCROLL_STEP_PX;
                }

                lastEdgeScrollDelta[0] = delta;
                if (delta != 0) {
                    if (!edgeScrollTimer.isRunning()) {
                        edgeScrollTimer.start();
                    }
                } else {
                    edgeScrollTimer.stop();
                }
            };

            Runnable clearFloatingCard = () -> {
                lastEdgeScrollDelta[0] = 0;
                edgeScrollTimer.stop();
                if (floatingCard[0] != null) {
                    layeredPane.remove(floatingCard[0]);
                    floatingCard[0] = null;
                    layeredPane.repaint();
                }
            };

            Runnable animateCardsSmoothly = () -> {
                if (reorderAnimTimer[0] != null && reorderAnimTimer[0].isRunning()) {
                    reorderAnimTimer[0].stop();
                }

                Component[] comps = cardContainer.getComponents();
                if (comps.length <= 1) {
                    return;
                }

                Map<Component, Point> startPositions = new HashMap<>();
                Map<Component, Point> targetPositions = new HashMap<>();

                cardContainer.doLayout();
                for (Component comp : comps) {
                    targetPositions.put(comp, comp.getLocation());
                }

                int shift = 52;
                for (int i = 0; i < comps.length; i++) {
                    Component comp = comps[i];
                    Point target = targetPositions.get(comp);
                    int offset = (i % 2 == 0) ? -shift : shift;
                    Point start = new Point(target.x + offset, target.y);
                    startPositions.put(comp, start);
                    comp.setLocation(start);
                }

                final long startTime = System.nanoTime();
                Timer timer = new Timer(REORDER_ANIM_TICK_MS, null);
                reorderAnimTimer[0] = timer;

                timer.addActionListener(e -> {
                    double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;
                    double rawT = Math.min(1.0, Math.max(0.0, elapsedMs / REORDER_ANIM_MS));
                    double eased = 1.0 - Math.pow(1.0 - rawT, 3.0);

                    for (Component comp : comps) {
                        Point s = startPositions.get(comp);
                        Point t = targetPositions.get(comp);
                        if (s != null && t != null) {
                            int nx = (int) Math.round(s.x + (t.x - s.x) * eased);
                            int ny = (int) Math.round(s.y + (t.y - s.y) * eased);
                            comp.setLocation(nx, ny);
                        }
                    }

                    cardContainer.repaint();
                    if (rawT >= 1.0) {
                        timer.stop();
                        for (Component comp : comps) {
                            Point t = targetPositions.get(comp);
                            if (t != null) {
                                comp.setLocation(t);
                            }
                        }
                        cardContainer.revalidate();
                        cardContainer.repaint();
                    }
                });
                timer.start();
            };

            final CustomizeOverlay[] customizeHolder = new CustomizeOverlay[1];

            Runnable layoutLayers = () -> {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                mainPanel.setBounds(0, 0, w, h);

                Dimension hs = helpOverlay.getPreferredSize();
                helpOverlay.setBounds((w - hs.width) / 2, (h - hs.height) / 2, hs.width, hs.height);

                Dimension ns = nameOverlay.getPreferredSize();
                nameOverlay.setBounds((w - ns.width) / 2, (h - ns.height) / 2, ns.width, ns.height);

                if (customizeHolder[0] != null) {
                    Dimension zs = customizeHolder[0].getPreferredSize();
                    customizeHolder[0].setBounds((w - zs.width) / 2, (h - zs.height) / 2, zs.width, zs.height);
                }

                mainPanel.revalidate();
                cardContainer.revalidate();
                scrollPane.revalidate();
                mainPanel.repaint();
            };

            layeredPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    layoutLayers.run();
                }
            });

            CustomizeOverlay customizeOverlay = new CustomizeOverlay(
                    mainPanel::setBackground,
                    cardContainer::setBackground,
                    color -> {
                        progressBar.setColor(color);
                        statusLabel.setForeground(color);
                    },
                    settings,
                    transitionManager,
                    statusLabel,
                    setLogoMode,
                    saveSettings
            );
            customizeHolder[0] = customizeOverlay;
            layeredPane.add(customizeOverlay, JLayeredPane.PALETTE_LAYER);

            Runnable rebuildDeck = () -> {
                clearFloatingCard.run();
                cardContainer.removeAll();
                List<MediaManager.SlideItem> slides = mediaManager.getSlides();

                if (slides.isEmpty()) {
                    Cards placeholder = new Cards((String) null, true);
                    dropSystem.attachTo(placeholder);
                    cardContainer.add(placeholder);
                } else {
                    for (int i = 0; i < slides.size(); i++) {
                        final int idx = i;
                        MediaManager.SlideItem item = slides.get(i);
                        Cards card = item.isColorCard()
                                ? new Cards(item.solidColor(), true)
                                : new Cards(item.imageFile().getAbsolutePath(), true);

                        card.setCardActionListener(c -> {
                            if (isRendering.get()) {
                                return;
                            }
                            if (mediaManager.removeSlideAt(idx)) {
                                statusLabel.setText("Removed card #" + (idx + 1)
                                        + " (remaining: " + mediaManager.getSlides().size() + ")");
                            }
                        });

                        card.setCardDragListener(new Cards.CardDragListener() {
                            @Override
                            public void onDragStart(Cards c, MouseEvent e) {
                                if (isRendering.get() || mediaManager.getSlides().size() <= 1) {
                                    return;
                                }
                                if (reorderAnimTimer[0] != null && reorderAnimTimer[0].isRunning()) {
                                    reorderAnimTimer[0].stop();
                                }
                                clearFloatingCard.run();
                                draggedIndex[0] = idx;
                                lastDragScreenX[0] = e.getXOnScreen();

                                Cards floater = item.isColorCard()
                                        ? new Cards(item.solidColor(), false)
                                        : new Cards(item.imageFile().getAbsolutePath(), false);

                                Dimension fullSize = c.getPreferredSize();
                                floater.setSize(fullSize);
                                floater.setPreferredSize(fullSize);
                                floater.setDraggingState(true, 0);

                                Point cardTopLeftInLayer = SwingUtilities.convertPoint(c, 0, 0, layeredPane);
                                Point pointerInLayer = SwingUtilities.convertPoint(c, e.getPoint(), layeredPane);
                                grabOffsetX[0] = pointerInLayer.x - cardTopLeftInLayer.x;
                                grabOffsetY[0] = pointerInLayer.y - cardTopLeftInLayer.y;

                                floater.setBounds(
                                        pointerInLayer.x - grabOffsetX[0],
                                        pointerInLayer.y - grabOffsetY[0],
                                        fullSize.width,
                                        fullSize.height
                                );

                                floatingCard[0] = floater;
                                c.setVisible(false);
                                layeredPane.add(floater, JLayeredPane.DRAG_LAYER);
                                layeredPane.repaint();
                                updateEdgeScrollFromPointer.run();
                            }

                            @Override
                            public void onDragMove(Cards c, MouseEvent e) {
                                if (draggedIndex[0] < 0 || floatingCard[0] == null) {
                                    return;
                                }
                                Point pointerInLayer = SwingUtilities.convertPoint(c, e.getPoint(), layeredPane);
                                int dxFromPress = e.getXOnScreen() - lastDragScreenX[0];
                                floatingCard[0].setLocation(
                                        pointerInLayer.x - grabOffsetX[0],
                                        pointerInLayer.y - grabOffsetY[0]
                                );
                                floatingCard[0].setDraggingState(true, dxFromPress);
                                updateEdgeScrollFromPointer.run();
                                layeredPane.repaint();
                            }

                            @Override
                            public void onDragEnd(Cards c, MouseEvent e) {
                                int from = draggedIndex[0];
                                Cards floater = floatingCard[0];
                                draggedIndex[0] = -1;

                                Point dropInContainer;
                                if (floater != null) {
                                    Point center = new Point(
                                            floater.getX() + floater.getWidth() / 2,
                                            floater.getY() + floater.getHeight() / 2
                                    );
                                    dropInContainer = SwingUtilities.convertPoint(layeredPane, center, cardContainer);
                                } else {
                                    dropInContainer = SwingUtilities.convertPoint(c, e.getPoint(), cardContainer);
                                }

                                clearFloatingCard.run();
                                c.setDraggingState(false, 0);
                                c.setVisible(true);

                                if (from >= 0 && !isRendering.get()) {
                                    int targetIndex = computeTargetSlot(cardContainer, dropInContainer.x, from);
                                    if (mediaManager.moveSlide(from, targetIndex)) {
                                        statusLabel.setText("Moved card " + (from + 1) + " -> " + (targetIndex + 1));
                                        SwingUtilities.invokeLater(animateCardsSmoothly);
                                    }
                                }
                            }
                        });

                        dropSystem.attachTo(card);
                        cardContainer.add(card);
                    }
                }

                cardContainer.revalidate();
                scrollPane.revalidate();
                cardContainer.repaint();
            };

            mediaManager.addChangeListener(manager -> SwingUtilities.invokeLater(rebuildDeck));
            rebuildDeck.run();

            mainPanel.add(scrollPane, BorderLayout.CENTER);
            mainPanel.add(bottomBar, BorderLayout.SOUTH);

            JRootPane rootPane = frame.getRootPane();
            InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = rootPane.getActionMap();

            inputMap.put(KeyStroke.getKeyStroke("control H"), "toggleHelp");
            actionMap.put("toggleHelp", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    customizeOverlay.hideOverlay();
                    helpOverlay.onCtrlH();
                    layoutLayers.run();
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control P"), "toggleCustomize");
            actionMap.put("toggleCustomize", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    helpOverlay.dismiss();
                    customizeOverlay.onCtrlP();
                    layoutLayers.run();
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control N"), "addColorCard");
            actionMap.put("addColorCard", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    Color c = mediaManager.addRandomColorCard();
                    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                    statusLabel.setText("Added unique color " + hex + " (" + mediaManager.getSlides().size() + ")");
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control V"), "pasteClipboardFiles");
            actionMap.put("pasteClipboardFiles", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    List<File> pastedFiles = extractFilesFromClipboard();
                    String summary = mediaManager.acceptFiles(pastedFiles);
                    statusLabel.setText(summary);
                }
            });

            // Ctrl+O -> file picker fallback when drag & drop / clipboard is blocked by Windows
            inputMap.put(KeyStroke.getKeyStroke("control O"), "openFiles");
            actionMap.put("openFiles", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Open images and an MP3");
                    chooser.setMultiSelectionEnabled(true);
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    int result = chooser.showOpenDialog(frame);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        statusLabel.setText(mediaManager.acceptFiles(List.of(chooser.getSelectedFiles())));
                    }
                }
            });

            // Ctrl+L -> Toggle Cardelle logo watermark on/off for renders
            inputMap.put(KeyStroke.getKeyStroke("control L"), "toggleLogoMode");
            actionMap.put("toggleLogoMode", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    boolean enabled = !includeLogo.get();
                    setLogoMode.accept(enabled);
                    saveSettings.run();
                    statusLabel.setText("Cardelle logo mode: " + (enabled ? "ON (will include logo)" : "OFF"));
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control X"), "resetAll");
            actionMap.put("resetAll", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isRendering.get() || nameOverlay.isVisible()) {
                        return;
                    }
                    mediaManager.resetAll();
                    transitionManager.resetHistory();
                    progressBar.setProgress(0.0);
                    statusLabel.setText("Reset • Ctrl+N color card • Ctrl+L logo:"
                            + (includeLogo.get() ? "ON" : "OFF")
                            + (settings.logo == null ? " (default)" : " (custom)")
                            + " • Ctrl+E render");
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control E"), "renderNormal");
            actionMap.put("renderNormal", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    triggerRenderWithOptions(
                            isRendering, mediaManager, videoRenderer,
                            nameOverlay, layoutLayers, statusLabel, progressBar,
                            false, includeLogo.get()
                    );
                }
            });

            inputMap.put(KeyStroke.getKeyStroke("control B"), "renderTopBlur");
            actionMap.put("renderTopBlur", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    triggerRenderWithOptions(
                            isRendering, mediaManager, videoRenderer,
                            nameOverlay, layoutLayers, statusLabel, progressBar,
                            true, includeLogo.get()
                    );
                }
            });

            frame.setContentPane(layeredPane);
            frame.setResizable(true);
            frame.setMinimumSize(new Dimension(520, 380));
            frame.setSize(900, 560);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);

            dropSystem.attachTo(mainPanel);
            dropSystem.attachTo(cardContainer);

            frame.setVisible(true);
            layoutLayers.run();
            SwingUtilities.invokeLater(() -> {
                layoutLayers.run();
                rebuildDeck.run();
            });
        });
    }

    private static void triggerRenderWithOptions(
            AtomicBoolean isRendering,
            MediaManager mediaManager,
            VideoRenderer videoRenderer,
            InPanelNameOverlay nameOverlay,
            Runnable layoutLayers,
            JLabel statusLabel,
            BlackProgressBar progressBar,
            boolean blurTrack,
            boolean withLogo
    ) {
        if (isRendering.get()) {
            statusLabel.setText("Already rendering...");
            return;
        }
        if (!mediaManager.hasMedia()) {
            statusLabel.setText("Add slides (or Ctrl+N) + .mp3 first.");
            return;
        }

        List<MediaManager.SlideItem> slidesSnapshot = mediaManager.getSlides();
        File mp3Snapshot = mediaManager.getMp3File();

        layoutLayers.run();
        nameOverlay.openEmpty(enteredName -> {
            if (!isRendering.compareAndSet(false, true)) {
                statusLabel.setText("Already rendering...");
                return;
            }

            File outputFile = videoRenderer.resolveOutputFile(slidesSnapshot, mp3Snapshot, enteredName);
            progressBar.setProgress(0.02);

            new Thread(() -> {
                try {
                    videoRenderer.render(
                            slidesSnapshot,
                            mp3Snapshot,
                            outputFile,
                            SECONDS_PER_SLIDE,
                            blurTrack,
                            withLogo,
                            (msg, prog) -> SwingUtilities.invokeLater(() -> {
                                statusLabel.setText(msg);
                                progressBar.setProgress(prog);
                            })
                    );
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setProgress(1.0);
                        statusLabel.setText("Playing: " + outputFile.getAbsolutePath());
                        videoRenderer.openVideoWindow(outputFile);
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setProgress(0.0);
                        statusLabel.setText("Render error: " + ex.getMessage());
                    });
                } finally {
                    isRendering.set(false);
                }
            }, "cardelle-renderer").start();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<File> extractFilesFromClipboard() {
        List<File> files = new ArrayList<>();
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = clipboard.getContents(null);
            if (t == null) {
                return files;
            }

            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof File f) {
                            files.add(f);
                        }
                    }
                }
            }

            if (files.isEmpty() && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object textObj = t.getTransferData(DataFlavor.stringFlavor);
                if (textObj instanceof String rawText) {
                    for (String line : rawText.split("\\R")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        if (trimmed.startsWith("file://")) {
                            try {
                                files.add(new File(URI.create(trimmed)));
                            } catch (Exception ignored) {
                            }
                        } else {
                            files.add(new File(trimmed));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[Cardelle] Clipboard read failed: " + ex.getMessage());
        }
        return files;
    }

    private static int computeTargetSlot(JPanel container, int pointerX, int currentIndex) {
        int count = container.getComponentCount();
        if (count <= 1) {
            return currentIndex;
        }
        for (int i = 0; i < count; i++) {
            Component comp = container.getComponent(i);
            int mid = comp.getX() + comp.getWidth() / 2;
            if (pointerX < mid) {
                return i;
            }
        }
        return count - 1;
    }

    /**
     * Horizontal card row. It centers the deck while it fits, then falls back to
     * a left-aligned scrolling strip once the cards are wider than the viewport.
     */
    private static final class CenteredCardLayout implements java.awt.LayoutManager {

        private static final long serialVersionUID = 1L;

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return layoutSize(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return layoutSize(parent, true);
        }

        private Dimension layoutSize(Container parent, boolean minimum) {
            Insets insets = parent.getInsets();
            int width = insets.left + insets.right;
            int height = 0;
            for (Component comp : parent.getComponents()) {
                if (!comp.isVisible()) {
                    continue;
                }
                Dimension size = minimum ? comp.getMinimumSize() : comp.getPreferredSize();
                width += size.width;
                height = Math.max(height, size.height);
            }
            return new Dimension(width, height + insets.top + insets.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            Component[] comps = parent.getComponents();

            int totalWidth = 0;
            int maxHeight = 0;
            for (Component comp : comps) {
                if (!comp.isVisible()) {
                    continue;
                }
                Dimension size = comp.getPreferredSize();
                totalWidth += size.width;
                maxHeight = Math.max(maxHeight, size.height);
            }

            int availableWidth = parent.getWidth() - insets.left - insets.right;
            int availableHeight = parent.getHeight() - insets.top - insets.bottom;
            int x = insets.left + Math.max(0, (availableWidth - totalWidth) / 2);
            int y = insets.top + Math.max(0, (availableHeight - maxHeight) / 2);

            for (Component comp : comps) {
                if (!comp.isVisible()) {
                    continue;
                }
                Dimension size = comp.getPreferredSize();
                comp.setBounds(x, y, size.width, size.height);
                x += size.width;
            }
        }
    }

    private static final class BlackProgressBar extends JComponent {
        private double progress = 0.0;
        private Color color = Color.BLACK;

        BlackProgressBar() {
            setPreferredSize(new Dimension(100, 4));
            setMinimumSize(new Dimension(0, 4));
            setOpaque(false);
        }

        void setProgress(double value) {
            this.progress = Math.max(0.0, Math.min(1.0, value));
            repaint();
        }

        void setColor(Color value) {
            this.color = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (progress <= 0.0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(color);
            int fillW = (int) Math.round(getWidth() * progress);
            g2.fillRect(0, 0, fillW, getHeight());
            g2.dispose();
        }
    }

    @FunctionalInterface
    private interface NameCallback {
        void onEnter(String name);
    }

    private static final class InPanelNameOverlay extends JPanel {

        private final JTextField field = new JTextField("", 18);
        private NameCallback callback;

        InPanelNameOverlay() {
            setLayout(new BorderLayout(4, 4));
            setBackground(new Color(26, 26, 30));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(120, 130, 150), 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            setVisible(false);

            JLabel lbl = new JLabel("Name + Enter:");
            lbl.setForeground(Color.WHITE);
            lbl.setFont(new Font("Arial", Font.BOLD, 11));

            field.setFont(new Font("Monospaced", Font.PLAIN, 12));
            field.setBackground(new Color(15, 15, 18));
            field.setForeground(Color.WHITE);
            field.setCaretColor(Color.WHITE);
            field.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            field.addActionListener(e -> {
                String text = field.getText();
                NameCallback cb = this.callback;
                setVisible(false);
                if (cb != null) {
                    cb.onEnter(text);
                }
            });

            field.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeNameBox");
            field.getActionMap().put("closeNameBox", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setVisible(false);
                }
            });

            add(lbl, BorderLayout.NORTH);
            add(field, BorderLayout.CENTER);
        }

        void openEmpty(NameCallback cb) {
            this.callback = cb;
            field.setText("");
            setVisible(true);
            repaint();
            SwingUtilities.invokeLater(field::requestFocusInWindow);
        }
    }

    private static final class InPanelHelpOverlay extends JPanel {

        private final JLabel hintLabel = new JLabel("3s then fade • Ctrl+H pin", SwingConstants.LEFT);
        private float alpha = 1.0f;
        private boolean pinned = false;
        private Timer fadeTimer;
        private Timer holdTimer;

        InPanelHelpOverlay() {
            setLayout(new BorderLayout(4, 4));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setVisible(false);

            JTextArea text = new JTextArea(
                    "Ctrl+H      Help / Pin / Close\n"
                            + "Ctrl+P      Customize theme / logo\n"
                            + "Ctrl+O      Open image / MP3 files\n"
                            + "Ctrl+N      Unique color card\n"
                            + "Ctrl+V      Paste image / MP3\n"
                            + "Ctrl+L      Toggle Cardelle logo\n"
                            + "Right-Click Delete card\n"
                            + "Left-Drag   Reorder & tilt card\n"
                            + "Ctrl+X      Reset deck & MP3\n"
                            + "Ctrl+E      Name video & render\n"
                            + "Ctrl+B      Render w/ full blur"
            );
            text.setEditable(false);
            text.setFocusable(false);
            text.setOpaque(false);
            text.setForeground(new Color(235, 238, 245));
            text.setFont(new Font("Monospaced", Font.PLAIN, 11));

            hintLabel.setForeground(new Color(165, 175, 195));
            hintLabel.setFont(new Font("Arial", Font.ITALIC, 10));

            add(text, BorderLayout.CENTER);
            add(hintLabel, BorderLayout.SOUTH);

            initTimers();
        }

        private void initTimers() {
            fadeTimer = new Timer(FADE_TICK_MS, e -> stepFade());
            holdTimer = new Timer(HOLD_BEFORE_FADE_MS, e -> onHoldElapsed());
            holdTimer.setRepeats(false);
        }

        private void stepFade() {
            if (pinned) {
                fadeTimer.stop();
                alpha = 1.0f;
                repaint();
                return;
            }
            alpha = Math.max(0.0f, alpha - FADE_STEP);
            repaint();
            if (alpha <= 0.0f) {
                fadeTimer.stop();
                setVisible(false);
            }
        }

        private void onHoldElapsed() {
            if (!pinned && isVisible() && fadeTimer != null) {
                fadeTimer.start();
            }
        }

        void dismiss() {
            holdTimer.stop();
            fadeTimer.stop();
            pinned = false;
            setVisible(false);
            repaint();
        }

        void onCtrlH() {
            if (isVisible()) {
                if (!pinned) {
                    holdTimer.stop();
                    fadeTimer.stop();
                    pinned = true;
                    alpha = 1.0f;
                    hintLabel.setText("Pinned • Ctrl+H to close");
                    repaint();
                } else {
                    holdTimer.stop();
                    fadeTimer.stop();
                    pinned = false;
                    setVisible(false);
                }
                return;
            }

            pinned = false;
            holdTimer.stop();
            fadeTimer.stop();
            alpha = 1.0f;
            hintLabel.setText("3s then fade • Ctrl+H pin");
            setVisible(true);
            repaint();
            holdTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 24, 28, 235));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            g2.setColor(new Color(110, 120, 145, 220));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}