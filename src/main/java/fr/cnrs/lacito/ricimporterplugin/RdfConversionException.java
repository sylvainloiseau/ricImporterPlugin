package fr.cnrs.lacito.ricimporterplugin;

import org.eclipse.rdf4j.common.exception.RDF4JException;

/**
 * Signals that a converter could not read or interpret its input (missing directory,
 * malformed metadata file, ...).
 *
 * <p>Unchecked, like every RDF4J exception. Exceptions raised by the downstream
 * {@link org.eclipse.rdf4j.rio.RDFHandler} are not wrapped in this type.
 */
public class RdfConversionException extends RDF4JException {

    private static final long serialVersionUID = 1L;

    public RdfConversionException(String message) {
        super(message);
    }

    public RdfConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
