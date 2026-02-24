package com.fettbot.mealtracker;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String label = intent.getStringExtra("label");
        int id = intent.getIntExtra("id", 0);
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        if (label == null) label = "Meal Tracker Reminder";

        Log.d(TAG, "Notification fired: " + label + " (id=" + id + ", hour=" + hour + ", min=" + minute + ")");

        String emoji = "\uD83D\uDD14"; // 🔔
        String lower = label.toLowerCase();
        if (lower.contains("water")) emoji = "\uD83D\uDCA7"; // 💧
        else if (lower.contains("shake")) emoji = "\uD83E\uDD64"; // 🥤
        else if (lower.contains("meal") || lower.contains("lunch") || lower.contains("dinner")) emoji = "\uD83C\uDF7D"; // 🍽️
        else if (lower.contains("snack")) emoji = "\uD83E\uDD5C"; // 🥜
        else if (lower.contains("test")) emoji = "\u2705"; // ✅

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "meal_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(emoji + " " + label)
            .setContentText("Time for your " + label.toLowerCase() + "!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(id, builder.build());
            Log.d(TAG, "Notification posted for id=" + id);
        }

        // Re-schedule for tomorrow using exact hour/minute (fixes drift bug)
        if (hour >= 0 && minute >= 0) {
            rescheduleForTomorrow(context, label, id, hour, minute);
        } else {
            Log.w(TAG, "No hour/minute extras — cannot reschedule id=" + id);
        }
    }

    private void rescheduleForTomorrow(Context context, String label, int id, int hour, int minute) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.putExtra("label", label);
            intent.putExtra("id", id);
            intent.putExtra("hour", hour);
            intent.putExtra("minute", minute);

            PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                Log.w(TAG, "Rescheduled id=" + id + " with inexact alarm (no permission)");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
            Log.d(TAG, "Rescheduled id=" + id + " for tomorrow at " + hour + ":" + minute);
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling", e);
        }
    }
}
