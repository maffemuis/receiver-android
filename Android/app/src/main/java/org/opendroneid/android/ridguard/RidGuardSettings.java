/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class RidGuardSettings {
    public static final int DEFAULT_RADIUS_METERS = 200;
    public static final int DEFAULT_COOLDOWN_SECONDS = 30;
    public static final int DEFAULT_LOG_RETENTION_HOURS = 48;
    public static final int DEFAULT_ALTITUDE_MIN = -50;
    public static final int DEFAULT_ALTITUDE_MAX = 150;

    private static final String PREF_RADIUS_METERS = "ridguard_radius_m";
    private static final String PREF_ALTITUDE_ENABLED = "ridguard_altitude_enabled";
    private static final String PREF_ALTITUDE_MIN = "ridguard_altitude_min";
    private static final String PREF_ALTITUDE_MAX = "ridguard_altitude_max";
    private static final String PREF_COOLDOWN_SECONDS = "ridguard_cooldown_s";
    private static final String PREF_SILENCE_UNTIL = "ridguard_silence_until";
    private static final String PREF_IGNORE_IDS = "ridguard_ignore_ids";
    private static final String PREF_LOG_RETENTION_HOURS = "ridguard_log_retention_hours";
    private static final String PREF_MAP_ENABLED = "ridguard_map_enabled";

    private static final String PREF_IGNORE_UNTIL_PREFIX = "ridguard_ignore_until_";

    private final SharedPreferences preferences;

    public RidGuardSettings(Context context) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public int getRadiusMeters() {
        return getIntPref(PREF_RADIUS_METERS, DEFAULT_RADIUS_METERS);
    }

    public boolean isAltitudeWindowEnabled() {
        return preferences.getBoolean(PREF_ALTITUDE_ENABLED, false);
    }

    public int getAltitudeMinMeters() {
        return getIntPref(PREF_ALTITUDE_MIN, DEFAULT_ALTITUDE_MIN);
    }

    public int getAltitudeMaxMeters() {
        return getIntPref(PREF_ALTITUDE_MAX, DEFAULT_ALTITUDE_MAX);
    }

    public int getCooldownSeconds() {
        return getIntPref(PREF_COOLDOWN_SECONDS, DEFAULT_COOLDOWN_SECONDS);
    }

    public int getLogRetentionHours() {
        return getIntPref(PREF_LOG_RETENTION_HOURS, DEFAULT_LOG_RETENTION_HOURS);
    }

    public boolean isMapEnabled() {
        return preferences.getBoolean(PREF_MAP_ENABLED, false);
    }

    public long getSilenceUntil() {
        return preferences.getLong(PREF_SILENCE_UNTIL, 0L);
    }

    public void setSilenceForMinutes(int minutes) {
        long until = System.currentTimeMillis() + minutes * 60L * 1000L;
        preferences.edit().putLong(PREF_SILENCE_UNTIL, until).apply();
    }

    public Set<String> getManualIgnoreIds() {
        String raw = preferences.getString(PREF_IGNORE_IDS, "");
        if (TextUtils.isEmpty(raw)) {
            return Collections.emptySet();
        }
        String[] entries = raw.split("[,\\n]");
        Set<String> result = new HashSet<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public boolean isManuallyIgnored(String id) {
        if (id == null) {
            return false;
        }
        for (String ignored : getManualIgnoreIds()) {
            if (ignored.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public void ignoreTemporarily(String id, int minutes) {
        if (id == null) {
            return;
        }
        String key = PREF_IGNORE_UNTIL_PREFIX + hashId(id);
        long until = System.currentTimeMillis() + minutes * 60L * 1000L;
        preferences.edit().putLong(key, until).apply();
    }

    public boolean isTemporarilyIgnored(String id) {
        if (id == null) {
            return false;
        }
        String key = PREF_IGNORE_UNTIL_PREFIX + hashId(id);
        long until = preferences.getLong(key, 0L);
        return until > System.currentTimeMillis();
    }

    private int getIntPref(String key, int defaultValue) {
        try {
            String raw = preferences.getString(key, null);
            if (raw != null) {
                return Integer.parseInt(raw);
            }
        } catch (NumberFormatException ignored) {
        }
        return preferences.getInt(key, defaultValue);
    }

    public static String hashId(String id) {
        if (id == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : Arrays.copyOf(hashed, 8)) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(id.hashCode());
        }
    }
}
