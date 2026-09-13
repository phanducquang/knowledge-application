package com.knowledgeapplication.api.attachment.storage;

import java.io.InputStream;

public record StoredImage(
        InputStream content,
        String contentType,
        long contentLength
) {
}
