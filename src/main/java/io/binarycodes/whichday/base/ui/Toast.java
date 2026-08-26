package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * What the application says back, at the top of the screen rather than over the button
 * that was just pressed.
 *
 * <p>Vaadin puts a notification bottom-left, which on a phone is exactly where the
 * primary action is — so for the five seconds a message showed, the button it was about
 * could not be pressed. Nothing said here is worth blocking the way forward, so it goes
 * up into the header band instead, where the worst it covers is a back chevron.
 *
 * <p>Five seconds is Vaadin's own default and is restated here because the position is
 * not: a reader who looks up a moment late should still catch it, and nothing here is
 * long enough to need longer.
 *
 * <p>Three flavours, and the colour is the theme's rather than ours. A refusal and a
 * confirmation are not the same message, and a reader who glanced away should be able to
 * tell which arrived without reading it. {@link #show} stays uncoloured for the messages
 * that are neither — a draft removed on request is not a triumph, and painting it green
 * would say the application is pleased about it.
 */
public final class Toast {

    private static final int VISIBLE_MILLIS = 5000;

    private Toast() {
    }

    /** Something happened that was neither asked for again nor refused. */
    public static void show(String message) {
        open(message, null, false);
    }

    /** Something the reader asked for, done. Painted in the theme's success colour. */
    public static void success(String message) {
        open(message, NotificationVariant.SUCCESS, false);
    }

    /**
     * Something was refused, and the message says what would fix it. Painted in the
     * theme's error colour, and assertive: it answers what the reader just did, so a
     * screen reader should interrupt with it rather than queue it behind whatever it was
     * already saying.
     */
    public static void error(String message) {
        open(message, NotificationVariant.ERROR, true);
    }

    private static void open(String message, NotificationVariant variant, boolean assertive) {
        var toast = new Notification(message);
        toast.setPosition(Notification.Position.TOP_CENTER);
        toast.setDuration(VISIBLE_MILLIS);
        toast.addClassName("toast");
        toast.setAssertive(assertive);
        if (variant != null) {
            toast.addThemeVariants(variant);
        }
        toast.open();
    }
}
