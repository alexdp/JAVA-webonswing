package com.webonswing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Main application entry point.
 * Starts an embedded Jetty server on the specified port with:
 * -  A servlet at / that serves mini.html which includes a video element and JavaScript to connect to the WebSocket and handle mouse events
 * -  A servlet at /stream that serves a multipart MJPEG stream of the exposed Swing component
 * -  A WebSocket endpoint at /events that receives mouse events from the client and dispatches them to the Swing component
 */
public class App {

    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) throws Exception {
        JComponent sampleComponent = createSampleComponent();
        WebOnSwingServer webServer = createServer(8080);
        webServer.exposeComponent(sampleComponent);
        webServer.start();
    }


    public static WebOnSwingServer createServer(int port) {
        return new WebOnSwingServer(port);
    }


    private static JComponent createSampleComponent() {
        JPanel panel = new JPanel(null);
        panel.setSize(800, 600);
        panel.setBackground(Color.LIGHT_GRAY);
        panel.setDoubleBuffered(true);

        JPanel draggable = new JPanel();
        draggable.setBackground(Color.RED);
        draggable.setDoubleBuffered(true);
        draggable.setBounds(50, 50, 100, 100);
        
        JLabel label = new JLabel("DRAG ME", SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        draggable.setLayout(new BorderLayout());
        draggable.add(label, BorderLayout.CENTER);

        panel.add(draggable);

        JButton button = new JButton("Click me");
        button.setBounds(10, 500, 100, 30);
        JLabel statusLabel = new JLabel("Hello WebSwing", SwingConstants.CENTER);
        statusLabel.setBounds(120, 500, 300, 30);

        JLabel clockLabel = new JLabel("Server clock: --:--:--", SwingConstants.CENTER);
        clockLabel.setBounds(450, 20, 320, 30);
        panel.add(clockLabel);

        Timer clockTimer = new Timer(1000, e -> {
            clockLabel.setText("Server clock: " + LocalTime.now().format(CLOCK_FORMATTER));
            panel.repaint(clockLabel.getBounds());
        });
        clockTimer.setInitialDelay(0);
        clockTimer.start();
        panel.putClientProperty("clockTimer", clockTimer);

        button.addActionListener(e -> statusLabel.setText("Clicked " + new Date()));

        panel.add(button);
        panel.add(statusLabel);

        panel.doLayout();
        panel.validate();

        return panel;
    }


}
