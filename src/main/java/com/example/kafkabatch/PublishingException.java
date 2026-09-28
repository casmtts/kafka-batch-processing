package com.example.kafkabatch;

public class PublishingException extends RuntimeException {
    public PublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
