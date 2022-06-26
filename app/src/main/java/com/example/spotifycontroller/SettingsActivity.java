package com.example.spotifycontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;

public class SettingsActivity extends AppCompatActivity {

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            getPreferenceScreen().findPreference("repetitionTolerance").setEnabled(prefs.getBoolean("allowMinorRepetition", false));

            //Setup a shared preference listener for trackrep
            SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals("allowMinorRepetition")) {
                        getPreferenceScreen().findPreference("repetitionTolerance").setEnabled(prefs.getBoolean("allowMinorRepetition", false));
                    }
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);

            // permissions
            findPreference("permissions").setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", "com.example.spotifycontroller", null);
                intent.setData(uri);
                startActivity(intent);
                return true;
            });

            // disconnect
            findPreference("disconnectSpotify").setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.spotify.com/account/apps/"));
                startActivity(browserIntent);
                getActivity().finishAffinity(); //close app, to ensure disconnection has effect
                return true;
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportActionBar().setTitle("Settings");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        return true;
    }

    @Override
    protected void onStop() {
        overridePendingTransition(0, 0);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(0, 0);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

//TODO, set default values