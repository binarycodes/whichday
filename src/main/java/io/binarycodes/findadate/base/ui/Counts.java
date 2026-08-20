package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.Component;

/**
 * The count phrases the screens build sentences out of. MessageFormat alone cannot
 * pick a plural, so the choice is made here once rather than at every call site.
 */
public final class Counts {

    private Counts() {
    }

    public static String days(Component owner, int count) {
        return owner.getTranslation(count == 1 ? "count.days.one" : "count.days.many", count);
    }

    public static String progress(Component owner, int answered, int invited) {
        return owner.getTranslation("count.progress", answered, invited);
    }
}
