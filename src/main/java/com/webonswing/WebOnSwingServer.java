package com.webonswing;

import com.google.gson.Gson;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class WebOnSwingServer {

    private final int port;
    private final Gson gson = new Gson();
    private JComponent componentToExpose;
    private Server server;
    private BufferedImage offscreen;
    private BufferedImage previousFrame;
    private volatile byte[] lastFrame = null;
    private volatile long lastFrameTimestamp = 0;
    private volatile FramePatch lastPatch = null;
    private volatile long lastPatchSequence = 0;

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
            this.componentToExpose = component;
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
            componentToExpose.printAll(g);
            g.dispose();

            byte[] initialFrame = encodeImage(offscreen, "jpg");
            if (initialFrame != null) {
                lastFrame = initialFrame;
                lastFrameTimestamp = System.currentTimeMillis();
            }
            previousFrame = copyImage(offscreen);

            byte[] initialPatch = encodeImage(offscreen, "png");
            if (initialPatch != null) {
                lastPatch = new FramePatch(new Rectangle(0, 0, width, height), initialPatch);
                lastPatchSequence = 1;
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

    public FramePatch getLastPatch() {
        return lastPatch;
    }

    public long getLastPatchSequence() {
        return lastPatchSequence;
    }

    public String toPatchMessage(FramePatch patch) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "FRAME_PATCH");
        payload.put("x", patch.rect.x);
        payload.put("y", patch.rect.y);
        payload.put("width", patch.rect.width);
        payload.put("height", patch.rect.height);
        payload.put("image", Base64.getEncoder().encodeToString(patch.imageBytes));
        return gson.toJson(payload);
    }

    public void forceRender() {
        Runnable renderTask = () -> {
            renderAndBuildPatch(true);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            renderTask.run();
        } else {
            SwingUtilities.invokeLater(renderTask);
        }
    }

    private Server getServer() {
        if (server == null) {
            server = new Server(port);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            JettyWebSocketServletContainerInitializer.configure(context,
                    (servletContext, wsContainer) -> wsContainer.addMapping("/events", (req, res) -> new WebOnSwingEventSocket(this)));

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
            try {
                getServer().join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startRenderLoop() {
        new Thread(() -> {
            try {
                while (true) {
                    SwingUtilities.invokeAndWait(() -> {
                        renderAndBuildPatch(false);
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

    private FramePatch renderAndBuildPatch(boolean forceFullFrame) {
        if (componentToExpose == null || offscreen == null) {
            return null;
        }

        int width = componentToExpose.getWidth();
        int height = componentToExpose.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        if (offscreen.getWidth() != width || offscreen.getHeight() != height) {
            offscreen = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            previousFrame = null;
            forceFullFrame = true;
        }

        Graphics2D g = offscreen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, offscreen.getWidth(), offscreen.getHeight());
        componentToExpose.printAll(g);
        g.dispose();

        Rectangle changedRect = forceFullFrame
                ? new Rectangle(0, 0, offscreen.getWidth(), offscreen.getHeight())
                : computeDiffRectangle(previousFrame, offscreen);

        if (changedRect == null) {
            return null;
        }

        byte[] patchBytes = encodeImage(
                offscreen.getSubimage(changedRect.x, changedRect.y, changedRect.width, changedRect.height),
                "png"
        );
        byte[] fullFrame = encodeImage(offscreen, "jpg");

        if (patchBytes == null || fullFrame == null) {
            return null;
        }

        previousFrame = copyImage(offscreen);
        lastFrame = fullFrame;
        lastFrameTimestamp = System.currentTimeMillis();
        lastPatch = new FramePatch(changedRect, patchBytes);
        lastPatchSequence++;

        return new FramePatch(changedRect, patchBytes);
    }

    private Rectangle computeDiffRectangle(BufferedImage previous, BufferedImage current) {
        if (previous == null) {
            return new Rectangle(0, 0, current.getWidth(), current.getHeight());
        }
        if (previous.getWidth() != current.getWidth() || previous.getHeight() != current.getHeight()) {
            return new Rectangle(0, 0, current.getWidth(), current.getHeight());
        }

        int minX = current.getWidth();
        int minY = current.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < current.getHeight(); y++) {
            for (int x = 0; x < current.getWidth(); x++) {
                if (current.getRGB(x, y) != previous.getRGB(x, y)) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private BufferedImage copyImage(BufferedImage source) {
        int type = source.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_RGB
                : source.getType();
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private byte[] encodeImage(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class FramePatch {
        private final Rectangle rect;
        private final byte[] imageBytes;

        private FramePatch(Rectangle rect, byte[] imageBytes) {
            this.rect = rect;
            this.imageBytes = imageBytes;
        }
    }
}

