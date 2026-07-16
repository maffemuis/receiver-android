/*
 * Copyright (C) 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

/**
 * Centralized capability and permission checks used by the UI and scanner service.
 */
public final class RidGuardDeviceStatus {
    private RidGuardDeviceStatus() {
    }

    public static BluetoothAdapter getBluetoothAdapter(Context context) {
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager != null ? manager.getAdapter() : null;
    }

    public static boolean hasBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                && getBluetoothAdapter(context) != null;
    }

    public static boolean isBluetoothEnabled(Context context) {
        BluetoothAdapter adapter = getBluetoothAdapter(context);
        if (adapter == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            return adapter.isEnabled();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static boolean supportsBluetoothLongRange(Context context) {
        BluetoothAdapter adapter = getBluetoothAdapter(context);
        if (adapter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        try {
            return adapter.isLeCodedPhySupported()
                    && adapter.isLeExtendedAdvertisingSupported();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        return manager != null && manager.isWifiEnabled();
    }

    public static boolean supportsWifiAware(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    public static boolean isWifiAwareAvailable(Context context) {
        if (!supportsWifiAware(context)) {
            return false;
        }
        WifiAwareManager manager =
                (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
        return manager != null && manager.isAvailable();
    }

    public static boolean isLocationEnabled(Context context) {
        LocationManager manager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return manager.isLocationEnabled();
        }
        try {
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBluetoothPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return hasLocationPermission(context);
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasNearbyWifiPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasRequiredScanningPermissions(Context context) {
        return hasLocationPermission(context)
                && hasBluetoothPermissions(context)
                && hasNearbyWifiPermission(context);
    }

    public static boolean hasNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return manager != null
                && manager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean isReadyForScanning(Context context) {
        return hasBluetooth(context)
                && isBluetoothEnabled(context)
                && isLocationEnabled(context)
                && hasRequiredScanningPermissions(context);
    }
}
