package io.binarycodes.whichday.poll.ui.share;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.vaadin.flow.component.Component;

import io.binarycodes.whichday.poll.domain.Poll;

/**
 * Sharing by mail, as a {@code mailto:} the browser hands to whatever the reader
 * actually uses. The application sends nothing itself — there is no mail server
 * behind it, and a button that silently did nothing would be worse than one that
 * opens a draft.
 */
public final class MailLink {

    private MailLink() {
    }

    public static String invitation(Component owner, Poll poll) {
        return mailto(owner.getTranslation("mail.invite.subject", poll.title()),
                owner.getTranslation("mail.invite.body", poll.title(), VotingLink.absolute(poll.id())));
    }

    public static String announcement(Component owner, Poll poll, String lockedDate) {
        return mailto(owner.getTranslation("mail.locked.subject", poll.title()),
                owner.getTranslation("mail.locked.body", poll.title(), lockedDate));
    }

    private static String mailto(String subject, String body) {
        return "mailto:?subject=" + encode(subject) + "&body=" + encode(body);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
