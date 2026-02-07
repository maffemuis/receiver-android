/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import org.opendroneid.android.R;
import org.opendroneid.android.app.AircraftViewModel;
import org.opendroneid.android.app.AircraftOsMapView;
import org.opendroneid.android.app.DeviceList;
import org.opendroneid.android.data.AircraftObject;

import java.util.ArrayList;
import java.util.List;

public class RidGuardActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 101;
    private RidGuardRepository repository;
    private AircraftViewModel aircraftViewModel;
    private RidGuardRadarView radarView;
    private TextView statusText;
    private TextView lastScanText;
    private View mapContainer;
    private View mapDisabledText;
    private Handler handler;
    private Runnable runnable;
    private final ActivityResultLauncher<Intent> enableWifiLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                    Toast.makeText(this, R.string.wifi_not_enabled_leaving, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rid_guard);
        repository = RidGuardRepository.getInstance(this);
        aircraftViewModel = new ViewModelProvider(this).get(AircraftViewModel.class);
        radarView = findViewById(R.id.rid_guard_radar);
        statusText = findViewById(R.id.rid_guard_status);
        lastScanText = findViewById(R.id.rid_guard_last_scan);
        mapContainer = findViewById(R.id.rid_guard_map_container);
        mapDisabledText = findViewById(R.id.rid_guard_map_disabled);

        Button startButton = findViewById(R.id.rid_guard_start);
        Button stopButton = findViewById(R.id.rid_guard_stop);
        Button silenceButton = findViewById(R.id.rid_guard_silence);
        Button settingsButton = findViewById(R.id.rid_guard_settings);

        startButton.setOnClickListener(v -> requestPermissionsAndStart());
        stopButton.setOnClickListener(v -> stopScanning());
        silenceButton.setOnClickListener(v -> {
            repository.getSettings().setSilenceForMinutes(30);
            Toast.makeText(this, R.string.rid_guard_silenced, Toast.LENGTH_SHORT).show();
        });
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, RidGuardSettingsActivity.class)));

        repository.getScanning().observe(this, scanning -> updateStatus());
        repository.getLastScanTime().observe(this, time -> updateStatus());

        addDeviceList();
        updateMapVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler = new Handler(Looper.getMainLooper());
        runnable = () -> {
            for (AircraftObject aircraft : repository.getDataManager().aircraft.values()) {
                aircraft.updateShadowBasicId();
                aircraft.connection.setValue(aircraft.connection.getValue());
            }
            aircraftViewModel.setAllAircraft(repository.getDataManager().aircraft);
            radarView.updateData(new ArrayList<>(repository.getDataManager().aircraft.values()),
                    repository.getReceiverLocation(),
                    repository.getSettings().getRadiusMeters());
            handler.postDelayed(runnable, 1000);
        };
        handler.post(runnable);
        updateStatus();
        updateMapVisibility();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
    }

    private void addDeviceList() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.rid_guard_list_container, new DeviceList()).commitAllowingStateLoss();
    }

    private void requestPermissionsAndStart() {
        List<String> missingPermissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        checkWifiEnabled();
        startScanning();
    }

    private void startScanning() {
        Intent intent = new Intent(this, RidGuardService.class);
        intent.setAction(RidGuardService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopScanning() {
        Intent intent = new Intent(this, RidGuardService.class);
        intent.setAction(RidGuardService.ACTION_STOP);
        startService(intent);
    }

    private void checkWifiEnabled() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            enableWifiLauncher.launch(new Intent(Settings.Panel.ACTION_WIFI));
        }
    }

    private void updateStatus() {
        Boolean scanning = repository.getScanning().getValue();
        long lastScan = repository.getLastScanTime().getValue() != null ? repository.getLastScanTime().getValue() : 0L;
        String status = repository.buildStatusSummary();
        statusText.setText(getString(R.string.rid_guard_status, status));
        if (lastScan > 0) {
            long seconds = Math.max(0, (System.currentTimeMillis() - lastScan) / 1000);
            lastScanText.setText(getString(R.string.rid_guard_last_scan_value, seconds));
        } else {
            lastScanText.setText(R.string.rid_guard_last_scan_idle);
        }
        if (scanning != null && scanning) {
            findViewById(R.id.rid_guard_state_badge).setBackgroundResource(R.drawable.rid_guard_scanning_badge);
        } else {
            findViewById(R.id.rid_guard_state_badge).setBackgroundResource(R.drawable.rid_guard_idle_badge);
        }
    }

    private void updateMapVisibility() {
        boolean mapEnabled = repository.getSettings().isMapEnabled();
        boolean online = isInternetAvailable();
        if (mapEnabled && online) {
            mapContainer.setVisibility(View.VISIBLE);
            mapDisabledText.setVisibility(View.GONE);
            if (getSupportFragmentManager().findFragmentById(R.id.rid_guard_map_container) == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.rid_guard_map_container, new AircraftOsMapView())
                        .commitAllowingStateLoss();
            }
        } else {
            mapContainer.setVisibility(View.GONE);
            mapDisabledText.setVisibility(View.VISIBLE);
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startScanning();
            } else {
                Toast.makeText(this, R.string.permission_required_toast, Toast.LENGTH_LONG).show();
            }
        }
    }
}
