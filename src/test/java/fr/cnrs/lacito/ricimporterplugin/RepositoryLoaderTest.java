package fr.cnrs.lacito.ricimporterplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryLoaderTest {

    private static final String BASE = "http://ex.org/corpus";
    private static final IRI OTHER = SimpleValueFactory.getInstance().createIRI("http://ex.org/other-graph");

    private Repository repo;
    private final SampleConverter converter = new SampleConverter(BASE);

    @BeforeEach
    void setUp() {
        repo = new SailRepository(new MemoryStore());
    }

    @AfterEach
    void tearDown() {
        repo.shutDown();
    }

    private long size(IRI context) {
        try (RepositoryConnection conn = repo.getConnection()) {
            return context == null ? conn.size() : conn.size(context);
        }
    }

    @Test
    void replaceGraphLoadsIntoSuggestedContext() {
        long n = RepositoryLoader.replaceGraph(repo, converter);
        assertThat(n).isEqualTo(SampleConverter.STATEMENT_COUNT);
        assertThat(size(converter.project())).isEqualTo(SampleConverter.STATEMENT_COUNT);
        assertThat(size(null)).isEqualTo(SampleConverter.STATEMENT_COUNT);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertThat(conn.getNamespace("rico")).isEqualTo(SampleConverter.RICO);
        }
    }

    @Test
    void replaceGraphTwiceDoesNotDuplicate() {
        RepositoryLoader.replaceGraph(repo, converter);
        RepositoryLoader.replaceGraph(repo, converter);
        assertThat(size(null)).isEqualTo(SampleConverter.STATEMENT_COUNT);
    }

    @Test
    void replaceGraphLeavesOtherGraphsAlone() {
        RepositoryLoader.addToGraph(repo, converter, OTHER);
        RepositoryLoader.replaceGraph(repo, converter);
        assertThat(size(OTHER)).isEqualTo(SampleConverter.STATEMENT_COUNT);
        assertThat(size(converter.project())).isEqualTo(SampleConverter.STATEMENT_COUNT);
    }

    @Test
    void addToGraphWithoutContextUsesDefaultGraph() {
        RepositoryLoader.addToGraph(repo, converter, null);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertThat(conn.size((IRI) null)).isEqualTo(SampleConverter.STATEMENT_COUNT);
        }
    }

    @Test
    void failureRollsBackAndKeepsPreviousGraph() {
        RepositoryLoader.replaceGraph(repo, converter);
        SampleConverter failing = new SampleConverter(BASE, 2);
        assertThatThrownBy(() -> RepositoryLoader.replaceGraph(repo, failing))
                .isInstanceOf(RdfConversionException.class);
        assertThat(size(converter.project())).isEqualTo(SampleConverter.STATEMENT_COUNT);
    }

    @Test
    void loadJoinsCallerTransaction() {
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            RepositoryLoader.load(conn, converter, converter.project(), true);
            assertThat(conn.isActive()).isTrue();
            assertThat(size(null)).as("not visible before the caller commits").isZero();
            conn.rollback();
        }
        assertThat(size(null)).isZero();
    }

    @Test
    void failureInsideCallerTransactionDoesNotRollBackForCaller() {
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            assertThatThrownBy(() -> RepositoryLoader.load(conn, new SampleConverter(BASE, 1), OTHER, false))
                    .isInstanceOf(RdfConversionException.class);
            assertThat(conn.isActive()).isTrue();
            conn.rollback();
        }
    }

    @Test
    void replaceWithoutContextIsRejected() {
        try (RepositoryConnection conn = repo.getConnection()) {
            assertThatThrownBy(() -> RepositoryLoader.load(conn, converter, null, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> RepositoryLoader.replaceGraph(repo, new RdfConverter() {
            @Override
            public void convert(org.eclipse.rdf4j.rio.RDFHandler handler) {
            }
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
