# ricImporterPlugin

A plugin API for converters that turn application data (Lameta projects, spreadsheets, …)
into [RiC-O](https://www.ica.org/standards/RiC/ontology) RDF graphs, built on
[Eclipse RDF4J](https://rdf4j.org/).

A converter streams its output into an RDF4J `RDFHandler`. The caller decides where the
triples go: a file, an in-memory `Model`, or a running RDF4J repository. Converters are
discovered with the JDK `ServiceLoader`.

```xml
<dependency>
  <groupId>fr.cnrs.lacito</groupId>
  <artifactId>ricImporterPlugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Requires Java 17+. Depends on `rdf4j-model` and `rdf4j-rio-api`. `rdf4j-repository-api` is
`provided`; it is only needed by `RepositoryLoader`. Add the `rdf4j-rio-*` writer modules
for the formats you want to write.

## Using a converter (host application)

```java
RdfConverter converter = RdfConverters.get("lameta")
        .create(Path.of("/data/MyLametaProject"),
                ConverterOptions.builder("http://example.org/corpus/mycorpus").build());

// 1. In memory
Model model = converter.toModel();

// 2. To a file; the format is guessed from the extension, with RDF/XML as the fallback
Path out = Path.of("out.ttl");
RdfOutput.write(converter, out, RdfOutput.resolveFormat(null, out, RDFFormat.RDFXML));

// 3. Into a running RDF4J repository (embedded or remote), replacing the named graph atomically
Repository repo = new HTTPRepository("http://localhost:8080/rdf4j-server", "corpus");
long n = RepositoryLoader.replaceGraph(repo, converter);          // graph = converter.suggestedContext()

// ... or inside your own transaction
try (RepositoryConnection conn = repo.getConnection()) {
    conn.begin();
    RepositoryLoader.load(conn, converter, graphIri, true);
    // other updates ...
    conn.commit();
}

// 4. Any other RDF4J sink
converter.convert(myRdfHandler);
```

`RdfConverters.available()` lists the installed converters. Each provider also describes its
options (`RdfConverterProvider.options()`).

## Writing a converter

1. Implement `RdfConverter`. In `convert(RDFHandler)`, call `startRDF()`, declare the
   namespaces, emit each statement once (normally without a context), then call `endRDF()`.
   Report input problems as `RdfConversionException`. See the `RdfConverter` Javadoc for the
   full contract.
2. Implement `RdfConverterProvider` (public no-arg constructor, unique `name()`).
3. Register it in `src/main/resources/META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider`.
   If you build a shaded jar, use the shade plugin's `ServicesResourceTransformer`, so that
   the service files of all jars are merged instead of overwritten.

## API summary

| Type | Purpose |
|---|---|
| `RdfConverter` | `convert(RDFHandler)`, `suggestedContext()`, `toModel()` |
| `RdfConverterProvider` | ServiceLoader SPI: `name()`, `description()`, `options()`, `create(Path, ConverterOptions)` |
| `RdfConverters` | Provider lookup: `available()`, `find(name)`, `get(name)` |
| `ConverterOptions` / `OptionDescriptor` | Base IRI + string options / option documentation |
| `RepositoryLoader` | `replaceGraph`, `addToGraph`, `load` into an RDF4J `Repository`, transactional |
| `RdfOutput` | `resolveFormat`, `parseFormatName`, `write` to a file (atomic) or stream |
| `RdfConversionException` | Unchecked input error (an `RDF4JException`) |

## Build

```
mvn verify     # compile + tests
mvn install    # make the snapshot available to converter modules
```
