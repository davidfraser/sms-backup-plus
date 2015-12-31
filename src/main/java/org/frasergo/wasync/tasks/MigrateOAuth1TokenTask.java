package org.frasergo.wasync.tasks;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import org.frasergo.wasync.auth.OAuth2Client;
import org.frasergo.wasync.auth.OAuth2Token;
import org.frasergo.wasync.auth.XOAuthConsumer;
import org.frasergo.wasync.preferences.AuthPreferences;

import java.io.IOException;

import static org.frasergo.wasync.App.TAG;

public class MigrateOAuth1TokenTask extends AsyncTask<Void, Void, OAuth2Token> {
    private final XOAuthConsumer xoauthConsumer;
    private final OAuth2Client oAuth2Client;
    private final AuthPreferences authPreferences;

    public MigrateOAuth1TokenTask(XOAuthConsumer xoauthConsumer,
                                  OAuth2Client oauth2Client,
                                  AuthPreferences authPreferences) {
        this.xoauthConsumer = xoauthConsumer;
        this.oAuth2Client = oauth2Client;
        this.authPreferences = authPreferences;
    }

    @Override
    protected OAuth2Token doInBackground(Void... empty) {
        try {
            String refreshToken = xoauthConsumer.migrateToken(oAuth2Client.getClientId());
            if (!TextUtils.isEmpty(refreshToken)) {
                return oAuth2Client.refreshToken(refreshToken);
            } else {
                Log.w(TAG, "did not get a refresh token");
            }
        } catch (IOException e) {
            Log.w(TAG, e);
        }
        return  null;
    }

    @Override
    protected void onPostExecute(OAuth2Token oAuth2Token) {
        if (oAuth2Token != null) {
            authPreferences.setOauth2Token(
                authPreferences.getUsername(),
                oAuth2Token.accessToken,
                oAuth2Token.refreshToken);

            authPreferences.clearOAuth1Data();
        }
    }
}
