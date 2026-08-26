package io.binarycodes.whichday;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * What a Spring test of anonymous mode declares — {@link WhichdayTest} with the other
 * mode's profile in front of it.
 *
 * <p>A second context, which is deliberate: the two modes differ in which beans exist
 * at all, so there is nothing to switch at runtime. Both are cached for the run.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = {Application.class, TestSupportConfiguration.class})
@ActiveProfiles({"anonymous", "test"})
public @interface AnonymousWhichdayTest {
}
