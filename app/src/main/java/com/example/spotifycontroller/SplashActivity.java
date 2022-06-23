package com.example.spotifycontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.spotify.android.appremote.api.AppRemote;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.internal.SpotifyLocator;
import com.spotify.protocol.client.RequiredFeatures;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;
import com.spotify.sdk.android.auth.LoginActivity;
import com.spotify.sdk.android.auth.app.SpotifyAuthHandler;
import com.spotify.sdk.android.auth.app.SpotifyNativeAuthUtil;

public class SplashActivity extends AppCompatActivity {

    private static final String CLIENT_ID = "c3ea15ea37eb4121a64ee8af3521f832";
    private static final String REDIRECT_URI = "com.example.spotifycontroller://callback";
    private static final int REQUEST_CODE = 1337;
    private static final String SCOPES = "user-read-email,user-read-private,playlist-read-private";

    SpotifyAppRemote mSpotifyAppRemote;
    private static String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Thread waitUntilAuthenticated = new Thread(){
            public void run() {

                while (mSpotifyAppRemote == null || token == null) {}

                try {
                    sleep(1000);
                }
                catch (InterruptedException e) {

                }

                Log.e("Splash", "Launching main");
                Intent newIntent = new Intent(SplashActivity.this, MainActivity.class);
                newIntent.putExtra("token", token);
                MainActivity.mSpotifyAppRemote = mSpotifyAppRemote;
                startActivity(newIntent);
                finishAffinity();

            }
        };
        waitUntilAuthenticated.start();
    }

    @Override
    protected void onStart() {
        super.onStart();

        PackageManager pm = getPackageManager();
        boolean isSpotifyInstalled;
        try {
            pm.getPackageInfo("com.spotify.music", 0);
            isSpotifyInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            isSpotifyInstalled = false;
        }

        if (isSpotifyInstalled) {
            connectToSpotifyApp();
        }
        else {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialogue_noSpotify)
                    .setTitle(R.string.dialogue_noSpotify_T)
                    .setPositiveButton(R.string.dialouge_takeMe, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            getSpotify();
                        }
                    })
                    .setNegativeButton(R.string.dialogue_exit, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    })
                    .setCancelable(false);

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void getSpotify() {

        final String referrer = "adjust_campaign=PACKAGE_NAME&adjust_tracker=ndjczk&utm_source=adjust_preinstall";

        try {
            Uri uri = Uri.parse("market://details")
                    .buildUpon()
                    .appendQueryParameter("id", "com.spotify.music")
                    .appendQueryParameter("referrer", referrer)
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException ignored) {
            Uri uri = Uri.parse("https://play.google.com/store/apps/details")
                    .buildUpon()
                    .appendQueryParameter("id", "com.spotify.music")
                    .appendQueryParameter("referrer", referrer)
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
        finish();

    }

    // SPOTIFY SDK
    private void connectToSpotifyApp() {
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(this, connectionParams,
                new Connector.ConnectionListener() {

                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", throwable.getMessage(), throwable);

                        // Handle errors here
                        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                        builder.setMessage(R.string.dialogue_appRemoteFail)
                                .setTitle(R.string.dialogue_appRemoteFail_T)
                                .setPositiveButton(R.string.dialouge_retry, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        connectToSpotifyApp();
                                    }
                                })
                                .setNegativeButton(R.string.dialogue_exit, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                })
                                .setCancelable(false);
                    }
                });

        connectToSpotifyAPI();
    }

    // SPOTIFY WEB API
    private void connectToSpotifyAPI() {
        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI);
        builder.setScopes(new String[]{SCOPES});
        AuthorizationRequest request = builder.build();
        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);

            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    token = response.getAccessToken();
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }
        }
    }
}