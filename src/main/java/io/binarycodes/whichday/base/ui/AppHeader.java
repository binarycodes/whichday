package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.html.Div;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.ui.AccountLabels;
import io.binarycodes.whichday.people.ui.AccountMenu;

/**
 * The wordmark and the account, which is the header the design draws on the screens
 * that open the application. The wordmark is the way home; the account sits hard
 * right, as it does wherever it appears.
 */
public class AppHeader extends Div {

    public AppHeader(String wordmark, Person viewer, AccountLabels labels,
            ColorSchemeChoice scheme, Runnable onSignOut, Runnable onHome) {
        addClassName("app-header");

        // The wordmark is the way home on the screens that have no back chevron.
        var name = Actions.link(wordmark, ignored -> onHome.run());
        name.addClassNames("wordmark", Actions.HOME_CLASS);

        add(name, new AccountMenu(viewer, labels, scheme, onSignOut));
    }
}
