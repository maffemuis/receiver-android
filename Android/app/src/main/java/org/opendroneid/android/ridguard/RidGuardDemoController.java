/*
 * Copyright (C) 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.AuthenticationData;
import org.opendroneid.android.data.Connection;
import org.opendroneid.android.data.Identification;
import org.opendroneid.android.data.LocationData;
import org.opendroneid.android.data.OperatorIdData;
import org.opendroneid.android.data.SelfIdData;
import org.opendroneid.android.data.SystemData;

import java.nio.charset.StandardCharsets;

/** Creates two clearly marked synthetic aircraft to test list, radar, map and alerts. */
final class RidGuardDemoController {
    private static final long DJI_DEMO_KEY = Long.MIN_VALUE + 3801;
    private static final long DRONETAG_DEMO_KEY = Long.MIN_VALUE + 3802;
    private static final long UPDATE_INTERVAL_MS = 1000L;

    private final RidGuardRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long startedAt;
    private AircraftObject djiDemo;
    private AircraftObject dronetagDemo;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            updateAircraft();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    RidGuardDemoController(RidGuardRepository repository) {
        this.repository = repository;
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        startedAt = System.currentTimeMillis();
        djiDemo = createAircraft(
                DJI_DEMO_KEY,
                "DEMO-DJI",
                "1581FR1DGUARDDEM0001",
                2,
                1,
                1,
                2,
                "DEMO (BT5)");
        dronetagDemo = createAircraft(
                DRONETAG_DEMO_KEY,
                "DEMO-DTAG",
                "1596FR1DGUARDDEM0002",
                4,
                1,
                2,
                6,
                "DEMO (Wi-Fi Beacon)");
        repository.getDataManager().aircraft.put(DJI_DEMO_KEY, djiDemo);
        repository.getDataManager().aircraft.put(DRONETAG_DEMO_KEY, dronetagDemo);
        updateAircraft();
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(updateRunnable);
        repository.getDataManager().aircraft.remove(DJI_DEMO_KEY);
        repository.getDataManager().aircraft.remove(DRONETAG_DEMO_KEY);
        djiDemo = null;
        dronetagDemo = null;
    }

    private AircraftObject createAircraft(long key, String mac, String serial, int uaType,
                                          int classificationType, int category, int classValue,
                                          String transport) {
        AircraftObject aircraft = new AircraftObject(key);
        long now = System.currentTimeMillis();

        Connection connection = new Connection();
        connection.firstSeen = now;
        connection.lastSeen = now;
        connection.macAddress = mac;
        connection.rssi = -58;
        connection.transportType = transport;
        connection.setMsgVersion(2);
        aircraft.connection.setValue(connection);

        Identification identification = new Identification();
        identification.setTimestamp(SystemClock.elapsedRealtimeNanos());
        identification.setMsgVersion(2);
        identification.setUaType(uaType);
        identification.setIdType(1);
        identification.setUasId(serial.getBytes(StandardCharsets.US_ASCII));
        aircraft.identification1.setValue(identification);
        aircraft.identification2.setValue(new Identification());
        aircraft.id1Shadow.setValue(identification);
        aircraft.id2Shadow.setValue(new Identification());

        aircraft.location.setValue(new LocationData());
        aircraft.authentication.setValue(new AuthenticationData());
        aircraft.selfid.setValue(new SelfIdData());

        SystemData system = new SystemData();
        system.setTimestamp(SystemClock.elapsedRealtimeNanos());
        system.setMsgVersion(2);
        system.setClassificationType(classificationType);
        system.setCategory(category);
        system.setClassValue(classValue);
        aircraft.system.setValue(system);
        aircraft.operatorid.setValue(new OperatorIdData());
        return aircraft;
    }

    private void updateAircraft() {
        if (djiDemo == null || dronetagDemo == null) {
            return;
        }
        double elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        updateOne(djiDemo, elapsedSeconds * 8.0, 125.0, 42.0, 8.5, "DEMO (BT5)", -54);
        updateOne(dronetagDemo, 210.0 - elapsedSeconds * 5.0, 185.0, 70.0, 13.0,
                "DEMO (Wi-Fi Beacon)", -67);
    }

    private void updateOne(AircraftObject aircraft, double angleDegrees, double distanceMeters,
                           double relativeHeightMeters, double speedMetersPerSecond,
                           String transport, int rssi) {
        long now = System.currentTimeMillis();
        Connection connection = aircraft.getConnection();
        connection.msgDelta = now - connection.lastSeen;
        connection.lastSeen = now;
        connection.rssi = rssi;
        connection.transportType = transport;
        connection.recordTransport("DEMO", now);
        connection.setTimestamp(SystemClock.elapsedRealtimeNanos());
        aircraft.connection.setValue(connection);

        Location receiver = repository.getReceiverLocation();
        double radians = Math.toRadians(angleDegrees);
        double northMeters = Math.cos(radians) * distanceMeters;
        double eastMeters = Math.sin(radians) * distanceMeters;

        LocationData location = new LocationData();
        location.setTimestamp(SystemClock.elapsedRealtimeNanos());
        location.setMsgVersion(2);
        location.setStatus(2);
        location.setHeightType(0);
        location.setDirection((angleDegrees + 90.0) % 360.0);
        location.setSpeedHorizontal(speedMetersPerSecond);
        location.setSpeedVertical(0.0);
        location.setHeight(relativeHeightMeters);
        location.setDistance((float) distanceMeters);
        location.setHorizontalAccuracy(11);
        location.setVerticalAccuracy(5);
        location.setSpeedAccuracy(3);
        location.setTimeAccuracy(0.3);

        if (receiver != null) {
            double latitude = receiver.getLatitude() + northMeters / 111320.0;
            double longitudeScale = Math.max(0.2,
                    Math.cos(Math.toRadians(receiver.getLatitude())));
            double longitude = receiver.getLongitude()
                    + eastMeters / (111320.0 * longitudeScale);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setAltitudeGeodetic(receiver.getAltitude() + relativeHeightMeters);
            location.setAltitudePressure(receiver.getAltitude() + relativeHeightMeters);
        }
        aircraft.location.setValue(location);
        repository.onDemoAircraftUpdated(aircraft);
    }
}
