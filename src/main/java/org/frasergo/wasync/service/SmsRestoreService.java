package org.frasergo.wasync.service;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Telephony;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import org.frasergo.wasync.App;
import org.frasergo.wasync.R;
import org.frasergo.wasync.auth.OAuth2Client;
import org.frasergo.wasync.auth.TokenRefresher;
import org.frasergo.wasync.contacts.ContactAccessor;
import org.frasergo.wasync.mail.MessageConverter;
import org.frasergo.wasync.mail.PersonLookup;
import org.frasergo.wasync.preferences.AuthPreferences;
import org.frasergo.wasync.service.exception.SmsProviderNotWritableException;
import org.frasergo.wasync.service.state.RestoreState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;

import static org.frasergo.wasync.App.LOCAL_LOGV;
import static org.frasergo.wasync.App.TAG;
/*
import static org.frasergo.wasync.mail.DataType.CALLLOG;
import static org.frasergo.wasync.mail.DataType.SMS;
*/
import static org.frasergo.wasync.service.state.SmsSyncState.ERROR;

public class SmsRestoreService extends ServiceBase {
    private static final int RESTORE_ID = 2;

    @NotNull private RestoreState mState = new RestoreState();
    @Nullable private static SmsRestoreService service;

    @Override @NotNull
    public RestoreState getState() {
        return mState;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        asyncClearCache();
        BinaryTempFileBody.setTempDirectory(getCacheDir());
        service = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (LOCAL_LOGV) Log.v(TAG, "SmsRestoreService#onDestroy(state"+getState()+")");
        service = null;
    }

    /**
     * Android KitKat and above require SMS Backup+ to be the default SMS application in order to
     * write to the SMS Provider.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean canWriteToSmsProvider() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ||
               getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }

    @Override
    protected void handleIntent(final Intent intent) {
        if (isWorking()) return;

        try {
            /*
            final boolean restoreCallLog = CALLLOG.isRestoreEnabled(service);
            final boolean restoreSms     = SMS.isRestoreEnabled(service);
            */
            final boolean restoreCallLog = false;
            final boolean restoreSms = false;

            if (restoreSms && !canWriteToSmsProvider()) {
                postError(new SmsProviderNotWritableException());
                return;
            }

            MessageConverter converter = new MessageConverter(service,
                    getPreferences(),
                    getAuthPreferences().getUserEmail(),
                    new PersonLookup(getContentResolver()),
                    ContactAccessor.Get.instance()
            );

            RestoreConfig config = new RestoreConfig(
                getBackupImapStore(),
                0,
                restoreSms,
                restoreCallLog,
                getPreferences().isRestoreStarredOnly(),
                getPreferences().getMaxItemsPerRestore(),
                0
            );

            final AuthPreferences authPreferences = new AuthPreferences(this);
            new RestoreTask(this, converter, getContentResolver(),
                    new TokenRefresher(service, new OAuth2Client(authPreferences.getOAuth2ClientId()), authPreferences)).execute(config);

        } catch (MessagingException e) {
            postError(e);
        }
    }

    private void postError(Exception exception) {
        App.bus.post(mState.transition(ERROR, exception));
    }

    private void asyncClearCache() {
        new Thread("clearCache") {
            @Override
            public void run() {
                clearCache();
            }
        }.start();
    }

    public synchronized void clearCache() {
        File tmp = getCacheDir();
        if (tmp == null) return; // not sure why this would return null

        Log.d(TAG, "clearing cache in " + tmp);
        for (File f : tmp.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("body");
            }
        })) {
            if (LOCAL_LOGV) Log.v(TAG, "deleting " + f);
            if (!f.delete()) Log.w(TAG, "error deleting " + f);
        }
    }

    @Subscribe public void restoreStateChanged(final RestoreState state) {
        mState = state;
        if (mState.isInitialState()) return;

        if (mState.isRunning()) {
            if (notification == null) {
                notification = createNotification(R.string.status_restore);
            }
            notification.setLatestEventInfo(this,
                    getString(R.string.status_restore),
                    state.getNotificationLabel(getResources()),
                    getPendingIntent());

            startForeground(RESTORE_ID, notification);
        } else {
            Log.d(TAG, "stopping service, state"+mState);
            stopForeground(true);
            stopSelf();
        }
    }

    @Produce public RestoreState produceLastState() {
        return mState;
    }

    @Override protected int wakeLockType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // hold a full wake lock when restoring on newer version of Android, since
            // the user needs to switch  back the sms app afterwards
            return PowerManager.FULL_WAKE_LOCK;
        } else {
            return super.wakeLockType();
        }
    }

    public static boolean isServiceWorking() {
        return service != null && service.isWorking();
    }
}
