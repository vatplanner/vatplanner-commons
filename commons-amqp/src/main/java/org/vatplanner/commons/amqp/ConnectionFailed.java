package org.vatplanner.commons.amqp;

/**
 * Thrown if an issue causes an AMQP channel or the entire connection to be terminated.
 */
public class ConnectionFailed extends RuntimeException {
    public ConnectionFailed(String msg, Throwable cause) {
        super(msg, cause);
    }
}
