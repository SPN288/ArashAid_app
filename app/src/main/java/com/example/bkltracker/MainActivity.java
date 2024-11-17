package com.example.bkltracker;
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private EditText mobileNumberEditText;
    private TextView sensorDataTextView;
    private Button toggleButton, editSaveButton;

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private boolean isSendingData = false;

    private float accelX, accelY, accelZ;
    private float gyroX, gyroY, gyroZ;
    private double latitude, longitude, speed;

    private String mobileNumber;
    private Handler handler = new Handler();
    private Runnable dataSender;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mobileNumberEditText = findViewById(R.id.mobileNumberEditText);
        sensorDataTextView = findViewById(R.id.sensorDataTextView);
        toggleButton = findViewById(R.id.toggleButton);
        editSaveButton = findViewById(R.id.editSaveButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
        mobileNumber = sharedPreferences.getString("mobileNumber", "");
        mobileNumberEditText.setText(mobileNumber);
        mobileNumberEditText.setEnabled(false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        }

        registerSensors();

        toggleButton.setOnClickListener(v -> toggleDataSending());
        editSaveButton.setOnClickListener(v -> toggleEditSaveNumber());
    }

    private void registerSensors() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void toggleDataSending() {
        if (isSendingData) {
            stopSendingData();
            toggleButton.setText("Start Sending Data");
            toggleButton.setBackgroundColor(Color.GREEN);
        } else {
            mobileNumber = mobileNumberEditText.getText().toString();
            if (!mobileNumber.isEmpty()) {
                startSendingData();
                toggleButton.setText("Stop Sending Data");
                toggleButton.setBackgroundColor(Color.RED);
            }
        }
    }

    private void toggleEditSaveNumber() {
        if (mobileNumberEditText.isEnabled()) {
            // Save the edited number
            mobileNumberEditText.setEnabled(false);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("mobileNumber", mobileNumberEditText.getText().toString());
            editor.apply();
            editSaveButton.setText("Edit");
            editSaveButton.setBackgroundColor(Color.GREEN);
        } else {
            // Enable editing
            mobileNumberEditText.setEnabled(true);
            editSaveButton.setText("Save");
            editSaveButton.setBackgroundColor(Color.RED);
        }
    }

    private void startSendingData() {
        isSendingData = true;
        dataSender = new Runnable() {
            @Override
            public void run() {
                sendDataToServer();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(dataSender);
    }

    private void stopSendingData() {
        isSendingData = false;
        handler.removeCallbacks(dataSender);
    }


    private void sendDataToServer() {
        new Thread(() -> {
            try {
                // Constructing JSON data
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("mobileNumber", mobileNumber);
                jsonObject.put("latitude", latitude);
                jsonObject.put("longitude", longitude);
                jsonObject.put("speed", speed);
                jsonObject.put("accelX", accelX);
                jsonObject.put("accelY", accelY);
                jsonObject.put("accelZ", accelZ);
                jsonObject.put("gyroX", gyroX);
                jsonObject.put("gyroY", gyroY);
                jsonObject.put("gyroZ", gyroZ);

                Log.d("DataTransmission", "Sending JSON: " + jsonObject.toString()); // Log the data here

                URL url = new URL("https://accident-cisl.onrender.com/upload_sensor_data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                OutputStream os = conn.getOutputStream();
                byte[] input = jsonObject.toString().getBytes("utf-8");
                os.write(input, 0, input.length);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Log.d("DataTransmission", "Data sent successfully");
                }

                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }




    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accelX = event.values[0];
                accelY = event.values[1];
                accelZ = event.values[2];
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroX = event.values[0];
                gyroY = event.values[1];
                gyroZ = event.values[2];
                break;
        }

        sensorDataTextView.setText("Accel:"+"\n"+"X=" + accelX +"\n"+ "Y=" + accelY +"\n"+ "Z=" + accelZ + "\n"
                + "Gyro:"+"\n"+"X=" + gyroX +"\n"+ "Y=" + gyroY +"\n"+ "Z=" + gyroZ + "\n"
                + "Location:"+"\n"+"Lat=" + latitude +"\n"+ "Long=" + longitude + "\n"
                + "Speed: " + speed);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        speed = location.getSpeed();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}

