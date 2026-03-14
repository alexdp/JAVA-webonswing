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
    private Component dragTarget;
    private Point dragOffset;

    public WebOnSwingEventSocket(WebOnSwingServer webServer) {
        this.webServer = webServer;
        this.dragOffset = new Point();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Event client connected");
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String msg) {
        Map<?, ?> event = webServer.getGson().fromJson(msg, Map.class);
        String type = (String) event.get("type");
        if (type == null) {
            return;
        }

        if ("READY".equals(type)) {
            webServer.forceRender();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            int id = 0;
            if ("MOUSE_PRESSED".equals(type)) id = MouseEvent.MOUSE_PRESSED;
            if ("MOUSE_RELEASED".equals(type)) id = MouseEvent.MOUSE_RELEASED;
            if ("MOUSE_DRAGGED".equals(type)) id = MouseEvent.MOUSE_DRAGGED;

            int x = toInt(event.get("x"));
            int y = toInt(event.get("y"));

            if (id != 0 && webServer.getComponentToExpose() != null) {
                JComponent componentExposed = webServer.getComponentToExpose();
                Component component = componentExposed.getComponentAt(x, y);

                if (id == MouseEvent.MOUSE_PRESSED) {
                    if (component != null && component != componentExposed) {
                        dragTarget = component;
                        Point p = SwingUtilities.convertPoint(componentExposed, x, y, dragTarget);
                        dragOffset = new Point(p.x, p.y);
                    } else {
                        dragTarget = null;
                    }
                }

                if (id == MouseEvent.MOUSE_DRAGGED && dragTarget != null) {
                    dragTarget.setLocation(x - dragOffset.x, y - dragOffset.y);
                    componentExposed.repaint();
                }

                Component dispatchTarget = component;
                if ((id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_RELEASED) && dragTarget != null) {
                    dispatchTarget = dragTarget;
                }

                MouseEvent me = new MouseEvent(componentExposed, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
                if (dispatchTarget != null && dispatchTarget != componentExposed) {
                    Point p = SwingUtilities.convertPoint(componentExposed, x, y, dispatchTarget);
                    MouseEvent componentEvent = new MouseEvent(dispatchTarget, id, System.currentTimeMillis(), 0, p.x, p.y, 1, false, MouseEvent.BUTTON1);
                    dispatchTarget.dispatchEvent(componentEvent);
                } else {
                    componentExposed.dispatchEvent(me);
                }

                if (id == MouseEvent.MOUSE_RELEASED) {
                    dragTarget = null;
                }
            }
        });
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
