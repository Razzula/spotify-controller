package com.example.spotifycontroller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

// for Google Maps Location Services
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
// for Spotify SDK
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.PlayerApi;
// for Spotify Web API
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.os.Looper;
import android.util.Log;
import android.content.Intent;
import android.location.Location;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.lang.Thread;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static MainActivity context;

    public static SpotifyAppRemote mSpotifyAppRemote;
    public static String token;

    NotificationManager notificationManager;

    static PlayerApi playerApi;

    //static boolean isCrossfadeEnabled = false;
    //static int crossFadeDuration;

    boolean active = false;

    String CHANNEL_ID = "test";

    ArrayList<Playlist> playlists;
    PlaylistsAdapter playlistsRecyclerViewAdapter;
    ArrayList<Track> playlist;
    String selectedPlaylistID;
    public static class Track {

        public String name;
        public String id;
        public float energy;

        public Track(String name, String id) {
            this.name = name;
            this.id = id;

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        playerApi = mSpotifyAppRemote.getPlayerApi();
        token = getIntent().getStringExtra("token");

        createNotificationChannel();

            // setup playlistsRecyclerView
        playlists = new ArrayList<>();
        playlistsRecyclerViewAdapter = new PlaylistsAdapter(playlists);
        RecyclerView playlistsRecyclerView = findViewById(R.id.recyclerPlaylists);
        playlistsRecyclerView.setAdapter(playlistsRecyclerViewAdapter);
        playlistsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        getUserPlaylists();

        // switch
        Switch toggle = findViewById(R.id.switchEnable);
        if (toggle != null) {
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    active = isChecked;

                    if (isChecked) {

                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {

                                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 45);
                                    return;
                                }
                            }

                            beginProcess();

                        }
                        else {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
                        }
                    }

                    else {
                        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
                        if (MainWorker.context != null) {
                            MainWorker.context.onStopped();
                        }
                        notificationManager.cancel(0);
                    }

                }
            });
        }
    }

    @Override
    protected void onStart() {
        Switch toggle = (Switch) findViewById(R.id.switchEnable);
        toggle.setChecked(MainWorker.active);
        notificationManager.cancel(0);

        super.onStart();
    }

    @Override
    protected void onStop() {

        if (MainWorker.active) {
            Intent intent = new Intent(getApplicationContext(), StopperService.class);
            PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo)
                    .setContentTitle("Controller is running")
                    .setContentText("Tap to stop.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(true)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

            notificationManager.notify(0, builder.build());
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        WorkManager.getInstance(MainActivity.context).cancelAllWork();
        if (MainWorker.context != null) {
            MainWorker.context.onStopped();
        }
        notificationManager.cancel(0);
        Log.e("", "Destroyed");
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 45) {
            Switch toggle = findViewById(R.id.switchEnable);

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getPlaylistTracks(selectedPlaylistID);
                playerApi.resume();

                WorkRequest testWorkRequest = new OneTimeWorkRequest.Builder(MainWorker.class).build();
                WorkManager.getInstance(MainActivity.context).enqueue(testWorkRequest);
            }  else {
                toggle.setChecked(false);
            }
        }
        else if (requestCode == 44) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {

                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 45);
                    return;
                }
            }
            beginProcess();
        }
    }

    private void beginProcess() {
        getPlaylistTracks(selectedPlaylistID);
        playerApi.resume();

        WorkRequest testWorkRequest = new OneTimeWorkRequest.Builder(MainWorker.class).build();
        WorkManager.getInstance(MainActivity.context).enqueue(testWorkRequest);
    }

    // INTERACTION WITH SPOTIFY WEB API

    private static JSONObject GET(final String endpoint, final String id) {

        class WebThread implements Runnable {
            private volatile JSONObject json;

            @Override
            public void run() {
                try {

                    // establish connection to API
                    URL url = new URL(endpoint+id);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization",  "Bearer " + token);

                    if (conn.getResponseCode() != 200) { //400 bad request, 401 unauthorised, 429 too many requests

                        switch (conn.getResponseCode()) {
                            case 400:
                                Log.e(TAG, "HTTP Error 400: Bad Request ("+endpoint+id+")");
                                break;

                            default:
                                Log.e(TAG, endpoint+id);
                                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
                        }
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
                    String data = br.lines().collect(Collectors.joining()); //get result from API
                    conn.disconnect();

                    try {
                        json = new JSONObject(data);
                    }
                    catch (JSONException e) {
                        Log.e(TAG, "Error parsing String to JSON");
                    }

                } catch (MalformedURLException e) {
                    Log.e(TAG, "Malformed URL Exception");
                } catch (IOException e) {
                    Log.e(TAG, "IO Exception connecting to Web API");
                }
            }

            public JSONObject getJSON() {
                return json;
            }
        }

        // run thread, getting JSONObject result
        WebThread webThread = new WebThread();
        Thread connectToWebAPI = new Thread(webThread);
        connectToWebAPI.start();
        try {
            connectToWebAPI.join();
        }
        catch (InterruptedException e) {
            Log.e(TAG, "connectToWebAPI.join() interrupted");
        }
        return webThread.getJSON();
    }

    private void getUserPlaylists() {

        try {
            JSONArray playlists = GET("https://api.spotify.com/v1/me/playlists", "").getJSONArray("items"); // get user's playlist data
            for (int i=0; i<playlists.length(); i++) { // for each track in playlist
                JSONObject playlist = playlists.getJSONObject(i);

                String id = playlist.getString("id");
                String name = playlist.getString("name");
                String description = playlist.getString("description");;
                int numberOfTracks = Integer.parseInt(playlist.getJSONObject("tracks").getString("total"));

                this.playlists.add(new Playlist(id, name, description, numberOfTracks));
            }
            playlistsRecyclerViewAdapter.notifyItemInserted(0);

        } catch (JSONException e) {
            Log.e(TAG, "Map does not exists in playlists JSONObject");
        }

    }

    private void getPlaylistTracks(String id) {

        playlist = new ArrayList<>();

        JSONObject playlist = GET("https://api.spotify.com/v1/playlists/", id+"/tracks"); // get playlist track data
        try {
            JSONArray playlistInfo = playlist.getJSONArray("items");
            for (int i=0; i<playlistInfo.length(); i++) { // for each track in playlist
                JSONObject trackInfo = playlistInfo.getJSONObject(i).getJSONObject("track"); // get track data

                if (trackInfo.getBoolean("is_local")) { // prevent local files from being used (unable to obtain audio analysis..)
                    //Log.e(TAG, trackInfo.getString("name"));
                    continue; //local track
                }

                Track track = new Track(trackInfo.getString("name"), trackInfo.getString("id"));
                this.playlist.add(track);
            }
        }
        catch (JSONException e) {
            Log.e(TAG, "Map does not exist in JSONObject");
            return;
        }

        getPlaylistEnergies();
        Log.e(TAG, "["+this.playlist.get(0).name+", "+this.playlist.get(0).id+", "+this.playlist.get(0).energy+"]"); //DEBUG
    }

    public static String getSingleTrackEnergy(String id) {
        JSONObject trackAnalysis = GET("https://api.spotify.com/v1/audio-features/", id);
        try {
            return trackAnalysis.get("energy").toString();
        }
        catch (JSONException e) {
            Log.e(TAG, "Map does not exist in trackAnalysis JSONObject");
            return null;
        }
        catch (NullPointerException e) {
            return null;
        }
    }

    private void getPlaylistEnergies() { // update playlist's energy values
        String request = "?ids=";
        for (int i=0; i<playlist.size(); i++) {
            request += playlist.get(i).id+",";
        }

        try {
            JSONArray tracksAnalyses = GET("https://api.spotify.com/v1/audio-features/", request).getJSONArray("audio_features"); //get data from API
            for (int i=0; i<tracksAnalyses.length(); i++) {
                JSONObject trackAnalysis = tracksAnalyses.getJSONObject(i);
                playlist.get(i).energy = Float.parseFloat(trackAnalysis.getString("energy")); //update Track's energy to value received from API
            }
        }
        catch (JSONException e) {
            Log.e(TAG, "Map does not exist in tracksAnalyses JSONObject");
        }
    }

    View previouslySelectedPlaylist;
    public void playlistsSelected(View view) {

        TextView textView = (TextView) view.findViewById(R.id.textName);
        String selectedPlaylist = textView.getText().toString();

        Switch toggle = (Switch) findViewById(R.id.switchEnable);

        if (toggle.isChecked()) {
            return;
        }

        if (previouslySelectedPlaylist != null) {
            previouslySelectedPlaylist.setBackgroundColor(0x00000000); // deselect previous selection
        }
        else {
            toggle.setEnabled(true);
        }

        if (previouslySelectedPlaylist == view) { // selected playlist is clicked

            // deselect
            previouslySelectedPlaylist = null;
            selectedPlaylistID = null;
            toggle.setEnabled(false);
            return;
        }
        view.setBackgroundColor(0x8800AA00);


        // get ID of playlist
        for (int i=0; i<playlists.size(); i++) {
            if (playlists.get(i).getName().equals(selectedPlaylist)) {
                selectedPlaylistID = playlists.get(i).getID();
                previouslySelectedPlaylist = view;
                break;
            }
        }
    }
}