package fr.cnrs.lacito.ricimporterplugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable options passed to {@link RdfConverterProvider#create}.
 *
 * <p>Every converter needs a base IRI (the corpus URI prefix under which it mints the IRIs of
 * the resources it describes). Anything else is a converter-specific string option, documented
 * by {@link RdfConverterProvider#options()}.
 */
public final class ConverterOptions {

    private final String baseIri;
    private final Map<String, String> values;

    private ConverterOptions(String baseIri, Map<String, String> values) {
        this.baseIri = baseIri;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder(String baseIri) {
        return new Builder(baseIri);
    }

    public static ConverterOptions of(String baseIri) {
        return builder(baseIri).build();
    }

    /** The corpus URI prefix, without trailing slash. */
    public String baseIri() {
        return baseIri;
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public String get(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    /** Accepts {@code true/false}, {@code yes/no} and {@code 1/0}, case-insensitively. */
    public boolean getBoolean(String name, boolean defaultValue) {
        String v = values.get(name);
        if (v == null) {
            return defaultValue;
        }
        switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true": case "yes": case "1":
                return true;
            case "false": case "no": case "0":
                return false;
            default:
                throw new IllegalArgumentException("Option '" + name + "' is not a boolean: " + v);
        }
    }

    public Map<String, String> asMap() {
        return values;
    }

    public Builder toBuilder() {
        Builder b = new Builder(baseIri);
        b.values.putAll(values);
        return b;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConverterOptions other
                && baseIri.equals(other.baseIri)
                && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseIri, values);
    }

    @Override
    public String toString() {
        return "ConverterOptions[baseIri=" + baseIri + ", " + values + "]";
    }

    public static final class Builder {
        private final String baseIri;
        private final Map<String, String> values = new LinkedHashMap<>();

        private Builder(String baseIri) {
            Objects.requireNonNull(baseIri, "baseIri");
            String trimmed = baseIri.strip();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("baseIri must not be empty");
            }
            this.baseIri = trimmed;
        }

        public Builder set(String name, String value) {
            Objects.requireNonNull(name, "name");
            if (value == null) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
            return this;
        }

        public Builder set(String name, boolean value) {
            return set(name, Boolean.toString(value));
        }

        public Builder setAll(Map<String, String> options) {
            options.forEach(this::set);
            return this;
        }

        public ConverterOptions build() {
            return new ConverterOptions(baseIri, values);
        }
    }
}
