package io.binarycodes.whichday;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * What every Spring test here declares. One annotation rather than three on each
 * class, because the context cache is keyed on the configuration: a class that drifts
 * by one annotation quietly bootstraps a second context and pays for it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = {Application.class, TestSupportConfiguration.class})
@ActiveProfiles("test")
public @interface WhichdayTest {
}
