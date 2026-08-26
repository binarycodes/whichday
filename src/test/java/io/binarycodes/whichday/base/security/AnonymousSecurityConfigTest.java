package io.binarycodes.whichday.base.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.binarycodes.whichday.Application;

/**
 * The same questions {@link LoginSecurityConfigTest} asks, put to the chain that has no
 * provider behind it. There is nowhere to send anybody, so nothing redirects.
 *
 * <p>The stylesheet cases look redundant next to the login ones and are not: anonymous
 * mode permits everything through a different configurer, and a chain that served the
 * routes but not the partials would render the application unstyled just as surely.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"anonymous", "test"})
@DisplayName("What the anonymous filter chain serves")
class AnonymousSecurityConfigTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("lets a visitor with no session straight in")
    void nobodyIsTurnedAway() {
        assertThat(get("/").statusCode()).isEqualTo(200);
    }

    /**
     * The link a poll is shared by. It is the case worth stating on its own: a mode
     * whose whole bargain is that a link works has to serve the link's own path to
     * somebody who has never been here.
     */
    @Test
    @DisplayName("serves a voting link to somebody who has never been here")
    void theSharedLinkOpens() {
        assertThat(get("/vote/" + UUID.randomUUID()).statusCode()).isEqualTo(200);
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
