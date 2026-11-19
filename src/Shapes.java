import javax.swing.*;
import java.awt.*;

public class Shapes {
    public static JComponent rectangle(int width, int height, Color color) {

        return new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;

                g.setColor(color);
                g.fillRect(x, y, width, height);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, height);
            }
        };
    }
}
