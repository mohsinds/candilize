package com.mohsindev.candilize.api.exception;

/** Thrown when an unsupported exchange is requested (e.g. from CandleDataProviderFactory). Mapped to 400. */
public class UnsupportedExchangeException extends RuntimeException {

    public UnsupportedExchangeException(String message) {
        super(message);
    }

    public UnsupportedExchangeException(String exchange, Throwable cause) {
        super("Unsupported exchange: " + exchange, cause);
    }
}
