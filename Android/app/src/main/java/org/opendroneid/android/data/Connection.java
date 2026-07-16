/*
 * Copyright (C) 2019 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.data;

import java.util.Locale;

public class Connection extends MessageData {
    public int rssi;
    public String transportType;
    public String macAddress;
    public long lastSeen;
    public long firstSeen;
    public long msgDelta;

    public long totalMessages;
    public long bluetoothLegacyMessages;
    public long bluetoothLongRangeMessages;
    public long wifiBeaconMessages;
    public long wifiAwareMessages;
    public long demoMessages;

    public long lastBluetoothLegacySeen;
    public long lastBluetoothLongRangeSeen;
    public long lastWifiBeaconSeen;
    public long lastWifiAwareSeen;
    public long lastDemoSeen;

    public synchronized void recordTransport(String transport, long receivedAtMillis) {
        totalMessages++;
        if (transport == null) {
            return;
        }
        switch (transport.toUpperCase(Locale.US)) {
            case "BT4":
                bluetoothLegacyMessages++;
                lastBluetoothLegacySeen = receivedAtMillis;
                break;
            case "BT5":
                bluetoothLongRangeMessages++;
                lastBluetoothLongRangeSeen = receivedAtMillis;
                break;
            case "BEACON":
            case "WIFI_BEACON":
            case "WI-FI BEACON":
                wifiBeaconMessages++;
                lastWifiBeaconSeen = receivedAtMillis;
                break;
            case "NAN":
            case "WIFI_AWARE":
            case "WI-FI AWARE":
                wifiAwareMessages++;
                lastWifiAwareSeen = receivedAtMillis;
                break;
            case "DEMO":
                demoMessages++;
                lastDemoSeen = receivedAtMillis;
                break;
            default:
                break;
        }
    }

    public String getMsgDeltaAsString() {
        if (msgDelta / 1000 == 0)
            return String.format(Locale.US,"%3d ms", msgDelta);
        else {
            double seconds = msgDelta;
            seconds /= 1000;
            return String.format(Locale.US,"%.1f s", seconds);
        }
    }

    public Connection() {
        super();
    }
}
