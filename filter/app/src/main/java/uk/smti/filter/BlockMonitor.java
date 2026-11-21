package uk.smti.filter;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides when a blocked lookup deserves the block page. Pages load many things in the background (ads,
 * statistics, parts of an approved site); only a site the person actually tried to open should pop up.
 * With "usage access": only while a browser is on screen. Without it: timing rules.
 */
final class BlockMonitor {

    private static final String[] NOISE = {
            "analytics", "doubleclick", "googlesyndication", "googleadservices", "adservice", "app-measurement", "crashlytics",
            "firebase", "googleapis", "gstatic", "ggpht", "googleusercontent", "facebook.net", "fbcdn", "metric", "telemetry",
            "sentry", "mtalk", "push", "track", "pixel", "beacon", "cdn", "static", "fonts", "ads.", "ad.", "stats", "log.",
            "logs.", "events", "update", "config", "api.", "play.google", "gvt1", "gvt2", "android.", "samsung", "xiaomi", "miui",
            "huawei", "oppo", "vivo", "whatsapp", "instagram", "tiktok", "msftconnecttest", "windowsupdate", "icloud", "apple",
    };

    private static long lastAllowed, lastShown;
    private static final Map<String, Long> shownAt = new HashMap<>();
    private static Set<String> browsers;

    private BlockMonitor() {
    }

    static void allowed() {
        lastAllowed = System.currentTimeMillis();
    }

    static void blocked(Context ctx, String domain) {
        Rules r = Store.get(ctx).rules();
        if (!r.blockPage) return;
        long now = System.currentTimeMillis();
        for (String n : NOISE) if (domain.contains(n)) return;
        if (now - lastShown < 6000) return;
        Long prev = shownAt.get(domain);
        if (prev != null && now - prev < 90_000) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        String fg = foregroundApp(ctx);
        if (fg != null) {
            if (!isBrowser(ctx, fg)) return;
        } else if (now - lastAllowed < 2500) {
            return; // most likely a part of an approved page
        }
        lastShown = now;
        shownAt.put(domain, now);
        if (canOverlay(ctx)) {
            Intent i = new Intent(ctx, BlockActivity.class)
                    .putExtra(BlockActivity.EXTRA_DOMAIN, domain)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try { ctx.startActivity(i); return; } catch (Exception ignored) { }
        }
        Notify.blocked(ctx, domain);
    }

    static boolean canOverlay(Context ctx) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
    }

    static boolean hasUsageAccess(Context ctx) {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** The app on screen (needs usage access), or null if unknown. */
    static String foregroundApp(Context ctx) {
        if (!hasUsageAccess(ctx)) return null;
        try {
            UsageStatsManager um = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 60 * 60_000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.getPackageName();
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized boolean isBrowser(Context ctx, String pkg) {
        if (browsers == null) {
            browsers = new HashSet<>();
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"));
                List<ResolveInfo> list = ctx.getPackageManager().queryIntentActivities(i, Build.VERSION.SDK_INT >= 23 ? PackageManager.MATCH_ALL : 0);
                for (ResolveInfo ri : list) browsers.add(ri.activityInfo.packageName);
            } catch (Exception ignored) {
            }
            browsers.add("com.android.chrome");
            browsers.add("org.mozilla.firefox");
            browsers.add("com.sec.android.app.sbrowser");
            browsers.add("com.opera.browser");
            browsers.add("com.microsoft.emmx");
            browsers.add("com.brave.browser");
        }
        return browsers.contains(pkg);
    }
}
