package com.webonswing;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class WebOnSwingStreamServlet extends HttpServlet {
    
    private final WebOnSwingServer webServer;

    public WebOnSwingStreamServlet(WebOnSwingServer webServer) {
        this.webServer = webServer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, private, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        
        OutputStream out = resp.getOutputStream();
        
        long lastSentSequence = -1;
        webServer.forceRender();

        while (true) {
            WebOnSwingServer.FramePatch patch = webServer.getLastPatch();
            long currentSequence = webServer.getLastPatchSequence();
            boolean patchChanged = currentSequence != lastSentSequence;
            if (patch != null && patchChanged) {
                try {
                    String payload = webServer.toPatchMessage(patch);
                    out.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    lastSentSequence = currentSequence;
                } catch (IOException e) {
                    break;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
