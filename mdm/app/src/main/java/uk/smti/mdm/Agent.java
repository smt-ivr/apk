package uk.smti.mdm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Always running while the phone is managed: checks in with the control center every few seconds
 * (policy + commands), applies changes, runs commands and reports results. A 15-minute job brings it back
 * if Android ever stops it.
 */
public class Agent extends Service {

    private static final int NOTE_ID = 7, JOB = 5301;
    private static volatile Agent instance;
    private Thread loop;
    private volatile boolean running;
    private final Object wake = new Object();
    private BroadcastReceiver screen;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, Agent.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) { }
        schedule(ctx);
    }

    /** Check in right now (after a change on the phone). */
    public static void kick(Context ctx) {
        Agent a = instance;
        if (a != null) synchronized (a.wake) { a.wake.notifyAll(); }
        else start(ctx);
    }

    static void schedule(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB) != null) return;
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(ctx, Watchdog.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60_000L).setPersisted(true).build());
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE_ID, n);
        if (!running) {
            running = true;
            instance = this;
            screen = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    synchronized (wake) { wake.notifyAll(); }
                }
            };
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            f.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            registerReceiver(screen, f);
            loop = new Thread(this::run, "mdm-agent");
            loop.start();
        }
        return START_STICKY;
    }

    private void run() {
        while (running) {
            try { checkIn(this); } catch (Throwable ignored) { }
            long wait = interval();
            synchronized (wake) {
                try { wake.wait(wait); } catch (InterruptedException e) { return; }
            }
        }
    }

    private long interval() {
        int sec = Store.get(this).policy().optInt("pollSeconds", 20);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean on = pm == null || pm.isInteractive();
        return (on ? Math.max(5, sec) : Math.max(60, sec * 4)) * 1000L;
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        try { if (screen != null) unregisterReceiver(screen); } catch (Exception ignored) { }
        if (loop != null) loop.interrupt();
        super.onDestroy();
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("agent", "ניהול המכשיר", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "agent") : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("המכשיר מנוהל")
                .setContentText(getString(R.string.app_name))
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    // ------------------------------------------------------------------ the check-in

    static synchronized String checkIn(Context ctx) {
        Store s = Store.get(ctx);
        if (s.appKey().isEmpty()) return "לא מחובר למרכז שליטה";
        try {
            JSONObject b = new JSONObject();
            b.put("deviceId", s.deviceId());
            b.put("info", Info.device(ctx));
            b.put("state", Info.state(ctx));
            JSONArray results = s.results();
            b.put("results", results);
            if (System.currentTimeMillis() - s.lastAppsReport() > 6 * 3600_000L) b.put("apps", Info.apps(ctx));
            if (s.takeLocationNew()) b.put("location", s.lastLocation());
            JSONObject r = post(s.server() + "/api/mdm/" + s.appKey() + "/sync", b);
            if (r == null || !r.optBoolean("ok")) return r == null ? "אין חיבור לשרת" : r.optString("error");
            s.clearResults(results.length());
            if (b.has("apps")) s.setLastAppsReport(System.currentTimeMillis());
            s.setLastSync(System.currentTimeMillis());
            JSONObject pol = r.optJSONObject("policy");
            if (pol != null && s.setPolicy(pol)) Owner.apply(ctx);
            JSONObject latest = r.optJSONObject("latest");
            if (latest != null && pol != null && pol.optBoolean("autoUpdate", true)) Updates.maybe(ctx, latest, false);
            JSONArray cmds = r.optJSONArray("commands");
            for (int i = 0; cmds != null && i < cmds.length(); i++) {
                JSONObject c = cmds.optJSONObject(i);
                if (c == null || !s.markDone(c.optLong("id"))) continue;
                Commands.run(ctx, c.optLong("id"), c.optString("cmd"), c.optJSONObject("args"), latest);
            }
            if (s.results().length() > 0) {
                // report results right away
                JSONObject b2 = new JSONObject().put("deviceId", s.deviceId()).put("results", s.results()).put("state", Info.state(ctx));
                if (s.takeLocationNew()) b2.put("location", s.lastLocation());
                JSONObject r2 = post(s.server() + "/api/mdm/" + s.appKey() + "/sync", b2);
                if (r2 != null && r2.optBoolean("ok")) s.clearResults(b2.getJSONArray("results").length());
            }
            return null;
        } catch (Exception e) {
            return "אין חיבור לשרת";
        }
    }

    static JSONObject post(String url, JSONObject body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
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

    /** Every 15 minutes: makes sure the agent runs, and checks in. */
    public static class Watchdog extends JobService {
        @Override
        public boolean onStartJob(JobParameters p) {
            Context ctx = getApplicationContext();
            new Thread(() -> {
                try {
                    if (instance == null) start(ctx);
                    checkIn(ctx);
                } finally {
                    jobFinished(p, false);
                }
            }).start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters p) {
            return true;
        }
    }
}
