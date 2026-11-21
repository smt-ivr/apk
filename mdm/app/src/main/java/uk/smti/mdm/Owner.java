package uk.smti.mdm;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the app can enforce as the phone's device owner ("managed device"):
 * kiosk (lock task), Android restrictions, hidden / suspended apps, silent install and removal,
 * lock, reboot, wipe, and releasing everything when it is removed.
 */
public final class Owner {

    private Owner() {
    }

    public static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, AdminReceiver.class);
    }

    static DevicePolicyManager dpm(Context ctx) {
        return (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isOwner(Context ctx) {
        try { return dpm(ctx).isDeviceOwnerApp(ctx.getPackageName()); } catch (Exception e) { return false; }
    }

    public static boolean isAdmin(Context ctx) {
        try { return dpm(ctx).isAdminActive(admin(ctx)); } catch (Exception e) { return false; }
    }

    public static String adbCommand(Context ctx) {
        return "adb shell dpm set-device-owner " + ctx.getPackageName() + "/" + AdminReceiver.class.getName();
    }

    /** Makes this app the device owner with root (no computer needed). */
    public static Root.Result becomeOwnerWithRoot(Context ctx) {
        return Root.run("dpm set-device-owner " + ctx.getPackageName() + "/" + AdminReceiver.class.getName());
    }

    // ------------------------------------------------------------------ policy

    /** Applies the whole policy. Safe to call often; returns a short report. */
    public static String apply(Context ctx) {
        if (!isOwner(ctx)) return "לא מכשיר מנוהל";
        Store s = Store.get(ctx);
        JSONObject pol = s.policy();
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        StringBuilder rep = new StringBuilder();
        String me = ctx.getPackageName();

        try { d.setUninstallBlocked(a, me, true); } catch (Exception ignored) { }

        // restrictions: switch on the listed ones, clear those this app switched on before and are no longer listed
        JSONArray want = pol.optJSONArray("restrictions");
        Set<String> wantSet = toSet(want);
        JSONArray before = s.appliedRestrictions();
        for (int i = 0; i < before.length(); i++) {
            String r = before.optString(i);
            if (!wantSet.contains(r)) try { d.clearUserRestriction(a, r); } catch (Exception ignored) { }
        }
        JSONArray applied = new JSONArray();
        for (String r : wantSet) {
            try { d.addUserRestriction(a, r); applied.put(r); } catch (Exception e) { rep.append("לא נתמך: ").append(r).append('\n'); }
        }
        s.setAppliedRestrictions(applied);

        // hidden / suspended / protected apps
        syncPackages(ctx, s, "hidden", toSet(pol.optJSONArray("hidden")), (p, on) -> d.setApplicationHidden(a, p, on));
        if (Build.VERSION.SDK_INT >= 24) {
            syncPackages(ctx, s, "suspended", toSet(pol.optJSONArray("suspended")), (p, on) -> d.setPackagesSuspended(a, new String[]{p}, on));
        }
        syncPackages(ctx, s, "blockun", toSet(pol.optJSONArray("blockUninstall")), (p, on) -> d.setUninstallBlocked(a, p, on));

        // device switches
        try { d.setCameraDisabled(a, pol.optBoolean("cameraDisabled")); } catch (Exception ignored) { }
        try { d.setScreenCaptureDisabled(a, pol.optBoolean("screenCaptureDisabled")); } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 23) {
            try { d.setStatusBarDisabled(a, pol.optBoolean("statusBarDisabled")); } catch (Exception ignored) { }
            try { d.setKeyguardDisabled(a, pol.optBoolean("keyguardDisabled")); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 24) {
            String msg = pol.optString("lockMessage", "");
            try { d.setDeviceOwnerLockScreenInfo(a, msg.isEmpty() ? null : msg); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                d.setPermissionPolicy(a, pol.optBoolean("autoGrant", true) ? DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT : DevicePolicyManager.PERMISSION_POLICY_PROMPT);
                // the app itself may always find the phone and post notifications
                d.setPermissionGrantState(a, me, android.Manifest.permission.ACCESS_FINE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                d.setPermissionGrantState(a, me, android.Manifest.permission.ACCESS_COARSE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                if (Build.VERSION.SDK_INT >= 33) d.setPermissionGrantState(a, me, "android.permission.POST_NOTIFICATIONS", DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                d.setGlobalSetting(a, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, pol.optBoolean("stayOn") ? "7" : "0");
            } catch (Exception ignored) { }
        }

        applyKiosk(ctx);
        return rep.length() == 0 ? "הוחל" : rep.toString().trim();
    }

    interface PkgOp {
        void set(String pkg, boolean on) throws Exception;
    }

    private static void syncPackages(Context ctx, Store s, String key, Set<String> want, PkgOp op) {
        JSONArray before = s.appliedList(key);
        for (int i = 0; i < before.length(); i++) {
            String p = before.optString(i);
            if (!want.contains(p)) try { op.set(p, false); } catch (Exception ignored) { }
        }
        JSONArray now = new JSONArray();
        for (String p : want) {
            if (p.equals(ctx.getPackageName())) continue;
            try { op.set(p, true); now.put(p); } catch (Exception ignored) { }
        }
        s.setAppliedList(key, now);
    }

    static Set<String> toSet(JSONArray a) {
        Set<String> s = new HashSet<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String v = a.optString(i);
            if (!v.isEmpty()) s.add(v);
        }
        return s;
    }

    // ------------------------------------------------------------------ kiosk

    /** Kiosk on: only the chosen apps, our screen is the home screen, and the phone is locked to them. */
    public static void applyKiosk(Context ctx) {
        if (!isOwner(ctx)) return;
        Store s = Store.get(ctx);
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        JSONObject k = s.kiosk();
        boolean on = s.kioskActive();
        ComponentName home = new ComponentName(ctx, KioskActivity.class);
        try { d.clearPackagePersistentPreferredActivities(a, ctx.getPackageName()); } catch (Exception ignored) { }
        if (on) {
            List<String> pk = new ArrayList<>();
            pk.add(ctx.getPackageName());
            JSONArray apps = k.optJSONArray("apps");
            for (int i = 0; apps != null && i < apps.length(); i++) pk.add(apps.optString(i));
            try { d.setLockTaskPackages(a, pk.toArray(new String[0])); } catch (Exception ignored) { }
            if (Build.VERSION.SDK_INT >= 28) {
                int f = DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
                // home always works in kiosk, so the exit screen can always be reached
                f |= DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
                if (k.optBoolean("systemInfo", true)) f |= DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
                if (k.optBoolean("notifications")) f |= DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS | DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
                if (k.optBoolean("globalActions")) f |= DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
                if (k.optBoolean("keyguard")) f |= DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
                if (k.optBoolean("overview")) f |= DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW | DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
                try { d.setLockTaskFeatures(a, f); } catch (Exception ignored) { }
            }
            IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
            f.addCategory(Intent.CATEGORY_HOME);
            f.addCategory(Intent.CATEGORY_DEFAULT);
            try { d.addPersistentPreferredActivity(a, f, home); } catch (Exception ignored) { }
            ctx.getPackageManager().setComponentEnabledSetting(home, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            if (!inLockTask(ctx)) {
                try {
                    ctx.startActivity(new Intent(ctx, KioskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                } catch (Exception ignored) { }
            }
        } else {
            try { d.setLockTaskPackages(a, new String[0]); } catch (Exception ignored) { }
            ctx.getPackageManager().setComponentEnabledSetting(home, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            KioskActivity.finishIfOpen();
        }
    }

    public static boolean inLockTask(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            return Build.VERSION.SDK_INT >= 23 ? am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE : am.isInLockTaskMode();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ actions

    public static String lock(Context ctx) {
        try { dpm(ctx).lockNow(); return "המסך ננעל"; } catch (Exception e) { return "נכשל: " + e.getMessage(); }
    }

    public static String reboot(Context ctx) {
        if (isOwner(ctx) && Build.VERSION.SDK_INT >= 24) {
            try { dpm(ctx).reboot(admin(ctx)); return "מפעיל מחדש"; } catch (Exception ignored) { }
        }
        if (Root.granted()) return Root.run("svc power reboot || reboot").ok ? "מפעיל מחדש (רוט)" : "נכשל";
        return "דורש מכשיר מנוהל (אנדרואיד 7+) או רוט";
    }

    public static String wipe(Context ctx) {
        if (!isOwner(ctx)) return "דורש מכשיר מנוהל";
        try { dpm(ctx).wipeData(0); return "מאפס"; } catch (Exception e) { return "נכשל: " + e.getMessage(); }
    }

    public static String clearData(Context ctx, String pkg) {
        if (!isOwner(ctx) || Build.VERSION.SDK_INT < 28) {
            if (Root.granted()) return Root.run("pm clear " + pkg).out;
            return "דורש מכשיר מנוהל (אנדרואיד 9+) או רוט";
        }
        try {
            dpm(ctx).clearApplicationUserData(admin(ctx), pkg, ctx.getMainExecutor(), (p, ok) -> { });
            return "הנתונים נמחקו";
        } catch (Exception e) {
            return "נכשל: " + e.getMessage();
        }
    }

    /** Downloads an APK and installs it silently. */
    public static String install(Context ctx, String url) {
        HttpURLConnection c = null;
        PackageInstaller.Session session = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            if (c.getResponseCode() >= 400) return "ההורדה נכשלה (" + c.getResponseCode() + ")";
            PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(params);
            session = pi.openSession(id);
            long total = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("app.apk", 0, -1)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
                session.fsync(out);
            }
            Intent done = new Intent(ctx, InstallReceiver.class);
            PendingIntent p = PendingIntent.getBroadcast(ctx, id, done, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            session.commit(p.getIntentSender());
            session.close();
            return "הורד (" + (total / 1024) + "KB) ונשלח להתקנה" + (isOwner(ctx) ? " שקטה" : "");
        } catch (Exception e) {
            if (session != null) session.abandon();
            return "נכשל: " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String uninstall(Context ctx, String pkg) {
        if (Store.get(ctx).policy().optBoolean("useRoot", true) && !isOwner(ctx) && Root.granted()) {
            return Root.run("pm uninstall " + pkg).out;
        }
        try {
            Intent done = new Intent(ctx, InstallReceiver.class);
            PendingIntent p = PendingIntent.getBroadcast(ctx, pkg.hashCode(), done, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            if (isOwner(ctx)) try { dpm(ctx).setUninstallBlocked(admin(ctx), pkg, false); } catch (Exception ignored) { }
            ctx.getPackageManager().getPackageInstaller().uninstall(pkg, p.getIntentSender());
            return "נשלח להסרה" + (isOwner(ctx) ? " שקטה" : "");
        } catch (Exception e) {
            return "נכשל: " + e.getMessage();
        }
    }

    /**
     * Removes the app completely: every restriction and kiosk is released, management ends, and the app is
     * uninstalled (silently with root; otherwise Android shows its confirmation).
     */
    public static String removeCompletely(Context ctx, Activity from) {
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        Store s = Store.get(ctx);
        if (isOwner(ctx)) {
            try { d.clearPackagePersistentPreferredActivities(a, ctx.getPackageName()); } catch (Exception ignored) { }
            try { d.setLockTaskPackages(a, new String[0]); } catch (Exception ignored) { }
            JSONArray r = s.appliedRestrictions();
            for (int i = 0; i < r.length(); i++) try { d.clearUserRestriction(a, r.optString(i)); } catch (Exception ignored) { }
            for (String key : new String[]{"hidden", "suspended", "blockun"}) {
                JSONArray l = s.appliedList(key);
                for (int i = 0; i < l.length(); i++) {
                    String p = l.optString(i);
                    try {
                        if (key.equals("hidden")) d.setApplicationHidden(a, p, false);
                        else if (key.equals("suspended") && Build.VERSION.SDK_INT >= 24) d.setPackagesSuspended(a, new String[]{p}, false);
                        else d.setUninstallBlocked(a, p, false);
                    } catch (Exception ignored) { }
                }
            }
            try { d.setCameraDisabled(a, false); } catch (Exception ignored) { }
            try { d.setScreenCaptureDisabled(a, false); } catch (Exception ignored) { }
            if (Build.VERSION.SDK_INT >= 23) {
                try { d.setStatusBarDisabled(a, false); } catch (Exception ignored) { }
                try { d.setKeyguardDisabled(a, false); } catch (Exception ignored) { }
            }
            if (Build.VERSION.SDK_INT >= 24) try { d.setDeviceOwnerLockScreenInfo(a, null); } catch (Exception ignored) { }
            try { d.setUninstallBlocked(a, ctx.getPackageName(), false); } catch (Exception ignored) { }
            try { d.clearDeviceOwnerApp(ctx.getPackageName()); } catch (Exception ignored) { }
        }
        try { d.removeActiveAdmin(a); } catch (Exception ignored) { }
        s.setKioskPaused(true);
        if (Root.granted()) {
            // give the report a moment, then remove silently
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                Root.run("pm uninstall " + ctx.getPackageName());
            }).start();
            return "כל ההגבלות שוחררו; מסיר עם רוט";
        }
        try {
            Intent del = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            (from != null ? from : ctx).startActivity(del);
        } catch (Exception ignored) { }
        return "כל ההגבלות שוחררו; בטלפון מוצג אישור להסרה";
    }

    /** Result of a silent install / uninstall. */
    public static class InstallReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(confirm); } catch (Exception ignored) { }
                }
            }
            Store.get(ctx).forceAppsReport();
            Agent.kick(ctx);
        }
    }
}
