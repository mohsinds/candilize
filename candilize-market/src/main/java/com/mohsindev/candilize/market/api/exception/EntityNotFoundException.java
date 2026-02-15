package com.mohsindev.candilize.market.api.exception;

/** Thrown when a requested entity (e.g. pair, interval) does not exist or is disabled. */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String entity, Long id) {
        super(entity + " not found: " + id);
    }
}
