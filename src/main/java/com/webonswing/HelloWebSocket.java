package com.webonswing;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;

/**
 * A simple WebSocket endpoint that echoes back received text messages
 * and greets each new connection with "Hello World".
 */
@WebSocket
public class HelloWebSocket {

    @OnWebSocketConnect
    public void onConnect(Session session) throws IOException {
        System.out.println("WebSocket connected: " + session.getRemoteAddress());
        session.getRemote().sendString("Hello World from WebSocket!");
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) throws IOException {
        System.out.println("WebSocket message received: " + message);
        session.getRemote().sendString("Echo: " + message);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.println("WebSocket closed: " + statusCode + " - " + reason);
    }
}
