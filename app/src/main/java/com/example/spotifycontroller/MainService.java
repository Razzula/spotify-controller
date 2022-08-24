package com.example.spotifycontroller;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.PlayerApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.UserApi;
import com.spotify.protocol.types.Capabilities;
import com.spotify.protocol.types.PlayerState;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class MainService extends Service {

    private static final String TAG = "MainService";
    private static final String CLIENT_ID = "c3ea15ea37eb4121a64ee8af3521f832";
    private static final String REDIRECT_URI = "com.example.spotifycontroller://callback";
    public static MainService context;

    //PowerManager.WakeLock wakeLock;

    SpotifyAppRemote mSpotifyAppRemote;
    PlayerApi playerApi;
    private BroadcastReceiver spotifyBroadcastReceiver;
    private AlarmBroadcastManager alarmBroadcastManager;

    int currentTrackLength = 0;
    ArrayList<Float> velocities;

    boolean valid = false;

    Handler getLocationCaller = new Handler();

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
    String playlistURI;

    boolean metaReceived = false;

    PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "Service created");

        this.context = this; //TODO, use sharedPreferences, not static variables
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        //START
        if (intent.getAction().equals("START")) {

            Log.e(TAG, "Service started");
            writeToFile("\n ------------------------------------------------\n");

            //data
            fullPlaylist = MainActivity.context.playlist;
            playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
            recentlyPlayed = new LinkedList();
            playlistURI = MainActivity.context.selectedPlaylistID;

            // PREFERENCES LISTENER
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            setMinorRepeat(prefs);
            repeatEnabled = prefs.getBoolean("repeatEnabled", false);
            updateLocationPriority(prefs);

            SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs1, key) -> {
                switch (key) {
                    case "allowMinorRepetition":
                    case "repetitionTolerance":
                        setMinorRepeat(prefs1);
                        break;
                    case "repeatEnabled":
                        repeatEnabled = prefs1.getBoolean("repeatEnabled", false);
                        break;
                    case "locationAccuracy":
                        updateLocationPriority(prefs1);
                        break;
                }

                if (repeatEnabled && minorRepeatEnabled) {
                    playlist = (ArrayList<MainActivity.Track>) fullPlaylist.clone();
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);

            connectToSpotifyApp();
            return START_STICKY;
        }
        // STOP
        else if (intent.getAction().equals("HALT")) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // BECOME FOREGROUND SERVICE
        else if (intent.getAction().equals("TO_FORE")) {
            // NOTIFICATION
            Intent stopSelf = new Intent(this, MainService.class);
            stopSelf.setAction("HALT");
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, stopSelf,PendingIntent.FLAG_CANCEL_CURRENT);

            createNotificationChannel();

            Notification notification =
                    new Notification.Builder(this, "foregroundAlert")
                            .setSmallIcon(R.drawable.logo)
                            .setContentTitle("Controller is still active")
                            .setContentText("Tap to stop.")
                            .setOngoing(true)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1234, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            }
            else {
                startForeground(1234, notification);
            }

            return START_STICKY;
        }
        // RETURN TO BACKGROUND
        else if (intent.getAction().equals("TO_BACK")) {
            stopForeground(true);
            /*if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }*/ //TEMP
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel("foregroundAlert", name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void beginProcess() {

        playerApi = mSpotifyAppRemote.getPlayerApi();

        playerApi.getCrossfadeState()
                 .setResultCallback(crossfadeState -> fadeDuration = crossfadeState.duration);

        active = true;

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotifycontroller::getLocationUpdates");

        // SPOTIFY BROADCAST RECEIVER
        spotifyBroadcastReceiver = new SpotifyBroadcastReceiver(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.spotify.music.playbackstatechanged");
        filter.addAction("com.spotify.music.metadatachanged");

        getApplicationContext().registerReceiver(spotifyBroadcastReceiver, filter);

        // ALARM BROADCAST RECEIVER
        alarmBroadcastManager = new AlarmBroadcastManager(this);

        filter = new IntentFilter();
        filter.addAction("com.example.spotifycontroller.endOfTrack");
        filter.addAction("com.example.spotifycontroller.startLocationUpdates");
        getApplicationContext().registerReceiver(alarmBroadcastManager, filter);

        // INITIALISE QUEUE
        playerApi.getPlayerState().setResultCallback(playerState -> {
            if (playerState.isPaused || playerState.track == null) {
                playerApi.play("spotify:playlist:"+playlistURI);
            }

            // make sure receiver is receiving, if not, point to Spotify settings
            getLocationCaller.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        sleep(1000);
                    } catch (InterruptedException ignored) {
                    }

                    if (!isMetaReceived) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.context);
                        builder.setMessage("Spotify doesn't appear to be broadcasting. Without this, the app cannot function properly.\n\nPlease enable 'Device Broadcast Status' in Spotify's settings.")
                                .setTitle("Uh oh")
                                .setPositiveButton("Close", (dialog, id) -> {});

                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
            });

        });
    }

    boolean isMetaReceived = false;

    private void updateLocationPriority(SharedPreferences prefs) {
        String priority = prefs.getString("locationAccuracy", "Balance Location Accuracy and Battery Life");
        switch (priority) {
            case "Favour Location Accuracy":
                LocationPriority = Priority.PRIORITY_HIGH_ACCURACY;
                break;
            case "Balance Location Accuracy and Battery Life":
                LocationPriority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
                break;
            case "Favour Battery Life":
                LocationPriority = Priority.PRIORITY_LOW_POWER;
                break;
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        try {
            getApplicationContext().unregisterReceiver(spotifyBroadcastReceiver);
        }
        catch (IllegalArgumentException ignored) {}
        try {
            getApplicationContext().unregisterReceiver(alarmBroadcastManager);
        }
        catch (IllegalArgumentException ignored) {}

        stopLocationUpdates();

        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        }

        MainActivity.context.setSwitch(false);

        Log.e(TAG, "Service stopped");
        super.onDestroy();
    }

    private void setMinorRepeat(SharedPreferences prefs) {
        minorRepeatEnabled = prefs.getBoolean("allowMinorRepetition", false);
        minorRepeatRate = (int) Math.ceil(prefs.getInt("repetitionTolerance", 1) * fullPlaylist.size() / 100f);
        if (minorRepeatRate >= playlist.size()) {
            minorRepeatRate = playlist.size() - 1;
        }
    }

    // INTERACTION WITH ANDROID SDK

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
                        beginProcess();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e(TAG, throwable.getMessage(), throwable);

                        // Handle errors here
                        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                        builder.setMessage(R.string.dialogue_appRemoteFail)
                                .setTitle(R.string.dialogue_appRemoteFail_T);

                        stopSelf();
                    }
                });
    }

    public void onMetadataChange(String trackID, int trackLength, String trackName) {

        isMetaReceived = true;

        currentTrackLength = trackLength;
        queued = false;

        Log.e(TAG, "META CHANGED");
        Log.e(TAG, "Playing " + trackName);
        writeToFile("Playing " + trackName + "\n");

        stopLocationUpdates();

        // quickly pause then resume track, to ensure playback is caught after meta
        playerApi.pause();
        Runnable r =  new Thread(() -> {
            playerApi.resume();
            metaReceived = true;
        });
        new Handler().postDelayed(r, 100);

        // make sure receiver is receiving by checking trackID is correct, if not, point to Spotify settings
        playerApi.getPlayerState().setResultCallback(playerState -> {
            if (!playerState.track.uri.equals(trackID)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.context);
                builder.setMessage("Spotify doesn't appear to be broadcasting. Without this, the app cannot function properly.\n\nPlease enable 'Device Broadcast Status' in Spotify's settings.")
                        .setTitle("Uh oh")
                        .setPositiveButton("Close", (dialog, id) -> {});

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    public void onPlaybackStateChange(boolean playing, int playbackPos) {

        if (!metaReceived) {
            return;
        }

        if (playing) {
            Log.e(TAG, "PLAYBACK STARTED");
            long timeToWait;

            // SET ALARM TO QUEUE NEXT TRACK AT END OF CURRENT TRACK
            timeToWait = currentTrackLength - playbackPos - fadeDuration - 5000; // time (in ms) from end of track

            alarmBroadcastManager.cancelAlarm("endOfTrack"); //interrupt existing
            if (timeToWait >= 0) {
                alarmBroadcastManager.setAlarm("endOfTrack", timeToWait);
            }

            // SET ALARM TO BEGIN LOCATION UPDATES
            timeToWait = currentTrackLength - playbackPos - 60000; // time (in ms) until 60s from end of track

            getLocationCaller.removeCallbacks(getLocation); //interrupt existing
            alarmBroadcastManager.cancelAlarm("startLocationUpdates");

            velocities = new ArrayList<>();
            if (timeToWait <= 0) {
                startLocationUpdates();
            }
            else {
                alarmBroadcastManager.setAlarm("startLocationUpdates", timeToWait);
            }

        } else {
            Log.e(TAG, "PLAYBACK STOPPED");

            //interrupt any alarms
            alarmBroadcastManager.cancelAlarm("endOfTrack");
            getLocationCaller.removeCallbacks(getLocation);
            alarmBroadcastManager.cancelAlarm("startLocationUpdates");
            stopLocationUpdates();
        }
    }

    /*public void queueNextTrack() {

    }*/

    public void queueNextTrack() {

        if (queued) {
            return;
        }
        queued = true;

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
                // wait until end of song, then play final track
                new Thread(() -> {
                    try {
                        sleep(2000 + fadeDuration);
                    }
                    catch (InterruptedException ignored) {}
                    playerApi.play("spotify:track:" + nextTrackID);
                }).start();

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
            writeToFile("Average Speed: " + currentVelocity + "m/s \n");

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
            writeToFile("QUEUED " + nextTrack.name + "\n");
        }

        metaReceived = false;
    };

    public void startLocationUpdates() {
        wakeLock.acquire(66*1000);
        getLocationCaller.post(getLocation);
    }

    private final Runnable getLocation = new Runnable() {
        @Override
        public void run() {
            getLocation();
            getLocationCaller.postDelayed(this, 5000);
        }
    };

    public void stopLocationUpdates() { //temp
        getLocationCaller.removeCallbacks(getLocation);
        if (alarmBroadcastManager != null) {
            alarmBroadcastManager.cancelAlarm("startLocationUpdates");
        }

        Log.e(TAG, "Location updates halted");
        writeToFile("Location updates halted\n");

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public void getLocation() {

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Tried to getLocation without FINE_LOCATION permission");
            return;
        }
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Tried to getLocation without FINE_LOCATION permission");
            return;
        }

        CurrentLocationRequest currentLocationRequest = new CurrentLocationRequest.Builder()
                .setPriority(LocationPriority)
                .setMaxUpdateAgeMillis(1000)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(30000)
                .build();

        try {

            fusedLocationProviderClient.getCurrentLocation(currentLocationRequest, null).addOnCompleteListener(task -> {
                Location currentLocation = task.getResult(); // get Location
                if (currentLocation != null) {
                    locationUpdated(currentLocation);
                }
                else {
                    Log.e(TAG, "Location is null");
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
            float timePassed = (SystemClock.elapsedRealtimeNanos() - lastKnownLocation.getElapsedRealtimeNanos()) / 1000000000f; //in ms

            float currentVelocity = distanceTravelled / timePassed;
            if (Float.isNaN(currentVelocity)) {
                currentVelocity = 0f;
                Log.e(TAG, "NaN: "+distanceTravelled+", "+timePassed);
            }
            velocities.add(currentVelocity);

            Log.e(TAG, "GPS: "+currentVelocity+"m/s ("+(currentVelocity*2.23694)+"mph)");
            writeToFile("GPS: "+currentVelocity+"m/s ("+(currentVelocity*2.23694)+"mph)\n");
        }
        else {
            Log.e(TAG, "Could not get location");
        }

        lastKnownLocation = newLocation;
    }

    //TEMP
    private void writeToFile(String data) {
        Context context = getApplicationContext();
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("log.txt", Context.MODE_APPEND));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

}