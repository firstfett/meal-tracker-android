package com.fettbot.mealtracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReminderBridge {
    private static final String TAG = "ReminderBridge";
    private final Context context;
    private static final String CHANNEL_ID = "meal_reminders";
    private static final String PREFS = "reminders";

    public ReminderBridge(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Meal Reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminders for meals, shakes, and water");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF6B9E7B);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @JavascriptInterface
    public void testNotification() {
        Log.d(TAG, "testNotification called");
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("label", "Test Notification");
        intent.putExtra("id", 99);
        new ReminderReceiver().onReceive(context, intent);
    }

    @JavascriptInterface
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        // Check notification permission
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        sb.append("Notifications enabled: ").append(nm != null && nm.areNotificationsEnabled()).append("\n");

        // Check exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            boolean canExact = am != null && am.canScheduleExactAlarms();
            sb.append("Exact alarms allowed: ").append(canExact).append("\n");
            if (!canExact) {
                sb.append("⚠️ Reminders may be delayed up to 10 min. Tap 'Grant Exact Alarm' in settings.\n");
            }
        } else {
            sb.append("Exact alarms allowed: true (pre-S)\n");
        }

        // Check saved reminders
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String data = prefs.getString("data", "[]");
        try {
            JSONArray arr = new JSONArray(data);
            sb.append("Saved reminders: ").append(arr.length()).append("\n");
        } catch (Exception e) {
            sb.append("Saved reminders: error\n");
        }

        return sb.toString();
    }

    @JavascriptInterface
    public void openAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Cannot open alarm settings", e);
            }
        }
    }

    @JavascriptInterface
    public void openBatterySettings() {
        try {
            // Samsung One UI: open battery optimization exclusion for this app
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback: open general battery settings
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception e2) {
                Log.e(TAG, "Cannot open battery settings", e2);
            }
        }
    }

    @JavascriptInterface
    public boolean isBatteryOptimized() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager)
                context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName()) == false;
        }
        return false;
    }

    @JavascriptInterface
    public void setReminders(String json) {
        Log.d(TAG, "setReminders called with " + json.length() + " chars");
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString("data", json).apply();

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) { Log.e(TAG, "AlarmManager is null"); return; }

            // Cancel all existing (up to 50)
            for (int i = 0; i < 50; i++) {
                PendingIntent pi = PendingIntent.getBroadcast(context, i,
                    new Intent(context, ReminderReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            }

            JSONArray arr = new JSONArray(json);
            int scheduled = 0;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                if (!r.getBoolean("on")) continue;

                String[] parts = r.getString("time").split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                }

                Intent intent = new Intent(context, ReminderReceiver.class);
                intent.putExtra("label", r.getString("label"));
                intent.putExtra("id", i);
                intent.putExtra("hour", hour);
                intent.putExtra("minute", minute);

                PendingIntent pi = PendingIntent.getBroadcast(context, i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // Use exact alarms that work through Doze
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    // Fallback if exact alarm permission not granted
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                }
                scheduled++;
                Log.d(TAG, "Scheduled: " + r.getString("label") + " at " + hour + ":" + minute + " (ms=" + cal.getTimeInMillis() + ")");
            }
            Log.d(TAG, "Total scheduled: " + scheduled);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling reminders", e);
        }
    }

    @JavascriptInterface
    public String getReminders() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString("data", "[]");
    }
}
