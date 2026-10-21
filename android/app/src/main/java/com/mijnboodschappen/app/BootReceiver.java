package com.mijnboodschappen.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wekkers verdwijnen bij een herstart van de telefoon; daarom plannen we ze hier opnieuw in. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AlertScheduler.rescheduleAll(context);
        }
    }
}
