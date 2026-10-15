package fr.cnrs.lacito.ricimporterplugin;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.helpers.RDFHandlerWrapper;

/** Puts every statement that has no context into a fixed context. */
final class ContextAssigningRDFHandler extends RDFHandlerWrapper {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private final Resource context;

    ContextAssigningRDFHandler(RDFHandler delegate, Resource context) {
        super(delegate);
        this.context = context;
    }

    @Override
    public void handleStatement(Statement st) {
        if (st.getContext() == null) {
            st = VF.createStatement(st.getSubject(), st.getPredicate(), st.getObject(), context);
        }
        super.handleStatement(st);
    }
}
