/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;
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
    private final OpenDroneIdDataManager dataManager;
    private final FusedLocationProviderClient fusedLocationProviderClient;

    private BluetoothScanner bluetoothScanner;
    private WiFiNaNScanner wiFiNaNScanner;
    private WiFiBeaconScanner wiFiBeaconScanner;
    private LocationCallback locationCallback;
    private Location receiverLocation;

    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<Long> lastScanTime = new MutableLiveData<>(0L);
    private final MutableLiveData<String> lastError = new MutableLiveData<>();
    private final MutableLiveData<String> activeTransports = new MutableLiveData<>("None");

    public static synchronized RidGuardRepository getInstance(Context context) {
        if (instance == null) {
            instance = new RidGuardRepository(context.getApplicationContext());
        }
        return instance;
    }

    private RidGuardRepository(Context context) {
        this.context = context;
        settings = new RidGuardSettings(context);
        alertManager = new RidGuardAlertManager(context, settings);
        logger = new RidGuardLogger(context, settings);
        dataManager = new OpenDroneIdDataManager(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
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

    public LiveData<String> getLastError() {
        return lastError;
    }

    public LiveData<String> getActiveTransports() {
        return activeTransports;
    }

    public Location getReceiverLocation() {
        return receiverLocation;
    }

    public RidGuardSettings getSettings() {
        return settings;
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean startScanning() {
        if (Boolean.TRUE.equals(scanning.getValue())) {
            return true;
        }
        clearError();

        if (!RidGuardDeviceStatus.hasRequiredScanningPermissions(context)) {
            return fail("Required Bluetooth, nearby-device or location permission is missing");
        }
        if (!RidGuardDeviceStatus.isLocationEnabled(context)) {
            return fail("Location services are turned off");
        }
        if (!RidGuardDeviceStatus.isBluetoothEnabled(context)) {
            return fail("Bluetooth is turned off");
        }

        stopScannerObjects();
        initLocationUpdates();

        bluetoothScanner = new BluetoothScanner(context, dataManager);
        if (!bluetoothScanner.startScan()) {
            String scannerError = bluetoothScanner.getLastError();
            stopScannerObjects();
            return fail(scannerError != null ? scannerError : "Bluetooth scanning could not start");
        }

        StringBuilder transports = new StringBuilder("Bluetooth");

        if (RidGuardDeviceStatus.isWifiEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    wiFiBeaconScanner = new WiFiBeaconScanner(context, dataManager, null);
                    wiFiBeaconScanner.startCountDownTimer();
                    transports.append(" + Wi-Fi beacon");
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Wi-Fi beacon scanner could not start", exception);
                    wiFiBeaconScanner = null;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && RidGuardDeviceStatus.supportsWifiAware(context)) {
                try {
                    wiFiNaNScanner = new WiFiNaNScanner(context, dataManager, null);
                    if (wiFiNaNScanner.startScan()) {
                        transports.append(" + Wi-Fi Aware");
                    }
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Wi-Fi Aware scanner could not start", exception);
                    wiFiNaNScanner = null;
                }
            }
        }

        activeTransports.postValue(transports.toString());
        scanning.postValue(true);
        Log.i(TAG, "RID Guard scanning started using " + transports);
        return true;
    }

    public synchronized void stopScanning() {
        stopScannerObjects();
        stopLocationUpdates();
        scanning.postValue(false);
        activeTransports.postValue("None");
        Log.i(TAG, "RID Guard scanning stopped");
    }

    public void reportError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        lastError.postValue(message);
        Log.e(TAG, message);
    }

    public void clearError() {
        lastError.postValue(null);
    }

    @Override
    public void onAircraftUpdated(AircraftObject object) {
        if (object == null) {
            return;
        }
        lastScanTime.postValue(System.currentTimeMillis());
        LocationData location = object.getLocation();
        float distanceMeters = location != null ? location.getDistance() : 0f;
        Double altitudeDiffMeters = getAltitudeDiffMeters(location);
        String aircraftId = RidGuardDroneUtils.getPrimaryId(object);
        String hashed = RidGuardSettings.hashId(aircraftId);
        Double speed = location != null && location.getSpeedHorizontal() != 255
                ? (double) location.getSpeedHorizontal() : null;
        Double heading = location != null && location.getDirection() != 361
                ? (double) location.getDirection() : null;
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

    @SuppressLint("MissingPermission")
    private void initLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        stopLocationUpdates();
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(1f)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || locationResult.getLastLocation() == null) {
                    return;
                }
                receiverLocation = locationResult.getLastLocation();
                dataManager.receiverLocation = receiverLocation;
            }
        };
        fusedLocationProviderClient.requestLocationUpdates(request, locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void stopScannerObjects() {
        if (bluetoothScanner != null) {
            bluetoothScanner.stopScan();
            bluetoothScanner = null;
        }
        if (wiFiNaNScanner != null) {
            wiFiNaNScanner.stopScan();
            wiFiNaNScanner = null;
        }
        if (wiFiBeaconScanner != null) {
            wiFiBeaconScanner.stopScan();
            wiFiBeaconScanner = null;
        }
    }

    private boolean fail(String message) {
        reportError(message);
        scanning.postValue(false);
        activeTransports.postValue("None");
        return false;
    }

    public String buildStatusSummary() {
        String bluetooth = RidGuardDeviceStatus.isBluetoothEnabled(context) ? "BLE on" : "BLE off";
        String longRange = RidGuardDeviceStatus.supportsBluetoothLongRange(context) ? "BT5 LR yes" : "BT5 LR no/unknown";
        String wifi = RidGuardDeviceStatus.isWifiEnabled(context) ? "Wi-Fi on" : "Wi-Fi off";
        String aware = RidGuardDeviceStatus.isWifiAwareAvailable(context) ? "NAN ready" : "NAN unavailable";
        return bluetooth + " · " + longRange + " · " + wifi + " · " + aware;
    }
}
