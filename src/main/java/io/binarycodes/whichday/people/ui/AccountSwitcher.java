package io.binarycodes.whichday.people.ui;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.contextmenu.ContextMenu;

import io.binarycodes.whichday.people.domain.Person;

/**
 * The account, top right, where the design puts it — and, with no login yet, also
 * how you become somebody else. See
 * {@code docs/clarifications/0003-voter-identity.md}.
 *
 * <p>A context menu on a bare avatar rather than a {@code MenuBar}: a menu bar
 * brings a button and an overflow arrow the design does not draw, and suppressing
 * both means styling into its shadow root.
 */
public class AccountSwitcher extends PersonAvatar {

    public AccountSwitcher(Person viewer, List<Person> people, Consumer<Person> onSwitch) {
        super(viewer);
        addClassName("account-avatar");
        getElement().setAttribute("title", getTranslation("nav.viewingAs", viewer.displayName()));

        var menu = new ContextMenu(this);
        menu.setOpenOnClick(true);
        people.forEach(person -> {
            var item = menu.addItem(getTranslation("nav.switchTo", person.displayName()),
                    ignored -> onSwitch.accept(person));
            item.setEnabled(!person.equals(viewer));
        });
    }
}
