package io.binarycodes.whichday;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Whichday — put a few days on the table and let a team pick every one that
 * works.
 */
@SpringBootApplication
@EnableScheduling
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("/styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] arguments) {
        SpringApplication.run(Application.class, arguments);
    }

    /**
     * Injected rather than read statically so a test can decide what "today" is —
     * every screen that dims a past day or counts down to a closing date goes
     * through this.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
