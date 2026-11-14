package uk.smti.lock;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Device admin and device owner ("strong mode") operations.
 * Strong mode is switched on once from a computer:
 *   adb shell dpm set-device-owner PACKAGE/uk.smti.lock.AdminReceiver
 */
public final class Owner {

    /** Strong-mode restrictions the user can switch on, with defaults. */
    public static final String[][] RESTRICTIONS = {
            // key, Android restriction, label, default
            {"safe_boot", UserManager.DISALLOW_SAFE_BOOT, "חסימת מצב בטוח (שבו ההגנה לא פועלת)", "1"},
            {"debug", UserManager.DISALLOW_DEBUGGING_FEATURES, "חסימת חיבור למחשב (מצב מפתחים)", "0"},
            {"install", UserManager.DISALLOW_INSTALL_APPS, "חסימת התקנת אפליקציות חדשות", "0"},
            {"unknown", UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "חסימת התקנה מקבצים (APK)", "0"},
            {"accounts", UserManager.DISALLOW_MODIFY_ACCOUNTS, "חסימת הוספת חשבונות", "0"},
            {"reset", UserManager.DISALLOW_FACTORY_RESET, "חסימת איפוס להגדרות יצרן (זהירות!)", "0"},
    };

    private Owner() {
    }

    public static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, AdminReceiver.class);
    }

    private static DevicePolicyManager dpm(Context ctx) {
        return (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isAdmin(Context ctx) {
        return dpm(ctx).isAdminActive(admin(ctx));
    }

    public static boolean isOwner(Context ctx) {
        return dpm(ctx).isDeviceOwnerApp(ctx.getPackageName());
    }

    public static String adbCommand(Context ctx) {
        return "adb shell dpm set-device-owner " + ctx.getPackageName() + "/" + AdminReceiver.class.getName();
    }

    /** Applies everything strong mode can enforce. Safe to call often; does nothing otherwise. */
    public static void apply(Context ctx) {
        if (!isOwner(ctx)) return;
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        Store s = Store.get(ctx);
        try {
            d.setUninstallBlocked(a, ctx.getPackageName(), true);
        } catch (Exception ignored) {
        }
        // Fully blocked apps are suspended by the system (greyed out, can't open).
        List<String> block = new ArrayList<>();
        List<String> open = new ArrayList<>();
        for (Map.Entry<String, Integer> e : s.rules().entrySet()) {
            if (e.getValue() == Store.BLOCK) block.add(e.getKey());
        }
        for (String p : s.previouslySuspended()) if (!block.contains(p)) open.add(p);
        try {
            if (!block.isEmpty()) d.setPackagesSuspended(a, block.toArray(new String[0]), true);
            if (!open.isEmpty()) d.setPackagesSuspended(a, open.toArray(new String[0]), false);
        } catch (Exception ignored) {
        }
        s.setPreviouslySuspended(block);
        for (String[] r : RESTRICTIONS) {
            try {
                if (s.flag(r[0], "1".equals(r[3]))) d.addUserRestriction(a, r[1]);
                else d.clearUserRestriction(a, r[1]);
            } catch (Exception ignored) {
            }
        }
    }

    /** Removes every protection so the app can be uninstalled (only after the PIN). */
    public static void release(Context ctx) {
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        Store s = Store.get(ctx);
        if (isOwner(ctx)) {
            try {
                List<String> sus = s.previouslySuspended();
                if (!sus.isEmpty()) d.setPackagesSuspended(a, sus.toArray(new String[0]), false);
                s.setPreviouslySuspended(new ArrayList<>());
                for (String[] r : RESTRICTIONS) d.clearUserRestriction(a, r[1]);
                d.setUninstallBlocked(a, ctx.getPackageName(), false);
                d.clearDeviceOwnerApp(ctx.getPackageName());
            } catch (Exception ignored) {
            }
        }
        try {
            if (d.isAdminActive(a)) d.removeActiveAdmin(a);
        } catch (Exception ignored) {
        }
    }
}
