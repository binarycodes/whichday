package io.binarycodes.findadate.poll.ui.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Screen;
import io.binarycodes.findadate.base.ui.Typography;

/** A link to a poll that is not here — expired, or never sent. */
@Route("gone")
public class NotFoundView extends Screen implements HasDynamicTitle {

    public NotFoundView() {
        body(Typography.displayMedium(getTranslation("notFound.headline")),
                Typography.lede(getTranslation("notFound.lede")));

        var back = new Button(getTranslation("notFound.action"),
                ignored -> getUI().ifPresent(ui -> ui.navigate(PollsView.class)));
        back.addClassNames("action", "action-primary");
        footer(back);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notFound.title");
    }
}
