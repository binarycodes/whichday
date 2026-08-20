package io.binarycodes.whichday;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the one trap the translation file can spring silently.
 *
 * <p>Vaadin's default provider runs a value through {@code MessageFormat} only when
 * the call passes arguments. MessageFormat then treats an apostrophe as a quoting
 * character, so "haven't typed {0}" swallows the apostrophe and prints {0} raw —
 * and a doubled apostrophe in a value with no arguments prints as two apostrophes,
 * because nothing unquotes it. Both are wrong on screen and neither fails a build.
 */
@DisplayName("The translation file")
class TranslationsTest {

    private static final String BUNDLE = "/vaadin-i18n/translations.properties";

    @Test
    @DisplayName("doubles every apostrophe in a value that takes arguments")
    void apostrophesInParameterisedValues() {
        var offenders = entries().entrySet().stream()
                .filter(entry -> entry.getValue().contains("{"))
                .filter(entry -> entry.getValue().replace("''", "").contains("'"))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(offenders)
                .as("these take arguments, so MessageFormat would eat the apostrophe "
                        + "and print the placeholder raw; double it")
                .isEmpty();
    }

    @Test
    @DisplayName("leaves apostrophes single in a value that takes none")
    void apostrophesInPlainValues() {
        var offenders = entries().entrySet().stream()
                .filter(entry -> !entry.getValue().contains("{"))
                .filter(entry -> entry.getValue().contains("''"))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(offenders)
                .as("these take no arguments, so nothing unquotes a doubled apostrophe "
                        + "and both would show on screen")
                .isEmpty();
    }

    @Test
    @DisplayName("is read as UTF-8, so a name with an accent survives it")
    void isUtf8() {
        assertThat(entries()).containsKey("app.name");
        assertThat(entries().get("app.name")).isEqualTo("Whichday");
    }

    private Map<String, String> entries() {
        var properties = new Properties();
        try (var stream = TranslationsTest.class.getResourceAsStream(BUNDLE)) {
            assertThat(stream).as("bundle %s is on the classpath", BUNDLE).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new AssertionError("Could not read " + BUNDLE, failure);
        }
        var entries = new LinkedHashMap<String, String>();
        properties.forEach((key, value) -> entries.put(String.valueOf(key), String.valueOf(value)));
        return entries;
    }
}
