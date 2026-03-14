package com.webonswing;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;


public class UMLEditorJettyServer {

	public static void main(String[] args) throws Exception {
		Server server = new Server(8080);

		ServletContextHandler handler = new ServletContextHandler();
		handler.setContextPath("/");

		// Serve static file mini.html from classpath:/web
		java.net.URL webRoot = UMLEditorJettyServer.class.getResource("/web");
		if (webRoot != null) {
			ServletHolder staticHolder = new ServletHolder("static", DefaultServlet.class);
			staticHolder.setInitParameter("resourceBase", webRoot.toExternalForm());
			staticHolder.setInitParameter("dirAllowed", "false");
			handler.addServlet(staticHolder, "/mini.html");
		}

		// Configure websocket mapping for /events (uses MiniWebSwingUltimate.EventSocket)
/* 		JettyWebSocketServletContainerInitializer.configure(handler, (servletContext, wsContainer) -> {
			wsContainer.addMapping("/events", (req, resp) -> new WebOnSwingEventSocket());
		}); */
		
		/* ServletHolder sh = new ServletHolder(new UMLEditorWebServlet());
		sh.setName("VioletJWT");
		handler.addServlet(sh, "/*");
		handler.addEventListener(new BeanFactoryServletContextListener());
		handler.addEventListener(new ServletInit());
		handler.setSessionHandler(new SessionHandler()); */
		
		GzipHandler gzipHandler = new GzipHandler();
	    gzipHandler.setIncludedMethods("PUT", "POST", "GET");
	    gzipHandler.setInflateBufferSize(2048);
	    gzipHandler.setHandler(handler);
	    server.setHandler(gzipHandler);
		
		server.start();
		server.join();
	}

}