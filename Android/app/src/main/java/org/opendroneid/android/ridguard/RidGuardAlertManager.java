/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.opendroneid.android.data.AircraftObject;
import java.util.HashMap;
import java.util.Map;

public class RidGuardAlertManager {
    private static final int ALERT_TONE_MS = 300;
    private final Context context;
    private final RidGuardSettings settings;
    private final Map<String, Long> lastAlertById = new HashMap<>();

    public RidGuardAlertManager(Context context, RidGuardSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public void maybeAlert(AircraftObject aircraft, String aircraftId, Double altitudeDiffMeters,
                           float distanceMeters) {
        if (aircraft == null) {
            return;
        }
        if (aircraftId == null) {
            return;
        }
        if (System.currentTimeMillis() < settings.getSilenceUntil()) {
            return;
        }
        if (settings.isManuallyIgnored(aircraftId) || settings.isTemporarilyIgnored(aircraftId)) {
            return;
        }
        if (distanceMeters <= 0 || distanceMeters > settings.getRadiusMeters()) {
            return;
        }
        if (settings.isAltitudeWindowEnabled() && altitudeDiffMeters != null) {
            int min = settings.getAltitudeMinMeters();
            int max = settings.getAltitudeMaxMeters();
            if (altitudeDiffMeters < min || altitudeDiffMeters > max) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        Long lastAlert = lastAlertById.get(aircraftId);
        if (lastAlert != null && now - lastAlert < settings.getCooldownSeconds() * 1000L) {
            return;
        }
        lastAlertById.put(aircraftId, now);
        triggerAlert();
    }

    private void triggerAlert() {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 150, 200}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 200, 150, 200}, -1);
            }
        }
        ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ALERT_TONE_MS);
        toneGenerator.release();
    }
}
