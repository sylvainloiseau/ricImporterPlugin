package fr.cnrs.lacito.ricimporterplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.rdf4j.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RdfConvertersTest {

    @Test
    void discoversRegisteredProvider() {
        assertThat(RdfConverters.available()).extracting(RdfConverterProvider::name).contains("sample");
        assertThat(RdfConverters.find("sample")).isPresent();
        assertThat(RdfConverters.find("nope")).isEmpty();
    }

    @Test
    void createsWorkingConverter() {
        RdfConverterProvider provider = RdfConverters.get("sample");
        assertThat(provider.options()).extracting(OptionDescriptor::name).containsExactly("failAfter");
        Model model = provider.create(Path.of("."), ConverterOptions.of("http://ex.org")).toModel();
        assertThat(model).hasSize(SampleConverter.STATEMENT_COUNT);
        assertThat(model.getNamespace("rico")).isPresent();
    }

    @Test
    void unknownNameListsAvailableProviders() {
        assertThatThrownBy(() -> RdfConverters.get("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sample");
    }

    @Test
    void duplicateNamesAreRejected(@TempDir Path tmp) throws IOException {
        Path services = tmp.resolve("META-INF/services/" + RdfConverterProvider.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, DuplicateSampleConverterProvider.class.getName() + "\n");
        URL extra = services.toUri().toURL();
        ClassLoader parent = getClass().getClassLoader();
        // Adds a second provider named "sample", as if another jar declared it.
        ClassLoader duplicating = new ClassLoader(parent) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                List<URL> urls = Collections.list(parent.getResources(name));
                if (name.equals("META-INF/services/" + RdfConverterProvider.class.getName())) {
                    urls.add(extra);
                }
                return Collections.enumeration(urls);
            }
        };
        assertThatThrownBy(() -> RdfConverters.available(duplicating))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'sample'");
    }
}
