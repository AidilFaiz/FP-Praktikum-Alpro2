package pacman2;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pac-Man Game"); // Judul jendela tetap "Pac-Man Game"
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            PacMan game = new PacMan(); // Membuat instance PacMan, yang sekarang juga berfungsi sebagai GamePanel
            frame.add(game); // Menambahkan PacMan (sebagai JPanel) ke frame

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            game.startGame(); // Memulai game loop
        });
    }
}