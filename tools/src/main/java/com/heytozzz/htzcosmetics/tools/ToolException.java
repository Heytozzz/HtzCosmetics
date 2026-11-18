package com.heytozzz.htzcosmetics.tools;

/** A problem the owner can fix; its message is shown as-is, without a stack trace. */
public final class ToolException extends RuntimeException {
    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
