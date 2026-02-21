package com.fettbot.mealtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-schedule all reminders after reboot
            SharedPreferences prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE);
            String data = prefs.getString("data", null);
            if (data != null) {
                new ReminderBridge(context).setReminders(data);
            }
        }
    }
}
