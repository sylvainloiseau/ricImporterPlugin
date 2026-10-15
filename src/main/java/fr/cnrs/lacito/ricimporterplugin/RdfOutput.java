package fr.cnrs.lacito.ricimporterplugin;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;

/**
 * Writes the output of an {@link RdfConverter} to a file or stream, and resolves the RDF
 * serialization format from a name or a file name. Shared by converter command-line tools.
 *
 * <p>Writers are found through Rio's registry, so the matching {@code rdf4j-rio-*} module
 * must be on the classpath.
 */
public final class RdfOutput {

    /** Short names accepted by {@link #parseFormatName(String)}, besides RDF4J format names and MIME types. */
    private static final Map<String, RDFFormat> SHORT_NAMES = new LinkedHashMap<>();

    static {
        SHORT_NAMES.put("rdfxml", RDFFormat.RDFXML);
        SHORT_NAMES.put("xml", RDFFormat.RDFXML);
        SHORT_NAMES.put("rdf", RDFFormat.RDFXML);
        SHORT_NAMES.put("turtle", RDFFormat.TURTLE);
        SHORT_NAMES.put("ttl", RDFFormat.TURTLE);
        SHORT_NAMES.put("ntriples", RDFFormat.NTRIPLES);
        SHORT_NAMES.put("nt", RDFFormat.NTRIPLES);
        SHORT_NAMES.put("nquads", RDFFormat.NQUADS);
        SHORT_NAMES.put("nq", RDFFormat.NQUADS);
        SHORT_NAMES.put("trig", RDFFormat.TRIG);
        SHORT_NAMES.put("trix", RDFFormat.TRIX);
        SHORT_NAMES.put("n3", RDFFormat.N3);
        SHORT_NAMES.put("jsonld", RDFFormat.JSONLD);
        SHORT_NAMES.put("rdfjson", RDFFormat.RDFJSON);
        SHORT_NAMES.put("rj", RDFFormat.RDFJSON);
        SHORT_NAMES.put("binary", RDFFormat.BINARY);
        SHORT_NAMES.put("brf", RDFFormat.BINARY);
    }

    private RdfOutput() {
    }

    /**
     * Resolves the output format: the explicit {@code formatName} if not {@code null}, else the
     * format guessed from {@code output}'s file name, else {@code fallback}.
     *
     * @throws IllegalArgumentException if {@code formatName} is not recognised
     */
    public static RDFFormat resolveFormat(String formatName, Path output, RDFFormat fallback) {
        if (formatName != null && !formatName.isBlank()) {
            return parseFormatName(formatName);
        }
        if (output != null && output.getFileName() != null) {
            Optional<RDFFormat> guessed = Rio.getWriterFormatForFileName(output.getFileName().toString());
            if (guessed.isPresent()) {
                return guessed.get();
            }
            // Rio only knows formats whose writer is on the classpath; also try known extensions.
            Optional<RDFFormat> known = RDFFormat.matchFileName(output.getFileName().toString(), SHORT_NAMES.values());
            if (known.isPresent()) {
                return known.get();
            }
        }
        return Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Parses a format name: a short name ({@code turtle}, {@code ttl}, {@code rdfxml},
     * {@code xml}, {@code nt}, {@code nq}, {@code jsonld}, {@code trig}, {@code trix},
     * {@code n3}, {@code rj}, {@code brf}...), an RDF4J format name ({@code "Turtle"},
     * {@code "RDF/XML"}...) or a MIME type.
     *
     * @throws IllegalArgumentException if the name is not recognised
     */
    public static RDFFormat parseFormatName(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        RDFFormat byShortName = SHORT_NAMES.get(key);
        if (byShortName != null) {
            return byShortName;
        }
        for (RDFFormat format : SHORT_NAMES.values()) {
            if (format.getName().equalsIgnoreCase(name.strip()) || format.hasMIMEType(key)) {
                return format;
            }
        }
        for (RDFFormat format : RDFWriterRegistry.getInstance().getKeys()) {
            if (format.getName().equalsIgnoreCase(name.strip()) || format.hasMIMEType(key)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown RDF format '" + name + "'. Accepted: " + SHORT_NAMES.keySet()
                + ", RDF4J format names or MIME types");
    }

    /**
     * Writes the conversion to {@code output}. The data goes to a temporary file in the same
     * directory, which then replaces {@code output}; on failure the temporary file is removed and
     * {@code output} is left untouched.
     *
     * @return the number of statements written
     */
    public static long write(RdfConverter converter, Path output, RDFFormat format) {
        Path target = output.toAbsolutePath();
        Path dir = target.getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "." + target.getFileName(), ".tmp");
            long count;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
                count = write(converter, out, format);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    /**
     * Writes the conversion to {@code out} (not closed). For formats supporting named graphs,
     * statements without a context go into the converter's suggested context, if any.
     *
     * @return the number of statements written
     * @throws UnsupportedRDFormatException if no writer for {@code format} is on the classpath
     */
    public static long write(RdfConverter converter, OutputStream out, RDFFormat format) {
        RDFWriter writer = createWriter(format, out);
        RDFHandler handler = writer;
        if (format.supportsContexts() && converter.suggestedContext().isPresent()) {
            handler = new ContextAssigningRDFHandler(writer, converter.suggestedContext().get());
        }
        CountingRDFHandler counter = new CountingRDFHandler(handler);
        converter.convert(counter);
        return counter.count();
    }

    /** Creates a Rio writer configured for readable output. */
    public static RDFWriter createWriter(RDFFormat format, OutputStream out) {
        RDFWriter writer;
        try {
            writer = Rio.createWriter(format, out);
        } catch (UnsupportedRDFormatException e) {
            throw new UnsupportedRDFormatException("No Rio writer available for format " + format.getName()
                    + "; add the corresponding org.eclipse.rdf4j:rdf4j-rio-* dependency", e);
        }
        writer.getWriterConfig().set(BasicWriterSettings.PRETTY_PRINT, true);
        if (format.equals(RDFFormat.TURTLE) || format.equals(RDFFormat.TRIG)) {
            writer.getWriterConfig().set(BasicWriterSettings.INLINE_BLANK_NODES, true);
        }
        return writer;
    }
}
