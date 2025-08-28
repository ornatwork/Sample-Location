package com.example.samplelocation;

import android.util.Log;
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
import android.os.Handler;
import android.os.Looper;
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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 123;
    private static final int REQUEST_GPS_SETTINGS = 456;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TextView locationText;
    private TextView clickText;
    private CompassView compassView;
    private TextView directionText;
    private TextView weatherText;

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] rotationMatrixAdj = new float[9];
    private final float[] orientation = new float[3];
    private Location lastLocation; // for declination correction (true north)
    private final OkHttpClient http = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationText = findViewById(R.id.locationText);
        clickText    = findViewById(R.id.clickText);

        compassView   = findViewById(R.id.compassView);
        directionText = findViewById(R.id.directionText);
        weatherText   = findViewById(R.id.weatherText);

        // Fused Location Provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Sensors for compass
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        // Location callback
        locationCallback = new LocationCallback() {

            double latitude = 0;
            double longitude = 0;

            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location location = locationResult.getLastLocation();
                if (location == null) return;

                lastLocation = location; // used for declination (true north)
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                // Altitude in feet (meters -> feet)
                String altitudeFeetStr = "—";
                if (location.hasAltitude()) {
                    long feet = Math.round(location.getAltitude() * 3.28084d);
                    altitudeFeetStr = feet + " ft";
                }

                // Update your original labels
                if (locationText != null) {
                    locationText.setText(
                            "Latitude: " + latitude +
                            "\nLongitude: " + longitude +
                            "\nAltitude: " + altitudeFeetStr
                    );
                }
                if (clickText != null) {
                    clickText.setText("http://maps.google.com/maps?q=loc:" + latitude + "," + longitude);
                }

                fetchWeather("https://api.openweathermap.org/data/2.5/weather?lon="
                        + longitude + "&lat=" + latitude +
                        "&appid=d1437fe63b9165a5569c09489f6c69f8&units=imperial");
           }
        };

        // Request permissions and start
        requestLocationPermissionAndFetchLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestLocationPermissionAndFetchLocation();

        // Register compass sensor updates
        if (rotationVectorSensor != null && sensorManager != null) {
            sensorManager.registerListener(rotationListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop location updates
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Stop sensor to save battery
        if (sensorManager != null) {
            sensorManager.unregisterListener(rotationListener);
        }
    }

    private void requestLocationPermissionAndFetchLocation() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
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
            boolean granted = false;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) {
                    granted = true; break;
                }
            }
            if (granted) {
                checkAndFetchLocation();
            } else {
                showToast("Please provide location access.");
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private final SensorEventListener rotationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Keep default axis mapping (portrait)
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

            // Convert to TRUE heading when we have a location (declination correction)
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

            // Drive the dial + direction label
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

    private static String toCardinal(float deg) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        int idx = Math.round(deg / 45f) % 8;
        return dirs[idx];
    }

    private void fetchWeather(String url) {
        Request req = new Request.Builder().url(url).get().build();
        Call call = http.newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postWeather("Weather: Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        postWeather("Weather: HTTP " + response.code());
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "";
                    //Log.d("HTTP", "~~~ Response body: " + body);

                    parseAndShowWeather(body);
                } catch (Exception e) {
                    postWeather("Weather: Parse error: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private void parseAndShowWeather(String json) {
        try {
            JSONObject root = new JSONObject(json);

            // If API returned error code, handle gracefully
            if (root.has("cod") && !"200".equals(String.valueOf(root.opt("cod")))) {
                String msg = root.optString("message", "unknown error");
                postWeather("Weather: API error – " + msg);
                return;
            }

            // City name
            String name = root.optString("name", "—");

            // Weather description
            JSONArray weatherArr = root.optJSONArray("weather");
            String description = "—";
            if (weatherArr != null && weatherArr.length() > 0) {
                description = ((JSONObject) weatherArr.get(0)).optString("description", "—");
            }

            // Main block
            JSONObject main = root.optJSONObject("main");
            double tempF     = main != null ? main.optDouble("temp", Double.NaN) : Double.NaN;
            double feelsLike = main != null ? main.optDouble("feels_like", Double.NaN) : Double.NaN;
            int humidity     = main != null ? main.optInt("humidity", -1) : -1;
            double pressure  = main != null ? main.optDouble("pressure", Double.NaN) : Double.NaN;

            // Wind
            JSONObject wind = root.optJSONObject("wind");
            double windSpeed = wind != null ? wind.optDouble("speed", Double.NaN) : Double.NaN;
            double windDeg   = wind != null ? wind.optDouble("deg", Double.NaN) : Double.NaN;

            String text = String.format(
                    Locale.US,
                    "Weather in %s\n%s, %.0f°F (feels %.0f°F)\nHumidity: %s%% • \nPressure: %.0f hPa\nWind: %.0f mph %s",
                    name,
                    capitalize(description),
                    tempF,
                    feelsLike,
                    safeInt(humidity), // keep %s here or switch to %d with an int
                    pressure,
                    windSpeed,
                    Double.isNaN(windDeg) ? "" : "(" + Math.round(windDeg) + "° " + toCardinal((float) windDeg) + ")"
            );

            postWeather(text);

        } catch (Exception e) {
            Log.d("HTTP", "~~~ Error: " + e);
            postWeather("Weather: Parse error – " + e.getMessage());
        }
    }

    private void postWeather(String text) {
        if (weatherText == null) return;
        mainHandler.post(() -> weatherText.setText(text));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String safeRound(double v) {
        return Double.isNaN(v) ? "—" : String.valueOf(Math.round(v));
    }

    private static String safeInt(int v) {
        return v < 0 ? "—" : String.valueOf(v);
    }
}
