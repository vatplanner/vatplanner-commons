package org.vatplanner.commons.crypto;

/**
 * Thrown if any cryptographic operation or setup fails.
 */
public class CryptoFailure extends RuntimeException {
    CryptoFailure(String msg) {
        super(msg);
    }

    CryptoFailure(String msg, Throwable cause) {
        super(msg, cause);
    }
}
