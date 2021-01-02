package main;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;
import java.net.URL;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class Main {

    public static void main(final String[] args) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InstantiationException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedLookAndFeelException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                // a windows screen server is an app that receives some well known input arguments, see more https://stackoverflow.com/a/2136019/903998
                if (args.length == 0) {
                    URL resource = ClassLoader.getSystemResource("main/bombim.jpg");
                    final ImageIcon icon = new ImageIcon(resource);
                    JOptionPane.showMessageDialog(null, "Todavia no hay pantalla de config! :(", "Configuración", JOptionPane.INFORMATION_MESSAGE, icon);
                    System.exit(0);
                } else {
                    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    GraphicsDevice device = env.getDefaultScreenDevice();
                    AnimatedJFrame jframe = new AnimatedJFrame(AnimatedJFrame.FPS);
                    jframe.setUndecorated(true);
                    jframe.pack();
                    jframe.setLocationRelativeTo(null);
                    device.setFullScreenWindow(jframe);
                    jframe.setVisible(true);
                    jframe.setup();
                    jframe.animate();
                }
            }
        });

    }

    public static class AnimatedJFrame extends Frame implements Runnable {

        private volatile boolean done = false, reached = false;
        private volatile boolean mouseMovementDetected = false;
        private double reach;
        private final long period;
        private static final long IDDLE_TIME_MS = 3000, DELAY_EXIT_TIME_MS = 4000;
        private static final int BALL_RADIUS = 10;
        private static final int FPS = 100;

        private volatile double ballX = 50, ballY = 50; // the mouse/ball center coordinates
        private double speedX = 1, speedY = 1; // the mouse/ball movement speed
        private volatile long lastMovement, timeReached;
        BufferStrategy bufferStrategy;
        private static final Font NORMAL_FONT = new Font("Tahoma", Font.PLAIN, 32);
        private static final Font SMALLER_FONT = new Font("Tahoma", Font.PLAIN, 14);

        int eyesSep = 10;
        int eyesHeight = 150;
        int eyesWidth = 100;
        private int eyesCenterX;
        private int eyesCenterY;
        int eyesTopY = 50;
        Rectangle leftEye, leftIris, rightEye, rightIris; // face

        Rectangle exit; // exit point

        int exitSize = 50;

        double maxDistanceToExit;
        private static final Color WHITE_GRAY = new Color(200, 200, 200);
        private static final Color IRIS_COLOR = Color.DARK_GRAY;
        private static final Random r = new Random();

        private AnimatedJFrame(int fps) {
            period = (int) 1000.0 / fps;

        }

        private void setup() {
            Listener listener = new Listener();
            addMouseMotionListener(listener);
            addMouseListener(listener);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    done = true;
                }

            });
            this.createBufferStrategy(2);
            bufferStrategy = getBufferStrategy();
            eyesCenterX = getWidth() - eyesWidth * 2 - eyesSep * 2 - 50;
            eyesCenterY = eyesTopY + eyesHeight / 2;
            leftEye = new Rectangle(eyesCenterX + eyesSep, eyesTopY, eyesWidth, eyesHeight);
            rightEye = new Rectangle(eyesCenterX - eyesSep - eyesWidth, eyesTopY, eyesWidth, eyesHeight);

            leftIris = createIrisRectangle(leftEye);
            rightIris = createIrisRectangle(rightEye);

//            exit = new Rectangle(r.nextInt(getWidth()),r.nextInt(getHeight() - eyesHeight*2) + eyesHeight*2,exitSize,exitSize);
           
            exit = new Rectangle(r.nextInt(200) + 100, getHeight() - 100, exitSize, exitSize);
            double maxDistanceToExitY = exit.y + exitSize / 2;
            double maxDistanteToExitX = getWidth() - 300 + exitSize / 2;
            maxDistanceToExit = distance(exit.x, exit.y, maxDistanteToExitX, maxDistanceToExitY);

            ExecutorService exec;
            exec = Executors.newFixedThreadPool(4,
                    new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
            });

            exec.execute(new Runnable() {
                @Override
                public void run() {
                    lastMovement = 0;
                    while (!done) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                        }
                        long diff = System.currentTimeMillis() - lastMovement;
                        if (!reached) {
                            mouseMovementDetected = diff < IDDLE_TIME_MS;
                        }
                    }
                }
            });
            exec.shutdown();
        }

        private Rectangle createIrisRectangle(Rectangle eye) {
            int halfHeight = eye.height / 2;
            int quarterWidth = eye.width / 4;
            return new Rectangle(eye.x + quarterWidth, eye.y + halfHeight, quarterWidth * 2, halfHeight);
        }

        public void animate() {
            new Thread(this).start();
        }

        @Override
        public void run() {
            long beforeTime = System.currentTimeMillis();
            while (!done) {
                Graphics g = bufferStrategy.getDrawGraphics();
                update();
                render(g);
                g.dispose();
                bufferStrategy.show();
                long timeDiff = System.currentTimeMillis() - beforeTime;
                long sleepTime = period - timeDiff;
                if (sleepTime <= 0) // update/render took longer than period
                {
                    sleepTime = 5; // sleep a bit anyway
                }
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                }
                beforeTime = System.currentTimeMillis();
            }
            dispose();
        }

        public void stop() {
            this.done = true;

        }

        private void update() {

            if (!reached) {
                reach = calculateReach(exit);
                if (!mouseMovementDetected) {
                    moveBall();
                } else if (reach > 0.99) {
                    reached = true;
                    removeMouseListener(getMouseListeners()[0]);
                    removeMouseMotionListener(getMouseMotionListeners()[0]);
                    timeReached = System.currentTimeMillis();
                }
            } else {
                long diff = System.currentTimeMillis() - timeReached;
                done = diff > DELAY_EXIT_TIME_MS;
            }
        }

        private void render(Graphics g) {
            int width = getWidth();
            int height = getHeight();

            drawBackground(g, width, height);

            drawEye(g, leftEye, leftIris, exit);
            drawEye(g, rightEye, rightIris, exit);
            drawExit(g, exit);
            drawBall(g);

            if (reached) {
                long diff = System.currentTimeMillis() - timeReached;

                g.setColor(WHITE_GRAY);
                g.setFont(NORMAL_FONT);
                g.drawString("Abuenahora!", 10, 40);
                g.setFont(SMALLER_FONT);
//                int remaingSeconds = (int) ((DELAY_EXIT_TIME_MS - diff) / 1000);
                g.drawString("Ahora el valiente protector dormirá feliz!", 10, 70);
                int zQuantity = (int) (diff / 100);
                String zzz = generateRepeteadSequence('z', zQuantity);
                g.setFont(NORMAL_FONT);
                int zzzWidth = g.getFontMetrics().stringWidth(zzz);
                g.drawString(zzz, leftEye.x - zzzWidth - 10, eyesCenterY);
            }

        }

        private String generateRepeteadSequence(char str, int quantity) {
            int n = quantity;
            char[] chars = new char[n];
            Arrays.fill(chars, str);
            String result = new String(chars);
            return result;
        }

        private void drawBackground(Graphics g, int width, int height) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
        }

        private void drawEye(Graphics g, Rectangle eye, Rectangle iris, Rectangle exit) {
            g.setColor(Color.WHITE);
            g.fillOval(eye.x, eye.y, eye.width, eye.height);
            int centerIrisX = iris.x + iris.width / 2;
            int centerIrisY = iris.y + iris.height / 2;

            double dx = ballX - centerIrisX;
            double dy = ballY - centerIrisY;
            double d = Math.sqrt(dx * dx + dy * dy);
            double vx = dx / d;
            double vy = dy / d;
            g.setColor(IRIS_COLOR);
            g.fillOval(iris.x + (int) (vx * iris.width / 4), iris.y + (int) (vy * iris.height / 2) - iris.height / 2, iris.width, iris.height);

            if (mouseMovementDetected) {
                g.setColor(Color.BLACK);
                g.fillOval(eye.x, eye.y, eye.width, (int) (eye.height * reach));
            }

        }

        private void drawExit(Graphics g, Rectangle exit) {
            g.setColor(Color.orange);
            g.fillOval(exit.x, exit.y, exit.width, exit.height);
        }

        private double calculateReach(Rectangle exit) {
            double centerExitX = exit.x + exit.width / 2;
            double centerExitY = exit.y + exit.height / 2;
            double distance = distance(ballX, ballY, centerExitX, centerExitY);
            return 1 - distance / maxDistanceToExit;
        }

        private double distance(double x0, double y0, double x1, double y1) {
            double dx = x0 - x1;
            double dy = y0 - y1;
            return Math.sqrt(dx * dx + dy * dy);
        }

        private double max(double... values) {
            double ret = Double.MIN_VALUE;
            for (double v : values) {
                if (v > ret) {
                    ret = v;
                }
            }
            return ret;
        }

        private void drawBall(Graphics g) {
            int diameter = BALL_RADIUS * 2;
            g.setColor(Color.YELLOW);
            g.fillOval((int) ballX - BALL_RADIUS, (int) ballY - BALL_RADIUS, diameter, diameter);
        }

        private void moveBall() {
            int width = getWidth();
            int height = getHeight();
            if (ballX + BALL_RADIUS > width || ballX < BALL_RADIUS) {
                speedX = -speedX;
            }
            if (ballY + BALL_RADIUS > height || ballY < BALL_RADIUS) {
                speedY = -speedY;
            }
            speedX += (r.nextDouble() - 0.5);
            speedY += (r.nextDouble() - 0.5);
            ballX += speedX;
            ballY += speedY;
        }

        class Listener extends MouseAdapter {

            @Override
            public void mouseMoved(MouseEvent e) {
                ballX = e.getX();
                ballY = e.getY();
                lastMovement = System.currentTimeMillis();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                done = true;
            }
        }
    }
}