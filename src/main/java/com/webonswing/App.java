package com.webonswing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.swing.BorderFactory;
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
        JButton showDialogButton = new JButton("Show dialog");
        showDialogButton.setBounds(10, 460, 110, 30);
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

        JPanel dialogPanel = new JPanel(null);
        dialogPanel.setBounds(220, 170, 360, 180);
        dialogPanel.setBackground(new Color(248, 248, 248));
        dialogPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        dialogPanel.setVisible(false);

        JLabel dialogTitle = new JLabel("Propagation Test Dialog", SwingConstants.CENTER);
        dialogTitle.setBounds(20, 16, 320, 30);
        JLabel dialogMessage = new JLabel("Dialog time: --:--:--", SwingConstants.CENTER);
        dialogMessage.setBounds(20, 70, 320, 30);
        JButton closeDialogButton = new JButton("Close");
        closeDialogButton.setBounds(140, 125, 80, 30);

        dialogPanel.add(dialogTitle);
        dialogPanel.add(dialogMessage);
        dialogPanel.add(closeDialogButton);

        Timer dialogTimer = new Timer(1000, e -> {
            if (dialogPanel.isVisible()) {
                dialogMessage.setText("Dialog time: " + LocalTime.now().format(CLOCK_FORMATTER));
                panel.repaint(dialogPanel.getBounds());
            }
        });
        dialogTimer.start();
        panel.putClientProperty("dialogTimer", dialogTimer);

        showDialogButton.addActionListener(e -> {
            dialogMessage.setText("Dialog time: " + LocalTime.now().format(CLOCK_FORMATTER));
            dialogPanel.setVisible(true);
            panel.repaint(dialogPanel.getBounds());
            statusLabel.setText("Dialog opened");
        });

        closeDialogButton.addActionListener(e -> {
            dialogPanel.setVisible(false);
            panel.repaint(dialogPanel.getBounds());
            statusLabel.setText("Dialog closed");
        });

        button.addActionListener(e -> statusLabel.setText("Clicked " + new Date()));

        panel.add(showDialogButton);
        panel.add(button);
        panel.add(statusLabel);
        panel.add(dialogPanel);
        panel.setComponentZOrder(dialogPanel, 0);

        panel.doLayout();
        panel.validate();

        return panel;
    }


}
