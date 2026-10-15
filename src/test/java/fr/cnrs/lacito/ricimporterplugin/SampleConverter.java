package fr.cnrs.lacito.ricimporterplugin;

import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFHandler;

/** Test converter emitting a small RiC-O graph; can be told to fail after a number of statements. */
final class SampleConverter implements RdfConverter {

    static final String RICO = "https://www.ica.org/standards/RiC/ontology#";
    static final int STATEMENT_COUNT = 4;

    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private final String baseIri;
    private final int failAfter;

    SampleConverter(String baseIri) {
        this(baseIri, -1);
    }

    /** @param failAfter number of statements emitted before throwing, or -1 to never fail */
    SampleConverter(String baseIri, int failAfter) {
        this.baseIri = baseIri;
        this.failAfter = failAfter;
    }

    IRI project() {
        return VF.createIRI(baseIri + "/project/Sample");
    }

    @Override
    public void convert(RDFHandler handler) {
        handler.startRDF();
        handler.handleNamespace("rico", RICO);
        handler.handleNamespace("rdfs", RDFS.NAMESPACE);
        IRI session = VF.createIRI(baseIri + "/session/S1");
        IRI[][] triples = {
                {project(), RDF.TYPE, VF.createIRI(RICO, "RecordSet")},
                {project(), VF.createIRI(RICO, "includesOrIncluded"), session},
                {session, RDF.TYPE, VF.createIRI(RICO, "RecordSet")},
        };
        int emitted = 0;
        for (IRI[] t : triples) {
            failIfNeeded(emitted++);
            handler.handleStatement(VF.createStatement(t[0], t[1], t[2]));
        }
        failIfNeeded(emitted);
        handler.handleStatement(VF.createStatement(session, VF.createIRI(RICO, "title"), VF.createLiteral("Séance 1", "fr")));
        handler.endRDF();
    }

    private void failIfNeeded(int emitted) {
        if (failAfter >= 0 && emitted >= failAfter) {
            throw new RdfConversionException("Simulated failure after " + emitted + " statements");
        }
    }

    @Override
    public Optional<IRI> suggestedContext() {
        return Optional.of(project());
    }
}
