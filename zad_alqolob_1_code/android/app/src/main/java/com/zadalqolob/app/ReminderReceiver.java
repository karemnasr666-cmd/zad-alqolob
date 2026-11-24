package com.zadalqolob.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "zad_daily_reminders";

    public static void schedule(Context context, String id, String title, String message, int hour, int minute) {
        if (id == null || id.trim().isEmpty() || hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.zadalqolob.REMINDER." + id);
        intent.putExtra("title", title == null ? "تذكير من زاد القلوب" : title);
        intent.putExtra("message", message == null ? "حان وقت وردك اليومي" : message);
        intent.putExtra("reminder_id", id);
        int requestCode = id.hashCode() & 0x7fffffff;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(Calendar.getInstance())) next.add(Calendar.DAY_OF_YEAR, 1);
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pending);
    }

    public static void scheduleSalawat(Context context, int minutes) {
        if (minutes < 1 || minutes > 1440) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.zadalqolob.REMINDER.SALAWAT");
        intent.putExtra("salawat", true);
        PendingIntent pending = PendingIntent.getBroadcast(context, 7402, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + minutes * 60L * 1000L;
        if (Build.VERSION.SDK_INT >= 23) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        else manager.set(AlarmManager.RTC_WAKEUP, when, pending);
    }

    public static void cancel(Context context, String id) {
        if (id == null) return;
        if ("salawat".equals(id)) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent salawatIntent = new Intent(context, ReminderReceiver.class);
            salawatIntent.setAction("com.zadalqolob.REMINDER.SALAWAT");
            PendingIntent salawatPending = PendingIntent.getBroadcast(context, 7402, salawatIntent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (am != null && salawatPending != null) { am.cancel(salawatPending); salawatPending.cancel(); }
            return;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.zadalqolob.REMINDER." + id);
        PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode() & 0x7fffffff,
                intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getBooleanExtra("salawat", false)) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("zad_reminders", Context.MODE_PRIVATE);
            int minutes = prefs.getInt("salawat_minutes", 60);
            String voice = prefs.getString("salawat_voice", "1");
            speakSalawat(context, voice);
            scheduleSalawat(context, minutes);
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "تذكيرات زاد القلوب",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("تذكيرات الأذكار والختمة والورد اليومي");
            manager.createNotificationChannel(channel);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent contentIntent = launch == null ? null : PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null ? "زاد القلوب" : title)
                .setContentText(message == null ? "حان وقت وردك اليومي" : message)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        String id = intent.getStringExtra("reminder_id");
        manager.notify((id == null ? "zad" : id).hashCode(), builder.build());
    }
    private static void speakSalawat(Context context, String voice) {
        final android.os.PowerManager.WakeLock wakeLock = ((android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ZadAlQolob:SalawatTTS");
        wakeLock.acquire(10000L);
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(context, status -> {
            TextToSpeech engine = holder[0];
            if (engine == null) return;
            if (status == TextToSpeech.SUCCESS) {
                int lang = engine.setLanguage(new Locale("ar"));
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.shutdown(); return;
                }
                float pitch = "2".equals(voice) ? 0.88f : ("3".equals(voice) ? 1.12f : 1.0f);
                engine.setPitch(pitch);
                engine.setSpeechRate(0.92f);
                if (Build.VERSION.SDK_INT >= 21) engine.speak("صلِّ على محمد", TextToSpeech.QUEUE_FLUSH, null, "zad_salawat");
                else engine.speak("صلِّ على محمد", TextToSpeech.QUEUE_FLUSH, null);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    engine.shutdown(); if (wakeLock.isHeld()) wakeLock.release();
                }, 8000);
            } else { engine.shutdown(); if (wakeLock.isHeld()) wakeLock.release(); }
        });
    }

}
