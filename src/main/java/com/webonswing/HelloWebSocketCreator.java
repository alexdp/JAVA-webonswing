package com.webonswing;

import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;

/**
 * Factory that creates {@link HelloWebSocket} instances for each new WebSocket connection.
 */
public class HelloWebSocketCreator implements JettyWebSocketCreator {

    @Override
    public Object createWebSocket(JettyServerUpgradeRequest request,
                                  JettyServerUpgradeResponse response) {
        return new HelloWebSocket();
    }
}
