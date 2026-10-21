package com.mijnboodschappen.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

public class NotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_WEEKLY_REMINDER =
            "com.mijnboodschappen.app.WEEKLY_SHOPPING_REMINDER";
    public static final String ACTION_PANTRY_ALERT =
            "com.mijnboodschappen.app.PANTRY_ALERT";
    public static final String EXTRA_ALERT = "alert";

    // Extra's waarmee een tik op de melding (of een knop) de app opent.
    public static final String EXTRA_ACTION = "mbAction";
    public static final String EXTRA_IDS = "mbIds";
    public static final String EXTRA_NOTIFICATION_ID = "mbNid";

    private static final String CHANNEL_ID = "shopping_reminders";
    static final String PANTRY_CHANNEL_ID = "pantry_alerts";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PANTRY_ALERT.equals(action)) {
            showPantryAlert(context, intent.getStringExtra(EXTRA_ALERT));
            return;
        }
        showWeeklyReminder(context);
    }

    private void showWeeklyReminder(Context context) {
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
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Tijd voor je boodschappen")
                .setContentText("Bekijk je boodschappenlijst en werk deze bij.")
                .setAutoCancel(true);
        if (contentIntent != null) builder.setContentIntent(contentIntent);

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(4101, builder.build());
    }

    private static PendingIntent openAppIntent(Context context, String action, String ids, int notificationId, int requestCode) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(EXTRA_ACTION, action);
        open.putExtra(EXTRA_IDS, ids == null ? "" : ids);
        open.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return PendingIntent.getActivity(
                context, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void showPantryAlert(Context context, String json) {
        JSONObject alert;
        try {
            alert = new JSONObject(json == null ? "{}" : json);
        } catch (Exception e) {
            return;
        }
        String title = alert.optString("title", "Voorraad");
        String text = alert.optString("text", "");
        String ids = alert.optString("ids", "");
        int notificationId = 6000 + (alert.optInt("nid", 0) & 0xfff);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PANTRY_CHANNEL_ID,
                    "Voorraad",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Meldingen over producten die bijna over de datum of bijna op zijn.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, PANTRY_CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
        }
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context, "open", ids, notificationId, notificationId * 10));

        JSONArray actions = alert.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length() && i < 3; i++) {
                JSONObject a = actions.optJSONObject(i);
                if (a == null) continue;
                String key = a.optString("a", "open");
                String label = a.optString("label", "Openen");
                PendingIntent pi = openAppIntent(context, key, ids, notificationId, notificationId * 10 + i + 1);
                builder.addAction(new android.app.Notification.Action.Builder(
                        R.drawable.ic_notification, label, pi).build());
            }
        }

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(notificationId, builder.build());
    }
}
