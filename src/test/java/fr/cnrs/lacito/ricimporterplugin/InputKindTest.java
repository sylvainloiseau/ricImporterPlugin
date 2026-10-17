package fr.cnrs.lacito.ricimporterplugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InputKindTest {

    @Test
    void providersThatDoNotSayAcceptBoth() {
        assertThat(new SampleConverterProvider().inputKind()).isEqualTo(InputKind.FILE_OR_DIRECTORY);
    }

    @Test
    void providersCanDeclareTheirInput() {
        RdfConverterProvider directoryProvider = new SampleConverterProvider() {
            @Override
            public InputKind inputKind() {
                return InputKind.DIRECTORY;
            }
        };
        assertThat(directoryProvider.inputKind()).isEqualTo(InputKind.DIRECTORY);
        assertThat(directoryProvider.create(Path.of("."), ConverterOptions.of("http://ex.org")).toModel()).isNotEmpty();
    }

    @Test
    void acceptedInputs() {
        assertThat(InputKind.FILE.acceptsFile()).isTrue();
        assertThat(InputKind.FILE.acceptsDirectory()).isFalse();
        assertThat(InputKind.DIRECTORY.acceptsFile()).isFalse();
        assertThat(InputKind.DIRECTORY.acceptsDirectory()).isTrue();
        assertThat(InputKind.FILE_OR_DIRECTORY.acceptsFile()).isTrue();
        assertThat(InputKind.FILE_OR_DIRECTORY.acceptsDirectory()).isTrue();
    }
}
