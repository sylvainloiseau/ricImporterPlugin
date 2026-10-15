package fr.cnrs.lacito.ricimporterplugin;

import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

/**
 * One conversion of an application's data (bound to its input when created by an
 * {@link RdfConverterProvider}) into an RDF graph.
 *
 * <p>The converter streams its output into an RDF4J {@link RDFHandler} and never chooses the
 * destination. Standard handlers cover the usual targets:
 * <ul>
 *   <li>a file or stream: {@code Rio.createWriter(format, out)} (see {@link RdfOutput});</li>
 *   <li>an in-memory model: {@link StatementCollector} (see {@link #toModel()});</li>
 *   <li>an RDF4J repository: {@code org.eclipse.rdf4j.repository.util.RDFInserter}
 *       (see {@link RepositoryLoader}).</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #convert} calls {@code handler.startRDF()} once, then any number of
 *       {@code handleNamespace}, {@code handleStatement} and {@code handleComment} calls, then
 *       {@code handler.endRDF()} once.</li>
 *   <li>Namespaces are declared before the first statement that uses them.</li>
 *   <li>Statements are normally emitted without a context; the caller decides the named
 *       graph (for instance with {@link #suggestedContext()}).</li>
 *   <li>No statement is emitted twice.</li>
 *   <li>Calling {@link #convert} again produces the same graph. Instances need not be
 *       thread-safe.</li>
 *   <li>Input problems are reported as {@link RdfConversionException}; exceptions thrown by
 *       the handler propagate unchanged.</li>
 * </ul>
 */
public interface RdfConverter {

    /**
     * Streams the whole graph into {@code handler}.
     *
     * @throws RdfConversionException if the input cannot be read or interpreted
     */
    void convert(RDFHandler handler);

    /**
     * Named graph in which the output would naturally be stored (for example the IRI of the
     * converted project), if the converter has an opinion.
     */
    default Optional<IRI> suggestedContext() {
        return Optional.empty();
    }

    /** Runs the conversion into a new in-memory model, namespaces included. */
    default Model toModel() {
        Model model = new LinkedHashModel();
        convert(new StatementCollector(model));
        return model;
    }
}
