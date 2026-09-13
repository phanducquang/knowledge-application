package com.knowledgeapplication.api.attachment.storage;

public class ObjectStorageUnavailableException extends RuntimeException {

    public ObjectStorageUnavailableException(Throwable cause) {
        super("Image storage is currently unavailable", cause);
    }
}
