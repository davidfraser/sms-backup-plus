package org.frasergo.wasync.service.exception;

import org.frasergo.wasync.R;

public class SmsProviderNotWritableException extends Exception implements LocalizableException {
    @Override public int errorResourceId() {
        return R.string.error_sms_provider_not_writable;
    }
}
