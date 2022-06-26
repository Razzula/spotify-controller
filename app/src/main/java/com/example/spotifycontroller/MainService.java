package com.example.spotifycontroller;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.spotify.android.appremote.api.PlayerApi;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class MainService extends Service {

    private static String TAG = "MainService";
    public static MainService context;

    PlayerApi playerApi;
    private BroadcastReceiver broadcastReceiver;

    int currentTrackLength = 0;
    ArrayList<Float> velocities;

    actionAtEndOfTrack endOfTrackAction;
    actionTowardsEndOfTrack startLocationTracking;

    FusedLocationProviderClient fusedLocationProviderClient;
    Location lastKnownLocation;

    boolean queued = false;
    public static boolean active = false;
    private boolean repeatEnabled;
    private boolean minorRepeatEnabled;
    private int minorRepeatRate;

    private int fadeDuration = 0;
    private int LocationPriority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;

    ArrayList<MainActivity.Track> playlist;
    ArrayList<MainActivity.Track> fullPlaylist;
    Queue<String> recentlyPlayed;

    public class actionAtEndOfTrack extends Thread {
        long timeToWait = 0;

        public void run() {

            if (timeToWait < 1000) {
                return;
            }

            try {
                sleep(timeToWait); // wait until near end of song
                if (!queued) {
                    queued = true;
                    //getLocation();
                    stopLocationUpdates();
                    queueNextTrack();
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void setTimeToWait(long timeToWait) {
            this.timeToWait = timeToWait;
        }
    }

    public class actionTowardsEndOfTrack extends Thread {
        long timeToWait = 0;

        public void run() {

            if (timeToWait < 0) {
                return;
            }

            try {
                sleep(timeToWait); // wait until near end of song
                startLocationUpdates();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void setTimeToWait(long timeToWait) {
            this.timeToWait = timeToWait;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "Service created");

        this.context = this;
        playerApi = MainActivity.playerApi; //TODO, use sharedPreferences, not static variables
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        if (intent.getAction().equals("HALT")) { // STOP SERVICE
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.e(TAG, "Service started");

        // NOTIFICATION
        Intent stopSelf = new Intent(this, MainService.class);
        stopSelf.setAction("HALT");
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, stopSelf,PendingIntent.FLAG_CANCEL_CURRENT);;

        Notification notification =
                new Notification.Builder(this, "foregroundAlert")
                        .setSmallIcon(R.drawable.logo)
                        .setContentTitle("Controller is running")
                        .setContentText("Tap to stop.")
                        .setOngoing(true)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build();

        startForeground(NotificationCompat.PRIORITY_LOW, notification);

        fullPlaylist = MainActivity.context.playlist;
        playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
        recentlyPlayed = new LinkedList();

        // BROADCAST RECIEVER
        broadcastReceiver = new SpotifyBroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.spotify.music.playbackstatechanged");
        filter.addAction("com.spotify.music.metadatachanged");

        getApplicationContext().registerReceiver(broadcastReceiver, filter);
        //TODO, ensure Spotify is setup to broadcast

        // PREFERENCES LISTENER
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        minorRepeatEnabled = prefs.getBoolean("allowMinorRepetition", false);
        minorRepeatRate = prefs.getInt("repetitionTolerance", 0);
        repeatEnabled = prefs.getBoolean("repeatEnabled", false);
        updateLocationPriority(prefs);

        SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key.equals("allowMinorRepetition")) {
                    minorRepeatEnabled = prefs.getBoolean("allowMinorRepetition", false);
                    minorRepeatRate = prefs.getInt("repetitionTolerance", 1);
                } else if (key.equals("repetitionTolerance")) {
                    minorRepeatRate = prefs.getInt("repetitionTolerance", 1);
                } else if (key.equals("repeatEnabled")) {
                    repeatEnabled = prefs.getBoolean("repeatEnabled", false);
                } else if (key.equals("locationAccuracy")) {
                    updateLocationPriority(prefs);
                }

                if (repeatEnabled && minorRepeatEnabled) {
                    playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);

        // GPS
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.context);

        // SPOTIFY
        playerApi
                .getCrossfadeState()
                .setResultCallback(
                        crossfadeState -> {
                            fadeDuration = crossfadeState.duration;
                        });

        active = true;
        return START_STICKY;

        //TODO, play playlist on start
        //TODO, make sure receiver is receiving, if not, point to Spotify settings
    }

    private void updateLocationPriority(SharedPreferences prefs) {
        String priority = prefs.getString("locationAccuracy", "Balance Location Accuracy and Battery Life");
        if (priority.equals("Favour Location Accuracy")) {
            LocationPriority = Priority.PRIORITY_HIGH_ACCURACY;
        }
        else if (priority.equals("Balance Location Accuracy and Battery Life")) {
            LocationPriority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        else if (priority.equals("Favour Battery Life")) {
            LocationPriority = Priority.PRIORITY_LOW_POWER;
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        try {
            getApplicationContext().unregisterReceiver(broadcastReceiver);
        }
        catch (IllegalArgumentException e) {}

        if (endOfTrackAction != null) {
            endOfTrackAction.interrupt();
        }
        if (startLocationTracking != null) {
            startLocationTracking.interrupt();
        }
        stopLocationUpdates();

        MainActivity.context.setSwitch(false);

        Log.e(TAG, "Service stopped");
        super.onDestroy();
    }

    // INTERACTION WITH ANDROID SDK

    public void onMetadataChange(String trackID, int trackLength, String trackName) {

        currentTrackLength = trackLength;
        queued = false;

        Log.e(TAG, "META CHANGED");
        try {
            String energy = MainActivity.getSingleTrackEnergy(trackID.split(":")[2]); // get id from URI
            Log.e(TAG, "Playing " + trackName + ", Energy:" + energy);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Invalid trackID");
        }

        stopLocationUpdates();
        new Thread()
        {
            public void run() { // quickly pause then resume track, to ensure playback is caught after meta
                playerApi.pause();
                try {
                    sleep(20);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                playerApi.resume();
            }
        }.start();
    }

    public void onPlaybackStateChange(boolean playing, int playbackPos) {

        if (playing) {
            Log.e(TAG, "PLAYBACK STARTED");
            long timeToWait;

            // start thread to queue next track at end of current track
            timeToWait = currentTrackLength - playbackPos - fadeDuration - 2000; // time (in ms) until 10s from end of track
            //Log.d(TAG, "" + timeToWait);

            if (endOfTrackAction != null) {
                endOfTrackAction.interrupt();
            }
            endOfTrackAction = new actionAtEndOfTrack();
            try {
                endOfTrackAction.setTimeToWait(timeToWait);
                endOfTrackAction.start();
            } catch (IllegalThreadStateException e) {
                Log.e(TAG, "Illegal State Exception");
            }

            // start thread to start location updates towards end of track
            timeToWait = currentTrackLength - playbackPos - 60000; // time (in ms) until 60s from end of track
            //Log.d(TAG, "" + timeToWait);

            if (startLocationTracking != null) {
                startLocationTracking.interrupt();
            }

            /*if (currentTrackLength - playbackPos < 10000) {
                return;
            }*/

            if (timeToWait < 0) {
                startLocationUpdates();
            }
            else {

                startLocationTracking = new actionTowardsEndOfTrack();
                try {
                    startLocationTracking.setTimeToWait(timeToWait);
                    startLocationTracking.start();
                } catch (IllegalThreadStateException e) {
                    Log.e(TAG, "Illegal State Exception");
                }
            }


        } else {
            Log.e(TAG, "PLAYBACK STOPPED");

            if (endOfTrackAction != null) {
                endOfTrackAction.interrupt();
            }
            if (startLocationTracking != null) {
                startLocationTracking.interrupt();
            }
            stopLocationUpdates();
        }
    }

    private void queueNextTrack() {

        if (playlist.size() == 0) { // PLAYLIST ENDED
            Log.d(TAG, "Playlist empty");
            onDestroy();
        }
        else if (playlist.size() == 1) { // END OF PLAYLIST
            String nextTrackID = playlist.get(0).id;
            if (repeatEnabled) {
                playerApi.queue("spotify:track:" + nextTrackID); //queue
                Log.d(TAG, "Playlist looped");
                playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
            }
            else {
                playerApi.play("spotify:track:" + nextTrackID); //play //TODO, delay this to prevent abrupt ending of current song
                playlist.remove(0);
            }
            // update data structures
            recentlyPlayed.add(nextTrackID);
            while (recentlyPlayed.size() > minorRepeatRate) {
                recentlyPlayed.remove();
            }
        }
        else {

            // calculate velocity
            float currentVelocity = 0;
            int i;
            for (i = 0; i < velocities.size(); i++) {
                if (!velocities.get(i).isNaN()) {
                    currentVelocity += velocities.get(i);
                }
            }
            currentVelocity /= i; //TODO, use modal average not mean
            Log.e(TAG, "Average Speed: " + currentVelocity + "m/s");

            float currentEnergy = currentVelocity / 31.2928f; //calculates energy as a % of car speed out of 70mph
            /*//FOR DEBUG
            Random rand = new Random();
            currentEnergy = rand.nextFloat();*/
            Log.e(TAG, "Energy: " + currentEnergy);

            // find song based off of energy //TODO, randomize to prevent always same order of tracks
            float minDelta = 1;
            MainActivity.Track nextTrack = null;
            for (i = 0; i < playlist.size(); i++) {
                float delta = Math.abs(currentEnergy - playlist.get(i).energy);

                if (delta <= minDelta) { // closer choice
                    if (!recentlyPlayed.contains(playlist.get(i).id)) { // track is only valid if not recently played
                        minDelta = delta;
                        nextTrack = playlist.get(i);
                    }

                }
            }

            if (nextTrack == null) { // catch
                Log.e(TAG, "Could not find next track");
                return;
            }

            // update data structures
            recentlyPlayed.add(nextTrack.id);
            while (recentlyPlayed.size() > minorRepeatRate) {
                recentlyPlayed.remove();
            }

            if (!minorRepeatEnabled) { // only remove track if not repeating
                playlist.remove(nextTrack);
            }

            //queue
            playerApi.queue("spotify:track:" + nextTrack.id);
            Log.e(TAG, "QUEUED " + nextTrack.name);
        }
    }

    // GPS LOCATION

    public class getLocationCaller extends Thread {

        public void run() {
            try {
                sleep(5000);
                getLocation();
                getNextLocation = new getLocationCaller();
                getNextLocation.start();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    Thread getNextLocation;

    private void startLocationUpdates() {

        velocities = new ArrayList<>();

        if (getNextLocation != null) {
            getNextLocation.interrupt();
        }
        getNextLocation = new getLocationCaller();
        getNextLocation.start();

        Log.e(TAG, "Location updates started");

        //getLocation();

    }

    private void stopLocationUpdates() {
        if (getNextLocation !=  null) {
            getNextLocation.interrupt();
            Log.e(TAG, "Location updates halted");
        }
    }

    private void getLocation() {

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Tried to getLocation without FINE_LOCATION permission");
            return;
        }
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Tried to getLocation without FINE_LOCATION permission");
            return;
        }
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Tried to getLocation without BACKGROUND_LOCATION permission");
            return;
        }

        //TODO, fix for background processing (always 0m/s)
        CurrentLocationRequest currentLocationRequest = new CurrentLocationRequest.Builder()
                .setPriority(LocationPriority)
                .setMaxUpdateAgeMillis(1000)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(30000)
                .build();

        try {

            fusedLocationProviderClient.getCurrentLocation(currentLocationRequest, null).addOnCompleteListener(new OnCompleteListener<Location>() {
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
        catch (NullPointerException e) {
            Log.e(TAG, "fusedLocationProviderClient is null");
        }

    }

    private void locationUpdated(Location newLocation) {

        if (lastKnownLocation != null) {
            // calculate velocity
            float distanceTravelled = newLocation.distanceTo(lastKnownLocation);
            float timePassed = (SystemClock.elapsedRealtimeNanos() - lastKnownLocation.getElapsedRealtimeNanos()) / 1000000000; //in ms

            Float currentVelocity = distanceTravelled / timePassed;
            if (currentVelocity.isNaN()) {
                currentVelocity = 0f;
                Log.e(TAG, "NaN: "+distanceTravelled+", "+timePassed);
            }
            velocities.add(currentVelocity);


            Log.e(TAG, "GPS: "+currentVelocity+"m/s ("+(currentVelocity*2.23694)+"mph)");

            MainActivity.context.setLocationText(
                    "lat:"+newLocation.getLatitude()+"\nlong:"+newLocation.getLongitude(),
                    "time :"+timePassed+"s\nGPS: "+currentVelocity+"m/s ("+Math.round(currentVelocity*2.23694*100)/100+"mph)"+"\n\nenergy: "+(currentVelocity/31.2928)
            );
        }
        else {
            MainActivity.context.setLocationText(
                    "lat:"+newLocation.getLatitude()+"\nlong:"+newLocation.getLongitude(),
                    "no data on velocity"
            );
        }

        lastKnownLocation = newLocation;
    }

}