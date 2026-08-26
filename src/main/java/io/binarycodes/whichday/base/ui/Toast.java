package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.notification.Notification;

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
 */
public final class Toast {

    private static final int VISIBLE_MILLIS = 5000;

    private Toast() {
    }

    public static void show(String message) {
        var toast = new Notification(message);
        toast.setPosition(Notification.Position.TOP_CENTER);
        toast.setDuration(VISIBLE_MILLIS);
        toast.addClassName("toast");
        toast.open();
    }
}
