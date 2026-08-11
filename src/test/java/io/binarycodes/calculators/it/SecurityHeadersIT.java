package io.binarycodes.calculators.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security headers must reach the Vaadin-rendered application page, not only
 * static resources. Spring Security writes its headers as the response commits,
 * which the page response doesn't go through — so a check against
 * {@code /colors.css} alone would pass while the page carried nothing.
 *
 * <p>Bound to the running server rather than {@code MockMvc} on purpose: the
 * behaviour under test is response-commit timing in a real container, which a
 * mocked response wouldn't reproduce.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Security headers")
class SecurityHeadersIT {

    private static final String REFERRER_POLICY = "strict-origin-when-cross-origin";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void bindToRunningServer() {
        this.client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + this.port)
                .build();
    }

    private RestTestClient.ResponseSpec get(String path) {
        return this.client.get().uri(path).exchange();
    }

    /** The bundle filename is content-hashed, so discover it from the page rather than pin it. */
    private String bundleScriptPath() {
        final byte[] body = get("/").expectStatus().isOk().expectBody().returnResult().getResponseBody();
        assertNotNull(body, "the application page returned no body");

        final Matcher matcher = Pattern.compile("/VAADIN/build/[A-Za-z0-9._/-]+\\.js")
                .matcher(new String(body));
        assertTrue(matcher.find(), "no /VAADIN/build script referenced by the page");
        return matcher.group();
    }

    @Test
    @DisplayName("The application page carries nosniff, referrer policy and DENY")
    void applicationPage_carriesSecurityHeaders() {
        get("/").expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", REFERRER_POLICY)
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    @DisplayName("Static resources carry the same headers")
    void staticResource_carriesSecurityHeaders() {
        get("/colors.css").expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", REFERRER_POLICY);
    }

    @Test
    @DisplayName("Bundled scripts carry nosniff")
    void bundleScript_carriesSecurityHeaders() {
        // Vaadin registers some framework paths as ignored by Spring Security, which
        // skips the filter chain entirely; nosniff on served JavaScript is the case
        // that header exists for, so pin that the bundle isn't among them. The status
        // check matters — a 404 travels the same chain and carries the headers too.
        get(bundleScriptPath()).expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", REFERRER_POLICY);
    }
}
