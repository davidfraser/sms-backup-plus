package org.frasergo.wasync.mail;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;

public final class Headers {
    // private headers
    public static final String ID             = "X-wasync-id";
    public static final String ADDRESS        = "X-wasync-address";
    public static final String DATATYPE       = "X-wasync-datatype";
    public static final String TYPE           = "X-wasync-type";
    public static final String DATE           = "X-wasync-date";
    public static final String THREAD_ID      = "X-wasync-thread";
    public static final String READ           = "X-wasync-read";
    public static final String STATUS         = "X-wasync-status";
    public static final String PROTOCOL       = "X-wasync-protocol";
    public static final String SERVICE_CENTER = "X-wasync-service_center";
    public static final String BACKUP_TIME    = "X-wasync-backup-time";
    public static final String VERSION        = "X-wasync-version";
    public static final String DURATION       = "X-wasync-duration";

    // standard headers
    public static final String REFERENCES = "References";
    public static final String MESSAGE_ID = "Message-ID";

    public static String get(Message msg, String header) {
        try {
            String[] hdrs = msg.getHeader(header);
            if (hdrs != null && hdrs.length > 0) {
                return hdrs[0];
            }
        } catch (MessagingException ignored) {
        }
        return null;
    }
}
