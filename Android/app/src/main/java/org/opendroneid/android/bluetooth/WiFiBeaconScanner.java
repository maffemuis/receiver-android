/*
 * Copyright (C) 2021 Skydio Inc
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.bluetooth;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WiFiBeaconScanner {
    private static final int CID_LEN = 3;
    private static final int DRI_START_BYTE_OFFSET = 4;
    private static final int SCAN_TIMER_INTERVAL_SECONDS = 10;
    private static final int[] DRI_CID = {0xFA, 0x0B, 0xBC};
    private static final int VENDOR_TYPE_LEN = 1;
    private static final int VENDOR_TYPE_VALUE = 0x0D;
    private static final String TAG = WiFiBeaconScanner.class.getSimpleName();

    private final OpenDroneIdDataManager dataManager;
    private final Context context;
    private final String startTime;
    private final BroadcastReceiver scanResultsReceiver;
    private LogWriter logger;
    private WifiManager wifiManager;
    private CountDownTimer countDownTimer;
    private boolean receiverRegistered;
    private boolean wifiScanSupported = true;
    private int scanSuccess;
    private int scanFailed;
    private String lastError;

    public WiFiBeaconScanner(Context context, OpenDroneIdDataManager dataManager, LogWriter logger) {
        this.context = context.getApplicationContext();
        this.dataManager = dataManager;
        this.logger = logger;
        this.startTime = getCurrTimeStr();

        scanResultsReceiver = new BroadcastReceiver() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                handleScanResults(intent);
            }
        };

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || !this.context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            wifiScanSupported = false;
            lastError = "Wi-Fi beacon scanning is not supported";
            return;
        }

        wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            wifiScanSupported = false;
            lastError = "Android did not provide a Wi-Fi manager";
            return;
        }

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        ContextCompat.registerReceiver(this.context, scanResultsReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    public void setLogger(LogWriter logger) {
        this.logger = logger;
    }

    public String getLastError() {
        return lastError;
    }

    void processRemoteIdVendorIE(ScanResult scanResult, ByteBuffer buf) {
        if (buf == null || buf.remaining() < 30) {
            return;
        }
        byte[] driCid = new byte[CID_LEN];
        byte[] arr = new byte[buf.remaining()];
        buf.get(driCid, 0, CID_LEN);
        byte[] vendorType = new byte[VENDOR_TYPE_LEN];
        buf.get(vendorType);
        if ((driCid[0] & 0xFF) == DRI_CID[0]
                && (driCid[1] & 0xFF) == DRI_CID[1]
                && (driCid[2] & 0xFF) == DRI_CID[2]
                && vendorType[0] == VENDOR_TYPE_VALUE) {
            buf.position(DRI_START_BYTE_OFFSET);
            buf.get(arr, 0, buf.remaining());
            LogMessageEntry logMessageEntry = new LogMessageEntry();
            long timeNano = SystemClock.elapsedRealtimeNanos();
            String transportType = "Beacon";
            dataManager.receiveDataWiFiBeacon(arr, scanResult.BSSID, scanResult.BSSID.hashCode(),
                    scanResult.level, timeNano, logMessageEntry, transportType);

            Log.i(TAG, "Remote ID Wi-Fi beacon: " + scanResult.BSSID + ": " + Arrays.toString(arr));
            StringBuilder csvLog = logMessageEntry.getMessageLogEntry();
            if (logger != null) {
                logger.logBeacon(logMessageEntry.getMsgVersion(), timeNano, scanResult, arr,
                        transportType, csvLog);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void handleScanResults(Intent intent) {
        if (wifiManager == null || intent == null) {
            return;
        }
        boolean freshScanResult = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        if (!freshScanResult || !WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            lastError = "Location permission is required for Wi-Fi beacon results";
            return;
        }

        try {
            List<ScanResult> wifiList = wifiManager.getScanResults();
            for (ScanResult scanResult : wifiList) {
                try {
                    handleResult(scanResult);
                } catch (NoSuchFieldException | IllegalAccessException exception) {
                    Log.w(TAG, "Unable to inspect Wi-Fi information elements", exception);
                }
            }
        } catch (SecurityException exception) {
            lastError = "Wi-Fi scan results were blocked by Android";
            Log.w(TAG, lastError, exception);
        }
    }

    void handleResult(ScanResult scanResult) throws NoSuchFieldException, IllegalAccessException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Object value = ScanResult.class.getField("informationElements").get(scanResult);
            ScanResult.InformationElement[] elements = (ScanResult.InformationElement[]) value;
            if (elements == null) {
                return;
            }
            for (ScanResult.InformationElement element : elements) {
                if (element == null) {
                    continue;
                }
                Object valueId = element.getClass().getField("id").get(element);
                if (!(valueId instanceof Integer) || ((Integer) valueId) != 221) {
                    continue;
                }
                Object valueBytes = element.getClass().getField("bytes").get(element);
                if (valueBytes instanceof byte[]) {
                    processRemoteIdVendorIE(scanResult,
                            ByteBuffer.wrap((byte[]) valueBytes).asReadOnlyBuffer());
                }
            }
        } else {
            for (ScanResult.InformationElement element : scanResult.getInformationElements()) {
                if (element != null && element.getId() == 221) {
                    processRemoteIdVendorIE(scanResult, element.getBytes());
                }
            }
        }
    }

    public boolean startScan() {
        if (!wifiScanSupported || wifiManager == null) {
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            lastError = "Wi-Fi is turned off; Bluetooth scanning remains active";
            return false;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            lastError = "Location permission is required for Wi-Fi beacon scanning";
            return false;
        }
        try {
            boolean started = wifiManager.startScan();
            if (started) {
                scanSuccess++;
                lastError = null;
            } else {
                scanFailed++;
                lastError = "Wi-Fi scan was throttled by Android";
            }
            printScanStats(started);
            return started;
        } catch (SecurityException exception) {
            scanFailed++;
            lastError = "Wi-Fi scan permission was rejected by Android";
            Log.w(TAG, lastError, exception);
            return false;
        }
    }

    public void startCountDownTimer() {
        if (!wifiScanSupported || countDownTimer != null) {
            return;
        }
        countDownTimer = new CountDownTimer(Long.MAX_VALUE, SCAN_TIMER_INTERVAL_SECONDS * 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                startScan();
            }

            @Override
            public void onFinish() {
                countDownTimer = null;
            }
        }.start();
        startScan();
    }

    public void stopScan() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(scanResultsReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was already removed by Android.
            }
            receiverRegistered = false;
        }
        Log.d(TAG, "Wi-Fi beacon scanning stopped");
    }

    private String getCurrTimeStr() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    private void printScanStats(boolean started) {
        Log.d(TAG, "Started: " + startTime
                + " success: " + scanSuccess
                + ", failed: " + scanFailed
                + ", current: " + started);
    }
}
