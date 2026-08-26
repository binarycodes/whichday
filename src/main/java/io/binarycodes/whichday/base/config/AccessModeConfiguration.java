package io.binarycodes.whichday.base.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the mode a deployment named into the bean everything else asks.
 *
 * <p>The property is written by the profile's own file, so a mode nobody has a file
 * for leaves it unset — which is exactly the case worth catching. The environment
 * variable is the fallback purely so the failure can quote what the operator actually
 * typed rather than the empty string it resolved to.
 */
@Configuration(proxyBeanMethods = false)
public class AccessModeConfiguration {

    @Bean
    AccessMode accessMode(@Value("${whichday.access.mode:${WHICHDAY_ACCESS_MODE:}}") String mode) {
        return AccessMode.named(mode);
    }
}
