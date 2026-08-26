package io.binarycodes.whichday.base.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.header.HeaderWriterFilter;

/**
 * The security headers, which are the same whichever way a deployment lets people in
 * — so they are configured once here rather than twice in the two chains.
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * Spring Security writes its headers as the response commits, and the response
     * Vaadin renders a page into never commits that way — so the application's own
     * routes would come out with no headers at all while static resources got the
     * full set. Verify a header change by curling a route, never only a static file
     * (CODING_CONVENTIONS.md §10a).
     */
    @Bean
    ObjectPostProcessor<HeaderWriterFilter> headersWrittenEagerly() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                filter.setShouldWriteHeadersEagerly(true);
                return filter;
            }
        };
    }
}
