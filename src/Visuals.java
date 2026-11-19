import javax.swing.*;
import java.awt.*;

public class Visuals {
    public static void main() {
        JFrame frame = new JFrame("Cards");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.lightGray);


        JLabel title = new JLabel("Cards", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 30));

//        JComponent rec = Shapes.rectangle(200, 270, Color.cyan.darker());
        Cards card = new Cards(true);
        Cards card_1 = new Cards("src/mahoraga.jpg", true);
        Cards card_2 = new Cards("src/inverted.png");
        Cards card_3 = new Cards("src/void.jpg");
        Cards card_4= new Cards(true);
//        ImageIcon image= new ImageIcon("src/mahoraga.jpg");
//        JLabel imag = new JLabel(image);
//        JPanel cardContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 150));
        JPanel cardContainer = new JPanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.X_AXIS));
        cardContainer.setBackground(Color.lightGray);
        cardContainer.add(card);
        cardContainer.add(card_1);
        cardContainer.add(card_2);
        cardContainer.add(card_3);
        cardContainer.add(card_4);

        JScrollPane scrollPane = new JScrollPane(cardContainer);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

        scrollPane.getHorizontalScrollBar().setUnitIncrement(40);

        scrollPane.addMouseWheelListener(e -> {
            JScrollBar bar = scrollPane.getHorizontalScrollBar();
            bar.setValue(bar.getValue() + e.getWheelRotation() * 40);
        });

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(scrollPane, BorderLayout.CENTER);
//        panel.add(cardContainer, BorderLayout.CENTER);
        panel.add(title, BorderLayout.SOUTH);
//        panel.add(imag,BorderLayout.CENTER);
//        panel.add(rec,BorderLayout.CENTER);
//        panel.add(card,BorderLayout.CENTER);

        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.add(panel);
    }


}
