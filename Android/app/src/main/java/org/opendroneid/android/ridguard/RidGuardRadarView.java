/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.opendroneid.android.ridguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.LocationData;

import java.util.Collections;
import java.util.List;

public class RidGuardRadarView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<AircraftObject> aircraft = Collections.emptyList();
    private Location receiverLocation;
    private int maxRangeMeters = RidGuardSettings.DEFAULT_RADIUS_METERS;

    public RidGuardRadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.GRAY);
        ringPaint.setStrokeWidth(2f);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.RED);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.WHITE);
    }

    public void updateData(List<AircraftObject> aircraft, Location receiverLocation, int maxRangeMeters) {
        this.aircraft = aircraft != null ? aircraft : Collections.emptyList();
        this.receiverLocation = receiverLocation;
        this.maxRangeMeters = Math.max(50, maxRangeMeters);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) * 0.9f;

        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius * 0.66f, ringPaint);
        canvas.drawCircle(cx, cy, radius * 0.33f, ringPaint);
        canvas.drawCircle(cx, cy, 6f, centerPaint);

        for (AircraftObject aircraftObject : aircraft) {
            LocationData location = aircraftObject.getLocation();
            if (location == null) {
                continue;
            }
            float distance = location.getDistance();
            if (distance <= 0) {
                continue;
            }
            float normalized = Math.min(distance / maxRangeMeters, 1f);
            float bearing = 0f;
            if (receiverLocation != null) {
                Location droneLocation = new Location("drone");
                droneLocation.setLatitude(location.getLatitude());
                droneLocation.setLongitude(location.getLongitude());
                bearing = receiverLocation.bearingTo(droneLocation);
            }
            double radians = Math.toRadians(bearing - 90);
            float r = radius * normalized;
            float x = cx + (float) (r * Math.cos(radians));
            float y = cy + (float) (r * Math.sin(radians));
            canvas.drawCircle(x, y, 8f, dotPaint);
        }
    }
}
