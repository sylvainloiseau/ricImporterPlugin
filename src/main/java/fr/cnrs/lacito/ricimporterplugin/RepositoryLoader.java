package fr.cnrs.lacito.ricimporterplugin;

import java.util.Objects;

import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.util.RDFInserter;

/**
 * Loads the output of an {@link RdfConverter} into an RDF4J {@link Repository}, embedded
 * ({@code SailRepository}) or remote ({@code HTTPRepository}).
 *
 * <p>Statements are streamed through RDF4J's standard {@link RDFInserter}; no intermediate
 * model is built. Requires {@code rdf4j-repository-api} on the classpath (a {@code provided}
 * dependency of this library).
 */
public final class RepositoryLoader {

    /**
     * Isolation level used when this class opens the transaction itself: {@code null}, meaning
     * the repository's default level. {@link IsolationLevels#NONE} is faster for bulk loads but,
     * on some stores (e.g. the memory store), changes are applied immediately and a failed
     * conversion cannot be rolled back; pass it explicitly only when that is acceptable.
     */
    public static final IsolationLevel DEFAULT_ISOLATION = null;

    private RepositoryLoader() {
    }

    /**
     * Replaces the converter's {@linkplain RdfConverter#suggestedContext() suggested named
     * graph} with a fresh conversion, atomically.
     *
     * @return the number of statements loaded
     * @throws IllegalArgumentException if the converter suggests no context
     */
    public static long replaceGraph(Repository repository, RdfConverter converter) {
        IRI context = converter.suggestedContext()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Converter suggests no context; pass one explicitly"));
        return replaceGraph(repository, converter, context);
    }

    /**
     * Clears {@code context} and loads the conversion into it, in one transaction.
     *
     * @return the number of statements loaded
     */
    public static long replaceGraph(Repository repository, RdfConverter converter, IRI context) {
        Objects.requireNonNull(context, "context");
        try (RepositoryConnection conn = repository.getConnection()) {
            return load(conn, converter, context, true, DEFAULT_ISOLATION);
        }
    }

    /**
     * Adds the conversion to {@code context} without removing anything, in one transaction.
     *
     * @param context target named graph, or {@code null} to keep the statements' own context
     *                (usually the default graph)
     * @return the number of statements loaded
     */
    public static long addToGraph(Repository repository, RdfConverter converter, IRI context) {
        try (RepositoryConnection conn = repository.getConnection()) {
            return load(conn, converter, context, false, DEFAULT_ISOLATION);
        }
    }

    /** Same as {@link #load(RepositoryConnection, RdfConverter, IRI, boolean, IsolationLevel)} with {@link #DEFAULT_ISOLATION}. */
    public static long load(RepositoryConnection conn, RdfConverter converter, IRI context, boolean replace) {
        return load(conn, converter, context, replace, DEFAULT_ISOLATION);
    }

    /**
     * Streams the conversion into {@code conn}.
     *
     * <p>If a transaction is already active on {@code conn}, the load takes part in it and
     * neither commits nor rolls back. Otherwise a transaction is opened with {@code isolation}
     * ({@code null} for the repository's default level), committed on success and rolled back
     * on any failure.
     *
     * @param context   target named graph; {@code null} keeps the statements' own context
     * @param replace   clear {@code context} before loading; requires a non-null context so
     *                  that the whole repository is never cleared by accident
     * @return the number of statements loaded
     */
    public static long load(RepositoryConnection conn, RdfConverter converter, IRI context, boolean replace,
            IsolationLevel isolation) {
        Objects.requireNonNull(conn, "conn");
        Objects.requireNonNull(converter, "converter");
        if (replace && context == null) {
            throw new IllegalArgumentException("Replacing requires a non-null context");
        }

        boolean ownTransaction = !conn.isActive();
        if (ownTransaction) {
            if (isolation == null) {
                conn.begin();
            } else {
                conn.begin(isolation);
            }
        }
        try {
            if (replace) {
                conn.clear(context);
            }
            RDFInserter inserter = new RDFInserter(conn);
            if (context != null) {
                inserter.enforceContext(context);
            }
            CountingRDFHandler counter = new CountingRDFHandler(inserter);
            converter.convert(counter);
            if (ownTransaction) {
                conn.commit();
            }
            return counter.count();
        } catch (RuntimeException | Error e) {
            if (ownTransaction && conn.isActive()) {
                conn.rollback();
            }
            throw e;
        }
    }
}
