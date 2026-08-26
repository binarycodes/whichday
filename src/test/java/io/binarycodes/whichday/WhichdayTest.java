package io.binarycodes.whichday;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * What a Spring test of login mode declares. One annotation rather than three on each
 * class, because the context cache is keyed on the configuration: a class that drifts
 * by one annotation quietly bootstraps a second context and pays for it.
 *
 * <p>Two profiles, in this order. {@code login} brings the mode's own file — the OIDC
 * registration among it — and {@code test} comes after so that its overrides win; the
 * order is the whole reason the offline provider works. The anonymous counterpart is
 * {@link AnonymousWhichdayTest}, and the two are separate contexts on purpose: they
 * differ in which beans exist, which is not something a test can switch at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = {Application.class, TestSupportConfiguration.class})
@ActiveProfiles({"login", "test"})
public @interface WhichdayTest {
}
