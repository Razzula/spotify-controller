package com.example.spotifycontroller;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void onStop() {
        overridePendingTransition(0, 0);
        super.onStop();
    }

    public void backToMain(View view) {
        finish();
        overridePendingTransition(0, 0);
    }
}

//TODO
// - Images:  Always, WiFi Only, Never
// - Allow Minor Repetition ,, Set Tolerance
// - Disable GPS Permissions
// - Disconnect Spotify Account
// - Location Accuracy