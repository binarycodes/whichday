package io.binarycodes.whichday.people.ui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.page.ColorScheme;

import io.binarycodes.whichday.base.ui.ColorSchemeChoice;
import io.binarycodes.whichday.people.domain.Person;

/**
 * The account, top right, where the design puts it. Tapping it offers the two things
 * there are to offer: which way the page is painted, and signing out.
 *
 * <p>A context menu on a bare avatar rather than a {@code MenuBar}: a menu bar brings
 * a button and an overflow arrow the design does not draw, and suppressing both means
 * styling into its shadow root.
 *
 * <p>The labels are handed in. Signing out of a provider and dropping a name you typed
 * are not the same act and do not read the same, and the component has no business
 * knowing which of the two it is offering.
 */
public class AccountMenu extends PersonAvatar {

    /**
     * System first, because it is the answer for anybody who has not thought about it,
     * and the one the session starts on. Listed rather than mapped so the order on
     * screen is the order written here.
     */
    private static final List<Map.Entry<ColorScheme.Value, String>> SCHEMES = List.of(
            Map.entry(ColorScheme.Value.SYSTEM, "nav.theme.system"),
            Map.entry(ColorScheme.Value.LIGHT, "nav.theme.light"),
            Map.entry(ColorScheme.Value.DARK, "nav.theme.dark"));

    public AccountMenu(Person viewer, AccountLabels labels, ColorSchemeChoice scheme, Runnable onSignOut) {
        super(viewer);
        addClassName("account-avatar");
        getElement().setAttribute("title", viewer.displayName());

        var menu = new ContextMenu(this);
        menu.setOpenOnClick(true);
        menu.addItem(labels.signedInAs()).setEnabled(false);
        addSchemeItems(menu, scheme);
        menu.addItem(labels.signOut(), ignored -> onSignOut.run());
    }

    /**
     * A submenu, because "Theme" is a heading and a menu has no other way to say so — a
     * disabled row looks like an item that refuses to work.
     *
     * <p>Checking one unchecks the others by hand. They are a choice of one, and a
     * checkable item knows nothing about the others.
     */
    private void addSchemeItems(ContextMenu menu, ColorSchemeChoice scheme) {
        var schemes = menu.addItem(getTranslation("nav.theme")).getSubMenu();
        var items = new EnumMap<ColorScheme.Value, MenuItem>(ColorScheme.Value.class);
        SCHEMES.forEach(entry -> {
            var value = entry.getKey();
            var item = schemes.addItem(getTranslation(entry.getValue()), ignored -> {
                scheme.choose(value);
                getUI().ifPresent(scheme::applyTo);
                items.forEach((each, menuItem) -> menuItem.setChecked(each == value));
            });
            item.setCheckable(true);
            item.setChecked(value == scheme.chosen());
            items.put(value, item);
        });
    }
}
