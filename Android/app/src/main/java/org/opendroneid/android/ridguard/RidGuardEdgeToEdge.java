/*
 * Copyright (C) 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Applies the system-bar insets required by Android 15 and Android 16 edge-to-edge mode.
 * The toolbar background is extended behind the status bar while scrolling content remains
 * clear of the navigation bar.
 */
public final class RidGuardEdgeToEdge {
    private RidGuardEdgeToEdge() {
    }

    public static void enable(AppCompatActivity activity) {
        // enableEdgeToEdge() was added in AndroidX Core 1.17; this project currently uses 1.16.
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);
    }

    public static void apply(View toolbar, View bottomInsetView) {
        final int toolbarLeft = toolbar.getPaddingLeft();
        final int toolbarTop = toolbar.getPaddingTop();
        final int toolbarRight = toolbar.getPaddingRight();
        final int toolbarBottom = toolbar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(
                    toolbarLeft,
                    toolbarTop + statusBars.top,
                    toolbarRight,
                    toolbarBottom);
            return windowInsets;
        });

        final int contentLeft = bottomInsetView.getPaddingLeft();
        final int contentTop = bottomInsetView.getPaddingTop();
        final int contentRight = bottomInsetView.getPaddingRight();
        final int contentBottom = bottomInsetView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(bottomInsetView, (view, windowInsets) -> {
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    contentLeft,
                    contentTop,
                    contentRight,
                    contentBottom + navigationBars.bottom);
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(toolbar);
        ViewCompat.requestApplyInsets(bottomInsetView);
    }
}
