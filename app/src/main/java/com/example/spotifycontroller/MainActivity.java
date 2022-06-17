package com.example.spotifycontroller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.util.Log;
import android.content.Intent;

import android.location.Location;
import android.view.View;
import android.widget.TextView;
import android.webkit.WebView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.PlayerApi;

import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import java.io.IOException;
import java.util.List;

import java.lang.Thread;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences.Editor editor;
    private SharedPreferences msharedPreferences;

    private static final String CLIENT_ID = "c3ea15ea37eb4121a64ee8af3521f832";
    private static final String REDIRECT_URI = "com.example.spotifycontroller://callback";
    private static final int REQUEST_CODE = 1337;
    private static final String SCOPES = "user-read-email,user-read-private";
    private SpotifyAppRemote mSpotifyAppRemote;

    Intent service;
    PlayerApi playerApi;

    static int currentTrackLength = 0;
    static float currentVelocity = 0;
    static long timeToWait = 0;
    static Thread thread;
    static Thread nextThread;


    FusedLocationProviderClient fusedLocationProviderClient;

    public class Test extends Thread {

        public void run() {
            nextThread = new Test();

            if (timeToWait < 500) {
                return;
            }

            try {

                sleep(timeToWait);
                getLocation();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // move this to trigger when made
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        nextThread = new Test();

        // SPOTIFY SDK
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

                        connected();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", throwable.getMessage(), throwable);

                        // Handle errors here
                    }
                });

        // SPOTIFY WEB API
        /*Thread connectToWebAPI = new Thread() {
            public void run() {
                try {
                    String url_auth =
                            "https://accounts.spotify.com/authorize?"
                                    + "client_id=" + CLIENT_ID + "&"
                                    + "response_type=code&"
                                    + "redirect_uri=" + REDIRECT_URI + "&"
                                    + "scope=user-read-private%20user-read-email&"
                                    + "state=34fFs29kd09";
                    Log.e("", url_auth);

                    URL url = new URL(url_auth);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    //conn.setRequestProperty("Accept", "application/json");

                    if (conn.getResponseCode() != 200) {
                        throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

                    String output;
                    System.out.println("Output from Server .... \n");
                    while ((output = br.readLine()) != null) {
                        //Log.e("", output);
                        test += output+"\n";
                    }
                    conn.disconnect();

                    Log.e("", test);
                    WebView webView = (WebView)findViewById(R.id.webView);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.loadUrl(url_auth);
                        }
                    });

                }
                catch (MalformedURLException e) {
                    Log.e("", "Malformed URL Exception");
                }
                catch (IOException e) {
                    Log.e("", "IO Exception connecting to Web API");
                }
            }
        };
        connectToWebAPI.start();*/

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
                    editor = getSharedPreferences("SPOTIFY", 0).edit();
                    editor.putString("token", response.getAccessToken());
                    token = response.getAccessToken();
                    editor.apply();
                    getBPM("6UDFkqHY5gLREnSh9jd5th");
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

    String token;

    private void getBPM(String id) {
        GET("https://api.spotify.com/v1/audio-features/", id);
    }

    private void GET(final String inURL, final String inID) {
        Thread connectToWebAPI = new Thread() {
            public void run() {
                String endpoint = inURL;
                String id = inID;
                try {

                    URL url = new URL(endpoint+id);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization",  "Bearer " + token);

                    if (conn.getResponseCode() != 200) {
                        throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

                    String output;
                    System.out.println("Output from Server .... \n");
                    while ((output = br.readLine()) != null) {
                        Log.e("", output);
                    }
                    conn.disconnect();

                } catch (MalformedURLException e) {
                    Log.e("", "Malformed URL Exception");
                } catch (IOException e) {
                    Log.e("", "IO Exception connecting to Web API");
                }

            }
        };
        connectToWebAPI.start();
    }

    private void connected() {

        playerApi = mSpotifyAppRemote.getPlayerApi();

        if (service != null) {
            this.stopService(service);
        }
        service = new Intent(this, ReceiverService.class);
        this.startService(service);

    }

    public static void onPlaybackStateChange(boolean playing, int playbackPos) {

        if (playing) {
            Log.e("", "PLAYBACK STARTED");

            timeToWait = currentTrackLength - playbackPos - 10000; //time (in ms) until 10s from end of track
            Log.e("", ""+timeToWait);

            if (thread != null) {
                thread.interrupt();
            }
            thread = nextThread;
            thread.start();
        }
        else {
            Log.e("", "PLAYBACK STOPPED");

            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    public static void onMetadataChange(String trackID, int trackLength) {

        Log.e("", "META CHANGED");

        currentTrackLength = trackLength;
    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        if (service != null) {
            this.stopService(service);
        }

    }

    private void getLocation() {

        TextView text = findViewById(R.id.textView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                text.setText("");
            }
        });

        try {

            fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {

                    Location location = task.getResult();
                    if (location != null) {
                        Log.e("", "lat:"+location.getLatitude()+" long:"+location.getLongitude());
                        Log.e("", ""+location.getSpeed());

                        //TEMP
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                text.setText("lat:"+location.getLatitude()+"\nlong:"+location.getLongitude()+"\nspeed:"+location.getSpeed());
                            }
                        });

                        queueNextTrack();
                    }
                    else {
                        Log.e("", "Location is null");
                    }
                }
            });;
        }
        catch (SecurityException e) {
            Log.e("", "Security Exception");
        }

    }

    public void Click(View view) {
        getLocation();
    }

    private void queueNextTrack() {
        playerApi.queue("spotify:track:6UDFkqHY5gLREnSh9jd5th");
    }
}
