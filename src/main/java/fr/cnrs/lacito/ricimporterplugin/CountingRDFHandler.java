package fr.cnrs.lacito.ricimporterplugin;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.helpers.RDFHandlerWrapper;

/** Counts the statements passed to the wrapped handler. */
final class CountingRDFHandler extends RDFHandlerWrapper {

    private long count;

    CountingRDFHandler(RDFHandler delegate) {
        super(delegate);
    }

    @Override
    public void handleStatement(Statement st) {
        super.handleStatement(st);
        count++;
    }

    long count() {
        return count;
    }
}
