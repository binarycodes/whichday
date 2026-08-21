package io.binarycodes.whichday.poll.ui.share;

import java.net.URI;
import java.util.UUID;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;

/**
 * The one link a poll is shared by, taken from the deployment's own request rather
 * than from configuration — so it is right in development and right behind a proxy
 * without either being told about the other.
 *
 * <p>A navigation inside the application arrives as a UIDL request whose path is
 * Vaadin's, not the route's; the scheme, host and port are still the browser's,
 * which is all this needs.
 */
public final class VotingLink {

    private static final String VOTE_PATH = "/vote/";
    private static final String SCHEME_SEPARATOR = "://";

    private VotingLink() {
    }

    /** The absolute URL, for the clipboard and for anything leaving the application. */
    public static String absolute(UUID id) {
        return origin() + path(id);
    }

    /** The same link without its scheme, which is how the design shows it on screen. */
    public static String display(UUID id) {
        var absolute = absolute(id);
        var separator = absolute.indexOf(SCHEME_SEPARATOR);
        return separator < 0 ? absolute : absolute.substring(separator + SCHEME_SEPARATOR.length());
    }

    /**
     * The clipboard is a browser capability with no server-side equivalent, so this
     * is the one place a share reaches for the client. A browser that refuses
     * permission is left alone rather than told twice.
     */
    public static void copyToClipboard(Component owner, UUID id) {
        owner.getUI().ifPresent(ui -> ui.getPage().executeJs(
                "if (navigator.clipboard) { navigator.clipboard.writeText($0).catch(() => {}); }",
                absolute(id)));
    }

    private static String path(UUID id) {
        return VOTE_PATH + id;
    }

    private static String origin() {
        if (VaadinRequest.getCurrent() instanceof VaadinServletRequest request) {
            var requested = URI.create(request.getRequestURL().toString());
            return requested.getScheme() + SCHEME_SEPARATOR + requested.getAuthority() + request.getContextPath();
        }
        return "";
    }
}
