package org.frasergo.wasync.service.exception;

/**
 * Exception connecting.
 */
public abstract class ConnectivityException extends Exception implements LocalizableException {
    public ConnectivityException(String msg) {
        super(msg);
    }
}
