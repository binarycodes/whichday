package io.binarycodes.whichday.base.security;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Anonymous mode: there is no provider, so there is nobody to send anybody to.
 *
 * <p>Every screen is reachable, and what a visitor may do is decided further in — a
 * link is what lets somebody see a poll and answer it, a six-digit code is what lets
 * somebody change one, and {@code PollService} is where both are checked. Nothing here
 * is a permission model; this only gets requests past the filter chain.
 *
 * <p>Navigation access control is turned off rather than every route being changed to
 * {@code @AnonymousAllowed}. Vaadin reads {@code @PermitAll} as <em>authenticated</em>,
 * and in this mode nobody is — so leaving the checker on would refuse every route.
 * Turning it off leaves the annotations meaning what they mean in
 * {@link LoginSecurityConfig}, where they are still consulted.
 *
 * <p>With the checker off, Vaadin's own request rules have nothing left to decide with:
 * they classify a URL by asking which view it reaches and what that view allows. So the
 * rule is stated here instead and the configurer is told not to add one — otherwise
 * every navigation logs that it could not tell whether the URL was public.
 */
@Configuration
@ConditionalOnProperty(name = "whichday.access.mode", havingValue = "anonymous")
public class AnonymousSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .with(vaadin(), configurer -> configurer
                        .enableNavigationAccessControl(false)
                        .enableAuthorizedRequestsConfiguration(false))
                .build();
    }
}
