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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private HttpResponse<String> get(String path) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + this.port + path))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String header(String path, String name) throws Exception {
        return get(path).headers().firstValue(name).orElse(null);
    }

    /** The bundle filename is content-hashed, so discover it from the page rather than pin it. */
    private String bundleScriptPath() throws Exception {
        final Matcher matcher = Pattern.compile("/VAADIN/build/[A-Za-z0-9._/-]+\\.js")
                .matcher(get("/").body());
        assertTrue(matcher.find(), "no /VAADIN/build script referenced by the page");
        return matcher.group();
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

    @Test
    @DisplayName("Bundled scripts carry nosniff")
    void bundleScript_carriesSecurityHeaders() throws Exception {
        // Vaadin registers some framework paths as ignored by Spring Security, which
        // skips the filter chain entirely; nosniff on served JavaScript is the case
        // that header exists for, so pin that the bundle isn't among them.
        final HttpResponse<String> response = get(bundleScriptPath());

        // A 404 would travel the same filter chain and carry the headers too, so the
        // assertions below only mean something once the script is known to be served.
        assertEquals(200, response.statusCode());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("strict-origin-when-cross-origin",
                response.headers().firstValue("Referrer-Policy").orElse(null));
    }
}
