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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
                List<PatchChunk> chunks = new ArrayList<>();
                chunks.add(new PatchChunk(new Rectangle(0, 0, width, height), initialPatch));
                lastPatch = new FramePatch(chunks);
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
        payload.put("type", "FRAME_PATCHES");

        List<Map<String, Object>> serializedPatches = new ArrayList<>(patch.chunks.size());
        for (PatchChunk chunk : patch.chunks) {
            Map<String, Object> serialized = new HashMap<>();
            serialized.put("x", chunk.rect.x);
            serialized.put("y", chunk.rect.y);
            serialized.put("width", chunk.rect.width);
            serialized.put("height", chunk.rect.height);
            serialized.put("image", Base64.getEncoder().encodeToString(chunk.imageBytes));
            serializedPatches.add(serialized);
        }
        payload.put("patches", serializedPatches);
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

        List<Rectangle> changedRects = forceFullFrame
                ? List.of(new Rectangle(0, 0, offscreen.getWidth(), offscreen.getHeight()))
                : computeDiffRectangles(previousFrame, offscreen);

        if (changedRects.isEmpty()) {
            return null;
        }

        if (shouldSendFullFrame(changedRects, offscreen.getWidth(), offscreen.getHeight())) {
            changedRects = List.of(new Rectangle(0, 0, offscreen.getWidth(), offscreen.getHeight()));
        }

        List<PatchChunk> chunks = new ArrayList<>(changedRects.size());
        for (Rectangle rect : changedRects) {
            byte[] patchBytes = encodeImage(
                    offscreen.getSubimage(rect.x, rect.y, rect.width, rect.height),
                    "png"
            );
            if (patchBytes == null) {
                return null;
            }
            chunks.add(new PatchChunk(rect, patchBytes));
        }

        byte[] fullFrame = encodeImage(offscreen, "jpg");

        if (fullFrame == null) {
            return null;
        }

        previousFrame = copyImage(offscreen);
        lastFrame = fullFrame;
        lastFrameTimestamp = System.currentTimeMillis();
        lastPatch = new FramePatch(chunks);
        lastPatchSequence++;

        return lastPatch;
    }

    private boolean shouldSendFullFrame(List<Rectangle> changedRects, int frameWidth, int frameHeight) {
        long totalChangedArea = 0;
        for (Rectangle rect : changedRects) {
            totalChangedArea += (long) rect.width * rect.height;
        }
        long frameArea = (long) frameWidth * frameHeight;
        return changedRects.size() > 64 || totalChangedArea > (frameArea * 60 / 100);
    }

    private List<Rectangle> computeDiffRectangles(BufferedImage previous, BufferedImage current) {
        if (previous == null) {
            return List.of(new Rectangle(0, 0, current.getWidth(), current.getHeight()));
        }
        if (previous.getWidth() != current.getWidth() || previous.getHeight() != current.getHeight()) {
            return List.of(new Rectangle(0, 0, current.getWidth(), current.getHeight()));
        }

        List<Rectangle> rectangles = new ArrayList<>();
        Map<RunKey, RectAccumulator> activeRuns = new LinkedHashMap<>();

        for (int y = 0; y < current.getHeight(); y++) {
            List<RunKey> rowRuns = new ArrayList<>();
            int x = 0;
            while (x < current.getWidth()) {
                if (current.getRGB(x, y) == previous.getRGB(x, y)) {
                    x++;
                    continue;
                }
                int startX = x;
                while (x < current.getWidth() && current.getRGB(x, y) != previous.getRGB(x, y)) {
                    x++;
                }
                rowRuns.add(new RunKey(startX, x - 1));
            }

            Map<RunKey, RectAccumulator> nextRuns = new LinkedHashMap<>();
            for (RunKey run : rowRuns) {
                RectAccumulator rect = activeRuns.remove(run);
                if (rect == null) {
                    rect = new RectAccumulator(run.startX, y, run.endX - run.startX + 1, 1);
                } else {
                    rect.height++;
                }
                nextRuns.put(run, rect);
            }

            for (RectAccumulator completed : activeRuns.values()) {
                rectangles.add(completed.toRectangle());
            }
            activeRuns = nextRuns;
        }

        for (RectAccumulator completed : activeRuns.values()) {
            rectangles.add(completed.toRectangle());
        }

        return rectangles;
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

    private static class RunKey {
        private final int startX;
        private final int endX;

        private RunKey(int startX, int endX) {
            this.startX = startX;
            this.endX = endX;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RunKey)) {
                return false;
            }
            RunKey other = (RunKey) obj;
            return startX == other.startX && endX == other.endX;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(startX);
            result = 31 * result + Integer.hashCode(endX);
            return result;
        }
    }

    private static class RectAccumulator {
        private final int x;
        private final int y;
        private final int width;
        private int height;

        private RectAccumulator(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private Rectangle toRectangle() {
            return new Rectangle(x, y, width, height);
        }
    }

    private static class PatchChunk {
        private final Rectangle rect;
        private final byte[] imageBytes;

        private PatchChunk(Rectangle rect, byte[] imageBytes) {
            this.rect = rect;
            this.imageBytes = imageBytes;
        }
    }

    public static class FramePatch {
        private final List<PatchChunk> chunks;

        private FramePatch(List<PatchChunk> chunks) {
            this.chunks = chunks;
        }
    }
}

