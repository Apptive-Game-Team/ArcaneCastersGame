package com.wordonline.server.local;

/**
 * A startup failure whose message is fit for the {@code error} line: a sentence a person reading the
 * client's log can act on, never a stack trace.
 */
public class LocalStartupException extends RuntimeException {

    public LocalStartupException(String reason) {
        super(reason);
    }

    public LocalStartupException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
