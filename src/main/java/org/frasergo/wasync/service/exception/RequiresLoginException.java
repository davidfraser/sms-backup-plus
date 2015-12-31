package org.frasergo.wasync.service.exception;

import org.frasergo.wasync.R;

public class RequiresLoginException extends Exception implements LocalizableException {
    @Override
    public int errorResourceId() {
        return R.string.err_sync_requires_login_info;
    }
}
