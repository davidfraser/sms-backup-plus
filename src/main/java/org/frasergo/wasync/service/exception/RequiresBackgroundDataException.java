package org.frasergo.wasync.service.exception;

import org.frasergo.wasync.R;

public class RequiresBackgroundDataException extends Exception implements LocalizableException {
    @Override
    public int errorResourceId() {
        return R.string.app_log_skip_backup_background_data;
    }
}
