package org.frasergo.wasync.service.exception;

import org.frasergo.wasync.R;

public class NoConnectionException extends ConnectivityException {
    public NoConnectionException() {
        super(null);
    }

    @Override public int errorResourceId() {
        return R.string.error_no_connection;
    }
}
