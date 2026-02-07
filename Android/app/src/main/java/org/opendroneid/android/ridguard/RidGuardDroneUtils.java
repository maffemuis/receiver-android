/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.Connection;
import org.opendroneid.android.data.Identification;

public final class RidGuardDroneUtils {
    private RidGuardDroneUtils() {}

    public static String getPrimaryId(AircraftObject aircraft) {
        if (aircraft == null) {
            return null;
        }
        Identification id1 = aircraft.getIdentification1();
        if (id1 != null && id1.getUasIdAsString() != null && !id1.getUasIdAsString().isEmpty()) {
            return id1.getUasIdAsString();
        }
        Identification id2 = aircraft.getIdentification2();
        if (id2 != null && id2.getUasIdAsString() != null && !id2.getUasIdAsString().isEmpty()) {
            return id2.getUasIdAsString();
        }
        Connection connection = aircraft.getConnection();
        if (connection != null) {
            return connection.macAddress;
        }
        return String.valueOf(aircraft.getMacAddress());
    }
}
