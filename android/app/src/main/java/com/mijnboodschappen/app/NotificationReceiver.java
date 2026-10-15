package com.mijnboodschappen.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class NotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_WEEKLY_REMINDER =
            "com.mijnboodschappen.app.WEEKLY_SHOPPING_REMINDER";
    private static final String CHANNEL_ID = "shopping_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Boodschappenherinneringen",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Wekelijkse herinnering om je boodschappenlijst te bekijken.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        PendingIntent contentIntent = null;
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            contentIntent = PendingIntent.getActivity(
                    context, 3001, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context)
                    .setPriority(android.app.Notification.PRIORITY_HIGH);
        }
        builder.setSmallIcon(com.mijnboodschappen.app.R.mipmap.ic_launcher)
                .setContentTitle("Tijd voor je boodschappen")
                .setContentText("Bekijk je boodschappenlijst en werk deze bij.")
                .setAutoCancel(true);
        if (contentIntent != null) builder.setContentIntent(contentIntent);

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(4101, builder.build());
    }
}
