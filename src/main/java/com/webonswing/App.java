package com.webonswing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Main application entry point.
 * Starts an embedded Jetty server on the specified port with:
 * -  A servlet at / that serves mini.html which includes a video element and JavaScript to connect to the WebSocket and handle mouse events
 * -  A servlet at /stream that serves a multipart MJPEG stream of the exposed Swing component
 * -  A WebSocket endpoint at /events that receives mouse events from the client and dispatches them to the Swing component
 */
public class App {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final long APP_LAUNCH_TIMEOUT_MS = 15000;
    private static final String DEFAULT_APP_MAIN_CLASS = "com.sun.swingset3.SwingSet3";
    private static final String APP_MAIN_CLASS_PROPERTY = "webonswing.app.mainClass";
    private static final String DESKTOP_VISIBLE_PROPERTY = "webonswing.app.desktopVisible";
    private static final boolean DEFAULT_DESKTOP_VISIBLE = false;

    public static void main(String[] args) throws Exception {
        JComponent sampleComponent = createSwingSet3Component();
        WebOnSwingServer webServer = createServer(8080);
        webServer.exposeComponent(sampleComponent);
        webServer.start();
    }


    public static WebOnSwingServer createServer(int port) {
        return new WebOnSwingServer(port);
    }


    private static JComponent createSwingSet3Component() {
        String mainClassName = System.getProperty(APP_MAIN_CLASS_PROPERTY, DEFAULT_APP_MAIN_CLASS);
        JComponent component = launchMainAndCaptureRoot(mainClassName);
        if (component != null) {
            return component;
        }

        System.err.println("Failed to capture app root for main class: " + mainClassName + ". Falling back to a simple panel.");
        return createFallbackComponent();
    }


    private static JComponent launchMainAndCaptureRoot(String mainClassName) {
        Set<Frame> existingFrames = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(existingFrames, Frame.getFrames());

        Class<?> appClass;
        try {
            appClass = Class.forName(mainClassName);
        } catch (ClassNotFoundException e) {
            System.err.println("Main class not found: " + mainClassName);
            return null;
        }

        Thread launcher = new Thread(() -> invokeMain(appClass), "app-main-launcher");
        launcher.setDaemon(true);
        launcher.start();

        long deadline = System.currentTimeMillis() + APP_LAUNCH_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            JComponent capturedRoot = findAppRoot(existingFrames, mainClassName);
            if (capturedRoot != null) {
                return capturedRoot;
            }

            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null;
    }


    private static void invokeMain(Class<?> appClass) {
        try {
            Method mainMethod = appClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) new String[0]);
        } catch (Throwable t) {
            Throwable rootCause = t;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            System.err.println("Failed to run app main: " + rootCause);
        }
    }


    private static JComponent findAppRoot(Set<Frame> existingFrames, String mainClassName) {
        final JComponent[] holder = new JComponent[1];
        Runnable captureTask = () -> {
            Frame[] frames = Frame.getFrames();
            for (Frame frame : frames) {
                if (existingFrames.contains(frame) || !(frame instanceof JFrame swingFrame)) {
                    continue;
                }

                if (!swingFrame.isDisplayable()) {
                    continue;
                }

                int width = swingFrame.getWidth();
                int height = swingFrame.getHeight();
                if (width <= 0 || height <= 0) {
                    continue;
                }

                if (!isLikelyTargetFrame(swingFrame, mainClassName)) {
                    continue;
                }

                JRootPane rootPane = swingFrame.getRootPane();
                if (rootPane == null) {
                    continue;
                }

                prepareExposedComponent(rootPane, width, height);
                if (!isDesktopVisible()) {
                    hideFrameForServerMode(swingFrame);
                }
                holder[0] = rootPane;
                return;
            }
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                captureTask.run();
            } else {
                SwingUtilities.invokeAndWait(captureTask);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }

        return holder[0];
    }


    private static boolean isLikelyTargetFrame(JFrame frame, String mainClassName) {
        String title = frame.getTitle();
        if (title != null && !title.isBlank()) {
            if (mainClassName.toLowerCase(Locale.ROOT).contains("swingset") && title.toLowerCase(Locale.ROOT).contains("swingset")) {
                return true;
            }
        }
        return true;
    }


    private static boolean isDesktopVisible() {
        return Boolean.parseBoolean(System.getProperty(DESKTOP_VISIBLE_PROPERTY, Boolean.toString(DEFAULT_DESKTOP_VISIBLE)));
    }


    private static void hideFrameForServerMode(JFrame frame) {
        // Hide the desktop window while keeping the Swing component tree alive for offscreen rendering.
        frame.setLocation(-32000, -32000);
        frame.setVisible(false);
    }


    private static void prepareExposedComponent(JComponent component) {
        prepareExposedComponent(component, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }


    private static void prepareExposedComponent(JComponent component, int width, int height) {
        int effectiveWidth = width > 0 ? width : DEFAULT_WIDTH;
        int effectiveHeight = height > 0 ? height : DEFAULT_HEIGHT;
        component.setPreferredSize(new Dimension(effectiveWidth, effectiveHeight));
        component.setSize(effectiveWidth, effectiveHeight);
        component.doLayout();
        component.validate();
    }


    private static JComponent createFallbackComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.LIGHT_GRAY);
        panel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        panel.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JLabel status = new JLabel("Fallback demo (SwingSet3 not available)", SwingConstants.CENTER);
        JButton button = new JButton("Click me");
        button.addActionListener(e -> status.setText("Clicked fallback component"));

        panel.add(status, BorderLayout.CENTER);
        panel.add(button, BorderLayout.SOUTH);
        panel.doLayout();
        panel.validate();
        return panel;
    }


}
