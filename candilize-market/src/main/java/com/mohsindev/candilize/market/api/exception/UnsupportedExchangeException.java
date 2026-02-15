package com.mohsindev.candilize.market.api.exception;

/** Thrown when the requested exchange is not supported. */
public class UnsupportedExchangeException extends RuntimeException {

    public UnsupportedExchangeException(String message) {
        super(message);
    }
}
