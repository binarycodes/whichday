package io.binarycodes.whichday.people.ui;

import com.vaadin.flow.component.contextmenu.ContextMenu;

import io.binarycodes.whichday.people.domain.Person;

/**
 * The account, top right, where the design puts it. Tapping it offers the one thing
 * an account can do here: sign out.
 *
 * <p>A context menu on a bare avatar rather than a {@code MenuBar}: a menu bar brings
 * a button and an overflow arrow the design does not draw, and suppressing both means
 * styling into its shadow root.
 */
public class AccountMenu extends PersonAvatar {

    public AccountMenu(Person viewer, Runnable onSignOut) {
        super(viewer);
        addClassName("account-avatar");
        getElement().setAttribute("title", viewer.displayName());

        var menu = new ContextMenu(this);
        menu.setOpenOnClick(true);
        menu.addItem(getTranslation("nav.signedInAs", viewer.displayName())).setEnabled(false);
        menu.addItem(getTranslation("nav.signOut"), ignored -> onSignOut.run());
    }
}
