/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;

import org.opendroneid.android.R;
import org.opendroneid.android.app.AircraftOsMapView;
import org.opendroneid.android.app.AircraftViewModel;
import org.opendroneid.android.app.DeviceList;
import org.opendroneid.android.data.AircraftObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RidGuardActivity extends AppCompatActivity {
    private RidGuardRepository repository;
    private AircraftViewModel aircraftViewModel;
    private RidGuardRadarView radarView;
    private TextView statusText;
    private TextView deviceStatusText;
    private TextView transportText;
    private TextView receivedStatsText;
    private TextView lastScanText;
    private TextView errorText;
    private View mapContainer;
    private View mapDisabledText;
    private Button startButton;
    private Button stopButton;
    private Button demoButton;
    private Handler handler;
    private Runnable refreshRunnable;
    private boolean resumeStartAfterSettings;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (RidGuardDeviceStatus.isBluetoothEnabled(this)) {
                    ensureDeviceReadyAndStart();
                } else {
                    Toast.makeText(this, R.string.rid_guard_bluetooth_required, Toast.LENGTH_LONG).show();
                }
                updateStatus();
            });

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateStatus();
                if (resumeStartAfterSettings) {
                    resumeStartAfterSettings = false;
                    ensureDeviceReadyAndStart();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RidGuardEdgeToEdge.enable(this);
        setContentView(R.layout.activity_rid_guard);

        MaterialToolbar toolbar = findViewById(R.id.rid_guard_toolbar);
        setSupportActionBar(toolbar);
        RidGuardEdgeToEdge.apply(toolbar, findViewById(R.id.rid_guard_scroll));

        repository = RidGuardRepository.getInstance(this);
        aircraftViewModel = new ViewModelProvider(this).get(AircraftViewModel.class);
        radarView = findViewById(R.id.rid_guard_radar);
        statusText = findViewById(R.id.rid_guard_status);
        deviceStatusText = findViewById(R.id.rid_guard_device_status);
        transportText = findViewById(R.id.rid_guard_transports);
        receivedStatsText = findViewById(R.id.rid_guard_received_stats);
        lastScanText = findViewById(R.id.rid_guard_last_scan);
        errorText = findViewById(R.id.rid_guard_error);
        mapContainer = findViewById(R.id.rid_guard_map_container);
        mapDisabledText = findViewById(R.id.rid_guard_map_disabled);
        startButton = findViewById(R.id.rid_guard_start);
        stopButton = findViewById(R.id.rid_guard_stop);
        demoButton = findViewById(R.id.rid_guard_demo);

        Button silenceButton = findViewById(R.id.rid_guard_silence);
        Button settingsButton = findViewById(R.id.rid_guard_settings);
        Button permissionsButton = findViewById(R.id.rid_guard_permissions);
        Button batteryButton = findViewById(R.id.rid_guard_battery);

        startButton.setOnClickListener(v -> requestPermissionsAndStart());
        stopButton.setOnClickListener(v -> stopScanning());
        demoButton.setOnClickListener(v -> toggleDemoMode());
        silenceButton.setOnClickListener(v -> {
            repository.getSettings().setSilenceForMinutes(30);
            Toast.makeText(this, R.string.rid_guard_silenced, Toast.LENGTH_SHORT).show();
        });
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, RidGuardSettingsActivity.class)));
        permissionsButton.setOnClickListener(v -> openAppSettings());
        batteryButton.setOnClickListener(v ->
                settingsLauncher.launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        repository.getScanning().observe(this, scanning -> updateStatus());
        repository.getDemoMode().observe(this, demo -> updateStatus());
        repository.getLastScanTime().observe(this, time -> updateStatus());
        repository.getLastError().observe(this, error -> updateStatus());
        repository.getActiveTransports().observe(this, transport -> updateStatus());

        addDeviceList();
        updateMapVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            for (AircraftObject aircraft : repository.getDataManager().aircraft.values()) {
                aircraft.updateShadowBasicId();
                aircraft.connection.setValue(aircraft.connection.getValue());
            }
            aircraftViewModel.setAllAircraft(repository.getDataManager().aircraft);
            radarView.updateData(new ArrayList<>(repository.getDataManager().aircraft.values()),
                    repository.getReceiverLocation(),
                    repository.getSettings().getRadiusMeters());
            updateStatus();
            handler.postDelayed(refreshRunnable, 1000L);
        };
        handler.post(refreshRunnable);
        updateStatus();
        updateMapVisibility();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void addDeviceList() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.rid_guard_list_container, new DeviceList())
                .commitAllowingStateLoss();
    }

    private void toggleDemoMode() {
        boolean wasRunning = Boolean.TRUE.equals(repository.getDemoMode().getValue());
        repository.toggleDemoMode();
        Toast.makeText(this, wasRunning
                ? R.string.rid_guard_demo_stopped
                : R.string.rid_guard_demo_started, Toast.LENGTH_LONG).show();
        updateStatus();
    }

    private void requestPermissionsAndStart() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!RidGuardDeviceStatus.hasRequiredScanningPermissions(this)
                || !RidGuardDeviceStatus.hasNotificationPermission(this)) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            ensureDeviceReadyAndStart();
        }
    }

    private void onPermissionsResult(Map<String, Boolean> result) {
        updateStatus();
        if (!RidGuardDeviceStatus.hasRequiredScanningPermissions(this)) {
            repository.reportError(getString(R.string.permission_required_toast));
            Toast.makeText(this, R.string.permission_required_toast, Toast.LENGTH_LONG).show();
            return;
        }
        ensureDeviceReadyAndStart();
    }

    private void ensureDeviceReadyAndStart() {
        if (!RidGuardDeviceStatus.hasBluetooth(this)) {
            repository.reportError(getString(R.string.rid_guard_bluetooth_not_supported));
            return;
        }
        if (!RidGuardDeviceStatus.isLocationEnabled(this)) {
            resumeStartAfterSettings = true;
            Toast.makeText(this, R.string.rid_guard_enable_location, Toast.LENGTH_LONG).show();
            settingsLauncher.launch(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        if (!RidGuardDeviceStatus.isBluetoothEnabled(this)) {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        if (!RidGuardDeviceStatus.isWifiEnabled(this)) {
            Toast.makeText(this, R.string.rid_guard_wifi_optional, Toast.LENGTH_LONG).show();
        }
        startScanningService();
    }

    private void startScanningService() {
        repository.clearError();
        Intent intent = new Intent(this, RidGuardService.class).setAction(RidGuardService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException exception) {
            repository.reportError(getString(R.string.rid_guard_service_start_failed));
        }
    }

    private void stopScanning() {
        Intent intent = new Intent(this, RidGuardService.class).setAction(RidGuardService.ACTION_STOP);
        try {
            startService(intent);
        } catch (RuntimeException exception) {
            repository.stopScanning();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        settingsLauncher.launch(intent);
    }

    private void updateStatus() {
        boolean isScanning = Boolean.TRUE.equals(repository.getScanning().getValue());
        boolean isDemo = Boolean.TRUE.equals(repository.getDemoMode().getValue());
        long lastScan = repository.getLastScanTime().getValue() != null
                ? repository.getLastScanTime().getValue() : 0L;

        if (isDemo && !isScanning) {
            statusText.setText(R.string.rid_guard_state_demo);
        } else {
            statusText.setText(getString(isScanning
                    ? R.string.rid_guard_state_scanning
                    : R.string.rid_guard_state_idle));
        }
        deviceStatusText.setText(buildDeviceStatusText());

        String transport = repository.getActiveTransports().getValue();
        transportText.setText(getString(R.string.rid_guard_transports,
                transport == null ? getString(R.string.rid_guard_none) : transport));
        receivedStatsText.setText(getString(R.string.rid_guard_received_stats,
                repository.buildDecodedTransportSummary()));

        if (lastScan > 0) {
            long seconds = Math.max(0, (System.currentTimeMillis() - lastScan) / 1000L);
            lastScanText.setText(getString(R.string.rid_guard_last_scan_value, seconds));
        } else {
            lastScanText.setText(R.string.rid_guard_last_scan_idle);
        }

        String error = repository.getLastError().getValue();
        errorText.setText(error);
        errorText.setVisibility(error == null || error.trim().isEmpty() ? View.GONE : View.VISIBLE);

        findViewById(R.id.rid_guard_state_badge).setBackgroundResource(isScanning || isDemo
                ? R.drawable.rid_guard_scanning_badge
                : R.drawable.rid_guard_idle_badge);
        startButton.setEnabled(!isScanning);
        stopButton.setEnabled(isScanning);
        demoButton.setText(isDemo ? R.string.rid_guard_demo_stop : R.string.rid_guard_demo_start);
    }

    private String buildDeviceStatusText() {
        String yes = getString(R.string.rid_guard_status_ok);
        String no = getString(R.string.rid_guard_status_problem);
        StringBuilder builder = new StringBuilder();
        builder.append(RidGuardDeviceStatus.isBluetoothEnabled(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_bluetooth)).append('\n');
        builder.append(RidGuardDeviceStatus.supportsBluetoothLongRange(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_long_range)).append('\n');
        builder.append(RidGuardDeviceStatus.isWifiEnabled(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_wifi)).append('\n');
        builder.append(RidGuardDeviceStatus.isWifiAwareAvailable(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_wifi_aware)).append('\n');
        builder.append(RidGuardDeviceStatus.isLocationEnabled(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_location)).append('\n');
        builder.append(RidGuardDeviceStatus.hasRequiredScanningPermissions(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_permissions)).append('\n');
        builder.append(RidGuardDeviceStatus.isIgnoringBatteryOptimizations(this) ? yes : no)
                .append(' ').append(getString(R.string.rid_guard_check_battery));
        return builder.toString();
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
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
