package io.binarycodes.whichday.poll.ui.share;

import java.net.URI;
import java.util.UUID;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.clipboard.Clipboard;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.webshare.ShareContent;
import com.vaadin.flow.component.webshare.WebShare;
import com.vaadin.flow.component.webshare.WebShareSupport;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;

import io.binarycodes.whichday.poll.domain.Poll;

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

    /** The absolute URL, for the share sheet and for anything leaving the application. */
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
     * Hands the link to whatever the browser shares with — the system share sheet
     * where there is one, the clipboard where there is not. Sharing has no
     * server-side equivalent, so this is the one place a share reaches for the client.
     *
     * <p>Bound to the click rather than run from a click listener, and that is the
     * whole of why the button used to do nothing. Both capabilities require a
     * transient user activation, and a click listener has already spent it: the
     * server hears the click, replies, and the reply arrives with no gesture left to
     * spend. {@code navigator.clipboard.writeText} rejected every time and the
     * {@code .catch(() => {})} it was written with swallowed the rejection, so the
     * button neither copied nor complained. These two bind the action to the click
     * itself, in the browser, where the activation still exists.
     *
     * <p>Which of the two a button gets is decided once, as the screen is built, from
     * the support signal the client seeds at bootstrap — {@code peek} rather than
     * {@code get} because that is a reading and not a subscription: re-running this
     * when the answer changed would leave one button carrying two actions, and there
     * is no registration to unbind the first. An answer that has not arrived yet gets
     * the clipboard, because Web Share is the narrower capability of the two — a phone
     * has it, a desktop browser often does not — and the clipboard is the one that
     * works either way.
     */
    public static void shareFrom(Button button, Poll poll) {
        if (WebShare.supportSignal().peek() == WebShareSupport.SUPPORTED) {
            WebShare.onClick(button).share(ShareContent.create()
                    .title(button.getTranslation("share.sheet.title", poll.title()))
                    .text(button.getTranslation("share.sheet.text", poll.title()))
                    .url(absolute(poll.id())));
            return;
        }
        Clipboard.onClick(button).writeText(absolute(poll.id()),
                copied -> Notification.show(button.getTranslation("share.copied")),
                failure -> Notification.show(button.getTranslation("share.copyFailed")));
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
