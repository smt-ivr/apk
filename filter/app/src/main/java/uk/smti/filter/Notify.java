package uk.smti.filter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** The filter's notifications: "filter on" (always there while it runs), "filter off!" and blocked sites. */
final class Notify {

    static final int ONGOING_ID = 1, OFF_ID = 2;

    private Notify() {
    }

    private static Notification.Builder builder(Context ctx, String channel) {
        FilterVpn.channels(ctx);
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, channel) : new Notification.Builder(ctx);
    }

    private static PendingIntent openApp(Context ctx) {
        return PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Notification ongoing(Context ctx) {
        Store s = Store.get(ctx);
        String text = s.paused() ? "הסינון מושהה זמנית" : "נחסמו היום " + s.blockedToday() + " · מאושרים " + s.rules().allowCount() + " אתרים";
        return builder(ctx, "status")
                .setSmallIcon(R.drawable.ic_shield)
                .setColor(0xFF1F5FAD)
                .setContentTitle(s.paused() ? "⏸ הסינון מושהה" : "🛡️ הסינון פעיל")
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(openApp(ctx))
                .build();
    }

    static void filterOff(Context ctx) {
        try {
            Notification n = builder(ctx, "alerts")
                    .setSmallIcon(R.drawable.ic_shield)
                    .setColor(0xFFDC2626)
                    .setContentTitle("⚠️ הסינון כבוי")
                    .setContentText("לחצו כדי להפעיל אותו מחדש")
                    .setOngoing(true)
                    .setContentIntent(openApp(ctx))
                    .build();
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(OFF_ID, n);
        } catch (Exception ignored) {
        }
    }

    static void clearOff(Context ctx) {
        try { ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(OFF_ID); } catch (Exception ignored) { }
    }

    /** Without "show over other apps": the block page opens from a notification instead. */
    static void blocked(Context ctx, String domain) {
        try {
            Intent i = new Intent(ctx, BlockActivity.class).putExtra(BlockActivity.EXTRA_DOMAIN, domain).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, domain.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = builder(ctx, "blocked")
                    .setSmallIcon(R.drawable.ic_shield)
                    .setColor(0xFF1F5FAD)
                    .setContentTitle("🚫 האתר חסום")
                    .setContentText(domain + " · לחצו לבקשת אישור")
                    .setAutoCancel(true)
                    .setTimeoutAfter(60_000)
                    .setContentIntent(pi)
                    .build();
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify("blocked", 3, n);
        } catch (Exception ignored) {
        }
    }
}
