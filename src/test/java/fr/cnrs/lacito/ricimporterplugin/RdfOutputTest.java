package fr.cnrs.lacito.ricimporterplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RdfOutputTest {

    private static final String BASE = "http://ex.org/corpus";

    @TempDir
    Path tmp;

    @ParameterizedTest
    @CsvSource({
            "out.rdf, RDF/XML",
            "out.xml, RDF/XML",
            "out.owl, RDF/XML",
            "out.ttl, Turtle",
            "out.nt, N-Triples",
            "out.nq, N-Quads",
            "out.jsonld, JSON-LD",
            "out.trig, TriG",
    })
    void formatGuessedFromExtension(String file, String expected) {
        assertThat(RdfOutput.resolveFormat(null, Path.of(file), RDFFormat.RDFXML).getName()).isEqualTo(expected);
    }

    @Test
    void explicitFormatOverridesExtension() {
        assertThat(RdfOutput.resolveFormat("turtle", Path.of("out.rdf"), RDFFormat.RDFXML)).isEqualTo(RDFFormat.TURTLE);
        assertThat(RdfOutput.resolveFormat("RDF/XML", Path.of("out.ttl"), null)).isEqualTo(RDFFormat.RDFXML);
        assertThat(RdfOutput.resolveFormat("text/turtle", Path.of("out"), null)).isEqualTo(RDFFormat.TURTLE);
    }

    @Test
    void fallbackWhenNothingMatches() {
        assertThat(RdfOutput.resolveFormat(null, Path.of("out.unknown"), RDFFormat.RDFXML)).isEqualTo(RDFFormat.RDFXML);
        assertThat(RdfOutput.resolveFormat(" ", Path.of("out"), RDFFormat.RDFXML)).isEqualTo(RDFFormat.RDFXML);
    }

    @Test
    void unknownFormatNameIsRejected() {
        assertThatThrownBy(() -> RdfOutput.parseFormatName("docx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turtle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"out.rdf", "out.ttl", "out.nt", "out.jsonld", "out.nq"})
    void writtenFileReparsesToSameGraph(String name) throws IOException {
        SampleConverter converter = new SampleConverter(BASE);
        Path out = tmp.resolve(name);
        RDFFormat format = RdfOutput.resolveFormat(null, out, RDFFormat.RDFXML);
        long n = RdfOutput.write(converter, out, format);
        assertThat(n).isEqualTo(SampleConverter.STATEMENT_COUNT);
        Model parsed;
        try (InputStream in = Files.newInputStream(out)) {
            parsed = Rio.parse(in, "", format);
        }
        Model expected = converter.toModel();
        if (format.supportsContexts()) {
            assertThat(parsed.contexts()).containsExactly(converter.project());
            expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
            for (var st : converter.toModel()) {
                expected.add(st.getSubject(), st.getPredicate(), st.getObject(), converter.project());
            }
        }
        assertThat(Models.isomorphic(parsed, expected)).as(Files.readString(out)).isTrue();
    }

    @Test
    void failedConversionLeavesNoFile() throws IOException {
        Path out = tmp.resolve("out.ttl");
        assertThatThrownBy(() -> RdfOutput.write(new SampleConverter(BASE, 2), out, RDFFormat.TURTLE))
                .isInstanceOf(RdfConversionException.class);
        try (Stream<Path> files = Files.list(tmp)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void failedConversionKeepsExistingFile() throws IOException {
        Path out = tmp.resolve("out.ttl");
        Files.writeString(out, "previous");
        assertThatThrownBy(() -> RdfOutput.write(new SampleConverter(BASE, 0), out, RDFFormat.TURTLE))
                .isInstanceOf(RdfConversionException.class);
        assertThat(Files.readString(out)).isEqualTo("previous");
    }

    @Test
    void existingFileIsReplaced() throws IOException {
        Path out = tmp.resolve("sub/dir/out.ttl");
        Files.createDirectories(out.getParent());
        Files.writeString(out, "previous");
        RdfOutput.write(new SampleConverter(BASE), out, RDFFormat.TURTLE);
        assertThat(Files.readString(out)).contains("rico:RecordSet").doesNotContain("previous");
    }

    @Test
    void missingWriterGivesHelpfulError() {
        assertThatThrownBy(() -> RdfOutput.write(new SampleConverter(BASE), tmp.resolve("out.trix"), RDFFormat.TRIX))
                .hasMessageContaining("rdf4j-rio");
    }
}
