package fr.cnrs.lacito.ricimporterplugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes one option understood by a converter, so that host applications and command-line
 * tools can list and document it.
 *
 * @param name         key used in {@link ConverterOptions}
 * @param description  human-readable explanation
 * @param defaultValue value used when the option is absent, or {@code null} if there is none
 */
public record OptionDescriptor(String name, String description, String defaultValue) {

    public OptionDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }

    public Optional<String> defaultValueIfAny() {
        return Optional.ofNullable(defaultValue);
    }
}
