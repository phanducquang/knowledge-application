package com.knowledgeapplication.api.attachment.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("app.object-storage")
public record ObjectStorageProperties(
        URI endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle
) {
    public ObjectStorageProperties {
        if (endpoint == null || !endpoint.isAbsolute()) {
            throw new IllegalArgumentException("Object storage endpoint must be an absolute URI");
        }
        requireText(region, "Object storage region");
        requireText(bucket, "Object storage bucket");
        requireText(accessKey, "Object storage access key");
        requireText(secretKey, "Object storage secret key");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured");
        }
    }
}
