package com.webonswing;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

public class WebOnSwingPageServlet extends HttpServlet {

    private static final String[] MINI_HTML_PATHS = {
            "/com/webonswing/web/mini.html",
            "/web/mini.html"
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=UTF-8");

        InputStream pageStream = null;
        for (String path : MINI_HTML_PATHS) {
            pageStream = WebOnSwingPageServlet.class.getResourceAsStream(path);
            if (pageStream != null) {
                break;
            }
        }

        if (pageStream == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "mini.html not found on classpath");
            return;
        }

        try (InputStream in = pageStream) {
            byte[] bytes = in.readAllBytes();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getOutputStream().write(bytes);
        }
    }
}
