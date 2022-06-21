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

    double[] calibration = {0,0,0};
    private SensorManager sensorManager;
    private long lastUpdate;

    int count = 0;

    //float appliedAcceleration = 0;
    //float currentAcceleration = 0;
    //float[] acceleration = {0,0,0};
    float[] velocity = {0,0,0};
    double speed;

    double[] appliedAcceleration = {0, 0, 0};
    double[] currentAcceleration;

    public AccelerometerEventListener(Context context) {
        lastUpdate = System.currentTimeMillis();
        sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        double x = sensorEvent.values[0];
        double y = sensorEvent.values[1];
        double z = sensorEvent.values[2];

        //CALIBRATE
        if (count < 1000) {
            double[] acceleration = {x, y, z};
            for (int i=0; i<3; i++) {
                calibration[i] += acceleration[i];
            }
            count += 1;
        }

        //CALCULATE
        else {

            if (currentAcceleration != null) {
                if (appliedAcceleration != null) {
                    long currentTime = System.currentTimeMillis();
                    for (int i = 0; i < 3; i++) {
                        velocity[i] += (appliedAcceleration[i] - (calibration[i] / count)) * (currentTime - lastUpdate) / 1000;
                    }
                    lastUpdate = currentTime;

                    speed = Math.sqrt(Math.pow(velocity[0], 2) + Math.pow(velocity[1], 2) + Math.pow(velocity[2], 2));

                    Log.e("", "" + speed);
                }
                appliedAcceleration = currentAcceleration;
            }
            double[] acceleration = {x,y,z};
            currentAcceleration = acceleration;
        }

    }

    public void startListening() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    protected void stopListening() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public float getVelocity() {
        return (float) speed;
    }
}