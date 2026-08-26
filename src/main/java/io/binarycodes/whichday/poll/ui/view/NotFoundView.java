package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Home;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/** A link to a poll that is not here — expired, or never sent. */
@PermitAll
@Route("gone")
public class NotFoundView extends Screen implements HasDynamicTitle {

    public NotFoundView(PollPresenter presenter) {
        body(Typography.displayMedium(getTranslation("notFound.headline")),
                Typography.lede(getTranslation("notFound.lede")));

        var home = Actions.primary(getTranslation(presenter.anonymous()
                        ? "notFound.action.anonymous"
                        : "notFound.action"),
                ignored -> getUI().ifPresent(ui -> ui.navigate(Home.viewFor(presenter))));
        home.addClassName(Actions.HOME_CLASS);
        footer(home);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notFound.title");
    }
}
