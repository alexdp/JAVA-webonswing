package com.webonswing;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

public class WebOnSwingStreamServlet extends HttpServlet {
    
    private final WebOnSwingServer webServer;

    public WebOnSwingStreamServlet(WebOnSwingServer webServer) {
        this.webServer = webServer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String boundary = "boundary";
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("multipart/x-mixed-replace; boundary=" + boundary);
        resp.setHeader("Cache-Control", "no-cache, private, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        
        OutputStream out = resp.getOutputStream();
        
        long lastSentTimestamp = -1;

        while (true) {
            byte[] frame = webServer.getLastFrame();
            long currentTimestamp = webServer.getLastFrameTimestamp();
            boolean frameChanged = currentTimestamp != lastSentTimestamp;
            if (frame != null && (lastSentTimestamp == -1 || frameChanged)) {
                try {
                    out.write(("--" + boundary + "\r\n").getBytes());
                    out.write("Content-Type: image/jpeg\r\n".getBytes());
                    out.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
                    out.write(frame);
                    out.write("\r\n".getBytes());
                    out.flush();
                    lastSentTimestamp = currentTimestamp;
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
