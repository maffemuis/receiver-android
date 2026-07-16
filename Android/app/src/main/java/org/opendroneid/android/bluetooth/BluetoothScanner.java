/*
 * Copyright (C) 2019 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.opendroneid.android.log.LogEntry;
import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";

    private final OpenDroneIdDataManager dataManager;
    private LogWriter logger;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private final Context context;
    private String lastError;

    public BluetoothScanner(Context context, OpenDroneIdDataManager dataManager) {
        this.context = context.getApplicationContext();
        this.dataManager = dataManager;

        Object object = this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (object != null) {
            bluetoothAdapter = ((android.bluetooth.BluetoothManager) object).getAdapter();
        }
    }

    public void setLogger(LogWriter logger) {
        this.logger = logger;
    }

    private static String dumpBytes(byte[] bytes) {
        return LogEntry.toHexString(bytes, bytes.length);
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public String getLastError() {
        return lastError;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord == null) {
                return;
            }
            byte[] bytes = scanRecord.getBytes();

            String addr = "unknown";
            try {
                String fullAddress = result.getDevice().getAddress();
                if (fullAddress != null) {
                    addr = fullAddress.substring(0, Math.min(8, fullAddress.length()));
                }
            } catch (SecurityException ignored) {
                // The payload can still be parsed even when Android withholds the address.
            }

            int advertiseFlags = scanRecord.getAdvertiseFlags();
            int rssi = result.getRssi();
            String string = String.format(Locale.US, "scan: addr=%s flags=0x%02X rssi=% d, len=%d",
                    addr, advertiseFlags, rssi, bytes != null ? bytes.length : -1);

            String transportType = "BT4";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && bluetoothAdapter != null
                    && bluetoothAdapter.isLeCodedPhySupported()
                    && result.getPrimaryPhy() == BluetoothDevice.PHY_LE_CODED) {
                transportType = "BT5";
            }

            LogMessageEntry logMessageEntry = new LogMessageEntry();
            dataManager.receiveDataBluetooth(bytes, result, logMessageEntry, transportType);

            StringBuilder csvLog = logMessageEntry.getMessageLogEntry();
            if (logger != null) {
                logger.logBluetooth(logMessageEntry.getMsgVersion(), result, transportType, csvLog);
            }

            Log.d(TAG, "onScanResult: " + string);
            if (bytes != null) {
                Log.v(TAG, "payload: " + dumpBytes(bytes));
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: " + results.size());
        }

        @Override
        public void onScanFailed(int errorCode) {
            lastError = "Bluetooth scan failed (code " + errorCode + ")";
            Log.e(TAG, lastError);
        }
    };

    /* OpenDroneID Bluetooth beacons identify themselves by setting the GAP AD Type to
     * "Service Data - 16-bit UUID" and the value to 0xFFFA for ASTM International, ASTM Remote ID.
     */
    private static final UUID SERVICE_UUID = UUID.fromString("0000fffa-0000-1000-8000-00805f9b34fb");
    private static final ParcelUuid SERVICE_PUUID = new ParcelUuid(SERVICE_UUID);
    private static final byte[] OPEN_DRONE_ID_AD_CODE = new byte[]{(byte) 0x0D};

    public boolean startScan() {
        lastError = null;
        if (bluetoothAdapter == null) {
            return fail("Bluetooth LE is not available on this phone");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                return fail("Nearby devices permission is missing");
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return fail("Bluetooth connect permission is missing");
            }
        }

        try {
            if (!bluetoothAdapter.isEnabled()) {
                return fail("Bluetooth is turned off");
            }
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                return fail("Android did not provide a Bluetooth LE scanner");
            }

            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceData(SERVICE_PUUID, OPEN_DRONE_ID_AD_CODE)
                    .build();
            List<ScanFilter> scanFilters = new ArrayList<>();
            scanFilters.add(filter);

            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && bluetoothAdapter.isLeCodedPhySupported()
                    && bluetoothAdapter.isLeExtendedAdvertisingSupported()) {
                settingsBuilder
                        .setLegacy(false)
                        .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
            }

            bluetoothLeScanner.startScan(scanFilters, settingsBuilder.build(), scanCallback);
            Log.i(TAG, "Bluetooth Remote ID scanning started");
            return true;
        } catch (SecurityException | IllegalStateException exception) {
            return fail("Bluetooth scan could not start: " + exception.getMessage());
        }
    }

    public void stopScan() {
        if (bluetoothLeScanner == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            bluetoothLeScanner.stopScan(scanCallback);
        } catch (SecurityException | IllegalStateException exception) {
            Log.w(TAG, "Bluetooth scan could not be stopped cleanly", exception);
        } finally {
            bluetoothLeScanner = null;
        }
    }

    private boolean fail(String message) {
        lastError = message;
        Log.e(TAG, message);
        return false;
    }
}
