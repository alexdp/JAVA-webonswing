package com.webonswing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the embedded Jetty server.
 */
class AppTest {

    private WebOnSwingServer server;
    private static final int TEST_PORT = 18080;

    @BeforeEach
    void setUp() throws Exception {
        server = App.createServer(TEST_PORT);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void testHelloWorldHttpResponse() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        try {
            int responseCode = connection.getResponseCode();
            assertEquals(200, responseCode, "Expected HTTP 200 OK");

            try (InputStream inputStream = connection.getInputStream()) {
                String responseBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(responseBody.contains("<canvas"), "Response should contain a video element");
            }
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void testServerStartsSuccessfully() {
        assertTrue(server.isRunning(), "Server should be running");
    }
}
