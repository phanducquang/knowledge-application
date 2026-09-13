package com.knowledgeapplication.api.attachment.validation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    private final ImageUploadValidator validator = new ImageUploadValidator(
            DataSize.ofMegabytes(10), 20_000, 100_000_000
    );

    @Test
    void acceptsRealAllowedImageAndUsesDetectedDimensions() {
        var image = validator.validate(new MockMultipartFile(
                "file", "pixel.png", "image/png", PNG
        ));

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.content()).isEqualTo(PNG);
        assertThat(image.width()).isEqualTo(1);
        assertThat(image.height()).isEqualTo(1);
    }

    @Test
    void rejectsSpoofedDeclaredType() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "pixel.jpg", "image/jpeg", PNG
        )))
                .isInstanceOf(MalformedImageException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsSvgAndMalformedSignature() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "active.svg", "image/svg+xml", "<svg/>".getBytes()
        ))).isInstanceOf(UnsupportedImageTypeException.class);

        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "broken.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        ))).isInstanceOf(MalformedImageException.class);
    }

    @Test
    void rejectsImageAboveConfiguredByteLimit() {
        var limited = new ImageUploadValidator(DataSize.ofBytes(PNG.length - 1), 20_000, 100_000_000);
        assertThatThrownBy(() -> limited.validate(new MockMultipartFile(
                "file", "pixel.png", "image/png", PNG
        ))).isInstanceOf(ImageTooLargeException.class);
    }
}
