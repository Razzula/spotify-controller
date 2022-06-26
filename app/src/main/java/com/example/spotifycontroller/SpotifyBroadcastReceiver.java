package com.example.spotifycontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SpotifyBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (action.equals("com.spotify.music.metadatachanged")) {
            String trackId = intent.getStringExtra("id");
            //String artistName = intent.getStringExtra("artist");
            //String albumName = intent.getStringExtra("album");
            String trackName = intent.getStringExtra("track");
            int trackLengthInSec = intent.getIntExtra("length", 0);

            MainService.context.onMetadataChange(trackId, trackLengthInSec, trackName);
        }
        else if (action.equals("com.spotify.music.playbackstatechanged")) {
            boolean playing = intent.getBooleanExtra("playing", false);
            int positionInMs = intent.getIntExtra("playbackPosition", 0);

            MainService.context.onPlaybackStateChange(playing, positionInMs);
        }
    }

}