package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.Home;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.domain.EmailAddress;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.PersonAvatar;
import io.binarycodes.whichday.people.ui.InviteeRow;
import io.binarycodes.whichday.poll.domain.AccountMatch;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * Who decides with you. A whole screen rather than an overlay: the search needs a
 * keyboard, a result list and a growing set of chips at once, and a dialog on a
 * phone gives all three the same few hundred pixels.
 *
 * <p>Nobody is listed until the organizer has typed enough of an address to have
 * known who they were looking for, and anything that is an address but not an
 * account becomes an invitation rather than an error.
 */
@PermitAll
@Route("new/invitees")
public class InviteeSearchView extends Screen implements BeforeEnterObserver, HasDynamicTitle {

    /** The design's rule: fire at three characters, debounced. */
    private static final int SEARCH_DEBOUNCE_MILLIS = 250;

    private final PollPresenter presenter;
    private final TextField query = new TextField();
    private final Div results = new Div();
    private final Div added = new Div();
    private final Div hint = new Div();

    public InviteeSearchView(PollPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Built on navigation rather than in the constructor: Vaadin reuses a view instance
     * when the route it is asked for is the one already showing, so a constructor-only
     * build leaves whatever it drew the first time.
     *
     * <p>Anonymous mode has no directory to search and nobody to invite into one, so
     * the screen is not part of that mode and the URL leads home instead. Nothing
     * navigates here — the field that did is not drawn — and this is for whoever typed
     * the path anyway.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (presenter.anonymous()) {
            event.forwardTo(Home.viewFor(presenter));
            return;
        }
        render();
    }

    private void render() {
        clearBody();
        clearFooter();

        body(new TopBar(titleOfPoll())
                .withBack(getTranslation("nav.back"), this::backToCreate)
                .withTrailingSpace());

        var headline = Typography.displaySmall(getTranslation("invitees.headline"));
        headline.addClassName("push-xl");
        body(headline);

        query.setPlaceholder(getTranslation("invitees.placeholder"));
        query.setValueChangeMode(ValueChangeMode.LAZY);
        query.setValueChangeTimeout(SEARCH_DEBOUNCE_MILLIS);
        query.setClearButtonVisible(true);
        query.setWidthFull();
        query.addClassNames("field-emphasis", "push-l");
        query.addValueChangeListener(event -> onQueryChanged(event.getValue()));

        hint.addClassNames("meta", "meta-faint", "push-s");
        results.addClassName("push-m");
        added.addClassName("push-2xl");
        body(query, hint, results, added);

        var reassurance = new HintBar(VaadinIcon.SEARCH,
                getTranslation("invitees.privacy", AccountDirectory.MINIMUM_QUERY_LENGTH));
        var next = Actions.primary(getTranslation("create.next"), ignored -> chooseDays());
        next.setEnabled(!presenter.draft().isEmpty());
        footer(reassurance, next);

        renderResults();
        renderAdded();
    }

    private void onQueryChanged(String value) {
        if (value != null && EmailAddress.looksPasted(value)) {
            acceptPastedList(value);
            return;
        }
        renderResults();
    }

    /**
     * A pasted list splits on commas and newlines. Whatever is a well-formed address
     * is added; the first thing that is not stays in the field, because an address
     * with a typo in it is worth fixing rather than silently dropping.
     */
    private void acceptPastedList(String pasted) {
        var leftovers = new StringBuilder();
        var accepted = 0;
        for (var candidate : EmailAddress.split(pasted)) {
            if (EmailAddress.isWellFormed(candidate)) {
                presenter.draft().invite(presenter.inviteeFor(EmailAddress.normalise(candidate)));
                accepted++;
            } else {
                leftovers.append(leftovers.isEmpty() ? "" : ", ").append(candidate);
            }
        }
        query.setValue(leftovers.toString());
        if (accepted > 0) {
            Toast.success(accepted == 1
                    ? getTranslation("invitees.pasted.one")
                    : getTranslation("invitees.pasted.many", accepted));
        }
        render();
    }

    private void renderResults() {
        results.removeAll();
        var typed = query.getValue() == null ? "" : query.getValue().strip();

        if (typed.isEmpty()) {
            hint.setText(getTranslation("invitees.minimum", AccountDirectory.MINIMUM_QUERY_LENGTH));
            return;
        }
        if (presenter.draft().contains(EmailAddress.normalise(typed))) {
            showProblem(getTranslation("invitees.already",
                    presenter.inviteeFor(EmailAddress.normalise(typed)).firstName()));
            return;
        }
        // The organizer is never a match, so a query only they answer to would come
        // back as "keep typing" — as if they did not exist.
        if (presenter.searchMatchesViewer(typed)) {
            showProblem(getTranslation("invitees.itsYou"));
            return;
        }
        if (typed.length() < AccountDirectory.MINIMUM_QUERY_LENGTH) {
            hint.setText(getTranslation("invitees.tooShort", AccountDirectory.MINIMUM_QUERY_LENGTH));
            return;
        }

        query.setInvalid(false);
        var matches = presenter.searchInvitees(typed);
        hint.setText(matches.isEmpty() && EmailAddress.isWellFormed(typed)
                ? getTranslation("invitees.noMatch")
                : "");

        var panel = new Div();
        panel.addClassName("match-panel");
        if (!matches.isEmpty()) {
            var header = new Span(getTranslation("invitees.matches", matches.size()));
            header.addClassName("match-header");
            panel.add(header);
            matches.forEach(match -> panel.add(matchRow(match, typed)));
        }
        panel.add(EmailAddress.isWellFormed(typed) ? inviteRow(typed) : keepTypingRow(typed));
        results.add(panel);
    }

    private void showProblem(String message) {
        query.setInvalid(true);
        query.setErrorMessage(message);
        hint.setText("");
    }

    private NativeButton matchRow(AccountMatch match, String typed) {
        var name = new Div(highlighted(match.person().email(), typed));
        name.addClassName("match-email");
        var reason = new Div(new Span(match.hasHistory()
                ? reasonForHistory(match)
                : getTranslation("invitees.sameWorkspace")));
        reason.addClassName("match-reason");
        var text = new Div(name, reason);
        text.addClassName("match-text");

        var row = new NativeButton();
        row.addClassName("match-row");
        row.setAriaLabel(getTranslation("invitees.addPerson", match.person().email()));
        row.add(new PersonAvatar(match.person()), text, new Icon(VaadinIcon.PLUS));
        row.addClickListener(ignored -> invite(match.person()));
        return row;
    }

    private String reasonForHistory(AccountMatch match) {
        return match.sharedPolls() == 1
                ? getTranslation("invitees.decidedTogether.one")
                : getTranslation("invitees.decidedTogether.many", match.sharedPolls());
    }

    /** The matched run in bold, so it is obvious why a row came back. */
    private Component[] highlighted(String email, String typed) {
        var position = email.toLowerCase(Locale.ROOT).indexOf(EmailAddress.normalise(typed));
        if (position < 0) {
            return new Component[] {new Span(email)};
        }
        var match = new Span(email.substring(position, position + typed.length()));
        match.addClassName("match-hit");
        return new Component[] {
                new Span(email.substring(0, position)),
                match,
                new Span(email.substring(position + typed.length()))};
    }

    /** An address with no account behind it. Not an error — an invitation. */
    private Div inviteRow(String email) {
        var normalised = EmailAddress.normalise(email);
        return outsiderCard(normalised, !presenter.hasAccount(normalised));
    }

    private Div outsiderCard(String email, boolean outsider) {
        var mark = new Span(new Icon(VaadinIcon.PAPERPLANE));
        mark.addClassName(outsider ? "outsider-mark" : "outsider-mark-known");

        var title = new Div(new Span(outsider
                ? getTranslation("invitees.noAccount", getTranslation("app.name"))
                : getTranslation("invitees.hasAccount")));
        title.addClassName("outsider-title");
        var body = new Div(new Span(outsider
                ? getTranslation("invitees.byLink")
                : getTranslation("invitees.addDirectly")));
        body.addClassName("outsider-body");
        var text = new Div(title, body);
        text.addClassName("match-text");

        var row = new Div(mark, text);
        row.addClassName("outsider-row");

        var invite = Actions.primary(getTranslation("invitees.invite", email),
                ignored -> invite(presenter.inviteeFor(email)));
        invite.addClassName("outsider-action");

        var card = new Div(row, invite);
        card.addClassName("outsider-card");
        return card;
    }

    /** Still typing: the raw string is offered so the row never disappears mid-thought. */
    private Div keepTypingRow(String typed) {
        var mark = new Span(new Icon(VaadinIcon.PAPERPLANE));
        mark.addClassName("outsider-mark-pending");
        var text = new Div(new Span(getTranslation("invitees.keepTyping", typed)));
        text.addClassNames("match-text", "match-pending");
        var row = new Div(mark, text);
        row.addClassNames("match-row", "match-row-quiet");
        return row;
    }

    private void renderAdded() {
        added.removeAll();
        if (presenter.draft().isEmpty()) {
            return;
        }
        var count = Typography.sectionLabel(getTranslation("invitees.added", presenter.draft().invitees().size()));
        var clear = Actions.link(getTranslation("invitees.clearAll"), ignored -> {
            presenter.draft().clear();
            render();
        });
        var header = new Div(count, clear);
        header.addClassName("row-between");

        var rows = new Div();
        rows.addClassNames("stack", "push-m");
        presenter.draft().invitees().forEach(person ->
                rows.add(new InviteeRow(person, getTranslation("invitees.remove"), this::uninvite)));
        added.add(header, rows);
    }

    private void uninvite(Person person) {
        presenter.draft().uninvite(person);
        render();
    }

    private void invite(Person person) {
        presenter.draft().invite(person);
        query.clear();
        render();
    }

    private String titleOfPoll() {
        return presenter.draft().title().isBlank()
                ? getTranslation("create.title")
                : presenter.draft().title();
    }

    private void backToCreate() {
        getUI().ifPresent(ui -> ui.navigate(NewPollView.class));
    }

    private void chooseDays() {
        var id = presenter.createFromDraft();
        getUI().ifPresent(ui -> ui.navigate(CandidateDaysView.class, new RouteParameters("id", id.toString())));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("invitees.title");
    }
}
