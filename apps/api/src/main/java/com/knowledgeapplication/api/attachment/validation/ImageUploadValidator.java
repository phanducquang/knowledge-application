package com.knowledgeapplication.api.attachment.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;

@Component
public class ImageUploadValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif"
    );

    private final long maxSizeBytes;
    private final int maxDimension;
    private final long maxPixels;

    public ImageUploadValidator(
            @Value("${app.knowledge.image-max-size}") DataSize maxSize,
            @Value("${app.knowledge.image-max-dimension}") int maxDimension,
            @Value("${app.knowledge.image-max-pixels}") long maxPixels
    ) {
        if (maxSize.toBytes() <= 0 || maxDimension <= 0 || maxPixels <= 0) {
            throw new IllegalArgumentException("Image resource limits must be positive");
        }
        this.maxSizeBytes = maxSize.toBytes();
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MalformedImageException("Image file must not be empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ImageTooLargeException();
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new MalformedImageException("Image could not be read");
        }
        if (content.length == 0) {
            throw new MalformedImageException("Image file must not be empty");
        }
        if (content.length > maxSizeBytes) {
            throw new ImageTooLargeException();
        }

        ImageInfo detected = detect(content);
        String declaredType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_TYPES.contains(declaredType)) {
            throw new UnsupportedImageTypeException();
        }
        if (!declaredType.equals(detected.contentType())) {
            throw new MalformedImageException("Declared media type does not match image content");
        }
        if (!"image/webp".equals(detected.contentType())) {
            detected = validateWithImageIo(content, detected.contentType());
        }
        validateDimensions(detected.width(), detected.height());
        return new ValidatedImage(content, detected.contentType(), detected.width(), detected.height());
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new MalformedImageException("Image dimensions are invalid");
        }
        if (width > maxDimension || height > maxDimension
                || (long) width * height > maxPixels) {
            throw new MalformedImageException("Image dimensions exceed the configured safety limit");
        }
    }

    private static ImageInfo detect(byte[] data) {
        if (matches(data, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) {
            return png(data);
        }
        if (matches(data, 0, 0xff, 0xd8)) {
            return jpeg(data);
        }
        if (ascii(data, 0, "GIF87a") || ascii(data, 0, "GIF89a")) {
            return gif(data);
        }
        if (ascii(data, 0, "RIFF") && ascii(data, 8, "WEBP")) {
            return webp(data);
        }
        throw new UnsupportedImageTypeException();
    }

    private static ImageInfo validateWithImageIo(byte[] content, String contentType) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) throw malformed();
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw malformed();
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return new ImageInfo(contentType, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof MalformedImageException malformed) throw malformed;
            throw malformed();
        }
    }

    private static ImageInfo png(byte[] data) {
        if (data.length < 24 || !ascii(data, 12, "IHDR")) {
            throw malformed();
        }
        return new ImageInfo("image/png", int32BigEndian(data, 16), int32BigEndian(data, 20));
    }

    private static ImageInfo gif(byte[] data) {
        if (data.length < 10) {
            throw malformed();
        }
        return new ImageInfo("image/gif", uint16LittleEndian(data, 6), uint16LittleEndian(data, 8));
    }

    private static ImageInfo jpeg(byte[] data) {
        int offset = 2;
        while (offset < data.length) {
            while (offset < data.length && unsigned(data[offset]) != 0xff) {
                offset++;
            }
            while (offset < data.length && unsigned(data[offset]) == 0xff) {
                offset++;
            }
            if (offset >= data.length) break;
            int marker = unsigned(data[offset++]);
            if (marker == 0xd9 || marker == 0xda) break;
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
            if (offset + 1 >= data.length) throw malformed();
            int segmentLength = uint16BigEndian(data, offset);
            if (segmentLength < 2 || offset + segmentLength > data.length) throw malformed();
            if (isStartOfFrame(marker)) {
                if (segmentLength < 7) throw malformed();
                return new ImageInfo(
                        "image/jpeg",
                        uint16BigEndian(data, offset + 5),
                        uint16BigEndian(data, offset + 3)
                );
            }
            offset += segmentLength;
        }
        throw malformed();
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xc3
                || marker >= 0xc5 && marker <= 0xc7
                || marker >= 0xc9 && marker <= 0xcb
                || marker >= 0xcd && marker <= 0xcf;
    }

    private static ImageInfo webp(byte[] data) {
        if (data.length < 30) throw malformed();
        if (ascii(data, 12, "VP8X")) {
            int width = 1 + uint24LittleEndian(data, 24);
            int height = 1 + uint24LittleEndian(data, 27);
            return new ImageInfo("image/webp", width, height);
        }
        if (ascii(data, 12, "VP8L") && unsigned(data[20]) == 0x2f) {
            int b1 = unsigned(data[21]);
            int b2 = unsigned(data[22]);
            int b3 = unsigned(data[23]);
            int b4 = unsigned(data[24]);
            int width = 1 + b1 + ((b2 & 0x3f) << 8);
            int height = 1 + (b2 >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
            return new ImageInfo("image/webp", width, height);
        }
        if (ascii(data, 12, "VP8 ") && matches(data, 23, 0x9d, 0x01, 0x2a)) {
            int width = uint16LittleEndian(data, 26) & 0x3fff;
            int height = uint16LittleEndian(data, 28) & 0x3fff;
            return new ImageInfo("image/webp", width, height);
        }
        throw malformed();
    }

    private static boolean ascii(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) return false;
        for (int index = 0; index < value.length(); index++) {
            if (unsigned(data[offset + index]) != value.charAt(index)) return false;
        }
        return true;
    }

    private static boolean matches(byte[] data, int offset, int... expected) {
        if (offset < 0 || offset + expected.length > data.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (unsigned(data[offset + index]) != expected[index]) return false;
        }
        return true;
    }

    private static int int32BigEndian(byte[] data, int offset) {
        return unsigned(data[offset]) << 24
                | unsigned(data[offset + 1]) << 16
                | unsigned(data[offset + 2]) << 8
                | unsigned(data[offset + 3]);
    }

    private static int uint24LittleEndian(byte[] data, int offset) {
        return unsigned(data[offset])
                | unsigned(data[offset + 1]) << 8
                | unsigned(data[offset + 2]) << 16;
    }

    private static int uint16BigEndian(byte[] data, int offset) {
        return unsigned(data[offset]) << 8 | unsigned(data[offset + 1]);
    }

    private static int uint16LittleEndian(byte[] data, int offset) {
        return unsigned(data[offset]) | unsigned(data[offset + 1]) << 8;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static MalformedImageException malformed() {
        return new MalformedImageException("Image content is malformed");
    }

    private record ImageInfo(String contentType, int width, int height) {
    }
}
