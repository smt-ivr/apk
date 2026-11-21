package uk.smti.filter;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Talks to the control center: reports the phone's state (filter on/off, protections, counters, recent
 * blocks) and receives the current rules. Runs every 15 minutes, on start, and right after changes.
 */
public final class Sync {

    public interface Done {
        void done(boolean ok, String error);
    }

    private static final int JOB = 4201;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private Sync() {
    }

    public static void schedule(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB) != null) return;
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(ctx, Job.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build());
    }

    /** Report soon, in the background. */
    public static void soon(Context ctx) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> run(app)).start();
    }

    public static void now(Context ctx, Done cb) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String err = run(app);
            ui.post(() -> cb.done(err == null, err));
        }).start();
    }

    /** null = ok, otherwise a message. */
    static synchronized String run(Context ctx) {
        Store s = Store.get(ctx);
        if (s.filterId().isEmpty()) return "האפליקציה לא מחוברת למרכז שליטה";
        try {
            JSONObject b = new JSONObject();
            b.put("deviceId", s.deviceId());
            b.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            b.put("android", Build.VERSION.RELEASE);
            b.put("version", versionName(ctx));
            b.put("rulesVersion", s.rules().version);
            b.put("running", FilterVpn.isRunning());
            b.put("wanted", s.wanted());
            b.put("paused", s.paused());
            b.put("stoppedAt", s.stoppedAt());
            JSONObject prot = new JSONObject();
            prot.put("overlay", BlockMonitor.canOverlay(ctx));
            prot.put("usage", BlockMonitor.hasUsageAccess(ctx));
            prot.put("admin", Owner.isAdmin(ctx));
            prot.put("owner", Owner.isOwner(ctx));
            prot.put("alwaysOn", Owner.alwaysOnState(ctx));
            prot.put("notifications", notificationsOn(ctx));
            prot.put("battery", batteryExempt(ctx));
            b.put("protections", prot);
            b.put("blockedToday", s.blockedToday());
            b.put("allowedToday", s.allowedToday());
            b.put("blockedTotal", s.blockedTotal());
            JSONArray rec = s.recent();
            JSONArray top = new JSONArray();
            for (int i = 0; i < rec.length() && i < 40; i++) top.put(rec.get(i));
            b.put("recent", top);
            if (System.currentTimeMillis() - s.lastAppsReport() > 12 * 3600_000L) b.put("apps", apps(ctx));
            JSONObject r = post(s.server() + "/api/filter/" + s.filterId() + "/sync", b);
            if (r == null) return "אין חיבור לשרת";
            if (!r.optBoolean("ok")) return r.optString("error", "שגיאה");
            if (b.has("apps")) s.setLastAppsReport(System.currentTimeMillis());
            JSONObject rules = r.optJSONObject("rules");
            if (rules != null) {
                boolean bypassChanged = !String.valueOf(rules.optJSONArray("bypassApps")).equals(String.valueOf(s.rulesJson().optJSONArray("bypassApps")));
                s.setRules(rules);
                if (bypassChanged) FilterVpn.reload(ctx);
            }
            s.setMessage(r.optString("message", ""));
            s.setLastSync(System.currentTimeMillis());
            Owner.apply(ctx);
            FilterVpn.refreshOngoing(ctx);
            return null;
        } catch (Exception e) {
            return "אין חיבור לשרת";
        }
    }

    /** "Ask to approve this site". */
    public static void request(Context ctx, String domain, String note, Done cb) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Store s = Store.get(app);
            String err = null;
            try {
                JSONObject b = new JSONObject().put("deviceId", s.deviceId()).put("domain", domain).put("note", note == null ? "" : note)
                        .put("model", Build.MANUFACTURER + " " + Build.MODEL);
                JSONObject r = post(s.server() + "/api/filter/" + s.filterId() + "/request", b);
                if (r == null) err = "אין חיבור לשרת";
                else if (!r.optBoolean("ok")) err = r.optString("error", "שגיאה");
            } catch (Exception e) {
                err = "אין חיבור לשרת";
            }
            String fe = err;
            ui.post(() -> cb.done(fe == null, fe));
        }).start();
    }

    private static JSONObject post(String url, JSONObject body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            c.setRequestProperty("user-agent", "SmtFilter/" + Build.VERSION.RELEASE);
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int st = c.getResponseCode();
            InputStream in = st >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static JSONArray apps(Context ctx) {
        JSONArray a = new JSONArray();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : list) {
                String p = ri.activityInfo.packageName;
                if (p.equals(ctx.getPackageName()) || !seen.add(p)) continue;
                a.put(new JSONObject().put("p", p).put("n", String.valueOf(ri.loadLabel(pm))));
                if (a.length() >= 300) break;
            }
        } catch (Exception ignored) {
        }
        return a;
    }

    static String versionName(Context ctx) {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    static boolean notificationsOn(Context ctx) {
        try { return ((android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled(); } catch (Exception e) { return false; }
    }

    static boolean batteryExempt(Context ctx) {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** Every 15 minutes: report, get rules, and switch the filter back on if it should be on. */
    public static class Job extends JobService {
        @Override
        public boolean onStartJob(JobParameters params) {
            Context ctx = getApplicationContext();
            new Thread(() -> {
                try {
                    Store s = Store.get(ctx);
                    if (s.wanted() && !FilterVpn.isRunning() && android.net.VpnService.prepare(ctx) == null) {
                        try { FilterVpn.start(ctx); } catch (Exception ignored) { }
                    }
                    run(ctx);
                } finally {
                    jobFinished(params, false);
                }
            }).start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return true;
        }
    }

}
