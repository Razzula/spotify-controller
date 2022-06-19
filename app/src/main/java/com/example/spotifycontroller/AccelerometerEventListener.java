package com.example.spotifycontroller;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.TextView;

public class AccelerometerEventListener implements SensorEventListener {

    double calibration = Double.NaN;
    private SensorManager sensorManager;
    private boolean color = false;
    private TextView view;
    private long lastUpdate;

    float appliedAcceleration = 0;
    float currentAcceleration = 0;
    float velocity = 0;

    public AccelerometerEventListener(Context context) {
        lastUpdate = System.currentTimeMillis();
        sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        double x = sensorEvent.values[0];
        double y = sensorEvent.values[1];
        double z = sensorEvent.values[2];
        double a = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
        if (calibration == Double.NaN)
            calibration = a;
        else {
            updateVelocity();
            currentAcceleration = (float) a;
        }

    }

    private void updateVelocity() {

        long timeNow = System.currentTimeMillis();
        long timeDelta = timeNow - lastUpdate;
        lastUpdate = timeNow;

        float deltaVelocity = appliedAcceleration * timeDelta / 1000;
        appliedAcceleration = currentAcceleration;

        velocity = deltaVelocity;
        //Log.e("", "ACC: "+velocity);

    }

    public void startListening() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void stopListening() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public float getVelocity() {
        return velocity;
    }
}