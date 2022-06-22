package com.example.spotifycontroller;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.work.WorkManager;

public class StopperService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        if (MainWorker.context != null) {
            MainWorker.context.onStopped();
        }
    }
}
