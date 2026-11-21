package uk.smti.mdm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What the phone reports: device details, management state, its apps, and where it is. */
final class Info {

    private Info() {
    }

    static int versionCode(Context ctx) {
        try { return (int) ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    static String versionName(Context ctx) {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    static JSONObject device(Context ctx) {
        JSONObject i = new JSONObject();
        try {
            i.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            i.put("android", Build.VERSION.RELEASE);
            i.put("sdk", Build.VERSION.SDK_INT);
            i.put("version", versionName(ctx));
            i.put("versionCode", versionCode(ctx));
            android.os.BatteryManager bm = (android.os.BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            i.put("battery", bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY));
            if (Build.VERSION.SDK_INT >= 23) i.put("charging", bm.isCharging());
        } catch (Exception ignored) { }
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            i.put("network", nc == null ? "none" : nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                    : nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular" : "other");
        } catch (Exception ignored) { }
        try {
            android.os.StatFs fs = new android.os.StatFs(ctx.getFilesDir().getAbsolutePath());
            i.put("storageFreeMb", fs.getAvailableBytes() / 1048576);
            i.put("storageTotalMb", fs.getTotalBytes() / 1048576);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            i.put("ramMb", mi.totalMem / 1048576);
        } catch (Exception ignored) { }
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            i.put("screenOn", pm.isInteractive());
        } catch (Exception ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 26 && Owner.isOwner(ctx)) i.put("serial", Build.getSerial());
        } catch (Exception ignored) { }
        return i;
    }

    static JSONObject state(Context ctx) {
        JSONObject s = new JSONObject();
        Store st = Store.get(ctx);
        try {
            s.put("owner", Owner.isOwner(ctx));
            s.put("admin", Owner.isAdmin(ctx));
            s.put("rootPresent", Root.present());
            s.put("root", Root.present() && Root.granted());
            s.put("kioskWanted", st.kiosk().optBoolean("enabled"));
            s.put("kioskPaused", st.kioskPaused());
            s.put("inLockTask", Owner.inLockTask(ctx));
            s.put("policyVersion", st.policy().optInt("version"));
            s.put("restrictions", st.appliedRestrictions().length());
        } catch (Exception ignored) { }
        return s;
    }

    static JSONArray apps(Context ctx) {
        JSONArray a = new JSONArray();
        try {
            PackageManager pm = ctx.getPackageManager();
            Set<String> launchable = new HashSet<>();
            List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            for (ResolveInfo ri : l) launchable.add(ri.activityInfo.packageName);
            int flags = PackageManager.GET_META_DATA | (Build.VERSION.SDK_INT >= 24 ? PackageManager.MATCH_UNINSTALLED_PACKAGES : 0);
            for (ApplicationInfo ai : pm.getInstalledApplications(flags)) {
                boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean hidden = Owner.isOwner(ctx) && isHidden(ctx, ai.packageName);
                if (sys && !launchable.contains(ai.packageName) && !hidden) continue; // skip background system parts
                a.put(new JSONObject().put("p", ai.packageName).put("n", String.valueOf(pm.getApplicationLabel(ai))).put("s", sys).put("h", hidden));
                if (a.length() >= 400) break;
            }
        } catch (Exception ignored) { }
        return a;
    }

    private static boolean isHidden(Context ctx, String pkg) {
        try { return Owner.dpm(ctx).isApplicationHidden(Owner.admin(ctx), pkg); } catch (Exception e) { return false; }
    }

    /** Last known position, and asks for a fresh one (reported on the next check-in). */
    static JSONObject locate(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                @SuppressWarnings("MissingPermission") Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestSingleUpdate(p, new android.location.LocationListener() {
                        public void onLocationChanged(Location l) {
                            try {
                                Store.get(ctx).setLastLocation(new JSONObject().put("lat", l.getLatitude()).put("lng", l.getLongitude())
                                        .put("acc", Math.round(l.getAccuracy())).put("t", l.getTime()));
                                Agent.kick(ctx);
                            } catch (Exception ignored) { }
                        }
                        public void onStatusChanged(String p, int s, android.os.Bundle b) { }
                        public void onProviderEnabled(String p) { }
                        public void onProviderDisabled(String p) { }
                    }, android.os.Looper.getMainLooper());
                }
            }
            if (best != null) {
                o.put("lat", best.getLatitude()).put("lng", best.getLongitude()).put("acc", Math.round(best.getAccuracy())).put("t", best.getTime());
                Store.get(ctx).setLastLocation(o);
            }
        } catch (SecurityException e) {
            try { o.put("error", "אין הרשאת מיקום"); } catch (Exception ignored) { }
        } catch (Exception e) {
            try { o.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
        }
        return o;
    }
}
