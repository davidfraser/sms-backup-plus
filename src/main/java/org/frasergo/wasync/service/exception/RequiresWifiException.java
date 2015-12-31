package org.frasergo.wasync.service.exception;

import org.frasergo.wasync.R;

public class RequiresWifiException extends ConnectivityException {
    public RequiresWifiException() {
        super(null);
    }

    @Override
    public int errorResourceId() {
        return R.string.error_wifi_only_no_connection;
    }
}
