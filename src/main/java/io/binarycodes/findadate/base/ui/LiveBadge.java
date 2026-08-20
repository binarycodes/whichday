package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.html.Span;

/**
 * The pulsing dot that says results are still moving. The animation is dropped
 * under prefers-reduced-motion; the dot and its label carry the meaning on their
 * own.
 */
public class LiveBadge extends Span {

    public LiveBadge(String label) {
        addClassName("live-badge");
        var dot = new Span();
        dot.addClassName("live-dot");
        add(dot, new Span(label));
    }
}
