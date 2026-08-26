package io.binarycodes.whichday.people.ui.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Home;
import io.binarycodes.whichday.base.ui.IdentityGuard;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * Anonymous mode's front door, and the only screen that stands in front of a shared
 * link. It asks the two things nobody else can supply: what to call you, and — if you
 * are coming back to a poll you called — the six digits that say so.
 *
 * <p>The name is the whole of identity here. The address behind it is minted, never
 * typed: an address anybody could type is an address anybody could type twice.
 *
 * <p>Login mode has a provider for this and reaches the screen through no path of its
 * own, so it is turned away at the door rather than left to render a form that would
 * mean nothing.
 */
@AnonymousAllowed
@Route("who")
public class IdentityView extends Screen implements BeforeEnterObserver, HasDynamicTitle {

    /** Long enough to be a name, short enough that the column it lands in holds it. */
    private static final int NAME_LIMIT = 60;
    private static final int CODE_LENGTH = 6;

    private final PollPresenter presenter;
    private final ViewerSession session;
    private final TextField name = new TextField();
    private final TextField code = new TextField();

    public IdentityView(PollPresenter presenter, ViewerSession session) {
        this.presenter = presenter;
        this.session = session;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.anonymous()) {
            event.forwardTo(Home.viewFor(presenter));
            return;
        }
        render();
    }

    private void render() {
        clearBody();
        clearFooter();

        var headline = Typography.hero(getTranslation("identity.headline"));
        headline.addClassName("push-3xl");
        var lede = Typography.lede(getTranslation("identity.lede"));
        lede.addClassName("push-l");
        body(headline, lede);

        var fields = new Div(nameField(), codeField());
        fields.addClassNames("field-column", "push-2xl");
        body(fields);

        var next = Actions.primary(getTranslation("identity.next"), ignored -> identify());
        next.setIcon(new Icon(VaadinIcon.ARROW_RIGHT));
        next.setIconAfterText(true);
        var footnote = Typography.meta(getTranslation("identity.footnote"));
        footnote.addClassNames("meta-faint", "meta-centred");
        footer(next, footnote);
    }

    private Div nameField() {
        name.setPlaceholder(getTranslation("identity.name.placeholder"));
        name.setMaxLength(NAME_LIMIT);
        name.addClassName("field-emphasis");
        name.setWidthFull();
        name.focus();

        var group = new Div(Typography.fieldLabel(getTranslation("identity.name")), name);
        group.addClassName("stack-xs");
        return group;
    }

    /**
     * Optional, and the hint says why: somebody arriving on a link has no code and
     * needs none, and an empty field that looks required is a field people invent an
     * answer for.
     */
    private Div codeField() {
        code.setPlaceholder(getTranslation("identity.code.placeholder"));
        code.setMaxLength(CODE_LENGTH);
        code.setAllowedCharPattern("[0-9]");
        code.setWidthFull();

        var hint = Typography.meta(getTranslation("identity.code.hint"));
        hint.addClassName("meta-faint");

        var group = new Div(Typography.fieldLabel(getTranslation("identity.code")), code, hint);
        group.addClassName("stack-xs");
        return group;
    }

    private void identify() {
        if (name.getValue().isBlank()) {
            Toast.show(getTranslation("identity.name.required"));
            return;
        }
        session.identify(name.getValue(), code.getValue());
        getUI().ifPresent(ui -> IdentityGuard.take()
                .ifPresentOrElse(ui::navigate, () -> ui.navigate(Home.viewFor(presenter))));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("identity.title");
    }
}
