/*
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.frasergo.wasync.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import org.frasergo.wasync.App;
import org.frasergo.wasync.R;
import org.frasergo.wasync.activity.MainActivity;
import org.frasergo.wasync.mail.BackupImapStore;
import org.frasergo.wasync.preferences.AuthPreferences;
import org.frasergo.wasync.preferences.Preferences;
import org.frasergo.wasync.service.state.State;
import org.frasergo.wasync.utils.AppLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.frasergo.wasync.App.LOCAL_LOGV;
import static org.frasergo.wasync.App.TAG;

public abstract class ServiceBase extends Service {
    @Nullable private PowerManager.WakeLock mWakeLock;
    @Nullable private WifiManager.WifiLock mWifiLock;

    private AppLog appLog;
    @Nullable protected Notification notification;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (new Preferences(this).isAppLogEnabled()) {
            char[] format;
            try {
                format = DateFormat.getDateFormatOrder(this);
            } catch (IllegalArgumentException e) {
                format = new char[] { DateFormat.DATE };
            }
            this.appLog = new AppLog(format);
        }
        App.bus.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (appLog != null) appLog.close();
        App.bus.unregister(this);
        notification = null;
    }

    // Android api level < 5
    @Override
    public void onStart(final Intent intent, int startId) {
        handleIntent(intent);
    }

    // Android api level >= 5
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    public abstract @NotNull State getState();

    public boolean isWorking() {
        return getState().isRunning();
    }

    protected BackupImapStore getBackupImapStore() throws MessagingException {
        final String uri = getAuthPreferences().getStoreUri();
        if (!BackupImapStore.isValidUri(uri)) {
            throw new MessagingException("No valid IMAP URI: "+uri);
        }
        return new BackupImapStore(getApplicationContext(), uri);
    }

    protected AuthPreferences getAuthPreferences() {
        return new AuthPreferences(this);
    }

    protected Preferences getPreferences() {
        return new Preferences(this);
    }

    protected synchronized void acquireLocks() {
        if (mWakeLock == null) {
            PowerManager pMgr = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pMgr.newWakeLock(wakeLockType(), TAG);
        }
        mWakeLock.acquire();

        if (isConnectedViaWifi()) {
            // we have Wifi, lock it
            WifiManager wMgr = getWifiManager();
            if (mWifiLock == null) {
                mWifiLock = wMgr.createWifiLock(getWifiLockType(), TAG);
            }
            mWifiLock.acquire();
        }
    }

    protected int wakeLockType() {
        return PowerManager.PARTIAL_WAKE_LOCK;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private int getWifiLockType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 ?
                WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL;
    }

    protected synchronized void releaseLocks() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
            mWifiLock = null;
        }
    }

    protected boolean isBackgroundTask() {
        return false;
    }

    protected abstract void handleIntent(final Intent intent);

    protected void appLog(int id, Object... args) {
        final String msg = getString(id, args);
        if (appLog != null) {
            appLog.append(msg);
        } else if (LOCAL_LOGV) {
            Log.d(App.TAG, "AppLog: "+msg);
        }
    }

    protected void appLogDebug(String message, Object... args) {
        if (getPreferences().isAppLogDebug() && appLog != null) {
            String formatted = String.format(Locale.ENGLISH, message, args);
            appLog.append(formatted);
        }
    }

    protected NotificationManager getNotifier() {
        return (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
    }

    protected ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    protected WifiManager getWifiManager() {
        return (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    protected @NotNull Notification createNotification(int resId) {
        Notification n = new Notification(R.drawable.ic_notification,
                getString(resId),
                System.currentTimeMillis());
        n.flags = Notification.FLAG_ONGOING_EVENT;
        return n;
    }

    protected PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT);
    }

    protected boolean isConnectedViaWifi() {
        WifiManager wifiManager = getWifiManager();
        return (wifiManager != null &&
                wifiManager.isWifiEnabled() &&
                getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null &&
                getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected());
    }
}
