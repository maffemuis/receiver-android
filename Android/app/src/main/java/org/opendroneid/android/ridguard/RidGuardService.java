/*
 * Copyright (C) 2024 Intel Corporation
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.opendroneid.android.R;

public class RidGuardService extends Service {
    public static final String ACTION_START = "org.opendroneid.android.ridguard.START";
    public static final String ACTION_STOP = "org.opendroneid.android.ridguard.STOP";
    public static final String ACTION_SILENCE = "org.opendroneid.android.ridguard.SILENCE";

    private static final String TAG = "RidGuardService";
    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "rid_guard_scanning";

    private RidGuardRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = RidGuardRepository.getInstance(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            repository.stopScanning();
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SILENCE.equals(action)) {
            repository.getSettings().setSilenceForMinutes(30);
            updateNotification();
            return START_STICKY;
        }

        try {
            promoteToForeground();
            if (!repository.startScanning()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
            updateNotification();
            return START_STICKY;
        } catch (SecurityException | IllegalStateException exception) {
            Log.e(TAG, "Foreground scanner could not start", exception);
            repository.reportError(getString(R.string.rid_guard_service_start_failed));
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        repository.stopScanning();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void promoteToForeground() {
        int foregroundType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType);
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openIntent = new Intent(this, RidGuardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 1, openIntent, pendingIntentFlags);

        Intent silenceIntent = new Intent(this, RidGuardService.class).setAction(ACTION_SILENCE);
        PendingIntent silencePendingIntent = PendingIntent.getService(
                this, 2, silenceIntent, pendingIntentFlags);

        Intent stopIntent = new Intent(this, RidGuardService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 3, stopIntent, pendingIntentFlags);

        String transport = repository.getActiveTransports().getValue();
        String content = transport == null || "None".equals(transport)
                ? getString(R.string.rid_guard_scanning)
                : getString(R.string.rid_guard_scanning_transports, transport);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.rid_guard_active))
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(0, getString(R.string.rid_guard_silence_short), silencePendingIntent)
                .addAction(0, getString(R.string.rid_guard_stop), stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.rid_guard_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.rid_guard_channel_description));
            channel.setShowBadge(false);
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
