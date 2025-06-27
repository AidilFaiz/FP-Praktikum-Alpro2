package pacman2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

public class PacMan extends JPanel implements Runnable, KeyListener {
    // Game states
    public enum GameState {
        MAIN_MENU, LEVEL_SELECT, PLAYING, GAME_OVER, GAME_WIN, PAUSED
    }
    
    private GameState gameState = GameState.MAIN_MENU;
    private int selectedMenuOption = 0;
    private int selectedLevelOption = 0;
    private int currentLevel = 1;
    private final int MAX_LEVEL = 3;
    
    // Game settings
    public final int TILE_SIZE = 32;
    public int MAX_COL = 19;
    public int MAX_ROW = 21;
    public int SCREEN_WIDTH, SCREEN_HEIGHT;
    
    // Game components
    private Thread gameThread;
    private final int FPS = 60;
    
    // Pac-Man properties
    public int pacManX, pacManY;
    public int initialPacManX, initialPacManY;
    public int pacManSpeed;
    public String pacManDirection;
    public Rectangle pacManSolidArea;
    public int pacManSpriteNum = 1;
    int pacManSpriteCounter = 0;

    // Ghosts properties
    public Ghost[] ghosts;
    public Rectangle ghostSolidArea;
    Random random = new Random();

    // Game map and items
    private String[] defaultTileMapLayout;
    private int[][] map;
    private HashSet<Point> foodLocations;
    
    // Game stats
    private int score = 0;
    private int lives = 3;
    private boolean gameRunning = false;
    
    // Menu options
    private final String[] MAIN_MENU_OPTIONS = {"Start Game", "Level Select", "Quit"};
    private final String[] LEVEL_OPTIONS = {"Level 1", "Level 2", "Level 3", "Back"};
    private final String[] GAME_OVER_OPTIONS = {"Restart", "Main Menu", "Quit"};
    private final String[] GAME_WIN_OPTIONS = {"Next Level", "Main Menu", "Quit"};
    private final String[] PAUSE_OPTIONS = {"Resume", "Restart", "Main Menu", "Quit"};
    
    public PacMan() {
        initializeScreenSize();
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true);
        this.addKeyListener(this);
        this.setFocusable(true);

        pacManSolidArea = new Rectangle(8, 8, TILE_SIZE - 16, TILE_SIZE - 16);
        ghostSolidArea = new Rectangle(8, 8, TILE_SIZE - 16, TILE_SIZE - 16);
        
        initializeDefaultMap();
        setDefaultValues();
    }
    
    private void initializeScreenSize() {
        SCREEN_WIDTH = TILE_SIZE * MAX_COL;
        SCREEN_HEIGHT = TILE_SIZE * MAX_ROW;
    }
    
    private void initializeDefaultMap() {
        defaultTileMapLayout = new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X        X        X",
            "X XX XXX X XXX XX X",
            "X                 X",
            "X XX X XXXXX X XX X",
            "X    X       X    X",
            "XXXX XXXX XXXX XXXX",
            "OOOX X       X XOOO",
            "XXXX X XXrXX X XXXX",
            "O       bpo       O",
            "XXXX X XXXXX X XXXX",
            "OOOX X       X XOOO",
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
    }

    public void setDefaultValues() {
        foodLocations = new HashSet<>();
        score = 0;
        lives = 3;
        gameRunning = true;
        
        // Initialize ghosts array
        ghosts = new Ghost[4];
        for (int i = 0; i < ghosts.length; i++) {
            ghosts[i] = new Ghost();
        }
        
        initializeMapAndCharacters();
    }
    
    public void loadLevel(int level) {
        currentLevel = level;
        setDefaultValues();
        
        // Adjust game difficulty based on level
        switch(level) {
            case 1:
                pacManSpeed = 4;
                for (Ghost ghost : ghosts) ghost.speed = 2;
                break;
            case 2:
                pacManSpeed = 5;
                for (Ghost ghost : ghosts) ghost.speed = 3;
                break;
            case 3:
                pacManSpeed = 6;
                for (Ghost ghost : ghosts) ghost.speed = 4;
                break;
        }
    }

    public void startGame() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        double drawInterval = 1000000000.0 / FPS;
        double delta = 0;
        long lastTime = System.nanoTime();
        long currentTime;

        while (gameThread != null) {
            currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            lastTime = currentTime;

            if (delta >= 1) {
                if (gameState == GameState.PLAYING) {
                    update();
                }
                repaint();
                delta--;
            }
        }
    }

    public void update() {
        updatePacMan();
        updateGhosts();
        checkCollisionPacManGhosts();
    }

    private void updatePacMan() {
        boolean collisionWithWall = false;

        int pacManLeftX = pacManX + pacManSolidArea.x;
        int pacManRightX = pacManX + pacManSolidArea.x + pacManSolidArea.width;
        int pacManTopY = pacManY + pacManSolidArea.y;
        int pacManBottomY = pacManY + pacManSolidArea.y + pacManSolidArea.height;

        switch (pacManDirection) {
            case "up":
                if (getTileTypeAt(pacManLeftX, pacManTopY - pacManSpeed) == 1 ||
                    getTileTypeAt(pacManRightX, pacManTopY - pacManSpeed) == 1) {
                    collisionWithWall = true;
                }
                break;
            case "down":
                if (getTileTypeAt(pacManLeftX, pacManBottomY + pacManSpeed) == 1 ||
                    getTileTypeAt(pacManRightX, pacManBottomY + pacManSpeed) == 1) {
                    collisionWithWall = true;
                }
                break;
            case "left":
                if (getTileTypeAt(pacManLeftX - pacManSpeed, pacManTopY) == 1 ||
                    getTileTypeAt(pacManLeftX - pacManSpeed, pacManBottomY) == 1) {
                    collisionWithWall = true;
                }
                break;
            case "right":
                if (getTileTypeAt(pacManRightX + pacManSpeed, pacManTopY) == 1 ||
                    getTileTypeAt(pacManRightX + pacManSpeed, pacManBottomY) == 1) {
                    collisionWithWall = true;
                }
                break;
        }

        if (!collisionWithWall) {
            switch (pacManDirection) {
                case "up":    pacManY -= pacManSpeed; break;
                case "down":  pacManY += pacManSpeed; break;
                case "left":  pacManX -= pacManSpeed; break;
                case "right": pacManX += pacManSpeed; break;
            }
        }
        
        if (pacManY == TILE_SIZE * 7 || pacManY == TILE_SIZE * 11) { 
            if (pacManX + TILE_SIZE <= 0 && pacManDirection.equals("left")) {
                pacManX = SCREEN_WIDTH;
            } else if (pacManX >= SCREEN_WIDTH && pacManDirection.equals("right")) {
                pacManX = -TILE_SIZE;
            }
        }

        int pacManTileCol = (pacManX + TILE_SIZE / 2) / TILE_SIZE;
        int pacManTileRow = (pacManY + TILE_SIZE / 2) / TILE_SIZE;
        eatFood(pacManTileCol, pacManTileRow);

        if (pacManX % pacManSpeed == 0 && pacManY % pacManSpeed == 0) {
            pacManSpriteCounter++;
            if (pacManSpriteCounter > 10) {
                if (pacManSpriteNum == 1) { pacManSpriteNum = 2; }
                else if (pacManSpriteNum == 2) { pacManSpriteNum = 1; }
                pacManSpriteCounter = 0;
                
                
                
                
            }
        } else {
            if (collisionWithWall && pacManSpeed > 0) {
                pacManSpriteNum = 1;
                pacManSpriteCounter = 0;
            }
        }
    }

    private void updateGhosts() {
        for (Ghost ghost : ghosts) {
            updateSingleGhost(ghost);
        }
    }

    private void updateSingleGhost(Ghost ghost) {
        ghost.directionChangeCounter++;
        
        if (ghost.directionChangeCounter > 60 || willCollideWithWall(ghost.x, ghost.y, ghost.direction)) {
            int attempts = 0;
            boolean foundNewDirection = false;
            String[] possibleDirections = {"up", "down", "left", "right"};
            
            for (int i = 0; i < possibleDirections.length; i++) {
                int swapIndex = random.nextInt(possibleDirections.length);
                String temp = possibleDirections[i];
                possibleDirections[i] = possibleDirections[swapIndex];
                possibleDirections[swapIndex] = temp;
            }

            for (String newDir : possibleDirections) {
                if (!willCollideWithWall(ghost.x, ghost.y, newDir)) {
                    ghost.direction = newDir;
                    foundNewDirection = true;
                    break;
                }
            }
            
            ghost.directionChangeCounter = 0;
        }

        if (!willCollideWithWall(ghost.x, ghost.y, ghost.direction)) {
            switch (ghost.direction) {
                case "up":    ghost.y -= ghost.speed; break;
                case "down":  ghost.y += ghost.speed; break;
                case "left":  ghost.x -= ghost.speed; break;
                case "right": ghost.x += ghost.speed; break;
            }
        }
        
        if (ghost.y == TILE_SIZE * 7 || ghost.y == TILE_SIZE * 11) {
            if (ghost.x + TILE_SIZE <= 0 && ghost.direction.equals("left")) {
                ghost.x = SCREEN_WIDTH;
            } else if (ghost.x >= SCREEN_WIDTH && ghost.direction.equals("right")) {
                ghost.x = -TILE_SIZE;
            }
        }
    }

    private boolean willCollideWithWall(int currentX, int currentY, String testDirection) {
        int testX = currentX;
        int testY = currentY;
        
        switch (testDirection) {
            case "up":    testY -= ghosts[0].speed; break;
            case "down":  testY += ghosts[0].speed; break;
            case "left":  testX -= ghosts[0].speed; break;
            case "right": testX += ghosts[0].speed; break;
        }

        int ghostLeftX = testX + ghostSolidArea.x;
        int ghostRightX = testX + ghostSolidArea.x + ghostSolidArea.width;
        int ghostTopY = testY + ghostSolidArea.y;
        int ghostBottomY = testY + ghostSolidArea.y + ghostSolidArea.height;

        return (getTileTypeAt(ghostLeftX, ghostTopY) == 1 ||
                getTileTypeAt(ghostRightX, ghostTopY) == 1 ||
                getTileTypeAt(ghostLeftX, ghostBottomY) == 1 ||
                getTileTypeAt(ghostRightX, ghostBottomY) == 1);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        switch (gameState) {
            case MAIN_MENU:
                drawMainMenu(g2);
                break;
            case LEVEL_SELECT:
                drawLevelSelect(g2);
                break;
            case PLAYING:
            case PAUSED:
                drawGame(g2);
                if (gameState == GameState.PAUSED) {
                    drawPauseScreen(g2);
                }
                break;
            case GAME_OVER:
                drawGame(g2);
                drawGameOverScreen(g2);
                break;
            case GAME_WIN:
                drawGame(g2);
                drawGameWinScreen(g2);
                break;
        }

        g2.dispose();
    }
    
    private void drawMainMenu(Graphics2D g2) {
        // Draw title
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "PAC-MAN";
        int titleX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;
        g2.drawString(title, titleX, SCREEN_HEIGHT / 4);
        
        // Draw menu options
        g2.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < MAIN_MENU_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(Color.WHITE);
            }
            int optionX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(MAIN_MENU_OPTIONS[i])) / 2;
            int optionY = SCREEN_HEIGHT / 2 + i * 40;
            g2.drawString(MAIN_MENU_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawLevelSelect(Graphics2D g2) {
        // Draw title
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "SELECT LEVEL";
        int titleX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;
        g2.drawString(title, titleX, SCREEN_HEIGHT / 4);
        
        // Draw level options
        g2.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < LEVEL_OPTIONS.length; i++) {
            if (i == selectedLevelOption) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(Color.WHITE);
            }
            int optionX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(LEVEL_OPTIONS[i])) / 2;
            int optionY = SCREEN_HEIGHT / 2 + i * 40;
            g2.drawString(LEVEL_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawGame(Graphics2D g2) {
        // Draw map
        for (int row = 0; row < MAX_ROW; row++) {
            for (int col = 0; col < MAX_COL; col++) {
                int tileType = map[row][col];
                int x = col * TILE_SIZE;
                int y = row * TILE_SIZE;

                if (tileType == 1) {
                    g2.setColor(Color.BLUE.darker());
                    g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                    g2.setColor(Color.BLUE.brighter());
                    g2.drawRect(x, y, TILE_SIZE, TILE_SIZE);
                } else if (tileType == 2) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x + TILE_SIZE / 2 - 3, y + TILE_SIZE / 2 - 3, 6, 6);
                }
            }
        }

        // Draw Pac-Man
        drawPacMan(g2);

        // Draw Ghosts
        drawGhost(g2, ghosts[0].x, ghosts[0].y, Color.RED);    // Red
        drawGhost(g2, ghosts[1].x, ghosts[1].y, Color.CYAN);   // Blue
        drawGhost(g2, ghosts[2].x, ghosts[2].y, Color.PINK);   // Pink
        drawGhost(g2, ghosts[3].x, ghosts[3].y, Color.ORANGE); // Orange

        // Draw score and lives
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20)); 
        g2.drawString("Score: " + score, 10, 25);
        g2.drawString("Lives: " + lives, SCREEN_WIDTH - 100, 25);
        g2.drawString("Level: " + currentLevel, SCREEN_WIDTH / 2 - 30, 25);
    }
    
    private void drawPauseScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "PAUSED";
        int titleX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;
        g2.drawString(title, titleX, SCREEN_HEIGHT / 3);
        
        // Draw pause menu options
        g2.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < PAUSE_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(Color.WHITE);
            }
            int optionX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(PAUSE_OPTIONS[i])) / 2;
            int optionY = SCREEN_HEIGHT / 2 + i * 40;
            g2.drawString(PAUSE_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawGameOverScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        g2.setColor(Color.RED);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "GAME OVER";
        int titleX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;
        g2.drawString(title, titleX, SCREEN_HEIGHT / 3);
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        String scoreText = "Final Score: " + score;
        int scoreX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(scoreText)) / 2;
        g2.drawString(scoreText, scoreX, SCREEN_HEIGHT / 2 - 20);
        
        // Draw game over options
        g2.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < GAME_OVER_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(Color.WHITE);
            }
            int optionX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(GAME_OVER_OPTIONS[i])) / 2;
            int optionY = SCREEN_HEIGHT / 2 + i * 40;
            g2.drawString(GAME_OVER_OPTIONS[i], optionX, optionY);
        }
    }
    
    private void drawGameWinScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        g2.setColor(Color.GREEN);
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "YOU WIN!";
        int titleX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(title)) / 2;
        g2.drawString(title, titleX, SCREEN_HEIGHT / 3);
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        String scoreText = "Score: " + score;
        int scoreX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(scoreText)) / 2;
        g2.drawString(scoreText, scoreX, SCREEN_HEIGHT / 2 - 20);
        
        // Draw win options
        g2.setFont(new Font("Arial", Font.BOLD, 32));
        for (int i = 0; i < GAME_WIN_OPTIONS.length; i++) {
            if (i == selectedMenuOption) {
                g2.setColor(Color.YELLOW);
            } else {
                g2.setColor(Color.WHITE);
            }
            int optionX = (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(GAME_WIN_OPTIONS[i])) / 2;
            int optionY = SCREEN_HEIGHT / 2 + i * 40;
            g2.drawString(GAME_WIN_OPTIONS[i], optionX, optionY);
        }
    }

    private void drawPacMan(Graphics2D g2) {
        g2.setColor(Color.YELLOW);

        int startAngle = 0;
        int arcAngle = 360;

        if (pacManSpriteNum == 1) {
            startAngle = 0;
            arcAngle = 360;
        } else if (pacManSpriteNum == 2) {
            switch (pacManDirection) {
                case "up":    startAngle = 135; arcAngle = 270; break;
                case "down":  startAngle = 315; arcAngle = 270; break;
                case "left":  startAngle = 225; arcAngle = 270; break;
                case "right": startAngle = 45;  arcAngle = 270; break;
                default:      startAngle = 0;   arcAngle = 360; break;
            }
        }
        
        g2.fillArc(pacManX, pacManY, TILE_SIZE, TILE_SIZE, startAngle, arcAngle);
    }

    private void drawGhost(Graphics2D g2, int x, int y, Color color) {
        g2.setColor(color);
        g2.fillRect(x, y, TILE_SIZE, TILE_SIZE);
        
        g2.setColor(Color.WHITE);
        g2.fillOval(x + TILE_SIZE/4, y + TILE_SIZE/4, TILE_SIZE/4, TILE_SIZE/4);
        g2.fillOval(x + TILE_SIZE/2, y + TILE_SIZE/4, TILE_SIZE/4, TILE_SIZE/4);
        g2.setColor(Color.BLACK);
        g2.fillOval(x + TILE_SIZE/4 + 3, y + TILE_SIZE/4 + 3, TILE_SIZE/8, TILE_SIZE/8);
        g2.fillOval(x + TILE_SIZE/2 + 3, y + TILE_SIZE/4 + 3, TILE_SIZE/8, TILE_SIZE/8);
    }

    private void initializeMapAndCharacters() {
        map = new int[MAX_ROW][MAX_COL];
        
        pacManSpeed = 4;
        for (Ghost ghost : ghosts) {
            ghost.speed = 2;
        }

        for (int r = 0; r < MAX_ROW; r++) {
            String rowString = defaultTileMapLayout[r];
            for (int c = 0; c < MAX_COL; c++) {
                char tileChar = rowString.charAt(c);
                int x = c * TILE_SIZE;
                int y = r * TILE_SIZE;

                switch (tileChar) {
                    case 'X': // Wall
                        map[r][c] = 1;
                        break;
                    case ' ': // Food
                        map[r][c] = 2;
                        foodLocations.add(new Point(c, r));
                        break;
                    case 'P': // Pac-Man start
                        map[r][c] = 0;
                        pacManX = x; pacManY = y;
                        initialPacManX = x; initialPacManY = y;
                        pacManDirection = "right";
                        break;
                    case 'r': // Red Ghost
                        map[r][c] = 0;
                        ghosts[0].x = x; ghosts[0].y = y;
                        ghosts[0].initialX = x; ghosts[0].initialY = y;
                        ghosts[0].direction = "up";
                        break;
                    case 'b': // Blue Ghost
                        map[r][c] = 0;
                        ghosts[1].x = x; ghosts[1].y = y;
                        ghosts[1].initialX = x; ghosts[1].initialY = y;
                        ghosts[1].direction = "up";
                        break;
                    case 'p': // Pink Ghost
                        map[r][c] = 0;
                        ghosts[2].x = x; ghosts[2].y = y;
                        ghosts[2].initialX = x; ghosts[2].initialY = y;
                        ghosts[2].direction = "up";
                        break;
                    case 'o': // Orange Ghost
                        map[r][c] = 0;
                        ghosts[3].x = x; ghosts[3].y = y;
                        ghosts[3].initialX = x; ghosts[3].initialY = y;
                        ghosts[3].direction = "up";
                        break;
                    default: // Empty space or tunnel
                        map[r][c] = 0;
                        break;
                }
            }
        }
    }

    public int getTileTypeAt(int pixelX, int pixelY) {
        if (pixelY == TILE_SIZE * 7 || pixelY == TILE_SIZE * 11) {
            if (pixelX < -TILE_SIZE) pixelX = SCREEN_WIDTH - 1;
            if (pixelX >= SCREEN_WIDTH + TILE_SIZE) pixelX = 0;
        }

        if (pixelX < 0 || pixelX >= SCREEN_WIDTH || pixelY < 0 || pixelY >= SCREEN_HEIGHT) {
            return 1;
        }
        
        int col = pixelX / TILE_SIZE;
        int row = pixelY / TILE_SIZE;

        if (row >= 0 && row < MAX_ROW && col >= 0 && col < MAX_COL) {
            return map[row][col];
        }
        return 1;
    }

    public void eatFood(int col, int row) {
        if (row >= 0 && row < MAX_ROW && col >= 0 && col < MAX_COL) {
            if (map[row][col] == 2) {
                map[row][col] = 0;
                score += 10;
                foodLocations.remove(new Point(col, row));
                if (foodLocations.isEmpty()) {
                    gameState = GameState.GAME_WIN;
                }
            }
        }
    }

    public void checkCollisionPacManGhosts() {
        Rectangle pacManHitbox = new Rectangle(pacManX + pacManSolidArea.x,
                                             pacManY + pacManSolidArea.y,
                                             pacManSolidArea.width,
                                             pacManSolidArea.height);

        for (Ghost ghost : ghosts) {
            Rectangle ghostHitbox = new Rectangle(ghost.x + ghostSolidArea.x, 
                                                ghost.y + ghostSolidArea.y, 
                                                ghostSolidArea.width, 
                                                ghostSolidArea.height);
            
            if (pacManHitbox.intersects(ghostHitbox)) {
                lives--;
                if (lives <= 0) {
                    gameState = GameState.GAME_OVER;
                } else {
                    // Reset positions
                    pacManX = initialPacManX; 
                    pacManY = initialPacManY;
                    
                    for (Ghost g : ghosts) {
                        g.x = g.initialX; 
                        g.y = g.initialY; 
                        g.direction = "up";
                        g.directionChangeCounter = 0;
                    }
                }
                break;
            }
        }
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
        }
    }
    
    private void handleMainMenuInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + MAIN_MENU_OPTIONS.length) % MAIN_MENU_OPTIONS.length;
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % MAIN_MENU_OPTIONS.length;
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Start Game
                    loadLevel(1);
                    gameState = GameState.PLAYING;
                    break;
                case 1: // Level Select
                    selectedLevelOption = 0;
                    gameState = GameState.LEVEL_SELECT;
                    break;
                case 2: // Quit
                    System.exit(0);
                    break;
            }
        }
    }
    
    private void handleLevelSelectInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedLevelOption = (selectedLevelOption - 1 + LEVEL_OPTIONS.length) % LEVEL_OPTIONS.length;
        } else if (code == KeyEvent.VK_DOWN) {
            selectedLevelOption = (selectedLevelOption + 1) % LEVEL_OPTIONS.length;
        } else if (code == KeyEvent.VK_ENTER) {
            if (selectedLevelOption < 3) { // Level 1-3
                loadLevel(selectedLevelOption + 1);
                gameState = GameState.PLAYING;
            } else { // Back
                selectedMenuOption = 0;
                gameState = GameState.MAIN_MENU;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            selectedMenuOption = 0;
            gameState = GameState.MAIN_MENU;
        }
    }
    
    private void handlePlayingInput(int code) {
        if (code == KeyEvent.VK_UP) {
            pacManDirection = "up";
            pacManSpriteNum = 2;
            pacManSpriteCounter = 0;
        } else if (code == KeyEvent.VK_DOWN) {
            pacManDirection = "down";
            pacManSpriteNum = 2;
            pacManSpriteCounter = 0;
        } else if (code == KeyEvent.VK_LEFT) {
            pacManDirection = "left";
            pacManSpriteNum = 2;
            pacManSpriteCounter = 0;
        } else if (code == KeyEvent.VK_RIGHT) {
            pacManDirection = "right";
            pacManSpriteNum = 2;
            pacManSpriteCounter = 0;
        } else if (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_P) {
            selectedMenuOption = 0;
            gameState = GameState.PAUSED;
        }
    }
    
    private void handlePauseMenuInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + PAUSE_OPTIONS.length) % PAUSE_OPTIONS.length;
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % PAUSE_OPTIONS.length;
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Resume
                    gameState = GameState.PLAYING;
                    break;
                case 1: // Restart
                    loadLevel(currentLevel);
                    gameState = GameState.PLAYING;
                    break;
                case 2: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    break;
                case 3: // Quit
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
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % GAME_OVER_OPTIONS.length;
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Restart
                    loadLevel(currentLevel);
                    gameState = GameState.PLAYING;
                    break;
                case 1: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    break;
                case 2: // Quit
                    System.exit(0);
                    break;
            }
        }
    }
    
    private void handleGameWinInput(int code) {
        if (code == KeyEvent.VK_UP) {
            selectedMenuOption = (selectedMenuOption - 1 + GAME_WIN_OPTIONS.length) % GAME_WIN_OPTIONS.length;
        } else if (code == KeyEvent.VK_DOWN) {
            selectedMenuOption = (selectedMenuOption + 1) % GAME_WIN_OPTIONS.length;
        } else if (code == KeyEvent.VK_ENTER) {
            switch (selectedMenuOption) {
                case 0: // Next Level
                    if (currentLevel < MAX_LEVEL) {
                        loadLevel(currentLevel + 1);
                        gameState = GameState.PLAYING;
                    } else {
                        // If it's the last level, go to main menu
                        gameState = GameState.MAIN_MENU;
                    }
                    break;
                case 1: // Main Menu
                    gameState = GameState.MAIN_MENU;
                    break;
                case 2: // Quit
                    System.exit(0);
                    break;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}
    
    // Inner class for Ghost
    class Ghost {
        int x, y;
        int initialX, initialY;
        int speed;
        String direction;
        int directionChangeCounter;
        
        public Ghost() {
            direction = "up";
            directionChangeCounter = 0;
        }
    }
    
    // Main method to start the game
    public static void main(String[] args) {
        JFrame window = new JFrame("Pac-Man");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        
        PacMan gamePanel = new PacMan();
        window.add(gamePanel);
        window.pack();
        
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        
        gamePanel.startGame();
    }
}