package com.webonswing;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import javax.swing.AbstractButton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Map;

@WebSocket
public class WebOnSwingEventSocket {
    
    private final WebOnSwingServer webServer;
    private Component dragTarget;
    private Component pressTarget;
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
            webServer.setCompressionProfile(toStringValue(event.get("profile")));
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
                Component hovered = SwingUtilities.getDeepestComponentAt(componentExposed, x, y);
                if (hovered == null) {
                    hovered = componentExposed;
                }

                if (id == MouseEvent.MOUSE_PRESSED) {
                    pressTarget = hovered;

                    // Do not capture drag on button-like controls; it breaks click actions.
                    if (pressTarget instanceof AbstractButton) {
                        dragTarget = null;
                    } else if (pressTarget != componentExposed) {
                        dragTarget = pressTarget;
                        Point p = SwingUtilities.convertPoint(componentExposed, x, y, dragTarget);
                        dragOffset = new Point(p.x, p.y);
                    } else {
                        dragTarget = null;
                    }

                    dispatchMouse(componentExposed, pressTarget, id, x, y, MouseEvent.BUTTON1_DOWN_MASK, 1);
                    return;
                }

                if (id == MouseEvent.MOUSE_DRAGGED && dragTarget != null) {
                    dragTarget.setLocation(x - dragOffset.x, y - dragOffset.y);
                    componentExposed.repaint();
                }

                Component dispatchTarget = hovered;
                if (id == MouseEvent.MOUSE_DRAGGED && dragTarget != null) {
                    dispatchTarget = dragTarget;
                } else if (id == MouseEvent.MOUSE_RELEASED && pressTarget != null) {
                    dispatchTarget = pressTarget;
                }

                int modifiersEx = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
                dispatchMouse(componentExposed, dispatchTarget, id, x, y, modifiersEx, 1);

                if (id == MouseEvent.MOUSE_RELEASED && pressTarget instanceof AbstractButton && dispatchTarget == pressTarget) {
                    dispatchMouse(componentExposed, pressTarget, MouseEvent.MOUSE_CLICKED, x, y, 0, 1);
                }

                if (id == MouseEvent.MOUSE_RELEASED) {
                    dragTarget = null;
                    pressTarget = null;
                }
            }
        });
    }

    private void dispatchMouse(JComponent root, Component target, int eventId, int x, int y, int modifiersEx, int clickCount) {
        Component actualTarget = target == null ? root : target;
        Point pointInTarget = SwingUtilities.convertPoint(root, x, y, actualTarget);
        MouseEvent event = new MouseEvent(
                actualTarget,
                eventId,
                System.currentTimeMillis(),
                modifiersEx,
                pointInTarget.x,
                pointInTarget.y,
                clickCount,
                false,
                MouseEvent.BUTTON1
        );
        actualTarget.dispatchEvent(event);
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String toStringValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}
