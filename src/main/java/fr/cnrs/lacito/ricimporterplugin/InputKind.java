package fr.cnrs.lacito.ricimporterplugin;

/**
 * What a converter reads, as declared by {@link RdfConverterProvider#inputKind()}: host
 * applications use it to offer a file chooser, a directory chooser, or both.
 */
public enum InputKind {

    /** A single file. */
    FILE,

    /** A directory (for instance an application's project folder). */
    DIRECTORY,

    /** Either a file or a directory; also the default for providers that do not say. */
    FILE_OR_DIRECTORY;

    /** Whether input of this kind may be a regular file. */
    public boolean acceptsFile() {
        return this != DIRECTORY;
    }

    /** Whether input of this kind may be a directory. */
    public boolean acceptsDirectory() {
        return this != FILE;
    }
}
