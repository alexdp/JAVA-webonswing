package com.webonswing;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import javax.swing.AbstractButton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.text.JTextComponent;

@WebSocket
public class WebOnSwingEventSocket {
    
    private final WebOnSwingServer webServer;
    private Component pressTarget;
    private Component keyboardTarget;
    private JTextComponent activeTextTarget;

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
        if (type == null) {
            return;
        }

        if ("READY".equals(type)) {
            webServer.setCompressionProfile(toStringValue(event.get("profile")));
            webServer.forceRender();
            return;
        }

        if (type.startsWith("KEY_")) {
            handleKeyEvent(type, event);
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
                    keyboardTarget = resolveKeyboardTarget(pressTarget, componentExposed);

                    Component textAtClick = findTextComponentAt(componentExposed, x, y);
                    if (textAtClick instanceof JTextComponent) {
                        keyboardTarget = textAtClick;
                        activeTextTarget = (JTextComponent) textAtClick;
                    } else {
                        activeTextTarget = null;
                    }

                    if (!(keyboardTarget instanceof JTextComponent)) {
                        Component byLocation = findTextComponentAt(componentExposed, x, y);
                        if (byLocation != null) {
                            keyboardTarget = byLocation;
                            if (byLocation instanceof JTextComponent) {
                                activeTextTarget = (JTextComponent) byLocation;
                            }
                        }
                    }
                    requestFocusForKeyboardTarget();
                    placeCaretAtClick(componentExposed, keyboardTarget, x, y);

                    dispatchMouse(componentExposed, pressTarget, id, x, y, MouseEvent.BUTTON1_DOWN_MASK, 1);
                    return;
                }

                Component dispatchTarget = hovered;
                if ((id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_RELEASED) && pressTarget != null) {
                    dispatchTarget = pressTarget;
                }

                int modifiersEx = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
                dispatchMouse(componentExposed, dispatchTarget, id, x, y, modifiersEx, 1);

                if (id == MouseEvent.MOUSE_RELEASED && pressTarget instanceof AbstractButton && dispatchTarget == pressTarget) {
                    dispatchMouse(componentExposed, pressTarget, MouseEvent.MOUSE_CLICKED, x, y, 0, 1);
                }

                if (id == MouseEvent.MOUSE_RELEASED) {
                    pressTarget = null;
                }
            }
        });
    }

    private void handleKeyEvent(String type, Map<?, ?> event) {
        SwingUtilities.invokeLater(() -> {
            JComponent root = webServer.getComponentToExpose();
            if (root == null) {
                return;
            }

            int keyEventId = 0;
            if ("KEY_PRESSED".equals(type)) keyEventId = KeyEvent.KEY_PRESSED;
            if ("KEY_RELEASED".equals(type)) keyEventId = KeyEvent.KEY_RELEASED;
            if ("KEY_TYPED".equals(type)) keyEventId = KeyEvent.KEY_TYPED;
            if (keyEventId == 0) {
                return;
            }

            Component target = keyboardTarget;
            if (target == null) {
                target = root;
            }

            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, root)) {
                target = focusOwner;
            }

            if (activeTextTarget != null) {
                target = activeTextTarget;
            }

            int pointerX = toOptionalInt(event.get("pointerX"), -1);
            int pointerY = toOptionalInt(event.get("pointerY"), -1);
            if (!(target instanceof JTextComponent) && pointerX >= 0 && pointerY >= 0) {
                Component byLocation = findTextComponentAt(root, pointerX, pointerY);
                if (byLocation instanceof JTextComponent) {
                    target = byLocation;
                    keyboardTarget = byLocation;
                    activeTextTarget = (JTextComponent) byLocation;
                    requestFocusForKeyboardTarget();
                    placeCaretAtClick(root, byLocation, pointerX, pointerY);
                }
            }

            int modifiersEx = toModifiersEx(event);
            int keyCode = toInt(event.get("keyCode"));
            String keyString = toStringValue(event.get("key"));
            char keyChar = toKeyChar(keyString);

            if (keyEventId == KeyEvent.KEY_TYPED) {
                keyCode = KeyEvent.VK_UNDEFINED;
                if (keyChar == KeyEvent.CHAR_UNDEFINED) {
                    return;
                }
            } else {
                keyCode = normalizeJavaKeyCode(keyCode, keyString);
                if (keyCode <= 0) {
                    return;
                }
                keyChar = KeyEvent.CHAR_UNDEFINED;
            }

                int keyLocation = keyEventId == KeyEvent.KEY_TYPED
                    ? KeyEvent.KEY_LOCATION_UNKNOWN
                    : KeyEvent.KEY_LOCATION_STANDARD;

            KeyEvent keyEvent = new KeyEvent(
                    target,
                    keyEventId,
                    System.currentTimeMillis(),
                    modifiersEx,
                    keyCode,
                    keyChar,
                    keyLocation
            );

            dispatchKey(root, target, keyEvent);
            root.repaint();
        });
    }

    private void dispatchKey(JComponent root, Component preferredTarget, KeyEvent keyEvent) {
        Component target = preferredTarget == null ? root : preferredTarget;
        if (target instanceof JComponent) {
            ((JComponent) target).requestFocusInWindow();
        }

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusOwner = manager.getFocusOwner();
        if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, root)) {
            target = focusOwner;
        }

        KeyEvent routedEvent = new KeyEvent(
                target,
                keyEvent.getID(),
                keyEvent.getWhen(),
                keyEvent.getModifiersEx(),
                keyEvent.getKeyCode(),
                keyEvent.getKeyChar(),
                keyEvent.getKeyLocation()
        );

        manager.redispatchEvent(target, routedEvent);
    }

    private int normalizeJavaKeyCode(int incomingKeyCode, String key) {
        if ("Enter".equals(key)) return KeyEvent.VK_ENTER;
        if ("Backspace".equals(key)) return KeyEvent.VK_BACK_SPACE;
        if ("Delete".equals(key)) return KeyEvent.VK_DELETE;
        if ("ArrowLeft".equals(key)) return KeyEvent.VK_LEFT;
        if ("ArrowRight".equals(key)) return KeyEvent.VK_RIGHT;
        if ("ArrowUp".equals(key)) return KeyEvent.VK_UP;
        if ("ArrowDown".equals(key)) return KeyEvent.VK_DOWN;
        if ("Home".equals(key)) return KeyEvent.VK_HOME;
        if ("End".equals(key)) return KeyEvent.VK_END;
        if ("Tab".equals(key)) return KeyEvent.VK_TAB;
        if ("Escape".equals(key)) return KeyEvent.VK_ESCAPE;
        if ("PageUp".equals(key)) return KeyEvent.VK_PAGE_UP;
        if ("PageDown".equals(key)) return KeyEvent.VK_PAGE_DOWN;

        if (incomingKeyCode == 13) return KeyEvent.VK_ENTER;
        if (incomingKeyCode == 8) return KeyEvent.VK_BACK_SPACE;
        if (incomingKeyCode == 46) return KeyEvent.VK_DELETE;

        return incomingKeyCode;
    }

    private void placeCaretAtClick(JComponent root, Component target, int x, int y) {
        if (!(target instanceof JTextComponent)) {
            return;
        }
        JTextComponent text = (JTextComponent) target;
        Point p = SwingUtilities.convertPoint(root, x, y, text);
        int pos = text.viewToModel2D(p);
        if (pos >= 0) {
            text.setCaretPosition(pos);
        }
        ensureCaretVisible(text);
    }

    private void ensureCaretVisible(JTextComponent text) {
        if (text.getCaret() != null) {
            text.getCaret().setVisible(true);
            text.getCaret().setSelectionVisible(true);
        }
    }

    private Component resolveKeyboardTarget(Component candidate, JComponent root) {
        if (candidate == null) {
            return root;
        }

        Component viewportView = viewFromViewportContainer(candidate);
        if (viewportView instanceof JTextComponent) {
            return viewportView;
        }

        Component current = candidate;
        while (current != null) {
            Component nestedView = viewFromViewportContainer(current);
            if (nestedView instanceof JTextComponent) {
                return nestedView;
            }

            // Prioritize explicit input/click controls; generic focusable containers
            // (e.g. scrollpane internals) can otherwise steal keyboard target.
            if (current instanceof JTextComponent || current instanceof AbstractButton) {
                return current;
            }

            if (current == root) {
                break;
            }
            current = current.getParent();
        }

        return root;
    }

    private Component viewFromViewportContainer(Component component) {
        if (component instanceof JViewport) {
            return ((JViewport) component).getView();
        }
        if (component instanceof JScrollPane) {
            JViewport viewport = ((JScrollPane) component).getViewport();
            return viewport == null ? null : viewport.getView();
        }
        return null;
    }

    private Component findTextComponentAt(Component root, int x, int y) {
        if (!(root instanceof Container)) {
            return (root instanceof JTextComponent) ? root : null;
        }

        Component deepest = SwingUtilities.getDeepestComponentAt((Container) root, x, y);
        if (deepest == null) {
            return null;
        }

        Component current = deepest;
        while (current != null) {
            if (current instanceof JTextComponent) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void requestFocusForKeyboardTarget() {
        if (keyboardTarget instanceof JComponent) {
            ((JComponent) keyboardTarget).requestFocusInWindow();
        }
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

    private int toOptionalInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private String toStringValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    private int toModifiersEx(Map<?, ?> event) {
        int modifiers = 0;
        if (toBoolean(event.get("ctrlKey"))) modifiers |= KeyEvent.CTRL_DOWN_MASK;
        if (toBoolean(event.get("shiftKey"))) modifiers |= KeyEvent.SHIFT_DOWN_MASK;
        if (toBoolean(event.get("altKey"))) modifiers |= KeyEvent.ALT_DOWN_MASK;
        if (toBoolean(event.get("metaKey"))) modifiers |= KeyEvent.META_DOWN_MASK;
        return modifiers;
    }

    private boolean toBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private char toKeyChar(Object keyValue) {
        String key = toStringValue(keyValue);
        if (key == null || key.isEmpty()) {
            return KeyEvent.CHAR_UNDEFINED;
        }
        if (key.length() == 1) {
            return key.charAt(0);
        }
        if ("Enter".equals(key)) {
            return '\n';
        }
        if ("Tab".equals(key)) {
            return '\t';
        }
        return KeyEvent.CHAR_UNDEFINED;
    }
}
