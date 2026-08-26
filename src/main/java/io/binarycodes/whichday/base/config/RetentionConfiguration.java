package io.binarycodes.whichday.base.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the two windows a deployment named into the bean the sweep asks.
 *
 * <p>Both properties are written by {@code application.properties} with a default, so
 * neither is ever absent — an empty one is an operator who set the variable to nothing,
 * and that is a mistake worth failing on rather than reading as "keep everything".
 * {@code never} is how that is said on purpose.
 */
@Configuration(proxyBeanMethods = false)
public class RetentionConfiguration {

    @Bean
    Retention retention(@Value("${whichday.retention.after-poll-ends}") String afterPollEnds,
                        @Value("${whichday.retention.days}") String maximumAge) {
        return new Retention(RetentionWindow.of("WHICHDAY_RETENTION_AFTER_POLL_ENDS", afterPollEnds),
                RetentionWindow.of("WHICHDAY_RETENTION_DAYS", maximumAge));
    }
}
