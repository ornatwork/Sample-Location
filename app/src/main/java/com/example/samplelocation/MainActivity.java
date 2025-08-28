package com.example.samplelocation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 123;
    private static final int REQUEST_GPS_SETTINGS = 456;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Existing label
    private TextView textView;
    // NEW: compass view (make sure you added it in activity_main.xml)
    private CompassView compassView;
    private TextView directionText; // NEW

    // NEW: compass sensor fields
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] rotationMatrixAdj = new float[9];
    private final float[] orientation = new float[3];
    private Location lastLocation; // for declination correction

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.locationText);
        // Requires <com.example.samplelocation.CompassView android:id="@+id/compassView" .../> in XML
        compassView = findViewById(R.id.compassView);
        directionText = findViewById(R.id.directionText); // NEW

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Init sensors (rotation vector gives best heading)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        // Initialize location callback (kept your logic; added altitude in feet + save lastLocation)
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        lastLocation = location; // used to compute true heading

                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();

                        // Altitude in feet (meters -> feet). If altitude unavailable, show "—"
                        String altitudeFeetStr;
                        if (location.hasAltitude()) {
                            long feet = Math.round(location.getAltitude() * 3.28084d);
                            altitudeFeetStr = feet + " ft";
                        } else {
                            altitudeFeetStr = "—";
                        }

                        TextView locationText = findViewById(R.id.locationText);
                        locationText.setText(
                          "Latitude: " + latitude +
                          "\nLongitude: " + longitude +
                          "\nAltitude: " + altitudeFeetStr
                        );
                        TextView clickText = findViewById(R.id.clickText);
                        clickText.setText("http://maps.google.com/maps?q=loc:" + latitude + "," + longitude);
                    }
                }
            }
        };

        requestLocationPermissionAndFetchLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestLocationPermissionAndFetchLocation();

        // Register compass sensor updates
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(rotationListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location & sensor updates to save battery
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(rotationListener);
        }
    }

    private void requestLocationPermissionAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_LOCATION_PERMISSION
            );
        } else {
            checkAndFetchLocation();
        }
    }

    private void checkAndFetchLocation() {
        if (isGPSEnabled()) {
            startLocationUpdates();
        } else {
            showToast("GPS is disabled. Please enable it.");
            openGPSSettings();
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10_000)
                .setFastestInterval(5_000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void openGPSSettings() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(intent, REQUEST_GPS_SETTINGS);
    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_GPS_SETTINGS) {
            checkAndFetchLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkAndFetchLocation();
            } else {
                showToast("Please Provide Location Access.");
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String toCardinal(float deg) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        int idx = Math.round(deg / 45f) % 8;
        return dirs[idx];
    }

    // ---- Compass listener: computes heading and updates the dial ----
    private final SensorEventListener rotationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Remap if needed; here we keep identity (portrait, Z up)
            SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    rotationMatrixAdj
            );

            SensorManager.getOrientation(rotationMatrixAdj, orientation);
            float azimuthRad = orientation[0];
            float azimuthDeg = (float) Math.toDegrees(azimuthRad);
            if (azimuthDeg < 0) azimuthDeg += 360f;

            // Convert to TRUE heading using declination when we have a fix
            float trueHeading = azimuthDeg;
            if (lastLocation != null) {
                GeomagneticField field = new GeomagneticField(
                        (float) lastLocation.getLatitude(),
                        (float) lastLocation.getLongitude(),
                        (float) (lastLocation.hasAltitude() ? lastLocation.getAltitude() : 0f),
                        System.currentTimeMillis()
                );
                trueHeading = azimuthDeg + field.getDeclination();
                if (trueHeading < 0) trueHeading += 360f;
                if (trueHeading >= 360f) trueHeading -= 360f;
            }

            if (compassView != null) {
                compassView.setHeading(trueHeading);
            }
            if (directionText != null) {
                String cardinal = toCardinal(trueHeading);
                directionText.setText(String.format("Direction: %.0f° (%s)", trueHeading, cardinal));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { /* no-op */ }
    };
}
