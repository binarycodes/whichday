package io.binarycodes.whichday.poll.ui.view;

import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Typography;

/** A link to a poll that is not here — expired, or never sent. */
@Route("gone")
public class NotFoundView extends Screen implements HasDynamicTitle {

    public NotFoundView() {
        body(Typography.displayMedium(getTranslation("notFound.headline")),
                Typography.lede(getTranslation("notFound.lede")));

        var home = Actions.primary(getTranslation("notFound.action"),
                ignored -> getUI().ifPresent(ui -> ui.navigate(PollsView.class)));
        home.addClassName(Actions.HOME_CLASS);
        footer(home);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notFound.title");
    }
}
