import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Cards extends JPanel {

    @FunctionalInterface
    public interface CardActionListener {
        void onDelete(Cards card);
    }

    public interface CardDragListener {
        void onDragStart(Cards card, MouseEvent e);
        void onDragMove(Cards card, MouseEvent e);
        void onDragEnd(Cards card, MouseEvent e);
    }

    private static final int PADDING = 38;

    private final int w = 200;
    private final int h = 270;
    private final int tilt = 20;

    private final double centerX = PADDING + w / 2.0;
    private final double centerY = PADDING + h / 2.0;

    private double mx = centerX;
    private double my = centerY;

    private BufferedImage img;
    private Color solidColor;
    private BufferedImage cachedWarped;
    private double lastWarpedMx = Double.NaN;
    private double lastWarpedMy = Double.NaN;

    private boolean isDragging = false;
    private double dragTiltAngleRad = 0.0;

    private CardActionListener actionListener;
    private CardDragListener dragListener;

    // ==========================================
    // SHINE SETTINGS
    // ==========================================

    private final boolean shine;
    private boolean isHovering = false;

    private int shineY = -100;
    private final int shineHeight = 50;

    private Timer shineTimer;
    private Timer shineDelay;

    private final int shineSpeed = 8;
    private final int shineDelayTime = 2000;

    // ==========================================
    // CONSTRUCTORS
    // ==========================================

    public Cards() {
        this((String) null, false);
    }

    public Cards(String path) {
        this(path, false);
    }

    public Cards(String path, boolean shine) {
        this.shine = shine;

        if (path != null) {
            try {
                File file = new File(path);
                img = ImageIO.read(file);
                if (img == null) {
                    System.err.println("Unsupported or invalid image format: " + path);
                }
            } catch (IOException e) {
                System.err.println("Failed to load image '" + path + "': " + e.getMessage());
            }
        }

        initCard();
    }

    public Cards(Color solidColor, boolean shine) {
        this.shine = shine;
        this.solidColor = solidColor;
        initCard();
    }

    private void initCard() {
        Dimension size = new Dimension(w + PADDING * 2, h + PADDING * 2);
        setPreferredSize(size);
        setMaximumSize(size);
        setOpaque(false);

        initializeMouse();
        initializeShine();
    }

    public void setCardActionListener(CardActionListener listener) {
        this.actionListener = listener;
    }

    public void setCardDragListener(CardDragListener listener) {
        this.dragListener = listener;
    }

    public void setDraggingState(boolean dragging, double dxOffset) {
        this.isDragging = dragging;
        double clamped = Math.max(-1.0, Math.min(1.0, dxOffset / 140.0));
        this.dragTiltAngleRad = Math.toRadians(clamped * 6.0);
        repaint();
    }

    public BufferedImage getImage() {
        return img;
    }

    public Color getSolidColor() {
        return solidColor;
    }

    // ==========================================
    // MOUSE INITIALIZATION
    // ==========================================

    private void initializeMouse() {
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mx = e.getX();
                my = e.getY();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    mx = e.getX();
                    my = e.getY();
                    double nx = Math.max(-1.0, Math.min(1.0, (mx - centerX) / (w / 2.0)));
                    dragTiltAngleRad = Math.toRadians(nx * 6.0);
                    if (dragListener != null) {
                        dragListener.onDragMove(Cards.this, e);
                    }
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (actionListener != null) {
                        actionListener.onDelete(Cards.this);
                    }
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDragging = true;
                    if (shineTimer != null) {
                        shineTimer.stop();
                    }
                    if (shineDelay != null) {
                        shineDelay.stop();
                    }
                    if (dragListener != null) {
                        dragListener.onDragStart(Cards.this, e);
                    }
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && isDragging) {
                    isDragging = false;
                    dragTiltAngleRad = 0.0;
                    if (dragListener != null) {
                        dragListener.onDragEnd(Cards.this, e);
                    }
                    repaint();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                isHovering = true;
                if (shine && !isDragging && shineTimer != null && !shineTimer.isRunning()) {
                    shineTimer.start();
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovering = false;
                if (!isDragging) {
                    mx = centerX;
                    my = centerY;
                }

                if (shineTimer != null) {
                    shineTimer.stop();
                }
                if (shineDelay != null) {
                    shineDelay.stop();
                }

                shineY = -100;
                repaint();
            }
        });
    }

    // ==========================================
    // SHINE INITIALIZATION
    // ==========================================

    private void initializeShine() {
        shineDelay = new Timer(shineDelayTime, event -> {
            if (isHovering && !isDragging && shineTimer != null) {
                shineTimer.start();
            }
        });
        shineDelay.setRepeats(false);

        shineTimer = new Timer(16, e -> {
            shineY += shineSpeed;

            if (shineY > h + 100) {
                shineTimer.stop();
                shineY = -100;
                shineDelay.restart();
            }

            repaint();
        });
    }

    // ==========================================
    // DRAW SHINE
    // ==========================================

    private void drawShine(Graphics2D g2, Polygon card) {
        Graphics2D shineGraphics = (Graphics2D) g2.create();
        shineGraphics.clip(card);
        shineGraphics.rotate(Math.toRadians(-25), w / 2.0, h / 2.0);
        shineGraphics.setColor(new Color(255, 255, 255, 65));
        shineGraphics.fillRect(-w, shineY, w * 3, shineHeight);
        shineGraphics.dispose();
    }

    // ==========================================
    // PAINT COMPONENT
    // ==========================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (isDragging) {
            g2.rotate(dragTiltAngleRad, getWidth() / 2.0, getHeight() / 2.0);
        }

        g2.translate(PADDING, PADDING);

        double nx = Math.max(-1.0, Math.min(1.0, (mx - centerX) / (w / 2.0)));
        double ny = Math.max(-1.0, Math.min(1.0, (my - centerY) / (h / 2.0)));

        int dx = (int) (nx * tilt);
        int dy = (int) (ny * tilt);

        int[] x = {dx, w - dx, w + dx, -dx};
        int[] y = {dy, -dy, h + dy, h - dy};

        Polygon p = new Polygon(x, y, 4);

        if (img != null) {
            if (cachedWarped == null || Double.compare(mx, lastWarpedMx) != 0 || Double.compare(my, lastWarpedMy) != 0) {
                cachedWarped = warp(img, x, y);
                lastWarpedMx = mx;
                lastWarpedMy = my;
            }
            g2.drawImage(cachedWarped, -PADDING, -PADDING, null);
        } else {
            g2.setColor(solidColor != null ? solidColor : new Color(50, 100, 180));
            g2.fillPolygon(p);
        }

        if (shine && isHovering && !isDragging) {
            drawShine(g2, p);
        }

        g2.setColor(isDragging ? new Color(255, 235, 140) : Color.WHITE);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolygon(p);

        g2.dispose();
    }

    // ==========================================
    // IMAGE PERSPECTIVE WARPING
    // ==========================================

    private BufferedImage warp(BufferedImage source, int[] x, int[] y) {
        int outW = w + PADDING * 2;
        int outH = h + PADDING * 2;

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);

        double iw = source.getWidth() - 1.0;
        double ih = source.getHeight() - 1.0;

        double[][] s = {
                {0, 0},
                {iw, 0},
                {iw, ih},
                {0, ih}
        };

        double[][] d = {
                {x[0] + PADDING, y[0] + PADDING},
                {x[1] + PADDING, y[1] + PADDING},
                {x[2] + PADDING, y[2] + PADDING},
                {x[3] + PADDING, y[3] + PADDING}
        };

        double[][] a = homography(s, d);
        double[][] inv = a == null ? null : inverse(a);
        if (inv == null) {
            return out;
        }

        int minX = outW;
        int maxX = 0;
        int minY = outH;
        int maxY = 0;

        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, x[i] + PADDING);
            maxX = Math.max(maxX, x[i] + PADDING);
            minY = Math.min(minY, y[i] + PADDING);
            maxY = Math.max(maxY, y[i] + PADDING);
        }

        int startY = Math.max(0, minY);
        int endY = Math.min(outH - 1, maxY);
        int startX = Math.max(0, minX);
        int endX = Math.min(outW - 1, maxX);

        for (int py = startY; py <= endY; py++) {
            for (int px = startX; px <= endX; px++) {
                double z = inv[2][0] * px + inv[2][1] * py + inv[2][2];
                if (Math.abs(z) < 1e-6) {
                    continue;
                }

                double sx = (inv[0][0] * px + inv[0][1] * py + inv[0][2]) / z;
                double sy = (inv[1][0] * px + inv[1][1] * py + inv[1][2]) / z;

                if (sx >= 0 && sx <= iw && sy >= 0 && sy <= ih) {
                    out.setRGB(px, py, sample(source, sx, sy));
                }
            }
        }

        return out;
    }

    // ==========================================
    // HOMOGRAPHY
    // ==========================================

    private static double[][] homography(double[][] s, double[][] d) {
        double[][] a = new double[8][9];

        for (int i = 0; i < 4; i++) {
            double x = s[i][0], y = s[i][1];
            double u = d[i][0], v = d[i][1];
            int r = i * 2;

            a[r][0] = x;
            a[r][1] = y;
            a[r][2] = 1;
            a[r][6] = -u * x;
            a[r][7] = -u * y;
            a[r][8] = u;

            a[r + 1][3] = x;
            a[r + 1][4] = y;
            a[r + 1][5] = 1;
            a[r + 1][6] = -v * x;
            a[r + 1][7] = -v * y;
            a[r + 1][8] = v;
        }

        for (int i = 0; i < 8; i++) {
            int p = i;
            for (int j = i + 1; j < 8; j++) {
                if (Math.abs(a[j][i]) > Math.abs(a[p][i])) {
                    p = j;
                }
            }

            double[] t = a[i];
            a[i] = a[p];
            a[p] = t;

            if (Math.abs(a[i][i]) < 1e-10) {
                return null;
            }

            double v = a[i][i];
            for (int j = i; j < 9; j++) {
                a[i][j] /= v;
            }

            for (int j = 0; j < 8; j++) {
                if (j == i) {
                    continue;
                }
                v = a[j][i];
                for (int k = i; k < 9; k++) {
                    a[j][k] -= v * a[i][k];
                }
            }
        }

        return new double[][]{
                {a[0][8], a[1][8], a[2][8]},
                {a[3][8], a[4][8], a[5][8]},
                {a[6][8], a[7][8], 1}
        };
    }

    // ==========================================
    // MATRIX INVERSE
    // ==========================================

    private static double[][] inverse(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2];
        double d = m[1][0], e = m[1][1], f = m[1][2];
        double g = m[2][0], h = m[2][1], i = m[2][2];

        double det = a * (e * i - f * h)
                - b * (d * i - f * g)
                + c * (d * h - e * g);

        if (Math.abs(det) < 1e-10) {
            return null;
        }

        return new double[][]{
                {
                        (e * i - f * h) / det,
                        (c * h - b * i) / det,
                        (b * f - c * e) / det
                },
                {
                        (f * g - d * i) / det,
                        (a * i - c * g) / det,
                        (c * d - a * f) / det
                },
                {
                        (d * h - e * g) / det,
                        (b * g - a * h) / det,
                        (a * e - b * d) / det
                }
        };
    }

    // ==========================================
    // BILINEAR IMAGE SAMPLING
    // ==========================================

    private static int sample(BufferedImage im, double x, double y) {
        int x1 = (int) x;
        int y1 = (int) y;

        int x2 = Math.min(x1 + 1, im.getWidth() - 1);
        int y2 = Math.min(y1 + 1, im.getHeight() - 1);

        double fx = x - x1;
        double fy = y - y1;

        int[] c = {
                im.getRGB(x1, y1),
                im.getRGB(x2, y1),
                im.getRGB(x1, y2),
                im.getRGB(x2, y2)
        };

        int result = 0;

        for (int s = 0; s <= 24; s += 8) {
            double a = (c[0] >> s) & 255;
            double b = (c[1] >> s) & 255;
            double d = (c[2] >> s) & 255;
            double e = (c[3] >> s) & 255;

            int v = (int) Math.round(
                    (a + (b - a) * fx) * (1 - fy)
                            + (d + (e - d) * fx) * fy
            );

            result |= v << s;
        }

        return result;
    }

    // ==========================================
    // CLEANUP
    // ==========================================

    @Override
    public void removeNotify() {
        if (shineTimer != null) {
            shineTimer.stop();
        }
        if (shineDelay != null) {
            shineDelay.stop();
        }
        super.removeNotify();
    }
}