package io.binarycodes.whichday.base.ui;

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

    /**
     * The same figure without a denominator, for a poll anybody with the link may
     * answer. There is no invited list to be out of there — the number below the
     * fraction would be whoever happened to have joined so far, which says nothing
     * about how many answers are still coming.
     */
    public static String progress(Component owner, int answered, int invited, boolean anonymous) {
        return anonymous ? owner.getTranslation("count.answered", answered)
                : progress(owner, answered, invited);
    }
}
