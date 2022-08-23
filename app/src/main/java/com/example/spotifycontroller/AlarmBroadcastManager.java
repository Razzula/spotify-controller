package com.example.spotifycontroller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class AlarmBroadcastManager extends BroadcastReceiver {

    private MainService service;
    private AlarmManager alarmManager;

    AlarmBroadcastManager(MainService service) {
        this.service = service;
        alarmManager = (AlarmManager) service.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotifycontroller::respondToBroadcasts");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);

        String action = intent.getAction();
        Log.e("", action+" called");
        if (action.equals("com.example.spotifycontroller.endOfTrack")) {
            service.stopLocationUpdates();
            service.queueNextTrack();
        }
        else if (action.equals("com.example.spotifycontroller.startLocationUpdates")) {
            service.startLocationUpdates();
        }

        wakeLock.release();
    }

    public void setAlarm(String intentAction, long timeUntilAlarm)
    {
        if (timeUntilAlarm < 0) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("", "cannot schedule exact alarms");
                return;
            }
        }

        PendingIntent sender = PendingIntent.getBroadcast(service, 0, new Intent("com.example.spotifycontroller." + intentAction), 0);
        //alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeUntilAlarm, sender);
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + timeUntilAlarm, sender);
        alarmManager.setAlarmClock(info, sender);

        Log.e("", intentAction+" in "+timeUntilAlarm);
    }

    public void cancelAlarm(String intentAction)
    {
        PendingIntent sender = PendingIntent.getBroadcast(service, 0, new Intent("com.example.spotifycontroller." + intentAction), 0);
        alarmManager.cancel(sender);

        Log.e("", intentAction+" cancelled");
    }

}