package com.webonswing;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

/**
 * Main application entry point.
 * Starts an embedded Jetty server on port 8080 with:
 *   - A "Hello World" HTTP handler at /
 *   - A WebSocket endpoint at /ws
 */
public class App {

    public static void main(String[] args) throws Exception {
        Server server = createServer(8080);
        server.start();
        System.out.println("Server started on http://localhost:8080");
        System.out.println("WebSocket available at ws://localhost:8080/ws");
        server.join();
    }

    /**
     * Creates and configures the Jetty server.
     *
     * @param port the TCP port to listen on
     * @return a configured (but not yet started) {@link Server}
     */
    public static Server createServer(int port) {
        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Register Hello World servlet
        context.addServlet(HelloWorldServlet.class, "/");

        // Configure WebSocket support and register the WebSocket endpoint
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", HelloWebSocketCreator.class);
        });

        server.setHandler(context);
        return server;
    }
}
