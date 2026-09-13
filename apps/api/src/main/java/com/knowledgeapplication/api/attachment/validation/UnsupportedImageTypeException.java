package com.knowledgeapplication.api.attachment.validation;

public class UnsupportedImageTypeException extends RuntimeException {

    public UnsupportedImageTypeException() {
        super("Only PNG, JPEG, WebP and GIF images are supported");
    }
}
