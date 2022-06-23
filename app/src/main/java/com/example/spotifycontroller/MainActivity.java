package com.example.spotifycontroller;

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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

// for Google Maps Location Services
// for Spotify SDK
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.PlayerApi;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.ImageUri;
// for Spotify Web API
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.util.Log;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.lang.Thread;
import java.util.Arrays;
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
    boolean repeat = false;

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

        View selectedPlaylistView = findViewById(R.id.selectedPlaylist);
        TextView itemName = selectedPlaylistView.findViewById(R.id.textName);
        TextView itemDesc = selectedPlaylistView.findViewById(R.id.textDescription);
        TextView itemInfo = selectedPlaylistView.findViewById(R.id.textInfo);
        ImageView itemImg = selectedPlaylistView.findViewById(R.id.imageView);

        itemName.setText("No Playlist Selected");
        itemDesc.setText("");
        itemInfo.setText("");
        itemImg.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
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

    //TEMP
    public void setLocationText(String location, String speed) {
        TextView textLocation = findViewById(R.id.location);
        TextView textSpeed = findViewById(R.id.data);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textLocation.setText(location);
                textSpeed.setText(speed);
            }
        });
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
            for (int i=0; i<playlists.length(); i++) { // for each playlist in list
                JSONObject playlist = playlists.getJSONObject(i);

                String id = playlist.getString("id");
                String name = playlist.getString("name");
                String description = playlist.getString("description");;
                int numberOfTracks = Integer.parseInt(playlist.getJSONObject("tracks").getString("total"));

                Bitmap newImg = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888);

                String url = playlist.getJSONArray("images").getJSONObject(0).getString("url").split("/")[4];
                //Log.e("", url);
                if (url.length() > 40) { //mosaic

                    ArrayList<Bitmap> images = new ArrayList<>();
                    ArrayList<ImageUri> uris = new ArrayList<>();

                    for (int n=0; n<4; n+=1) {
                        String tempurl = String.valueOf(Arrays.copyOfRange(url.toCharArray(), n * 40, (n + 1) * 40));
                        uris.add(new ImageUri("spotify:image:" + tempurl));
                    }

                        mSpotifyAppRemote.getImagesApi().getImage(uris.get(0)).setResultCallback(
                                bitmap -> {
                                    images.add(Bitmap.createScaledBitmap(bitmap, 144, 144, false));

                                    mSpotifyAppRemote.getImagesApi().getImage(uris.get(1)).setResultCallback(
                                            bitmap2 -> {
                                                images.add(Bitmap.createScaledBitmap(bitmap2, 144, 144, false));

                                                mSpotifyAppRemote.getImagesApi().getImage(uris.get(2)).setResultCallback(
                                                        bitmap3 -> {
                                                            images.add(Bitmap.createScaledBitmap(bitmap3, 144, 144, false));

                                                            mSpotifyAppRemote.getImagesApi().getImage(uris.get(3)).setResultCallback(
                                                                    bitmap4 -> {
                                                                        images.add(Bitmap.createScaledBitmap(bitmap4, 144, 144, false));

                                                                        for (int x = 0; x < 144; x++) {
                                                                            for (int y = 0; y < 144; y++) {
                                                                                int pixelColour = images.get(0).getPixel(x, y);
                                                                                newImg.setPixel(x, y, pixelColour);

                                                                                pixelColour = images.get(1).getPixel(x, y);
                                                                                newImg.setPixel(x+144, y, pixelColour);

                                                                                pixelColour = images.get(2).getPixel(x, y);
                                                                                newImg.setPixel(x, y+144, pixelColour);

                                                                                pixelColour = images.get(3).getPixel(x, y);
                                                                                newImg.setPixel(x+144, y+144, pixelColour);

                                                                            }
                                                                        }
                                                                        this.playlists.add(new Playlist(id, name, description, numberOfTracks, newImg));
                                                                        playlistsRecyclerViewAdapter.notifyItemInserted(0);

                                                                    });
                                                        });
                                            });
                                });
                }
                else { //single image
                    ImageUri imageUri = new ImageUri("spotify:image:"+url);

                    mSpotifyAppRemote
                            .getImagesApi()
                            .getImage(imageUri, Image.Dimension.THUMBNAIL)
                            .setResultCallback(
                                    bitmap -> {
                                        this.playlists.add(new Playlist(id, name, description, numberOfTracks, bitmap));
                                    })
                            .setErrorCallback(
                                    error -> {
                                        this.playlists.add(new Playlist(id, name, description, numberOfTracks));
                                    });
                }

            }
            playlistsRecyclerViewAdapter.notifyItemInserted(0);

            if (playlists.length() <= 0) {
                findViewById(R.id.textNoPlaylists).setVisibility(View.VISIBLE);
            }

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

    // UI

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuSettings:
                Intent newIntent = new Intent(MainActivity.this, SettingsActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(newIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    View previouslySelectedPlaylist;
    public void playlistsSelected(View view) {

        Object id = view.getTag();
        if (id == null) {
            return;
        }
        selectedPlaylistID = String.valueOf(id);

        TextView textView = view.findViewById(R.id.textName);
        View selectedPlaylistView = findViewById(R.id.selectedPlaylist);

        Switch toggle = findViewById(R.id.switchEnable);

        if (toggle.isChecked()) {
            return;
        }

        // current selection views
        TextView itemName = selectedPlaylistView.findViewById(R.id.textName);
        TextView itemDesc = selectedPlaylistView.findViewById(R.id.textDescription);
        TextView itemInfo = selectedPlaylistView.findViewById(R.id.textInfo);
        ImageView itemImg = selectedPlaylistView.findViewById(R.id.imageView);

        if (previouslySelectedPlaylist != null) {
            previouslySelectedPlaylist.setBackgroundColor(0x00000000); // deselect previous selection
            selectedPlaylistView.setBackgroundColor(0x00000000);

            itemName.setText("No Playlist Selected");
            itemDesc.setText("");
            itemInfo.setText("");
            itemImg.setVisibility(View.GONE);
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
        selectedPlaylistView.setBackgroundColor(0x8800AA00);

        previouslySelectedPlaylist = view;

        //get playlist data
        for (int i=0; i<playlists.size(); i++) {
            if (playlists.get(i).getID().equals(selectedPlaylistID)) {
                Playlist selectedPlaylist = playlists.get(i);

                itemName.setText(selectedPlaylist.getName());
                itemDesc.setText(selectedPlaylist.getDescription());
                itemInfo.setText(String.valueOf(selectedPlaylist.getNumberOfTracks())+" tracks");
                if (selectedPlaylist.getImage() != null) {
                    itemImg.setImageBitmap(selectedPlaylist.getImage());
                }

                itemImg.setVisibility(View.VISIBLE);
                itemImg.setTag(selectedPlaylistID);

                break;
            }
        }

    }

    public void togglePlaylistList (View view) {
        RecyclerView recyclerView = findViewById(R.id.recyclerPlaylists);
        View selectedPlaylistView = findViewById(R.id.selectedPlaylist);

        ImageButton btn = (ImageButton) view;
        if (recyclerView.getVisibility() == View.VISIBLE) { // show specific
            recyclerView.setVisibility(View.GONE);
            selectedPlaylistView.setVisibility(View.VISIBLE);
            btn.setImageResource(android.R.drawable.arrow_down_float);

            findViewById(R.id.textNoPlaylists).setVisibility(View.GONE);
        }
        else { // show all
            recyclerView.setVisibility(View.VISIBLE);
            selectedPlaylistView.setVisibility(View.GONE);
            btn.setImageResource(android.R.drawable.arrow_up_float);

            if (playlists.size() <= 0) {
                findViewById(R.id.textNoPlaylists).setVisibility(View.VISIBLE);
            }
        }
    }

    public void goToPlaylist(View view) {

        String uri = String.valueOf(view.getTag());

        PackageManager pm = getPackageManager();
        boolean isSpotifyInstalled;
        try {
            pm.getPackageInfo("com.spotify.music", 0);
            isSpotifyInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            isSpotifyInstalled = false;
        }

        if (isSpotifyInstalled) {
            launchSpotify("playlist:"+uri);
        }
        else {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/playlist/"+uri));
            startActivity(browserIntent);
        }

    }

    public void toggleRepeat (View view) {
        if (repeat) { // disable
            view.setBackgroundColor(0x00000000);
        }
        else { // enable
            view.setBackgroundColor(0x8800AA00);
        }
        repeat = !repeat;
    }

    public void goToSpotify(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/collection/playlists"));
        startActivity(browserIntent);
    }

    private void launchSpotify(String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("spotify:" + uri));
        intent.putExtra(Intent.EXTRA_REFERRER,
                Uri.parse("android-app://" + context.getPackageName()));
        startActivity(intent);
    }
}