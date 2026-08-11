package io.binarycodes.calculators.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Security headers must reach the Vaadin-rendered application page, not only
 * static resources. Spring Security writes its headers when the response is
 * committed, which the page response doesn't go through — so a check against
 * {@code /colors.css} alone would pass while the page carried nothing.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Security headers")
class SecurityHeadersIT {

    @LocalServerPort
    private int port;

    private String header(String path, String name) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + this.port + path))
                .build();
        final HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        return response.headers().firstValue(name).orElse(null);
    }

    @Test
    @DisplayName("The application page carries nosniff, referrer policy and DENY")
    void applicationPage_carriesSecurityHeaders() throws Exception {
        assertEquals("nosniff", header("/", "X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", header("/", "Referrer-Policy"));
        assertEquals("DENY", header("/", "X-Frame-Options"));
    }

    @Test
    @DisplayName("Static resources carry the same headers")
    void staticResource_carriesSecurityHeaders() throws Exception {
        assertEquals("nosniff", header("/colors.css", "X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", header("/colors.css", "Referrer-Policy"));
    }
}
