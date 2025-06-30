package pacman;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javazoom.jl.player.Player;
import javazoom.jl.decoder.JavaLayerException;

public class PacMan extends JPanel implements ActionListener, KeyListener {
    // Game states
    public enum GameState {
        MAIN_MENU, LEVEL_SELECT, PLAYING, GAME_OVER, GAME_WIN, PAUSED, LEVEL_INFO
    }
    
    private GameState gameState = GameState.MAIN_MENU;
    private int selectedMenuOption = 0;
    private int selectedLevelOption = 0;
    private int currentLevel = 1;
    private final int MAX_LEVEL = 3;
    private int levelInfoTimer = 0;
    private final int LEVEL_INFO_DURATION = 60;
    
    // Game elements
    private Map<Integer, Integer> highScores = new HashMap<>();
    private int currentScore = 0;
    private int lives = 3;
    
    // Menu options
    private final String[] MAIN_MENU_OPTIONS = {"Start Game", "Level Select", "Quit"};
    private final String[] LEVEL_OPTIONS = {"Level 1 - Easy", "Level 2 - Medium", "Level 3 - Hard", "Back"};
    private final String[] GAME_OVER_OPTIONS = {"Restart", "Main Menu", "Quit"};
    private final String[] GAME_WIN_OPTIONS = {"Next Level", "Main Menu", "Quit"};
    private final String[] PAUSE_OPTIONS = {"Resume", "Restart", "Main Menu", "Quit"};
    
    class Block {
        int x, y, width, height;
        Image image;
        Color color;
        int startX, startY;
        char direction = 'U';
        int velocityX = 0, velocityY = 0;
        int speed;
        boolean moving = true;
        boolean isScared = false;
        boolean isFrozen = false;

        Block(Image image, Color color, int x, int y, int width, int height) {
            this.image = image;
            this.color = color;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.startX = x;
            this.startY = y;
        }

        void updateDirection(char direction) {
            char prevDirection = this.direction;
            this.direction = direction;
            updateVelocity();
            
            // Test movement
            int oldX = x, oldY = y;
            x += velocityX;
            y += velocityY;
            
            boolean canMove = true;
            for (Block wall : walls) {
                if (collision(this, wall)) {
                    canMove = false;
                    break;
                }
            }
            
            if (!canMove) {
                x = oldX;
                y = oldY;
                this.direction = prevDirection;
                updateVelocity();
                this.moving = false;
            } else {
                this.moving = true;
                x = oldX;
                y = oldY;
            }
        }

        void updateVelocity() {
            if (isFrozen) {
                this.velocityX = 0;
                this.velocityY = 0;
                return;
            }
            
            int effectiveSpeed = this.speed;
            if (this == pacman && isPoweredUp) {
                effectiveSpeed += 1;
            }
            
            switch (this.direction) {
                case 'U': velocityX = 0; velocityY = -effectiveSpeed; break;
                case 'D': velocityX = 0; velocityY = effectiveSpeed; break;
                case 'L': velocityX = -effectiveSpeed; velocityY = 0; break;
                case 'R': velocityX = effectiveSpeed; velocityY = 0; break;
            }
        }

        void reset() {
            this.x = this.startX;
            this.y = this.startY;
            this.direction = 'R';
            this.moving = true;
            this.isScared = false;
            this.isFrozen = false;
            updateVelocity();
        }
    }

    // Game board dimensions
    private final int rowCount = 21;
    private final int columnCount = 19;
    private final int tileSize = 32;
    private final int boardWidth = columnCount * tileSize;
    private final int boardHeight = rowCount * tileSize;

    // Game difficulty variables
    private int pacmanSpeed = 4;
    private int ghostBaseSpeed = 2;
    private int ghostChaseRange = 5 * tileSize;
    private double ghostChaseProbability = 0.7;
    private int ghostDirectionChangeCounter = 0;
    private final int GHOST_DIRECTION_CHANGE_INTERVAL = 30;
    
    // Power-up variables
    private boolean isPoweredUp = false;
    private int powerUpTimer = 0;
    private final int POWER_UP_DURATION = 300;
    
    // Spawn timers
    private int cherrySpawnTimer = 0;
    private int powerFoodSpawnTimer = 0;
    private final int CHERRY_SPAWN_INTERVAL = 500;
    private final int POWER_FOOD_SPAWN_INTERVAL = 800;
    
    // Images
    private Image scaredGhostImage;
    private Image powerFoodImage;
    private Image wallImage;
    private Image cherryImage;
    private Image blueGhostImage;
    private Image orangeGhostImage;
    private Image pinkGhostImage;
    private Image redGhostImage;
    private Image heartImage;
    private Color wallColor = new Color(0, 0, 255);

    // Animation
    private int pacmanSpriteNum = 1;
    private int pacmanSpriteCounter = 0;
    private final int PACMAN_ANIMATION_SPEED = 6;
    
    // Sound
    private PlayerThread mainMenuPlayer;
    private PlayerThread inGamePlayer;
    private BufferedInputStream mainMenuStream;
    private BufferedInputStream inGameStream;
    
    // Movement
    private javax.swing.Timer gameLoop;
    char[] directions = {'U', 'D', 'L', 'R'};
    Random random = new Random();

    // Fixed tileMap with consistent row lengths
    private final String[] tileMap = {
        "XXXXXXXXXXXXXXXXXXX",
        "X        X        X",
        "X XX XXX X XXX XX X",
        "X                 X",
        "X XX X XXXXX X XX X",
        "X    X       X    X",
        "XXXX XXXX XXXX XXXX",
        "X    X       X    X",
        "XXXX X XXrXX X XXXX",
        "X       bpo       X",
        "XXXX X XXXXX X XXXX",
        "X    X       X    X",
        "XXXX X XXXXX X XXXX",
        "X        X        X",
        "X XX XXX X XXX XX X",
        "X  X     P     X  X",
        "XX X X XXXXX X X XX",
        "X    X   X   X    X",
        "X XXXXXX X XXXXXX X",
        "X                 X",
        "XXXXXXXXXXXXXXXXXXX"
    };

    // Game objects
    HashSet<Block> walls = new HashSet<>();
    HashSet<Block> foods = new HashSet<>();
    HashSet<Block> ghosts = new HashSet<>();
    Block pacman;
    Block cherry;
    Block powerFood;

    class PlayerThread extends Thread {
        private Player player;
        private boolean loop;
        private String filename;
        private volatile boolean running = true;
        private BufferedInputStream stream;

        public PlayerThread(String filename, boolean loop) {
            this.filename = filename;
            this.loop = loop;
        }

        public void run() {
            try {
                do {
                    InputStream is = getClass().getResourceAsStream(filename);
                    if (is == null) {
                        System.out.println("Could not find music file: " + filename);
                        break;
                    }
                    stream = new BufferedInputStream(is);
                    player = new Player(stream);
                    player.play();
                    if (!loop) break;
                } while (running);
            } catch (Exception e) {
                System.out.println("Error playing music: " + e.getMessage());
            }
        }

        public void stopMusic() {
            running = false;
            if (player != null) {
                player.close();
            }
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing stream: " + e.getMessage());
            }
        }
    }

    public PacMan() {
        setPreferredSize(new Dimension(boardWidth, boardHeight));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);

        // Initialize high scores
        for (int i = 1; i <= MAX_LEVEL; i++) {
            highScores.put(i, 0);
        }

        // Load images
        try {
            blueGhostImage = new ImageIcon(getClass().getResource("/pacman/blueGhost.png")).getImage();
            orangeGhostImage = new ImageIcon(getClass().getResource("/pacman/orangeGhost.png")).getImage();
            pinkGhostImage = new ImageIcon(getClass().getResource("/pacman/pinkGhost.png")).getImage();
            redGhostImage = new ImageIcon(getClass().getResource("/pacman/redGhost.png")).getImage();
            heartImage = new ImageIcon(getClass().getResource("/pacman/heart.png")).getImage();
            scaredGhostImage = new ImageIcon(getClass().getResource("/pacman/scaredGhost.png")).getImage();
            powerFoodImage = new ImageIcon(getClass().getResource("/pacman/powerFood.png")).getImage();
            wallImage = new ImageIcon(getClass().getResource("/pacman/wall.png")).getImage();
            cherryImage = new ImageIcon(getClass().getResource("/pacman/cherry.png")).getImage();
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
        }

        setLevelDifficulty(currentLevel);
        loadMap();
        initializeGhosts();
        
        gameLoop = new javax.swing.Timer(16, this);
        gameLoop.start();
        
        // Start main menu music
        playMainMenuMusic();
    }

    private void setLevelDifficulty(int level) {
        currentLevel = level;
        switch(level) {
            case 1: // Easy
                pacmanSpeed = 4;
                ghostBaseSpeed = 2;
                ghostChaseProbability = 0.7;
                ghostChaseRange = 5 * tileSize;
                break;
            case 2: // Medium
                pacmanSpeed = 5;
                ghostBaseSpeed = 3;
                ghostChaseProbability = 0.8;
                ghostChaseRange = 7 * tileSize;
                break;
            case 3: // Hard
                pacmanSpeed = 6;
                ghostBaseSpeed = 4;
                ghostChaseProbability = 0.9;
                ghostChaseRange = 9 * tileSize;
                break;
        }
        
        if (pacman != null) {
            pacman.speed = pacmanSpeed;
        }
        for (Block ghost : ghosts) {
            ghost.speed = ghostBaseSpeed;
        }
    }

    public void loadMap() {
        walls.clear();
        foods.clear();
        ghosts.clear();
        cherry = null;
        powerFood = null;

        for (int r = 0; r < rowCount; r++) {
            String row = tileMap[r];
            for (int c = 0; c < columnCount && c < row.length(); c++) {
                char tileMapChar = row.charAt(c);
                int x = c * tileSize;
                int y = r * tileSize;

                switch (tileMapChar) {
                    case 'X':
                        walls.add(new Block(wallImage, wallColor, x, y, tileSize, tileSize));
                        break;
                    case 'b':
                        Block blueGhost = new Block(blueGhostImage, null, x, y, tileSize, tileSize);
                        blueGhost.speed = ghostBaseSpeed;
                        ghosts.add(blueGhost);
                        break;
                    case 'o':
                        Block orangeGhost = new Block(orangeGhostImage, null, x, y, tileSize, tileSize);
                        orangeGhost.speed = ghostBaseSpeed;
                        ghosts.add(orangeGhost);
                        break;
                    case 'p':
                        Block pinkGhost = new Block(pinkGhostImage, null, x, y, tileSize, tileSize);
                        pinkGhost.speed = ghostBaseSpeed;
                        ghosts.add(pinkGhost);
                        break;
                    case 'r':
                        Block redGhost = new Block(redGhostImage, null, x, y, tileSize, tileSize);
                        redGhost.speed = ghostBaseSpeed;
                        ghosts.add(redGhost);
                        break;
                    case 'P':
                        pacman = new Block(null, Color.YELLOW, x, y, tileSize, tileSize);
                        pacman.direction = 'R';
                        pacman.speed = pacmanSpeed;
                        break;
                    case ' ':
                        foods.add(new Block(null, Color.WHITE, x + 14, y + 14, 4, 4));
                        break;
                }
            }
        }
        
        // Spawn initial items immediately after loading map
        spawnInitialItems();
    }
    
    private void spawnInitialItems() {
        ArrayList<Point> emptySpaces = new ArrayList<>();
        
        // Find all empty spaces not occupied by walls, pacman, or ghosts
        for (int r = 0; r < rowCount; r++) {
            String row = tileMap[r];
            for (int c = 0; c < columnCount && c < row.length(); c++) {
                if (row.charAt(c) == ' ') {
                    int x = c * tileSize;
                    int y = r * tileSize;
                    boolean occupied = false;
                    
                    // Check if this space is near pacman or ghosts
                    if (pacman != null && 
                        Math.abs(pacman.x - x) < tileSize && 
                        Math.abs(pacman.y - y) < tileSize) {
                        occupied = true;
                    }
                    
                    for (Block ghost : ghosts) {
                        if (Math.abs(ghost.x - x) < tileSize && 
                            Math.abs(ghost.y - y) < tileSize) {
                            occupied = true;
                            break;
                        }
                    }
                    
                    if (!occupied) {
                        emptySpaces.add(new Point(x, y));
                    }
                }
            }
        }
        
        // Spawn cherry if we have empty spaces
        if (!emptySpaces.isEmpty()) {
            Collections.shuffle(emptySpaces);
            Point cherryPos = emptySpaces.remove(0);
            cherry = new Block(cherryImage, null, cherryPos.x, cherryPos.y, tileSize, tileSize);
            
            // Spawn power food if we have another empty space
            if (!emptySpaces.isEmpty()) {
                Point powerPos = emptySpaces.remove(0);
                powerFood = new Block(powerFoodImage, null, powerPos.x + 8, powerPos.y + 8, 16, 16);
            }
        }
    }

    private void playMainMenuMusic() {
        stopAllMusic();
        mainMenuPlayer = new PlayerThread("/pacman/backsoundMain.mp3", true);
        mainMenuPlayer.start();
    }

    private void playInGameMusic() {
        stopAllMusic();
        inGamePlayer = new PlayerThread("/pacman/backsoundingame.mp3", true);
        inGamePlayer.start();
    }

    private void stopAllMusic() {
        if (mainMenuPlayer != null) {
            mainMenuPlayer.stopMusic();
            mainMenuPlayer = null;
        }
        if (inGamePlayer != null) {
            inGamePlayer.stopMusic();
            inGamePlayer = null;
        }
    }

    private void initializeGhosts() {
        for (Block ghost : ghosts) {
            resetGhostPosition(ghost);
        }
    }

    private void resetGhostPosition(Block ghost) {
        ghost.reset();
        
        // Try different directions until we find one that works
        ArrayList<Character> dirs = new ArrayList<>(Arrays.asList('U', 'D', 'L', 'R'));
        Collections.shuffle(dirs);
        
        for (char dir : dirs) {
            ghost.updateDirection(dir);
            if (ghost.moving) break;
        }
        
        // Force initial movement
        ghost.x += ghost.velocityX;
        ghost.y += ghost.velocityY;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g) {
        switch (gameState) {
            case MAIN_MENU:
                drawMainMenu(g);
                break;
            case LEVEL_SELECT:
                drawLevelSelect(g);
                break;
            case LEVEL_INFO:
                drawGame(g);
                drawLevelInfo(g);
                break;
            case PLAYING:
            case PAUSED:
                drawGame(g);
                if (gameState == GameState.PAUSED) {
                    drawPauseScreen(g);
                }
                break;
            case GAME_OVER:
                drawGame(g);
                drawGameOverScreen(g);
                break;
            case GAME_WIN:
                drawGame(g);
                drawGameWinScreen(g);
                break;
        }
    }
    
    private void drawMainMenu(Graphics g) {
        // Draw title
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "PAC-MAN";
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 4);
        
        // Draw current level and high score
        g.setFont(new Font("Arial", Font.BOLD, 24));
        String levelText = "Level: " + currentLevel;
        int levelX = (boardWidth - g.getFontMetrics().stringWidth(levelText)) / 2;
        g.drawString(levelText, levelX, boardHeight / 3);
        
        String highScoreText = "High Score: " + highScores.get(currentLevel);
        int highScoreX = (boardWidth - g.getFontMetrics().stringWidth(highScoreText)) / 2;
        g.drawString(highScoreText, highScoreX, boardHeight / 3 + 30);
        
        // Draw menu options
        g.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < MAIN_MENU_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.WHITE);
            }
            int optionX = (boardWidth - g.getFontMetrics().stringWidth(MAIN_MENU_OPTIONS[i])) / 2;
            int optionY = boardHeight / 2 + i * 40;
            g.drawString(MAIN_MENU_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawLevelSelect(Graphics g) {
        // Draw title
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "SELECT LEVEL";
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 4);
        
        // Draw level options
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < LEVEL_OPTIONS.length; i++) {
            if (i == selectedLevelOption) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.WHITE);
            }
            int optionX = (boardWidth - g.getFontMetrics().stringWidth(LEVEL_OPTIONS[i])) / 2;
            int optionY = boardHeight / 2 + i * 40;
            g.drawString(LEVEL_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawLevelInfo(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, boardWidth, boardHeight);
        
        // Level info text
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "LEVEL " + currentLevel;
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 2);
        
        String difficulty = "";
        switch(currentLevel) {
            case 1: difficulty = "Easy"; break;
            case 2: difficulty = "Medium"; break;
            case 3: difficulty = "Hard"; break;
        }
        
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        int diffX = (boardWidth - g.getFontMetrics().stringWidth(difficulty)) / 2;
        g.drawString(difficulty, diffX, boardHeight / 2 + 40);
        
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String startText = "Press any key to start";
        int startX = (boardWidth - g.getFontMetrics().stringWidth(startText)) / 2;
        g.drawString(startText, startX, boardHeight / 2 + 80);
    }
    
    private void drawGame(Graphics g) {
        // Draw walls
        for (Block wall : walls) {
            if (wall.image != null) {
                g.drawImage(wall.image, wall.x, wall.y, wall.width, wall.height, null);
            } else {
                g.setColor(wall.color);
                g.fillRect(wall.x, wall.y, wall.width, wall.height);
            }
        }

        // Draw food
        for (Block food : foods) {
            g.setColor(food.color);
            g.fillOval(food.x, food.y, food.width, food.height);
        }
        
        // Draw power food
        if (powerFood != null && powerFood.image != null) {
            g.drawImage(powerFood.image, powerFood.x, powerFood.y, powerFood.width, powerFood.height, null);
        }
        
        // Draw cherry
        if (cherry != null && cherry.image != null) {
            g.drawImage(cherry.image, cherry.x, cherry.y, cherry.width, cherry.height, null);
        }
        
        // Draw ghosts
        for (Block ghost : ghosts) {
            if (ghost.isScared) {
                if (scaredGhostImage != null) {
                    g.drawImage(scaredGhostImage, ghost.x, ghost.y, ghost.width, ghost.height, null);
                } else {
                    g.setColor(Color.CYAN);
                    g.fillRect(ghost.x, ghost.y, ghost.width, ghost.height);
                }
            } else {
                if (ghost.image != null) {
                    g.drawImage(ghost.image, ghost.x, ghost.y, ghost.width, ghost.height, null);
                } else {
                    g.setColor(Color.RED);
                    g.fillRect(ghost.x, ghost.y, ghost.width, ghost.height);
                }
            }
        }

        // Draw Pac-Man
        drawPacMan(g);
        
        // Draw UI
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        
        // Score and high score
        g.drawString("Score: " + currentScore, 10, 25);
        g.drawString("High: " + highScores.get(currentLevel), 150, 25);
        
        // Level
        g.drawString("Level: " + currentLevel, boardWidth / 2 - 30, 25);
        
        // Lives
        if (heartImage != null) {
            for (int i = 0; i < lives; i++) {
                g.drawImage(heartImage, 10 + (i * 30), boardHeight - 30, 25, 25, null);
            }
        } else {
            g.setColor(Color.RED);
            for (int i = 0; i < lives; i++) {
                g.fillOval(10 + (i * 30), boardHeight - 30, 25, 25);
            }
        }
        
        // Power-up timer
        if (isPoweredUp) {
            int timeLeft = (POWER_UP_DURATION - powerUpTimer) / 60;
            g.setColor(Color.YELLOW);
            String powerText = "Power: " + timeLeft + "s";
            int powerX = (boardWidth - g.getFontMetrics().stringWidth(powerText)) / 2;
            g.drawString(powerText, powerX, boardHeight - 10);
        }
    }

    private void drawPacMan(Graphics g) {
        Color pacmanColor = isPoweredUp ? Color.ORANGE : Color.YELLOW;
        g.setColor(pacmanColor);
        
        int startAngle = 0;
        int arcAngle = 360;
        
        // Update animation
        pacmanSpriteCounter++;
        if (pacmanSpriteCounter > PACMAN_ANIMATION_SPEED) {
            pacmanSpriteNum = (pacmanSpriteNum == 1) ? 2 : 1;
            pacmanSpriteCounter = 0;
        }
        
        if (pacmanSpriteNum == 2) {
            switch (pacman.direction) {
                case 'U': startAngle = 135; arcAngle = 270; break;
                case 'D': startAngle = 315; arcAngle = 270; break;
                case 'L': startAngle = 225; arcAngle = 270; break;
                case 'R': startAngle = 45; arcAngle = 270; break;
            }
        }
        
        // Draw Pac-Man
        g.fillArc(pacman.x, pacman.y, pacman.width, pacman.height, startAngle, arcAngle);
        
        // Draw eye
        g.setColor(Color.BLACK);
        int eyeSize = tileSize / 8;
        int eyeX = pacman.x + tileSize/2;
        int eyeY = pacman.y + tileSize/4;
        
        switch (pacman.direction) {
            case 'U': eyeX -= eyeSize/2; break;
            case 'D': eyeX -= eyeSize/2; eyeY = pacman.y + tileSize/2; break;
            case 'L': eyeX = pacman.x + tileSize/4; eyeY -= eyeSize/2; break;
            case 'R': eyeX = pacman.x + tileSize*3/4 - eyeSize; eyeY -= eyeSize/2; break;
        }
        
        g.fillOval(eyeX, eyeY, eyeSize, eyeSize);
    }

    private void drawPauseScreen(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, boardWidth, boardHeight);
        
        // Pause text
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "PAUSED";
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 3);
        
        // Draw menu options
        g.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < PAUSE_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.WHITE);
            }
            int optionX = (boardWidth - g.getFontMetrics().stringWidth(PAUSE_OPTIONS[i])) / 2;
            int optionY = boardHeight / 2 + i * 40;
            g.drawString(PAUSE_OPTIONS[i], optionX, optionY);
        }
    }

    private void drawGameOverScreen(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, boardWidth, boardHeight);
        
        // Game over text
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "GAME OVER";
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 3);
        
        // Score text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String scoreText = "Score: " + currentScore;
        int scoreX = (boardWidth - g.getFontMetrics().stringWidth(scoreText)) / 2;
        g.drawString(scoreText, scoreX, boardHeight / 2 - 40);
        
        // Draw menu options
        g.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < GAME_OVER_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.WHITE);
            }
            int optionX = (boardWidth - g.getFontMetrics().stringWidth(GAME_OVER_OPTIONS[i])) / 2;
            int optionY = boardHeight / 2 + i * 40;
            g.drawString(GAME_OVER_OPTIONS[i], optionX, optionY);
        }
    }

    private void drawGameWinScreen(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, boardWidth, boardHeight);
        
        // Game win text
        g.setColor(Color.GREEN);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "LEVEL COMPLETE!";
        int titleX = (boardWidth - g.getFontMetrics().stringWidth(title)) / 2;
        g.drawString(title, titleX, boardHeight / 3);
        
        // Score text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String scoreText = "Score: " + currentScore;
        int scoreX = (boardWidth - g.getFontMetrics().stringWidth(scoreText)) / 2;
        g.drawString(scoreText, scoreX, boardHeight / 2 - 40);
        
        // Draw menu options
        g.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < GAME_WIN_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.WHITE);
            }
            int optionX = (boardWidth - g.getFontMetrics().stringWidth(GAME_WIN_OPTIONS[i])) / 2;
            int optionY = boardHeight / 2 + i * 40;
            g.drawString(GAME_WIN_OPTIONS[i], optionX, optionY);
        }
    }

    public void move() {
        if (gameState != GameState.PLAYING && gameState != GameState.LEVEL_INFO) return;
        
        if (gameState == GameState.LEVEL_INFO) {
            levelInfoTimer--;
            if (levelInfoTimer <= 0) {
                gameState = GameState.PLAYING;
                playInGameMusic();
            }
            repaint();
            return;
        }
        
        // Update power-up timer
        if (isPoweredUp) {
            powerUpTimer++;
            if (powerUpTimer >= POWER_UP_DURATION) {
                isPoweredUp = false;
                powerUpTimer = 0;
                for (Block ghost : ghosts) {
                    ghost.isScared = false;
                    ghost.isFrozen = false;
                }
            }
        }
        
        // Spawn cherry periodically
        cherrySpawnTimer++;
        if (cherry == null && cherrySpawnTimer > CHERRY_SPAWN_INTERVAL && 
            random.nextDouble() < 0.015) {
            spawnCherry();
            cherrySpawnTimer = 0;
        }
        
        // Spawn power food periodically (only if none exists)
        powerFoodSpawnTimer++;
        if (powerFood == null && powerFoodSpawnTimer > POWER_FOOD_SPAWN_INTERVAL && 
            random.nextDouble() < 0.01) {
            spawnPowerFood();
            powerFoodSpawnTimer = 0;
        }
        
        // Move Pac-Man
        pacman.x += pacman.velocityX;
        pacman.y += pacman.velocityY;

        // Wall collision for Pac-Man
        for (Block wall : walls) {
            if (collision(pacman, wall)) {
                pacman.x -= pacman.velocityX;
                pacman.y -= pacman.velocityY;
                break;
            }
        }

        // Move and handle ghosts
        ghostDirectionChangeCounter++;
        for (Block ghost : ghosts) {
            if (ghost.isFrozen) continue;
            
            // Change direction periodically or when stuck
            if (ghostDirectionChangeCounter % GHOST_DIRECTION_CHANGE_INTERVAL == 0 || !ghost.moving) {
                if (ghost.isScared) {
                    // Improved escape behavior
                    char bestDir = getBestEscapeDirection(ghost);
                    ghost.updateDirection(bestDir);
                } else {
                    double distanceToPacman = Math.sqrt(
                        Math.pow(ghost.x - pacman.x, 2) + 
                        Math.pow(ghost.y - pacman.y, 2));
                    
                    // More intelligent chasing with randomness
                    if (distanceToPacman < ghostChaseRange) {
                        double chaseRand = random.nextDouble();
                        if (chaseRand < ghostChaseProbability) {
                            // Chase Pac-Man
                            char chaseDirection = getDirectionTowardsPacman(ghost);
                            ghost.updateDirection(chaseDirection);
                        } else if (chaseRand < ghostChaseProbability + 0.1) {
                            // Move randomly
                            char newDirection = directions[random.nextInt(4)];
                            ghost.updateDirection(newDirection);
                        } else {
                            // Move to intercept Pac-Man
                            char interceptDirection = getInterceptDirection(ghost);
                            ghost.updateDirection(interceptDirection);
                        }
                    } else {
                        // Random movement when far away
                        char newDirection = directions[random.nextInt(4)];
                        ghost.updateDirection(newDirection);
                    }
                }
            }
            
            
            // Move ghost
            ghost.x += ghost.velocityX;
            ghost.y += ghost.velocityY;
            
            // Check ghost-wall collisions
            boolean ghostCollided = false;
            for (Block wall : walls) {
                if (collision(ghost, wall)) {
                    ghost.x -= ghost.velocityX;
                    ghost.y -= ghost.velocityY;
                    ghost.moving = false;
                    ghostCollided = true;
                    break;
                }
            }
            
            if (!ghostCollided) {
                ghost.moving = true;
            }
            
            // Check ghost-pacman collision
            if (collision(ghost, pacman)) {
                if (isPoweredUp && ghost.isScared) {
                    ghost.reset();
                    ghost.isScared = false;
                    currentScore += 200;
                    if (currentScore > highScores.get(currentLevel)) {
                        highScores.put(currentLevel, currentScore);
                    }
                } else if (!isPoweredUp) {
                    lives--;
                    if (lives <= 0) {
                        gameState = GameState.GAME_OVER;
                        stopAllMusic();
                        return;
                    }
                    resetPositions();
                    break;
                }
            }
            
        }
        

        // Food collision
        Block foodEaten = null;
        for (Block food : foods) {
            if (collision(pacman, food)) {
                foodEaten = food;
                currentScore += 10;
                if (currentScore > highScores.get(currentLevel)) {
                    highScores.put(currentLevel, currentScore);
                }
            }
        }
        foods.remove(foodEaten);
        
        // Power food collision
        if (powerFood != null && collision(pacman, powerFood)) {
            currentScore += 50;
            isPoweredUp = true;
            powerUpTimer = 0;
            for (Block ghost : ghosts) {
                ghost.isScared = true;
                ghost.isFrozen = false;
            }
            powerFood = null;
        }
        
        // Cherry collision
        if (cherry != null && collision(pacman, cherry)) {
            currentScore += 100;
            cherry = null;
        }

        if (foods.isEmpty() && powerFood == null) {
            if (currentLevel < MAX_LEVEL) {
                currentLevel++;
                setLevelDifficulty(currentLevel);
                loadMap();
                initializeGhosts();
                resetPositions();
                showLevelInfo();
            } else {
                gameState = GameState.GAME_WIN;
                stopAllMusic();
            }
        }
    }
    
    private char getBestEscapeDirection(Block ghost) {
        // Calculate distance to Pac-Man
        int dx = pacman.x - ghost.x;
        int dy = pacman.y - ghost.y;
        
        // Try directions that maximize distance from Pac-Man
        char[] possibleDirs = {
            dx > 0 ? 'L' : 'R', // Opposite X direction
            dy > 0 ? 'U' : 'D',  // Opposite Y direction
            directions[random.nextInt(4)], // Random direction as fallback
            directions[random.nextInt(4)]  // Another random direction
        };
        
        // Try each direction until we find a valid one
        for (char dir : possibleDirs) {
            ghost.updateDirection(dir);
            if (ghost.moving) {
                return dir;
            }
        }
        
        // If all else fails, don't move
        return ghost.direction;
    }

    private char getDirectionTowardsPacman(Block ghost) {
        int dx = pacman.x - ghost.x;
        int dy = pacman.y - ghost.y;
        
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? 'R' : 'L';
        } else {
            return dy > 0 ? 'D' : 'U';
        }
    }

    private void spawnCherry() {
        ArrayList<Point> emptySpaces = getEmptySpaces();
        if (!emptySpaces.isEmpty()) {
            Point spawnPos = emptySpaces.get(random.nextInt(emptySpaces.size()));
            cherry = new Block(cherryImage, null, spawnPos.x, spawnPos.y, tileSize, tileSize);
        }
    }
    
    private void spawnPowerFood() {
        ArrayList<Point> emptySpaces = getEmptySpaces();
        if (!emptySpaces.isEmpty()) {
            Point spawnPos = emptySpaces.get(random.nextInt(emptySpaces.size()));
            powerFood = new Block(powerFoodImage, null, spawnPos.x + 8, spawnPos.y + 8, 16, 16);
        }
    }
    
    private ArrayList<Point> getEmptySpaces() {
        ArrayList<Point> emptySpaces = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            String row = tileMap[r];
            for (int c = 0; c < Math.min(columnCount, row.length()); c++) {
                if (row.charAt(c) == ' ') {
                    Point testPoint = new Point(c * tileSize, r * tileSize);
                    boolean occupied = false;
                    
                    // Check if this space already has food
                    for (Block food : foods) {
                        if (Math.abs(food.x - testPoint.x) < tileSize && 
                            Math.abs(food.y - testPoint.y) < tileSize) {
                            occupied = true;
                            break;
                        }
                    }
                    
                    if (!occupied && 
                        (cherry == null || !(Math.abs(cherry.x - testPoint.x) < tileSize && 
                                             Math.abs(cherry.y - testPoint.y) < tileSize)) &&
                        (powerFood == null || !(Math.abs(powerFood.x - testPoint.x) < tileSize && 
                                              Math.abs(powerFood.y - testPoint.y) < tileSize))) {
                        emptySpaces.add(testPoint);
                    }
                }
            }
        }
        return emptySpaces;
    }
    private char getInterceptDirection(Block ghost) {
        // Predict Pac-Man's future position based on current direction
        int predictSteps = 5;
        int pacmanFutureX = pacman.x;
        int pacmanFutureY = pacman.y;
        
        switch (pacman.direction) {
            case 'U': pacmanFutureY -= predictSteps * pacman.speed; break;
            case 'D': pacmanFutureY += predictSteps * pacman.speed; break;
            case 'L': pacmanFutureX -= predictSteps * pacman.speed; break;
            case 'R': pacmanFutureX += predictSteps * pacman.speed; break;
        }
        
        // Get direction towards predicted position
        int dx = pacmanFutureX - ghost.x;
        int dy = pacmanFutureY - ghost.y;
        
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? 'R' : 'L';
        } else {
            return dy > 0 ? 'D' : 'U';
        }
    }
    

    private void showLevelInfo() {
        gameState = GameState.LEVEL_INFO;
        levelInfoTimer = LEVEL_INFO_DURATION;
    }

    public boolean collision(Block a, Block b) {
        int padding = 2;
        return a.x + padding < b.x + b.width - padding &&
               a.x + a.width - padding > b.x + padding &&
               a.y + padding < b.y + b.height - padding &&
               a.y + a.height - padding > b.y + padding;
    }

    public void resetPositions() {
        pacman.reset();
        pacman.velocityX = 0;
        pacman.velocityY = 0;
        
        for (Block ghost : ghosts) {
            resetGhostPosition(ghost);
        }
        
        isPoweredUp = false;
        powerUpTimer = 0;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        move();
        repaint();
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        
        switch (gameState) {
            case MAIN_MENU:
                handleMainMenuInput(code);
                break;
            case LEVEL_SELECT:
                handleLevelSelectInput(code);
                break;
            case PLAYING:
                handlePlayingInput(code);
                break;
            case PAUSED:
                handlePauseMenuInput(code);
                break;
            case GAME_OVER:
                handleGameOverInput(code);
                break;
            case GAME_WIN:
                handleGameWinInput(code);
                break;
            case LEVEL_INFO:
                if (code != KeyEvent.VK_ESCAPE) {
                    gameState = GameState.PLAYING;
                    playInGameMusic();
                }
                break;
        }
    }
    
    private void handleMainMenuInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + MAIN_MENU_OPTIONS.length) % MAIN_MENU_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % MAIN_MENU_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Start Game
                    currentScore = 0;
                    lives = 3;
                    loadMap();
                    initializeGhosts();
                    showLevelInfo();
                    break;
                case 1: // Level Select
                    selectedLevelOption = 0;
                    gameState = GameState.LEVEL_SELECT;
                    break;
                case 2: // Quit
                    stopAllMusic();
                    System.exit(0);
                    break;
            }
        }
    }
    
    private void handleLevelSelectInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedLevelOption = (selectedLevelOption - 1 + LEVEL_OPTIONS.length) % LEVEL_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_DOWN) {
            selectedLevelOption = (selectedLevelOption + 1) % LEVEL_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_ENTER) {
            if (selectedLevelOption < 3) {
                setLevelDifficulty(selectedLevelOption + 1);
                gameState = GameState.MAIN_MENU;
            } else {
                gameState = GameState.MAIN_MENU;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            gameState = GameState.MAIN_MENU;
        }
    }
    
    private void handlePlayingInput(int code) {
        if (code == KeyEvent.VK_UP) {
            pacman.updateDirection('U');
        } else if (code == KeyEvent.VK_DOWN) {
            pacman.updateDirection('D');
        } else if (code == KeyEvent.VK_LEFT) {
            pacman.updateDirection('L');
        } else if (code == KeyEvent.VK_RIGHT) {
            pacman.updateDirection('R');
        } else if (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_P) {
            selectedMenuOption = 0;
            gameState = GameState.PAUSED;
        }
    }
    
    private void handlePauseMenuInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + PAUSE_OPTIONS.length) % PAUSE_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % PAUSE_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Resume
                    gameState = GameState.PLAYING;
                    break;
                case 1: // Restart
                    loadMap();
                    initializeGhosts();
                    resetPositions();
                    currentScore = 0;
                    lives = 3;
                    gameState = GameState.PLAYING;
                    break;
                case 2: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    playMainMenuMusic();
                    break;
                case 3: // Quit
                    stopAllMusic();
                    System.exit(0);
                    break;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            gameState = GameState.PLAYING;
        }
    }
    
    private void handleGameOverInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + GAME_OVER_OPTIONS.length) % GAME_OVER_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % GAME_OVER_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Restart
                    loadMap();
                    initializeGhosts();
                    resetPositions();
                    currentScore = 0;
                    lives = 3;
                    gameState = GameState.PLAYING;
                    playInGameMusic();
                    break;
                case 1: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    playMainMenuMusic();
                    break;
                case 2: // Quit
                    stopAllMusic();
                    System.exit(0);
                    break;
            }
        }
    }
    
    private void handleGameWinInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + GAME_WIN_OPTIONS.length) % GAME_WIN_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % GAME_WIN_OPTIONS.length;
            repaint();
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Next Level
                    if (currentLevel < MAX_LEVEL) {
                        currentLevel++;
                        setLevelDifficulty(currentLevel);
                        loadMap();
                        initializeGhosts();
                        resetPositions();
                        showLevelInfo();
                    } else {
                        gameState = GameState.MAIN_MENU;
                        playMainMenuMusic();
                    }
                    break;
                case 1: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    playMainMenuMusic();
                    break;
                case 2: // Quit
                    stopAllMusic();
                    System.exit(0);
                    break;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Pac Man");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        
        PacMan pacmanGame = new PacMan();
        frame.add(pacmanGame);
        frame.pack();
        
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        pacmanGame.requestFocus();
    }
}