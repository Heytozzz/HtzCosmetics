package com.heytozzz.htzcosmetics.core;

/**
 * Thrown when downloaded data fails validation. Callers must catch it, log a warning
 * and ignore the offending item - it must never crash the game.
 */
public final class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
