/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RidGuardLogger {
    private static final String LOG_DIR = "ridguard_logs";
    private static final String HEADER = "timestamp,hashed_id,distance_m,alt_diff_m,speed_mps,heading_deg,last_seen_ms\n";
    private final Context context;
    private final RidGuardSettings settings;

    public RidGuardLogger(Context context, RidGuardSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public synchronized void logEntry(String hashedId, float distanceMeters, Double altitudeDiff,
                                      Double speedMetersPerSec, Double headingDeg, long lastSeenMs) {
        File dir = new File(context.getFilesDir(), LOG_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        cleanupOldLogs(dir);
        File file = new File(dir, getFileName());
        boolean newFile = !file.exists();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            if (newFile) {
                writer.write(HEADER);
            }
            writer.write(String.format(Locale.US, "%d,%s,%.1f,%s,%s,%s,%d\n",
                    System.currentTimeMillis(),
                    hashedId,
                    distanceMeters,
                    altitudeDiff == null ? "" : String.format(Locale.US, "%.1f", altitudeDiff),
                    speedMetersPerSec == null ? "" : String.format(Locale.US, "%.1f", speedMetersPerSec),
                    headingDeg == null ? "" : String.format(Locale.US, "%.0f", headingDeg),
                    lastSeenMs));
        } catch (IOException ignored) {
        }
    }

    private String getFileName() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return "ridguard_" + date + ".csv";
    }

    private void cleanupOldLogs(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long retentionMs = settings.getLogRetentionHours() * 60L * 60L * 1000L;
        for (File file : files) {
            if (now - file.lastModified() > retentionMs) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
