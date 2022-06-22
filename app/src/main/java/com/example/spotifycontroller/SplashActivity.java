package com.example.spotifycontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

        token = "BQDnIbSo5uZCVFo4IBgDXgWhGLtCJi1xLXt0E_6DBhaGJTxjx_2w3EF7E2rENa2hHnUkIczzSA6gJrkbvgVXnlrJ9sTM82ElAzV_8bCWXHFbXXSyYJ3E3_7s09wg-i381xTK7woAGrHjTAPx3Ed7ItQQrq6DmxfJ9r3eqPkQnuHbDVx9Y3WrOGFHsOQA2lJzbPQ09pr5bXmttPEWOOs554zkq0-yJ6e2";

        // SPOTIFY SDK
        List<String> test = new ArrayList<>();
        test.add(RequiredFeatures.FEATURES_V1);
        test.add(RequiredFeatures.FEATURES_V2);
        test.add(RequiredFeatures.FEATURES_V3);

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
                    }
                });

        //SPOTIFY WEB API
        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI);
        builder.setScopes(new String[]{SCOPES});
        AuthorizationRequest request = builder.build();
        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);

        Thread waitUntilAuthenticated = new Thread(){
            public void run() {

                while (mSpotifyAppRemote == null || token == null) {

                }
                try {
                    sleep(500);
                }
                catch (InterruptedException e) {

                }

                Log.e("Splash", "Launching main");
                Intent newIntent = new Intent(SplashActivity.this, MainActivity.class);
                newIntent.putExtra("token", token);
                MainActivity.mSpotifyAppRemote = mSpotifyAppRemote;
                startActivity(newIntent);
                finish();

            }
        };
        waitUntilAuthenticated.start();
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