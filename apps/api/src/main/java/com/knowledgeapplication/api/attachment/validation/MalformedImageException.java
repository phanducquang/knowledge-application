package com.knowledgeapplication.api.attachment.validation;

public class MalformedImageException extends RuntimeException {

    public MalformedImageException(String message) {
        super(message);
    }
}
