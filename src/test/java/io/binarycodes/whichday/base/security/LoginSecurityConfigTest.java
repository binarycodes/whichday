package io.binarycodes.whichday.base.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.binarycodes.whichday.Application;

/**
 * What the filter chain lets through, over a real servlet container.
 *
 * <p>A browserless test cannot see any of this: it bypasses the servlet filter chain
 * entirely, so a route that redirects to the provider and a stylesheet that does the
 * same both look fine from there. The JDK's own client rather than a Spring one,
 * because it is here to follow no redirects and read two headers.
 *
 * <p>This is login mode. {@link AnonymousSecurityConfigTest} is the same questions put
 * to the other chain, and the pair is what stops one mode's rule from silently becoming
 * the other's.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"login", "test"})
@DisplayName("What the login filter chain serves")
class LoginSecurityConfigTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("sends an unauthenticated visitor to the provider")
    void loginIsRequired() {
        var response = get("/");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).get().asString()
                .endsWith("/oauth2/authorization/oidc");
    }

    /**
     * The entry point and every partial it imports. A redirect carries no content type,
     * which a browser rejects as "not a supported stylesheet MIME type" — so a
     * stylesheet behind authentication is an unstyled application, not a login prompt.
     */
    @Test
    @DisplayName("serves the stylesheet and every partial it imports, as CSS")
    void stylesheetsAreServed() {
        for (var path : new String[] {"/styles.css", "/styles/colors.css", "/styles/screen.css",
                "/styles/shell.css", "/styles/calendar.css", "/styles/day-ballot.css",
                "/styles/tally.css", "/styles/poster.css", "/styles/poll-list.css",
                "/styles/people.css", "/styles/share.css", "/styles/ballot.css",
                "/styles/invitees.css"}) {
            var response = get(path);

            assertThat(response.statusCode()).as("%s status", path).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type")).as("%s content type", path)
                    .get().asString().startsWith("text/css");
        }
    }

    @Test
    @DisplayName("serves the theme and the font it asks for")
    void themeIsServed() {
        assertThat(get("/aura/aura.css").statusCode()).isEqualTo(200);
        assertThat(get("/aura/fonts/InstrumentSans/InstrumentSans.woff2").statusCode()).isEqualTo(200);
    }

    private HttpResponse<Void> get(String path) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException failure) {
            throw new AssertionError("Could not GET " + path, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while getting " + path, interrupted);
        }
    }
}
