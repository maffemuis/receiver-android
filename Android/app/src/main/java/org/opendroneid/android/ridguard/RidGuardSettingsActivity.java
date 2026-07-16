/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;

import org.opendroneid.android.R;

public class RidGuardSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RidGuardEdgeToEdge.enable(this);
        setContentView(R.layout.activity_rid_guard_settings);

        MaterialToolbar toolbar = findViewById(R.id.rid_guard_settings_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIconTint(Color.WHITE);
        RidGuardEdgeToEdge.apply(toolbar, findViewById(R.id.rid_guard_settings_container));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.rid_guard_settings_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.rid_guard_settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.rid_guard_settings, rootKey);

            EditTextPreference altitudeMin = findPreference("ridguard_altitude_min");
            EditTextPreference altitudeMax = findPreference("ridguard_altitude_max");
            EditTextPreference cooldown = findPreference("ridguard_cooldown_s");
            EditTextPreference retention = findPreference("ridguard_log_retention_hours");
            EditTextPreference ignoredIds = findPreference("ridguard_ignore_ids");
            SwitchPreferenceCompat altitudeEnabled = findPreference("ridguard_altitude_enabled");

            setValueSummary(altitudeMin, R.string.rid_guard_value_meters);
            setValueSummary(altitudeMax, R.string.rid_guard_value_meters);
            setValueSummary(cooldown, R.string.rid_guard_value_seconds);
            setValueSummary(retention, R.string.rid_guard_value_hours);

            if (ignoredIds != null) {
                ignoredIds.setSummaryProvider(preference -> {
                    String value = ((EditTextPreference) preference).getText();
                    if (value == null || value.trim().isEmpty()) {
                        return getString(R.string.rid_guard_ignore_none);
                    }
                    int count = 0;
                    for (String id : value.split("[,\\r\\n]+")) {
                        if (!id.trim().isEmpty()) {
                            count++;
                        }
                    }
                    return getResources().getQuantityString(
                            R.plurals.rid_guard_ignore_count, count, count);
                });
            }

            if (altitudeEnabled != null) {
                setAltitudePreferencesEnabled(
                        altitudeEnabled.isChecked(), altitudeMin, altitudeMax);
                altitudeEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                    setAltitudePreferencesEnabled(
                            Boolean.TRUE.equals(newValue), altitudeMin, altitudeMax);
                    return true;
                });
            }
        }

        private void setValueSummary(EditTextPreference preference, int formatString) {
            if (preference == null) {
                return;
            }
            preference.setSummaryProvider(item -> {
                String value = ((EditTextPreference) item).getText();
                if (value == null || value.trim().isEmpty()) {
                    return getString(R.string.rid_guard_value_not_set);
                }
                return getString(formatString, value.trim());
            });
        }

        private void setAltitudePreferencesEnabled(boolean enabled,
                                                   Preference altitudeMin,
                                                   Preference altitudeMax) {
            if (altitudeMin != null) {
                altitudeMin.setEnabled(enabled);
            }
            if (altitudeMax != null) {
                altitudeMax.setEnabled(enabled);
            }
        }
    }
}
