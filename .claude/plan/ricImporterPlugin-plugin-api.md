# Plan: `ricImporterPlugin`, the plugin API for application → RiC-O RDF converters

**Status (2026-10-15): implemented.** §1–§9 are done, with 36 tests passing (`mvn verify`), and the snapshot is installed in the local Maven repository.

## 1. Goal

`ricImporterPlugin` is a small, converter-neutral Maven library. It defines how a converter (Lameta
today, others later: spreadsheets, Tropy, directory trees…) produces an RDF graph, and how a
host application finds converters, runs them and sends their output to:

- a file in any standard RDF serialization;
- an in-memory RDF4J `Model`;
- a running RDF4J repository (embedded `SailRepository` or remote `HTTPRepository`).

It contains no conversion logic. `lameta2rdf` (`../lameta2rico`) is the first implementation.

## 2. Design principles

1. **Output contract = `org.eclipse.rdf4j.rio.RDFHandler`.** This is RDF4J's standard
   streaming statement sink. A converter never chooses the destination. Files (`RDFWriter`),
   models (`StatementCollector`) and repositories (`RDFInserter`) are all `RDFHandler`s already
   provided by RDF4J.
2. **Discovery = `java.util.ServiceLoader`.** This is the JDK standard, the same mechanism Rio
   uses for its parsers and writers. It needs no framework (no OSGi, no Spring), and it works on
   the classpath and with JPMS (`uses`/`provides`).
3. **Minimal dependencies.** Compile scope: `rdf4j-model` and `rdf4j-rio-api` only.
   `rdf4j-repository-api` is `provided`, because a host that loads into a repository already has
   it, and other users never load `RepositoryLoader`. No Rio writer is bundled: the host or the
   CLI picks its formats.
4. **Unchecked exceptions**, consistent with RDF4J (`RDF4JException` is a `RuntimeException`).

## 3. Coordinates and build

- `groupId` `fr.cnrs.lacito`, `artifactId` `ricImporterPlugin`, `version` `0.1.0-SNAPSHOT`, packaging `jar`.
- Java package root: `fr.cnrs.lacito.ricimporterplugin`.
- Java release **17**.
- RDF4J **5.2.2**, through a `rdf4j.version` property.
- JPMS: `Automatic-Module-Name: fr.cnrs.lacito.ricimporterplugin` in the jar manifest. RDF4J itself is
  not modular, so a full `module-info` is not worth it yet.
- Test dependencies: JUnit Jupiter 5.10, AssertJ 3.27, `rdf4j-repository-sail` +
  `rdf4j-sail-memory`, `rdf4j-rio-turtle`, `rdf4j-rio-rdfxml`, `rdf4j-rio-nquads`,
  `rdf4j-rio-ntriples`, `rdf4j-rio-jsonld`.

## 4. Public API (`fr.cnrs.lacito.ricimporterplugin`)

| Type | Role |
|---|---|
| `RdfConverter` | One conversion run, bound to its input. `void convert(RDFHandler)`, `Optional<IRI> suggestedContext()`, `default Model toModel()` |
| `RdfConverterProvider` | SPI, registered in `META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider`. `name()`, `description()`, `default List<OptionDescriptor> options()`, `RdfConverter create(Path input, ConverterOptions options)` |
| `ConverterOptions` | Immutable options: the required `baseIri` (corpus URI prefix) + string key/value pairs. Builder; typed getters (`get`, `getBoolean`) |
| `OptionDescriptor` | `record(name, description, defaultValue)`: lets host UIs and CLIs list a converter's options |
| `RdfConverters` | Registry facade over `ServiceLoader`: `available()`, `available(ClassLoader)`, `find(name)`, `get(name)`. Fails on duplicate provider names |
| `RdfConversionException` | `extends RDF4JException`: input errors (missing directory, unreadable XML…) |
| `RepositoryLoader` | Loads a converter's output into an RDF4J repository (§5) |
| `RdfOutput` | Format resolution + file writing, shared by every converter CLI (§6) |

### `RdfConverter` contract (Javadoc)

- `convert` calls `handler.startRDF()` once, then any number of `handleNamespace` /
  `handleStatement` / `handleComment` calls, then `handler.endRDF()` once.
- Namespaces should be declared before the first statement that uses them, since streaming
  writers (Turtle) need this.
- Statements are normally emitted without a context. The caller decides the named graph
  (`RDFInserter.enforceContext`, `RepositoryLoader`).
- The converter should not emit duplicate statements, since streamed files would contain
  them.
- A converter instance may be run more than once, and each call produces the same graph.
  It does not need to be thread-safe.
- Errors are reported as `RdfConversionException`. Exceptions thrown by the handler
  (`RDFHandlerException`) propagate unchanged.

## 5. `RepositoryLoader`

```java
long replaceGraph(Repository repo, RdfConverter c)                // context = c.suggestedContext(), required
long replaceGraph(Repository repo, RdfConverter c, IRI context)
long addToGraph(Repository repo, RdfConverter c, IRI context)     // context null → statements keep their own context (usually the default graph)
long load(RepositoryConnection conn, RdfConverter c, IRI context, boolean replace)
```

- Return value: the number of statements sent.
- `load` takes part in the caller's transaction if `conn.isActive()`. Otherwise it opens its
  own with the repository's **default** isolation level, commits, and rolls back on any
  exception. *(Changed during implementation: the first draft used `IsolationLevels.NONE`.
  The rollback test showed that the memory store applies `clear` immediately at `NONE`, so a
  failed conversion lost the previous graph. `NONE` remains available through the
  `IsolationLevel` overload, for bulk loads where atomicity does not matter.)*
- `replace = true` clears **only** the target context. A `null` context with `replace` is
  rejected, so the whole repository can never be wiped by accident.
- Streaming goes through `org.eclipse.rdf4j.repository.util.RDFInserter`, with `enforceContext`
  set when a context is given. This works the same for embedded and remote (`HTTPRepository`)
  repositories, and no intermediate `Model` is built.
- An overload taking an `IsolationLevel` lets hosts pick stronger or weaker guarantees.

## 6. `RdfOutput`

```java
RDFFormat resolveFormat(String formatOrNull, Path output, RDFFormat fallback)
RDFFormat parseFormatName(String name)        // "turtle", "ttl", "rdfxml", "xml", "nt", "nq", "jsonld", "trig", "trix", "n3", "rj", "brf", or an RDFFormat name / MIME type
long write(RdfConverter c, Path output, RDFFormat format)
long write(RdfConverter c, OutputStream out, RDFFormat format)
```

- Resolution order: the explicit name; then `Rio.getWriterFormatForFileName(output)`; then the
  fallback. Unknown names → `IllegalArgumentException` listing the accepted names.
- An explicitly named format whose writer jar is missing → a clear error ("no Rio writer
  available for format X; add rdf4j-rio-…").
- Writer settings: `BasicWriterSettings.PRETTY_PRINT = true`; for Turtle/TriG also
  `BasicWriterSettings.INLINE_BLANK_NODES = true`.
- For quad formats (`format.supportsContexts()`), statements without a context are placed in
  `c.suggestedContext()`, when there is one.
- `write(…, Path, …)` writes to a temporary file in the target directory, then does an
  `ATOMIC_MOVE` (falling back to `REPLACE_EXISTING`). On failure the temporary file is
  deleted, so a failed conversion never leaves a truncated output file.

## 7. Package-private helpers

- `CountingRDFHandler`: wraps a handler and counts statements (used for the return values).
- `ContextAssigningRDFHandler`: puts statements that have no context into a given context
  (quad output).

## 8. Implementation steps

1. `pom.xml`, `.gitignore`, `README.md`.
2. Core types: `RdfConversionException`, `ConverterOptions`, `OptionDescriptor`,
   `RdfConverter`, `RdfConverterProvider`.
3. `RdfConverters` (ServiceLoader registry).
4. Handler helpers, then `RepositoryLoader`.
5. `RdfOutput`.
6. Tests (§9), `mvn verify`, `mvn install` so that `lameta2rdf` can depend on the
   snapshot.

## 9. Tests

- Test fixtures: `SampleConverter` (a few triples and namespaces, configurable to fail
  mid-stream), registered with a test-scope `META-INF/services` file as provider `"sample"`.
- `ConverterOptionsTest`: builder, required `baseIri`, immutability, `getBoolean`.
- `RdfConvertersTest`: discovery of `"sample"`; `get` on an unknown name lists the available
  names; duplicate names are detected (custom `ClassLoader`).
- `RdfConverterTest`: `toModel()` gets the statements and namespaces.
- `RepositoryLoaderTest` (in-memory `SailRepository`):
  - data lands in the requested context;
  - a second `replaceGraph` leaves the same size;
  - `addToGraph` with another context keeps both graphs;
  - a failure mid-stream rolls back, leaving the graph unchanged;
  - `load` inside a caller transaction does not commit by itself;
  - a `null` context with replace is rejected.
- `RdfOutputTest`:
  - format resolution (extension, explicit override, fallback, unknown name);
  - each written file re-parses as an isomorphic graph;
  - a quad format gets the suggested context;
  - a failed conversion leaves no file and no temporary file;
  - an existing file is replaced.

## 10. Out of scope (possible later additions)

- A generic CLI (`ricImporterPlugin <converter> <input> <output>`) that dispatches through
  `RdfConverters`. Converter CLIs can reuse `RdfOutput` meanwhile.
- SHACL validation of converter output against RiC-O shapes.
- Progress reporting and cancellation hooks for long conversions.
