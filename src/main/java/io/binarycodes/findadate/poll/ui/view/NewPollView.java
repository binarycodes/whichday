package io.binarycodes.findadate.poll.ui.view;

import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.AppHeader;
import io.binarycodes.findadate.base.ui.Screen;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.people.ui.TeamField;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * Where a poll starts: a name, the team it goes to, and one button through to the
 * calendar.
 */
@Route("new")
public class NewPollView extends Screen implements HasDynamicTitle {

    private final PollPresenter presenter;
    private final Binder<PollDraft> binder = new Binder<>(PollDraft.class);

    public NewPollView(PollPresenter presenter) {
        this.presenter = presenter;
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

        var fields = new Div(nameField(), teamField());
        fields.addClassNames("field-column", "push-2xl");
        binder.setBean(new PollDraft(null, Set.copyOf(presenter.everyone())));
        body(fields);

        var next = Actions.primary(getTranslation("create.next"), ignored -> create());
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
        field.addClassName("field-emphasis");
        field.setWidthFull();
        binder.forField(field)
                .asRequired(getTranslation("create.eventName.required"))
                .bind(PollDraft::getTitle, PollDraft::setTitle);

        var group = new Div(Typography.fieldLabel(getTranslation("create.eventName")), field);
        group.addClassName("stack-xs");
        return group;
    }

    /** Who the poll goes to. Everybody by default; tapping it opens the picker. */
    private Div teamField() {
        var field = new TeamField(presenter.everyone(), presenter.viewer(), presenter.teamName());
        field.setWidthFull();
        binder.forField(field)
                .withValidator(people -> people.size() > 1, getTranslation("create.deciders.needOne"))
                .bind(PollDraft::getInvited, PollDraft::setInvited);

        var group = new Div(Typography.fieldLabel(getTranslation("create.deciders")), field);
        group.addClassName("stack-xs");
        return group;
    }

    private void create() {
        var draft = new PollDraft();
        binder.writeBeanAsDraft(draft);
        if (!binder.validate().isOk()) {
            return;
        }
        var slug = presenter.create(draft.getTitle(), draft.getInvited());
        getUI().ifPresent(ui -> ui.navigate(CandidateDaysView.class, new RouteParameters("slug", slug)));
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
