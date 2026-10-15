package fr.cnrs.lacito.ricimporterplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ConverterOptionsTest {

    @Test
    void trailingSlashesAreRemovedFromBaseIri() {
        assertThat(ConverterOptions.of("http://ex.org/corpus//").baseIri()).isEqualTo("http://ex.org/corpus");
    }

    @Test
    void baseIriIsRequired() {
        assertThatThrownBy(() -> ConverterOptions.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ConverterOptions.of(" / ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valuesAndBooleans() {
        ConverterOptions o = ConverterOptions.builder("http://ex.org")
                .set("a", "x").set("flag", true).set("no", "NO").build();
        assertThat(o.get("a")).contains("x");
        assertThat(o.get("missing")).isEmpty();
        assertThat(o.get("missing", "d")).isEqualTo("d");
        assertThat(o.getBoolean("flag", false)).isTrue();
        assertThat(o.getBoolean("no", true)).isFalse();
        assertThat(o.getBoolean("missing", true)).isTrue();
        assertThatThrownBy(() -> o.getBoolean("a", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void immutableAndCopyable() {
        ConverterOptions o = ConverterOptions.builder("http://ex.org").setAll(Map.of("k", "v")).build();
        assertThatThrownBy(() -> o.asMap().put("k2", "v2")).isInstanceOf(UnsupportedOperationException.class);
        ConverterOptions copy = o.toBuilder().set("k", null).build();
        assertThat(copy.get("k")).isEmpty();
        assertThat(o.get("k")).contains("v");
        assertThat(o.toBuilder().build()).isEqualTo(o);
    }
}
