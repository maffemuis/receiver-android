/*
 * Copyright (C) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.bluetooth;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;

import java.util.Arrays;
import java.util.List;

public class WiFiNaNScanner {
    private static final String TAG = WiFiNaNScanner.class.getSimpleName();

    private final OpenDroneIdDataManager dataManager;
    private final Context context;
    private final BroadcastReceiver stateReceiver;
    private LogWriter logger;
    private boolean wifiAwareSupported;
    private boolean receiverRegistered;
    private WifiAwareManager wifiAwareManager;
    private WifiAwareSession wifiAwareSession;
    private SubscribeDiscoverySession discoverySession;
    private String lastError;

    @RequiresApi(api = Build.VERSION_CODES.O)
    public WiFiNaNScanner(Context context, OpenDroneIdDataManager dataManager, LogWriter logger) {
        this.context = context.getApplicationContext();
        this.dataManager = dataManager;
        this.logger = logger;

        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (wifiAwareManager != null && wifiAwareManager.isAvailable()) {
                    startScan();
                } else {
                    lastError = "Wi-Fi Aware is temporarily unavailable";
                    closeSession();
                }
            }
        };

        if (!this.context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            lastError = "Wi-Fi Aware is not supported by this phone";
            return;
        }

        wifiAwareManager = (WifiAwareManager) this.context.getSystemService(Context.WIFI_AWARE_SERVICE);
        if (wifiAwareManager == null) {
            lastError = "Android did not provide a Wi-Fi Aware manager";
            return;
        }

        wifiAwareSupported = true;
        IntentFilter filter = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        ContextCompat.registerReceiver(this.context, stateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    public void setLogger(LogWriter logger) {
        this.logger = logger;
    }

    public String getLastError() {
        return lastError;
    }

    private final AttachCallback attachCallback = new AttachCallback() {
        @Override
        public void onAttached(WifiAwareSession session) {
            if (!wifiAwareSupported) {
                session.close();
                return;
            }
            wifiAwareSession = session;
            SubscribeConfig config = new SubscribeConfig.Builder()
                    .setServiceName("org.opendroneid.remoteid")
                    .build();

            if (!hasPermissions()) {
                lastError = "Nearby devices or location permission is missing for Wi-Fi Aware";
                closeSession();
                return;
            }

            try {
                wifiAwareSession.subscribe(config, new DiscoverySessionCallback() {
                    @Override
                    public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
                        discoverySession = session;
                        lastError = null;
                        Log.i(TAG, "Wi-Fi Aware Remote ID subscription started");
                    }

                    @Override
                    public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
                                                    List<byte[]> matchFilter) {
                        if (serviceSpecificInfo == null || serviceSpecificInfo.length == 0) {
                            return;
                        }
                        LogMessageEntry logMessageEntry = new LogMessageEntry();
                        long timeNano = SystemClock.elapsedRealtimeNanos();
                        String transportType = "NAN";
                        dataManager.receiveDataNaN(serviceSpecificInfo, peerHandle.hashCode(), timeNano,
                                logMessageEntry, transportType);

                        StringBuilder csvLog = logMessageEntry.getMessageLogEntry();
                        if (logger != null) {
                            logger.logNaN(logMessageEntry.getMsgVersion(), timeNano, peerHandle.hashCode(),
                                    serviceSpecificInfo, transportType, csvLog);
                        }
                        Log.d(TAG, "Wi-Fi Aware Remote ID: " + Arrays.toString(serviceSpecificInfo));
                    }

                    @Override
                    public void onSessionConfigFailed() {
                        lastError = "Wi-Fi Aware subscription configuration failed";
                    }

                    @Override
                    public void onSessionTerminated() {
                        discoverySession = null;
                    }
                }, null);
            } catch (SecurityException exception) {
                lastError = "Wi-Fi Aware permission was rejected by Android";
                Log.w(TAG, lastError, exception);
                closeSession();
            }
        }

        @Override
        public void onAttachFailed() {
            lastError = "Wi-Fi Aware could not attach";
            Log.w(TAG, lastError);
        }
    };

    private final IdentityChangedListener identityChangedListener = new IdentityChangedListener() {
        @Override
        public void onIdentityChanged(byte[] mac) {
            Log.v(TAG, "Wi-Fi Aware identity changed");
        }
    };

    public boolean startScan() {
        if (!wifiAwareSupported || wifiAwareManager == null) {
            return false;
        }
        if (!wifiAwareManager.isAvailable()) {
            lastError = "Wi-Fi Aware is temporarily unavailable";
            return false;
        }
        if (!hasPermissions()) {
            lastError = "Nearby devices or location permission is missing for Wi-Fi Aware";
            return false;
        }
        if (wifiAwareSession != null || discoverySession != null) {
            return true;
        }
        try {
            wifiAwareManager.attach(attachCallback, identityChangedListener, null);
            return true;
        } catch (SecurityException | IllegalStateException exception) {
            lastError = "Wi-Fi Aware could not start: " + exception.getMessage();
            Log.w(TAG, lastError, exception);
            return false;
        }
    }

    public void stopScan() {
        closeSession();
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(stateReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was already removed by Android.
            }
            receiverRegistered = false;
        }
    }

    private boolean hasPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void closeSession() {
        if (discoverySession != null) {
            discoverySession.close();
            discoverySession = null;
        }
        if (wifiAwareSession != null) {
            wifiAwareSession.close();
            wifiAwareSession = null;
        }
    }
}
