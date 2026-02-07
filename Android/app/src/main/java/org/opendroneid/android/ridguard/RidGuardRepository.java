/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.opendroneid.android.bluetooth.BluetoothScanner;
import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.android.bluetooth.WiFiBeaconScanner;
import org.opendroneid.android.bluetooth.WiFiNaNScanner;
import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.LocationData;

public class RidGuardRepository extends OpenDroneIdDataManager.Callback {
    private static final String TAG = "RidGuardRepository";
    private static RidGuardRepository instance;

    private final Context context;
    private final RidGuardSettings settings;
    private final RidGuardAlertManager alertManager;
    private final RidGuardLogger logger;

    private OpenDroneIdDataManager dataManager;
    private BluetoothScanner bluetoothScanner;
    private WiFiNaNScanner wiFiNaNScanner;
    private WiFiBeaconScanner wiFiBeaconScanner;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Location receiverLocation;

    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<Long> lastScanTime = new MutableLiveData<>(0L);

    public static synchronized RidGuardRepository getInstance(Context context) {
        if (instance == null) {
            instance = new RidGuardRepository(context.getApplicationContext());
        }
        return instance;
    }

    private RidGuardRepository(Context context) {
        this.context = context;
        this.settings = new RidGuardSettings(context);
        this.alertManager = new RidGuardAlertManager(context, settings);
        this.logger = new RidGuardLogger(context, settings);
        this.dataManager = new OpenDroneIdDataManager(this);
        this.fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public OpenDroneIdDataManager getDataManager() {
        return dataManager;
    }

    public LiveData<Boolean> getScanning() {
        return scanning;
    }

    public LiveData<Long> getLastScanTime() {
        return lastScanTime;
    }

    public Location getReceiverLocation() {
        return receiverLocation;
    }

    public RidGuardSettings getSettings() {
        return settings;
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (Boolean.TRUE.equals(scanning.getValue())) {
            return;
        }
        initLocationUpdates();
        bluetoothScanner = new BluetoothScanner(context, dataManager);
        wiFiNaNScanner = new WiFiNaNScanner(context, dataManager, null);
        wiFiBeaconScanner = new WiFiBeaconScanner(context, dataManager, null);

        bluetoothScanner.startScan();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wiFiNaNScanner.startScan();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            wiFiBeaconScanner.startCountDownTimer();
        }
        scanning.postValue(true);
        Log.d(TAG, "RID Guard scanning started.");
    }

    public void stopScanning() {
        if (!Boolean.TRUE.equals(scanning.getValue())) {
            return;
        }
        if (bluetoothScanner != null) {
            bluetoothScanner.stopScan();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wiFiNaNScanner != null) {
            wiFiNaNScanner.stopScan();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && wiFiBeaconScanner != null) {
            wiFiBeaconScanner.stopScan();
        }
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        scanning.postValue(false);
        Log.d(TAG, "RID Guard scanning stopped.");
    }

    @Override
    public void onAircraftUpdated(AircraftObject object) {
        lastScanTime.postValue(System.currentTimeMillis());
        LocationData location = object.getLocation();
        float distanceMeters = location != null ? location.getDistance() : 0f;
        Double altitudeDiffMeters = getAltitudeDiffMeters(location);
        String aircraftId = RidGuardDroneUtils.getPrimaryId(object);
        String hashed = RidGuardSettings.hashId(aircraftId);
        Double speed = location != null ? location.getSpeedHorizontal() : null;
        Double heading = location != null ? location.getDirection() : null;
        long lastSeen = object.getConnection() != null ? object.getConnection().lastSeen : 0L;
        alertManager.maybeAlert(object, aircraftId, altitudeDiffMeters, distanceMeters);
        logger.logEntry(hashed, distanceMeters, altitudeDiffMeters, speed, heading, lastSeen);
    }

    @Override
    public void onNewAircraft(AircraftObject object) {
        onAircraftUpdated(object);
    }

    private Double getAltitudeDiffMeters(LocationData locationData) {
        if (locationData == null || receiverLocation == null) {
            return null;
        }
        double droneAltitude = locationData.getAltitudeGeodetic();
        if (droneAltitude == -1000) {
            droneAltitude = locationData.getAltitudePressure();
        }
        if (droneAltitude == -1000) {
            return null;
        }
        return droneAltitude - receiverLocation.getAltitude();
    }

    private void initLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                receiverLocation = locationResult.getLastLocation();
                dataManager.receiverLocation = receiverLocation;
            }
        };
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public String buildStatusSummary() {
        boolean bluetoothEnabled = bluetoothScanner != null && bluetoothScanner.getBluetoothAdapter() != null
                && bluetoothScanner.getBluetoothAdapter().isEnabled();
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();
        boolean nanSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
        return "BLE " + (bluetoothEnabled ? "on" : "off") +
                " · Wi-Fi " + (wifiEnabled ? "on" : "off") +
                " · NAN " + (nanSupported ? "on" : "off");
    }
}
