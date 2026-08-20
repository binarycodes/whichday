package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;

/**
 * The design's type roles, as factories rather than a class each: they carry a
 * class name and nothing else, and a component per role would be nine files that
 * do the same thing.
 */
public final class Typography {

    private Typography() {
    }

    /** The opening question on the create screen — the largest type in the design. */
    public static H1 hero(String text) {
        var heading = new H1(text);
        heading.addClassName("display-hero");
        return heading;
    }

    public static H2 displayLarge(String text) {
        return heading(text, "display-l");
    }

    public static H2 displayMedium(String text) {
        return heading(text, "display-m");
    }

    public static H2 displaySmall(String text) {
        return heading(text, "display-s");
    }

    /** The oversized count a results screen opens with — "6 of 7". */
    public static Span stat(String text) {
        return span(text, "stat");
    }

    public static Paragraph lede(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("lede");
        return paragraph;
    }

    public static Paragraph body(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("body-s");
        return paragraph;
    }

    public static Span meta(String text) {
        return span(text, "meta");
    }

    public static Span sectionLabel(String text) {
        return span(text, "section-label");
    }

    public static Span fieldLabel(String text) {
        return span(text, "field-label");
    }

    private static H2 heading(String text, String className) {
        var heading = new H2(text);
        heading.addClassName(className);
        return heading;
    }

    private static Span span(String text, String className) {
        var span = new Span(text);
        span.addClassName(className);
        return span;
    }
}
