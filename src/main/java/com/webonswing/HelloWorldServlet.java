package com.webonswing;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * A simple HTTP servlet that responds with "Hello World".
 */
public class HelloWorldServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        try (PrintWriter writer = response.getWriter()) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head><meta charset=\"UTF-8\"><title>Hello World</title></head>");
            writer.println("<body>");
            writer.println("<h1>Hello World</h1>");
            writer.println("<p>Jetty server is running.</p>");
            writer.println("<p>WebSocket endpoint: <code>/ws</code></p>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }
}
