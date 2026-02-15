package com.mohsindev.candilize.auth.api.exception;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String entity, Long id) {
        super(entity + " not found: " + id);
    }
}
