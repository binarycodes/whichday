package io.binarycodes.whichday.poll.ui.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.AppHeader;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.InviteeChips;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * Where a poll starts: a name, who decides it, and one button through to the
 * calendar. Both fields write to the session's draft, so stepping out to search for
 * somebody and coming back loses nothing.
 */
@Route("new")
public class NewPollView extends Screen implements BeforeEnterObserver, HasDynamicTitle {

    private final PollPresenter presenter;

    public NewPollView(PollPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Built on navigation rather than in the constructor: Vaadin reuses a view instance
     * when the route it is asked for is the one already showing, so a constructor-only
     * build leaves whatever it drew the first time.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        render();
    }

    private void render() {
        clearBody();
        clearFooter();

        body(new AppHeader(getTranslation("app.name"), presenter.viewer(), presenter.everyone(),
                this::switchViewer, this::goHome));

        var headline = Typography.hero(getTranslation("create.headline"));
        headline.addClassName("push-3xl");
        var lede = Typography.lede(getTranslation("create.lede"));
        lede.addClassName("push-l");
        body(headline, lede);

        var fields = new Div(nameField(), inviteeField());
        fields.addClassNames("field-column", "push-2xl");
        body(fields);

        var next = Actions.primary(getTranslation("create.next"), ignored -> chooseDays());
        next.setIcon(new Icon(VaadinIcon.ARROW_RIGHT));
        next.setIconAfterText(true);
        var footnote = Typography.meta(getTranslation("create.footnote"));
        footnote.addClassNames("meta-faint", "meta-centred");
        footer(next, footnote);
    }

    private Div nameField() {
        var field = new TextField();
        field.setPlaceholder(getTranslation("create.eventName.placeholder"));
        field.setValueChangeMode(ValueChangeMode.LAZY);
        field.setValue(presenter.draft().title());
        field.addValueChangeListener(event -> presenter.draft().rename(event.getValue()));
        field.addClassName("field-emphasis");
        field.setWidthFull();

        var group = new Div(Typography.fieldLabel(getTranslation("create.eventName")), field);
        group.addClassName("stack-xs");
        return group;
    }

    /**
     * Chips for whoever is already on the poll, and a prompt that opens the screen
     * that searches. The search is not inline: a phone keyboard over a wrapping chip
     * field leaves nowhere to show matches.
     */
    private Div inviteeField() {
        var chips = new InviteeChips(presenter.draft().invitees(),
                getTranslation("invitees.remove"),
                presenter.draft().isEmpty()
                        ? getTranslation("invitees.addFirst")
                        : getTranslation("invitees.add"),
                this::uninvite,
                this::openSearch);

        var hint = Typography.meta(getTranslation("invitees.minimum", AccountDirectory.MINIMUM_QUERY_LENGTH));
        hint.addClassName("meta-faint");

        var group = new Div(Typography.fieldLabel(getTranslation("create.deciders")), chips, hint);
        group.addClassName("stack-xs");
        return group;
    }

    private void uninvite(Person person) {
        presenter.draft().uninvite(person);
        render();
    }

    private void openSearch() {
        if (titleIsMissing()) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(InviteeSearchView.class));
    }

    private void chooseDays() {
        if (titleIsMissing()) {
            return;
        }
        if (presenter.draft().isEmpty()) {
            Notification.show(getTranslation("invitees.needOne"));
            return;
        }
        var slug = presenter.createFromDraft();
        getUI().ifPresent(ui -> ui.navigate(CandidateDaysView.class, new RouteParameters("slug", slug)));
    }

    private boolean titleIsMissing() {
        if (!presenter.draft().title().isBlank()) {
            return false;
        }
        Notification.show(getTranslation("create.eventName.required"));
        return true;
    }

    private void goHome() {
        getUI().ifPresent(ui -> ui.navigate(PollsView.class));
    }

    private void switchViewer(Person person) {
        presenter.switchViewer(person);
        render();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("create.title");
    }
}
