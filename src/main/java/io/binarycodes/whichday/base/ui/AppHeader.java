package io.binarycodes.whichday.base.ui;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Div;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.ui.PersonAvatar;

/**
 * The wordmark and the viewer's avatar, which is where the design puts the account.
 *
 * <p>With no login yet, that avatar is also how you become somebody else — the
 * organizer's screens and a voter's screens are the same application, and without a
 * switcher only one of the two would be reachable. See
 * {@code docs/clarifications/0003-voter-identity.md}.
 *
 * <p>A context menu on a bare avatar rather than a {@code MenuBar}: a menu bar
 * brings a button and an overflow arrow the design does not draw, and suppressing
 * both means styling into its shadow root.
 */
public class AppHeader extends Div {

    public AppHeader(String wordmark,
                     Person viewer,
                     List<Person> people,
                     Consumer<Person> onSwitch,
                     Runnable onHome) {
        addClassName("app-header");

        // The wordmark is the way home on the screens that have no back chevron.
        var name = Actions.link(wordmark, ignored -> onHome.run());
        name.addClassNames("wordmark", Actions.HOME_CLASS);

        var avatar = new PersonAvatar(viewer);
        avatar.addClassName("account-avatar");
        avatar.getElement().setAttribute("title", getTranslation("nav.viewingAs", viewer.name()));

        var menu = new ContextMenu(avatar);
        menu.setOpenOnClick(true);
        people.forEach(person -> {
            var item = menu.addItem(getTranslation("nav.switchTo", person.name()),
                    ignored -> onSwitch.accept(person));
            item.setEnabled(!person.equals(viewer));
        });

        add(name, avatar);
    }
}
