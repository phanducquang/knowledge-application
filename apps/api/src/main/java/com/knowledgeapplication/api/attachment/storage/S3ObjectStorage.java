package com.knowledgeapplication.api.attachment.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3ObjectStorage {

    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(S3Client client, ObjectStorageProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    public void put(String objectKey, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content)
            );
        } catch (RuntimeException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    public StoredImage get(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build()
            );
            return new StoredImage(
                    response,
                    response.response().contentType(),
                    response.response().contentLength()
            );
        } catch (S3Exception exception) {
            throw new ObjectStorageUnavailableException(exception);
        } catch (RuntimeException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    public void deleteBestEffort(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException ignored) {
            // PostgreSQL and S3 cannot share a transaction. Cleanup is deliberately best-effort.
        }
    }
}
