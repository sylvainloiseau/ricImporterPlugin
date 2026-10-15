package fr.cnrs.lacito.ricimporterplugin;

import java.nio.file.Path;
import java.util.List;

/** Provider registered in the test-scope META-INF/services file. */
public class SampleConverterProvider implements RdfConverterProvider {

    @Override
    public String name() {
        return "sample";
    }

    @Override
    public String description() {
        return "Sample converter for tests";
    }

    @Override
    public List<OptionDescriptor> options() {
        return List.of(new OptionDescriptor("failAfter", "Fail after N statements", "-1"));
    }

    @Override
    public RdfConverter create(Path input, ConverterOptions options) {
        return new SampleConverter(options.baseIri(), Integer.parseInt(options.get("failAfter", "-1")));
    }
}
