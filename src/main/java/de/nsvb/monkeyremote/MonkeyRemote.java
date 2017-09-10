/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.nsvb.monkeyremote;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.TouchPressType;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.event.MouseInputAdapter;

/**
 *
 * @author ns130291
 */
public class MonkeyRemote extends JFrame {

    private static final String ADB = "C:\\Users\\ns130291\\Android\\sdk\\platform-tools\\adb.exe";
    private static final long TIMEOUT = 5000;

    private static float scalingFactor = 0.5f;

    private final IChimpDevice device;

    public MonkeyRemote(IChimpDevice device, int deviceWidth, int deviceHeight, BufferedImage initialScreen) {
        this.device = device;

        final int dWScaled = (int) (deviceWidth * scalingFactor);
        final int dHScaled = (int) (deviceHeight * scalingFactor);

        setTitle("MonkeyRemote");
        
        setSize(dWScaled + 50, dHScaled + 50);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                MonkeyRemote.this.device.dispose();
            }

        });

        DeviceScreen screen = new DeviceScreen(initialScreen, dWScaled, dHScaled);

        GestureListener gestureListener = new GestureListener(device);
        KeyListener keyListener = new KeyListener(device);
        
        screen.addMouseListener(gestureListener);
        screen.addMouseMotionListener(gestureListener);
        screen.addKeyListener(keyListener);
        screen.setFocusable(true);
        
        add(screen);
        //pack();
        setVisible(true);

        int i = 1;
        while (true) {
            System.out.println("#" + i++);
            try {
                screen.setImage(device.takeSnapshot().getBufferedImage());
                screen.repaint();
            } catch (Exception ex) {
                System.out.println("Couldn't aquire screenshot: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String adb = ADB;
        if(args.length == 2){
            adb = args[0];
            scalingFactor = Float.parseFloat(args[1]);
            args = new String[0];
        } 
        if (args.length > 0 || !new File(adb).exists() || new File(adb).isDirectory()){
            if(!new File(adb).exists()){
                System.err.println("Error: ADB executable wasn't found at \"" + adb + "\"");
            }
            if(new File(adb).isDirectory()){
                System.err.println("Error: Path to ADB executable is a directory");
            }
            System.out.println("Usage: MonkeyRemote [Path to ADB executable] [Scaling factor]");
            return;
        }
        
        //http://stackoverflow.com/questions/6686085/how-can-i-make-a-java-app-using-the-monkeyrunner-api
        Map<String, String> options = new TreeMap<>();
        options.put("backend", "adb");
        options.put("adbLocation", adb);
        ChimpChat chimpchat = ChimpChat.getInstance(options);
        IChimpDevice device = chimpchat.waitForConnection(TIMEOUT, ".*");

        if(device == null){
            System.err.println("Error: Couldn't connect to device");
            return;
        }
        
        /*for (String prop : device.getPropertyList()) {
         System.out.println(prop + ": " + device.getProperty(prop));
         }*/
        device.wake();
        BufferedImage screen = device.takeSnapshot().getBufferedImage();

        int width = screen.getWidth();
        int height = screen.getHeight();

        System.out.println("Device screen dimension:" + height + "x" + width);

        MonkeyRemote remote = new MonkeyRemote(device, width, height, screen);
        //chimpchat.shutdown();
    }

    private class DeviceScreen extends JPanel {

        private BufferedImage image;
        private final int dWScaled;
        private final int dHScaled;

        public DeviceScreen(BufferedImage image, int dWScaled, int dHScaled) {
            this.image = image;
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
        }

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, dWScaled, dHScaled, null);
            //setSize(dWScaled, dHScaled);
            //MonkeyRemote monkeyRemote = (MonkeyRemote) SwingUtilities.getAncestorOfClass(MonkeyRemote.class, this);
            //monkeyRemote.pack();
        }
    }

    private class GestureListener extends MouseInputAdapter {

        private boolean gestureActive = false;
        private final IChimpDevice device;
        private long lastSent = 0;
        private int lastX = 0;
        private int lastY = 0;
        TouchPressType keyActionType;
        String key;
        public GestureListener(IChimpDevice device) {
            this.device = device;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            switch (e.getButton()) 
            {
                case MouseEvent.BUTTON1:
                    if (gestureActive) {
                        sendTouchEvent(lastX, lastY, TouchPressType.UP);
                        System.out.println("UP, cancelling old gesture " + lastX + " " + lastY);
                    }
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    gestureActive = true;
                    lastX = x;
                    lastY = y;
                    sendTouchEvent(x, y, TouchPressType.DOWN);
                    System.out.println("DOWN " + x + " " + y);
                    break;
                case MouseEvent.BUTTON2:
                    keyActionType = TouchPressType.DOWN_AND_UP;
                    key = String.valueOf(3);
                    device.press(key, keyActionType);
                    break;
                case MouseEvent.BUTTON3:
                    keyActionType = TouchPressType.DOWN_AND_UP;
                    key = String.valueOf(4);
                    device.press(key, keyActionType);
                    break;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (gestureActive) {
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.UP);
                    gestureActive = false;
                    System.out.println("UP " + x + " " + y);
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (gestureActive) {
                //System.out.println("mouse dragged " + (int) (e.getX() / scalingFactor) + " " + (int) (e.getY() / scalingFactor) + " " + e.getButton());
                if (System.currentTimeMillis() - lastSent > 1) { // max every 2 milliseconds
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.MOVE);
                    lastX = x;
                    lastY = y;
                    lastSent = System.currentTimeMillis();
                    System.out.println("MOVE " + x + " " + y);
                }
            }
        }

        private void sendTouchEvent(int x, int y, TouchPressType type) {
            device.touch(x, y, type);
        }
    }

    public class KeyListener extends KeyAdapter
    {
        //android.view.KeyEvent AndroidKeyEvent = new android.view.KeyEvent();
        private boolean keyActive = false;
        private String key;
        private final IChimpDevice device;
        public KeyListener(IChimpDevice device) {
            this.device = device;
        }
        
	@Override
	public void keyPressed(KeyEvent e)
	{
            if(!keyActive)
            {
                switch (e.getKeyCode()) 
                {
                    case KeyEvent.VK_BACK_SPACE:
                        key = String.format("%x", 0x67); //KEYCODE_DEL;
                        break;
                    case KeyEvent.VK_DELETE:
                        key = String.format("%x", 0x112); //KEYCODE_FORWARD_DEL;
                        break;
                    case KeyEvent.VK_SPACE:
                        key = String.format("%x", 0x62); //KEYCODE_SPACE
                        break;
                    case KeyEvent.VK_LEFT:
                        key = String.format("%x", 0x21); //KEYCODE_LEFT 
                        break;
                    case KeyEvent.VK_RIGHT:
                        key = String.format("%x", 0x22); //KEYCODE_RIGHT
                        break;
                    case KeyEvent.VK_UP:
                        key = String.format("%x", 0x19); //KEYCODE_UP
                        break;
                    case KeyEvent.VK_DOWN:
                        key = String.format("%x", 0x20); //KEYCODE_DOWN
                        break;
                    default :
                        key = String.valueOf(e.getKeyChar());
                }
                keyActive = true;                  
            }
	}
        @Override
	public void keyReleased(KeyEvent e)
        {
            TouchPressType type;
            type = TouchPressType.DOWN_AND_UP;
            if(keyActive)
            {
                device.press(key, type);
                keyActive = false;
            }
        }
    }   
    
}
