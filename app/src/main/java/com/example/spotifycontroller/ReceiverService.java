package com.example.spotifycontroller;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class ReceiverService extends Service {
    static final class BroadcastTypes {
        static final String SPOTIFY_PACKAGE = "com.spotify.music";
        static final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";
        static final String QUEUE_CHANGED = SPOTIFY_PACKAGE + ".queuechanged";
        static final String METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged";
    }

    private static BroadcastReceiver receiver;

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        Log.e("ReceiverService", "Service created");
        registerSpotifyReceiver();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        receiver = null;
    }

    private void registerSpotifyReceiver()
    {
        receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String action = intent.getAction();
                if (action.equals(BroadcastTypes.PLAYBACK_STATE_CHANGED)) {
                    boolean playing = intent.getBooleanExtra("playing", false);
                    int positionInMs = intent.getIntExtra("playbackPosition", 0);

                    MainActivity.onPlaybackStateChange(playing, positionInMs);
                }

                else if (action.equals(BroadcastTypes.METADATA_CHANGED)) {
                    String trackId = intent.getStringExtra("id");
                    String artistName = intent.getStringExtra("artist");
                    String albumName = intent.getStringExtra("album");
                    String trackName = intent.getStringExtra("track");
                    int trackLengthInSec = intent.getIntExtra("length", 0);

                    MainActivity.onMetadataChange(trackId, trackLengthInSec, trackName);
                }
            }
        };

        final String SPOTIFY_PACKAGE = "com.spotify.music";
        final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.spotify.music.playbackstatechanged");
        filter.addAction("com.spotify.music.metadatachanged");

        registerReceiver(receiver, filter);
        Log.e("ReceiverService", "Service running");
    }
}
