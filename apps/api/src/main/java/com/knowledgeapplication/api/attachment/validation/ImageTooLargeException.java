package com.knowledgeapplication.api.attachment.validation;

public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException() {
        super("Image exceeds the configured upload limit");
    }
}
