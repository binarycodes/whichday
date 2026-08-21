package io.binarycodes.whichday;

import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The two things a Spring test needs and the application does not provide: a clock the
 * test can move, and a way to empty the database between methods.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    /**
     * Primary rather than an override of {@code Application.clock()}: Boot leaves bean
     * overriding off, and {@code @Primary} is what makes every injection point and
     * {@code getBean(Clock.class)} resolve to this one.
     */
    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock(TestClock.START, ZoneOffset.UTC);
    }

    @Bean
    TestDatabase testDatabase(JdbcTemplate jdbc) {
        return new TestDatabase(jdbc);
    }
}
