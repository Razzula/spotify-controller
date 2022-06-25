package com.example.spotifycontroller;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.spotify.android.appremote.api.PlayerApi;

import java.util.ArrayList;

public class MainWorker extends Worker {

    private static String TAG = "MainWorker";
    public static MainWorker context;

    PlayerApi playerApi;
    private BroadcastReceiver broadcastReceiver;

    int currentTrackLength = 0;
    ArrayList<Float> velocities;

    Intent broadcastReceiverService;

    actionAtEndOfTrack endOfTrackAction;
    actionTowardsEndOfTrack startLocationTracking;

    FusedLocationProviderClient fusedLocationProviderClient;
    Location lastKnownLocation;

    boolean queued = false;
    public static boolean active = false;
    private boolean repeatEnabled;

    private int fadeDuration = 0;

    ArrayList<MainActivity.Track> playlist;
    ArrayList<MainActivity.Track> fullPlaylist;

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

    public MainWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = this;

        playerApi = MainActivity.playerApi;
    }

    @Override
    public Result doWork() {
        Log.e(TAG, "Worker started");

        fullPlaylist = MainActivity.context.playlist;
        playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
        repeatEnabled = MainActivity.context.getRepeatStatus();

        // BROADCAST RECIEVER
        broadcastReceiver = new SpotifyBroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.spotify.music.playbackstatechanged");
        filter.addAction("com.spotify.music.metadatachanged");

        getApplicationContext().registerReceiver(broadcastReceiver, filter);
        //TODO, ensure Spotify is setup to broadcast

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
        return Result.success();
    }

    @Override
    public void onStopped() {
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
        super.onStopped();

        MainActivity.context.setSwitch(false);

        Log.e(TAG, "Worker stopped");
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
                    sleep(100);
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

        if (playlist.size() == 0) {
            Log.d(TAG, "Playlist empty");
            onStopped();
        }
        else if (playlist.size() == 1) {
            if (repeatEnabled) {
                playerApi.queue("spotify:track:" + playlist.get(0).id); //queue
                Log.d(TAG, "Playlist looped");
                playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
            }
            else {
                playerApi.play("spotify:track:" + playlist.get(0).id); //play //TODO, delay this to prevent abrupt ending of current song
                playlist.remove(0);
            }
        }
        else {

            float currentVelocity = 0;
            int i;
            for (i = 0; i < velocities.size(); i++) {
                currentVelocity += velocities.get(i);
            }
            currentVelocity /= i; //TODO, use modal average not mean
            Log.e(TAG, "Average Speed: " + currentVelocity + "m/s");

            float currentEnergy = currentVelocity / 31.2928f; //calculates energy as a % of car speed out of 70mph
            /*//FOR DEBUG
            Random rand = new Random();
            currentEnergy = rand.nextFloat();*/
            Log.e(TAG, "Energy: " + currentEnergy);


            // find song based off of energy
            //TODO, don't allow current track to be queued
            float minDelta = 1;
            MainActivity.Track nextTrack = new MainActivity.Track("", "");
            for (i = 0; i < playlist.size(); i++) {
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

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            return;
        }

        try {
            CurrentLocationRequest currentLocationRequest = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(1000)
                    .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    .build();

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