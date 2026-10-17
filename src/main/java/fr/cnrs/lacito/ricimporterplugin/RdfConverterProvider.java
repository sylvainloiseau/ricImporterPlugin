package fr.cnrs.lacito.ricimporterplugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Service provider interface for converters, discovered with {@link java.util.ServiceLoader}.
 *
 * <p>An implementation is registered by listing its fully qualified class name in
 * {@code META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider} (or with a
 * {@code provides} clause in a {@code module-info}). It must have a public no-argument
 * constructor. Host applications look providers up through {@link RdfConverters}.
 */
public interface RdfConverterProvider {

    /** Short, unique, stable identifier, e.g. {@code "lameta"}. */
    String name();

    /** One-line human-readable description. */
    String description();

    /** Converter-specific options accepted in {@link ConverterOptions}. */
    default List<OptionDescriptor> options() {
        return List.of();
    }

    /**
     * Whether {@link #create} expects a file or a directory. Defaults to
     * {@link InputKind#FILE_OR_DIRECTORY}, so providers written before this method existed keep
     * working; override it so that host applications can offer the right chooser.
     */
    default InputKind inputKind() {
        return InputKind.FILE_OR_DIRECTORY;
    }

    /**
     * Creates a converter bound to {@code input} (a file or a directory, as declared by
     * {@link #inputKind()}). Implementations should validate the input eagerly.
     *
     * @throws RdfConversionException if the input is not acceptable
     */
    RdfConverter create(Path input, ConverterOptions options);
}
