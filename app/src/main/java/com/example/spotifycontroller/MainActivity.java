package com.example.spotifycontroller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
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

    public static SpotifyAppRemote mSpotifyAppRemote;
    public static String token;

    Intent service;
    static PlayerApi playerApi;

    static int currentTrackLength = 0;
    static float currentVelocity = 0;
    static long timeToWait = 0;
    static Thread thread;
    static Thread nextThread;

    //static boolean isCrossfadeEnabled = false;
    //static int crossFadeDuration;

    FusedLocationProviderClient fusedLocationProviderClient;
    Location lastKnownLocation;

    static boolean active = false;

    ArrayList<Playlist> playlists;
    PlaylistsAdapter playlistsRecyclerViewAdapter;
    ArrayList<Track> playlist;
    String selectedPlaylistID;
    private static class Track {

        public String name;
        public String id;
        public float energy;

        private Track(String name, String id) {
            this.name = name;
            this.id = id;

        }
    }

    public class actionAtEndOfTrack extends Thread {
        public void run() {
            nextThread = new actionAtEndOfTrack(); // creates the next instance of this class prematurely, so that it can be started in static methods

            if (timeToWait < 1000) {
                return;
            }

            try {
                sleep(timeToWait); // wait until near end of song
                getLocation();
                queueNextTrack();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Switch toggle = findViewById(R.id.switchEnable);
        if (toggle != null) {
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    active = isChecked;

                    if (isChecked) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                            getPlaylistTracks(selectedPlaylistID);
                            playerApi.resume();
                            startLocationUpdates();
                        }
                        else {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
                        }
                    }
                    else {
                        if (thread != null) {
                            thread.interrupt();
                            stopLocationUpdates();
                        }
                    }

                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        nextThread = new actionAtEndOfTrack();

        token = getIntent().getStringExtra("token");

        connected();

        // setup playlistsRecyclerView
        playlists = new ArrayList<>();
        playlistsRecyclerViewAdapter = new PlaylistsAdapter(playlists);
        RecyclerView playlistsRecyclerView = findViewById(R.id.recyclerPlaylists);
        playlistsRecyclerView.setAdapter(playlistsRecyclerViewAdapter);
        playlistsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        getUserPlaylists();

    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        if (service != null) {
            this.stopService(service);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 44: // ACCESS_FINE_LOCATION

                Switch toggle = findViewById(R.id.switchEnable);

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getPlaylistTracks(selectedPlaylistID);
                    playerApi.resume();
                    startLocationUpdates();
                }  else {
                    toggle.setChecked(false);
                }
        }
    }

    private void connected() { // run when Spotify SDK connection established

        playerApi = mSpotifyAppRemote.getPlayerApi();

        // create background service to listen to Spotify app
        if (service != null) {
            this.stopService(service);
        }
        service = new Intent(this, ReceiverService.class);
        this.startService(service);

        /*CallResult<CrossfadeState> crossfadeStateCall = playerApi.getCrossfadeState();
        Result<CrossfadeState> crossfadeStateResult = crossfadeStateCall.await(10, TimeUnit.SECONDS);
        if (crossfadeStateResult.isSuccessful()) {
            CrossfadeState crossfadeState = crossfadeStateResult.getData();
            isCrossfadeEnabled = crossfadeState.isEnabled;
            crossFadeDuration = crossfadeState.duration;
            Log.e("", ""+crossFadeDuration);
        }*/

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

    private static String getSingleTrackEnergy(String id) {
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

    // INTERACTION WITH SPOTIFY SDK

    public static void onMetadataChange(String trackID, int trackLength, String trackName) {

        currentTrackLength = trackLength;

        Log.e(TAG, "META CHANGED");
        try {
            String energy = getSingleTrackEnergy(trackID.split(":")[2]); // get id from URI
            Log.e(TAG, "Playing " + trackName + ", Energy:" + energy);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Invalid trackID");
        }

        if (active) {
            playerApi.resume();
        }
    }

    public static void onPlaybackStateChange(boolean playing, int playbackPos) {

        if (active) {
            if (playing) {
                Log.e(TAG, "PLAYBACK STARTED");

                timeToWait = currentTrackLength - playbackPos - 10000; // time (in ms) until 10s from end of track
                Log.e(TAG, "" + timeToWait);

                if (thread != null) {
                    thread.interrupt();
                }
                thread = nextThread;
                try {
                    thread.start();
                } catch (IllegalThreadStateException e) {
                    Log.e(TAG, "Illegal State Exception");
                }
            } else {
                Log.e(TAG, "PLAYBACK STOPPED");

                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }

    private void queueNextTrack() {
        float currentEnergy = currentVelocity / 31.2928f; //calculates energy as a % of car speed out of 70mph
            /*//FOR DEBUG
            Random rand = new Random();
            currentEnergy = rand.nextFloat();*/
        Log.e(TAG, "Energy: "+currentEnergy);


        // find song based off of energy
        if (playlist.size() > 0) {
            float minDelta = 1;
            Track nextTrack = new Track("", "");
            for (int i = 0; i < playlist.size(); i++) {
                float delta = Math.abs(currentEnergy - playlist.get(i).energy);
                if (delta <= minDelta) {
                    minDelta = delta;
                    nextTrack = playlist.get(i);
                }
            }
            playlist.remove(nextTrack);

            //queue
            playerApi.queue("spotify:track:" + nextTrack.id);
            Log.e(TAG, "QUEUED " + nextTrack.name);
        }
        else {
            Log.e(TAG, "Playlist empty");
        }
    }

    // GPS LOCATION

    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);

            locationUpdated(locationResult.getLastLocation());
        }
    };

    private void startLocationUpdates() {

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setSmallestDisplacement(0); //DEBUG

        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
        catch (SecurityException e) {
            Log.e(TAG, "Invalid permission to requestLocationUpdates");
        }

    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private void getLocation() {

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            return;
        }

        //TEMP
        TextView textLocation = findViewById(R.id.location);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textLocation.setText("");
            }
        });

        try {

            fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {

                    Location currentLocation = task.getResult(); // get Location
                    if (currentLocation != null) {
                        locationUpdated(currentLocation);
                    }
                    else {
                        Log.e(TAG, "Location is null");
                    }
                }
            });
        }
        catch (SecurityException e) {
            Log.e(TAG, "Security Exception");
        }

    }

    private void locationUpdated(Location newLocation) {
        Log.e(TAG, "lat:"+newLocation.getLatitude()+" long:"+newLocation.getLongitude());

        TextView textLocation = findViewById(R.id.location);
        TextView textSpeed = findViewById(R.id.data);

        if (lastKnownLocation != null) {
            // calculate velocity
            float distanceTravelled = newLocation.distanceTo(lastKnownLocation);
            float timePassed = (SystemClock.elapsedRealtimeNanos() - lastKnownLocation.getElapsedRealtimeNanos()) / 1000000000; //in ms
            currentVelocity = distanceTravelled / timePassed;

            //TEMP
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textSpeed.setText("distance: "+distanceTravelled+"m\ntime :"+timePassed+"s\nspeed: "+currentVelocity+"m/s ("+(currentVelocity*2.23694)+"mph)\n\nenergy: "+(currentVelocity/31.2928));
                }
            });
        }

        //TEMP
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textLocation.setText("lat:"+newLocation.getLatitude()+"\nlong:"+newLocation.getLongitude());
            }
        });

        lastKnownLocation = newLocation;
    }

    //TEMP
    public void forceLocationUpdate(View view) {
        getLocation();
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