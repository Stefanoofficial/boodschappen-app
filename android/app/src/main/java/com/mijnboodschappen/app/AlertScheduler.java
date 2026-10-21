package com.mijnboodschappen.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Plant de lokale meldingen van de app (wekkers op de telefoon zelf, geen server).
 * - Voorraadmeldingen: lijst met tijdstippen die de app (index.html) berekent.
 * - Wekelijkse boodschappenherinnering.
 * Alles wordt ook in SharedPreferences bewaard, zodat het na een herstart opnieuw wordt ingepland.
 */
public final class AlertScheduler {
    static final String PREFS = "mb_alerts";
    static final String KEY_ALERTS = "pantry_alerts";
    static final String KEY_COUNT = "pantry_count";
    static final String KEY_WEEKLY_DAY = "weekly_day";
    static final String KEY_WEEKLY_TIME = "weekly_time";
    static final int PANTRY_BASE = 5000;
    static final int PANTRY_MAX = 40;
    static final int WEEKLY_REQUEST_CODE = 2001;

    private AlertScheduler() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent pantryIntent(Context context, int index, JSONObject alert) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(NotificationReceiver.ACTION_PANTRY_ALERT);
        if (alert != null) intent.putExtra(NotificationReceiver.EXTRA_ALERT, alert.toString());
        return PendingIntent.getBroadcast(
                context, PANTRY_BASE + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** Vervangt alle voorraadmeldingen door de meegegeven lijst (JSON-array). */
    static int setPantryAlerts(Context context, String json) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = prefs(context);

        // Eerst alle oude wekkers weghalen.
        for (int i = 0; i < PANTRY_MAX; i++) {
            PendingIntent old = pantryIntent(context, i, null);
            if (alarmManager != null) alarmManager.cancel(old);
            old.cancel();
        }

        JSONArray input;
        try {
            input = new JSONArray(json == null ? "[]" : json);
        } catch (Exception e) {
            input = new JSONArray();
        }

        JSONArray kept = new JSONArray();
        long now = System.currentTimeMillis();
        int count = 0;
        for (int i = 0; i < input.length() && count < PANTRY_MAX; i++) {
            JSONObject alert = input.optJSONObject(i);
            if (alert == null) continue;
            long at = alert.optLong("at", 0L);
            if (at <= now) continue;
            if (alarmManager != null) {
                PendingIntent pi = pantryIntent(context, count, alert);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
            kept.put(alert);
            count++;
        }
        prefs.edit().putString(KEY_ALERTS, kept.toString()).putInt(KEY_COUNT, count).apply();
        return count;
    }

    private static PendingIntent weeklyIntent(Context context) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(NotificationReceiver.ACTION_WEEKLY_REMINDER);
        return PendingIntent.getBroadcast(
                context, WEEKLY_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** Wekelijkse herinnering. day: 1 = zondag … 7 = zaterdag (zoals java.util.Calendar). */
    static void scheduleWeekly(Context context, String day, String time) {
        String[] parts = (time == null ? "18:00" : time).split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        int requestedDay = Integer.parseInt(day);

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.DAY_OF_WEEK, requestedDay);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        calendar.set(java.util.Calendar.MINUTE, minute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 7);
        }

        PendingIntent pendingIntent = weeklyIntent(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            // Wekelijkse herinnering; Android kan deze tijdens batterijbesparing iets uitstellen.
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7L,
                    pendingIntent
            );
        }
        prefs(context).edit().putString(KEY_WEEKLY_DAY, day).putString(KEY_WEEKLY_TIME, time).apply();
    }

    static void cancelWeekly(Context context) {
        PendingIntent pendingIntent = weeklyIntent(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        prefs(context).edit().remove(KEY_WEEKLY_DAY).remove(KEY_WEEKLY_TIME).apply();
    }

    /** Na het opnieuw opstarten van de telefoon of een update van de app: alles opnieuw inplannen. */
    static void rescheduleAll(Context context) {
        SharedPreferences prefs = prefs(context);
        setPantryAlerts(context, prefs.getString(KEY_ALERTS, "[]"));
        String day = prefs.getString(KEY_WEEKLY_DAY, null);
        String time = prefs.getString(KEY_WEEKLY_TIME, null);
        if (day != null && time != null) {
            try {
                scheduleWeekly(context, day, time);
            } catch (Exception ignored) {
            }
        }
    }
}
