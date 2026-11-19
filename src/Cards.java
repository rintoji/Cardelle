
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.ImageIO;

public class Cards extends JPanel {

    int w = 200, h = 270, tilt = 20;

    double mx = 125, my = 160;

    BufferedImage img;

    // ==========================================
    // SHINE SETTINGS
    // ==========================================

    private boolean shine;
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

    // Empty card
    public Cards() {
        this(null, false);
    }

    // Image card
    public Cards(String path) {
        this(path, false);
    }

    // Empty card with shine
    public Cards(boolean shine) {
        this(null, shine);
    }

    // Image card with shine option
    public Cards(String path, boolean shine) {

        this.shine = shine;

        if (path != null) {
            try {
                img = ImageIO.read(new File(path));

                if (img == null) {
                    System.out.println("Invalid image format!");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        setPreferredSize(new Dimension(w + 50, h + 50));
        setOpaque(false);

        initializeMouse();
        initializeShine();
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
        });

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {

                isHovering = true;

                if (shine && shineTimer != null) {
                    shineTimer.start();
                }

                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {

                isHovering = false;

                mx = 125;
                my = 160;

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

        shineTimer = new Timer(16, e -> {

            shineY += shineSpeed;

            if (shineY > h + 100) {

                shineTimer.stop();

                shineY = -100;

                shineDelay = new Timer(shineDelayTime, event -> {

                    if (isHovering) {
                        shineTimer.start();
                    }

                });

                shineDelay.setRepeats(false);
                shineDelay.start();
            }

            repaint();
        });
    }

    // ==========================================
    // DRAW SHINE
    // ==========================================

    private void drawShine(Graphics2D g2, Polygon card) {

        Graphics2D shineGraphics =
                (Graphics2D) g2.create();

        // Clip shine to card
        shineGraphics.clip(card);

        // Rotate shine
        shineGraphics.rotate(
                Math.toRadians(-25),
                w / 2.0,
                h / 2.0
        );

        // Sharp white rectangle with opacity
        shineGraphics.setColor(
                new Color(255, 255, 255, 65)
        );

        shineGraphics.fillRect(
                -w,
                shineY,
                w * 3,
                shineHeight
        );

        shineGraphics.dispose();
    }

    // ==========================================
    // PAINT COMPONENT
    // ==========================================

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.translate(25, 25);

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Normalize mouse coordinates
        double nx = Math.max(
                -1,
                Math.min(1, (mx - 125) / 100)
        );

        double ny = Math.max(
                -1,
                Math.min(1, (my - 160) / 135)
        );

        // Calculate displacement
        int dx = (int) (nx * tilt);
        int dy = (int) (ny * tilt);

        // Polygon corners
        int[] x = {
                dx,
                w - dx,
                w + dx,
                -dx
        };

        int[] y = {
                dy,
                -dy,
                h + dy,
                h - dy
        };

        Polygon p = new Polygon(x, y, 4);

        // Draw image or empty card
        if (img != null) {

            g2.drawImage(
                    warp(img, x, y),
                    -25,
                    -25,
                    null
            );

        } else {

            g2.setColor(new Color(50, 100, 180));
            g2.fillPolygon(p);
        }

        // Draw shine only when hovering
        if (shine && isHovering) {
            drawShine(g2, p);
        }

        // Draw border
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawPolygon(p);

        g2.dispose();
    }

    // ==========================================
    // IMAGE PERSPECTIVE WARPING
    // ==========================================

    static BufferedImage warp(
            BufferedImage img,
            int[] x,
            int[] y
    ) {

        int W = 250, H = 320;

        BufferedImage out = new BufferedImage(
                W,
                H,
                BufferedImage.TYPE_INT_ARGB
        );

        double iw = img.getWidth() - 1.0;
        double ih = img.getHeight() - 1.0;

        double[][] s = {
                {0, 0},
                {iw, 0},
                {iw, ih},
                {0, ih}
        };

        double[][] d = {
                {x[0] + 25, y[0] + 25},
                {x[1] + 25, y[1] + 25},
                {x[2] + 25, y[2] + 25},
                {x[3] + 25, y[3] + 25}
        };

        double[][] a = homography(s, d);
        double[][] inv = a == null ? null : inverse(a);

        if (inv == null) return out;

        int minX = W, maxX = 0;
        int minY = H, maxY = 0;

        for (int i = 0; i < 4; i++) {

            minX = Math.min(minX, x[i] + 25);
            maxX = Math.max(maxX, x[i] + 25);

            minY = Math.min(minY, y[i] + 25);
            maxY = Math.max(maxY, y[i] + 25);
        }

        for (
                int py = Math.max(0, minY);
                py <= Math.min(H - 1, maxY);
                py++
        ) {

            for (
                    int px = Math.max(0, minX);
                    px <= Math.min(W - 1, maxX);
                    px++
            ) {

                double z =
                        inv[2][0] * px +
                                inv[2][1] * py +
                                inv[2][2];

                if (Math.abs(z) < 1e-6) continue;

                double sx =
                        (inv[0][0] * px +
                                inv[0][1] * py +
                                inv[0][2]) / z;

                double sy =
                        (inv[1][0] * px +
                                inv[1][1] * py +
                                inv[1][2]) / z;

                if (
                        sx >= 0 && sx <= iw &&
                                sy >= 0 && sy <= ih
                ) {

                    out.setRGB(
                            px,
                            py,
                            sample(img, sx, sy)
                    );
                }
            }
        }

        return out;
    }

    // ==========================================
    // HOMOGRAPHY
    // ==========================================

    static double[][] homography(double[][] s, double[][] d) {

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

            if (Math.abs(a[i][i]) < 1e-10) return null;

            double v = a[i][i];

            for (int j = i; j < 9; j++) {
                a[i][j] /= v;
            }

            for (int j = 0; j < 8; j++) {

                if (j == i) continue;

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

    static double[][] inverse(double[][] m) {

        double a = m[0][0], b = m[0][1], c = m[0][2];
        double d = m[1][0], e = m[1][1], f = m[1][2];
        double g = m[2][0], h = m[2][1], i = m[2][2];

        double det =
                a * (e * i - f * h)
                        - b * (d * i - f * g)
                        + c * (d * h - e * g);

        if (Math.abs(det) < 1e-10) return null;

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

    static int sample(BufferedImage im, double x, double y) {

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
