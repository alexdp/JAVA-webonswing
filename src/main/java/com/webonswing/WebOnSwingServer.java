package com.webonswing;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import com.google.gson.Gson;

public class WebOnSwingServer {

    private int port = 8080;
    private final Gson gson = new Gson();
    private final JComponent componentToExpose;
    private Server server;
    private BufferedImage offscreen;
    private volatile byte[] lastFrame = null;
    private volatile long lastFrameTimestamp = 0;

    public WebOnSwingServer(int port) {
        this.port = port;
        this.componentToExpose = new JLabel("Hello World", SwingConstants.CENTER);
    }


    public void exposeComponent(JComponent component) {
        if (component == null) {
            System.err.println("Cannot expose null component");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            this.componentToExpose.removeAll();
            this.componentToExpose.setLayout(new BorderLayout());
            this.componentToExpose.add(component, BorderLayout.CENTER);
            this.componentToExpose.revalidate();
            this.componentToExpose.repaint();
        });
    }

    public void start() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (componentToExpose == null) {
                System.err.println("WARNING: Component to expose is null - using blank component");
                return;
            }

            
            
            int width = componentToExpose.getWidth();
            int height = componentToExpose.getHeight();

            if (width <= 0 || height <= 0) {
                System.err.println("WARNING: Component dimensions are invalid (width: " + width + ", height: " + height + ") - using default size 800x600");
                return;
            }
            
            
            offscreen = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g = offscreen.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            // printAll is more reliable than paint for offscreen snapshots before the component is shown.
            componentToExpose.printAll(g);
            g.dispose();

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(offscreen, "jpg", out);
                lastFrame = out.toByteArray();
                lastFrameTimestamp = System.currentTimeMillis();
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("Headless component initialized and first frame captured");
        });
        startServer();
        startRenderLoop();
    }

    public Gson getGson() {
        return gson;
    }

    public JComponent getComponentToExpose() {
        return componentToExpose;
    }

    public byte[] getLastFrame() {
        return lastFrame;
    }

    public long getLastFrameTimestamp() {
        return lastFrameTimestamp;
    }

    private Server getServer() {
        if (server == null) {
            server = new Server(port);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            JettyWebSocketServletContainerInitializer.configure(context,
                    (servletContext, wsContainer) -> {
                        wsContainer.addMapping("/events", (req, res) -> new WebOnSwingEventSocket(this));
                    });

            context.addServlet(new ServletHolder(new WebOnSwingStreamServlet(this)), "/stream");

            ResourceHandler resourceHandler = new ResourceHandler();
            resourceHandler.setDirectoriesListed(false);
            resourceHandler.setWelcomeFiles(new String[]{"mini.html"});
            resourceHandler.setBaseResource(Resource.newClassPathResource("/web"));

            HandlerList handlers = new HandlerList();
            handlers.setHandlers(new org.eclipse.jetty.server.Handler[]{
                    resourceHandler,
                    context
            });

            server.setHandler(handlers);
        }
        return server;
    }


    private void startServer() throws Exception {
        getServer().start();
        System.out.println("Server started http://localhost:" + port);

        new Thread(() -> {
            try { getServer().join(); } catch (InterruptedException e) { e.printStackTrace(); }
        }).start();
    }

    private void startRenderLoop() {
        new Thread(() -> {
            try {
                while (true) {
                    SwingUtilities.invokeAndWait(() -> {
                        byte[] newFrame = renderFrameToBytes();
                        if (newFrame != null) {
                            if (lastFrame == null || !Arrays.equals(newFrame, lastFrame)) {
                                lastFrame = newFrame;
                                lastFrameTimestamp = System.currentTimeMillis();
                            }
                        }
                    });
                    Thread.sleep(16);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    public boolean isRunning() {
        return server != null && server.isStarted();
    }

    private byte[] renderFrameToBytes() {
        if (componentToExpose == null || offscreen == null) return null;
        
        int width = componentToExpose.getWidth();
        int height = componentToExpose.getHeight();
        
        if (width <= 0 || height <= 0) return null;

        Graphics2D g = offscreen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        componentToExpose.printAll(g);
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(offscreen, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

