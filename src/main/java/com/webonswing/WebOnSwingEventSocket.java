package com.webonswing;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Map;

@WebSocket
public class WebOnSwingEventSocket {
    
    private final WebOnSwingServer webServer;

    public WebOnSwingEventSocket(WebOnSwingServer webServer) {
        this.webServer = webServer;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Event client connected");
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String msg) {
        Map<?, ?> event = webServer.getGson().fromJson(msg, Map.class);
        String type = (String) event.get("type");
        int x = ((Number) event.get("x")).intValue();
        int y = ((Number) event.get("y")).intValue();

        SwingUtilities.invokeLater(() -> {
            int id = 0;
            if ("MOUSE_PRESSED".equals(type)) id = MouseEvent.MOUSE_PRESSED;
            if ("MOUSE_RELEASED".equals(type)) id = MouseEvent.MOUSE_RELEASED;
            if ("MOUSE_DRAGGED".equals(type)) id = MouseEvent.MOUSE_DRAGGED;

            if (id != 0 && webServer.getComponentToExpose() != null) {
                JComponent componentExposed = webServer.getComponentToExpose();
                Component component = componentExposed.getComponentAt(x, y);
                if (id == MouseEvent.MOUSE_DRAGGED && component != componentExposed && component != null) {
                    component.setLocation(x - component.getWidth() / 2, y - component.getHeight() / 2);
                    componentExposed.repaint();
                }

                MouseEvent me = new MouseEvent(componentExposed, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
                if (component != null && component != componentExposed) {
                    Point p = SwingUtilities.convertPoint(componentExposed, x, y, component);
                    MouseEvent componentEvent = new MouseEvent(component, id, System.currentTimeMillis(), 0, p.x, p.y, 1, false, MouseEvent.BUTTON1);
                    component.dispatchEvent(componentEvent);
                } else {
                    componentExposed.dispatchEvent(me);
                }
            }
        });
    }
}
