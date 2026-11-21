package uk.smti.filter;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;

/**
 * Protection against removal.
 * Device admin (one tap in the app): the app can't simply be uninstalled.
 * Strong mode (device owner, once from a computer): the filter is a permanent VPN that can't be switched
 * off, the app can't be removed, VPN settings, safe mode and other VPN apps are blocked.
 */
public final class Owner {

    private Owner() {
    }

    public static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, AdminReceiver.class);
    }

    private static DevicePolicyManager dpm(Context ctx) {
        return (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isAdmin(Context ctx) {
        try { return dpm(ctx).isAdminActive(admin(ctx)); } catch (Exception e) { return false; }
    }

    public static boolean isOwner(Context ctx) {
        try { return dpm(ctx).isDeviceOwnerApp(ctx.getPackageName()); } catch (Exception e) { return false; }
    }

    public static String adbCommand(Context ctx) {
        return "adb shell dpm set-device-owner " + ctx.getPackageName() + "/" + AdminReceiver.class.getName();
    }

    /** "on" / "lockdown" (always-on + block without VPN) / "off" / "unknown". */
    public static String alwaysOnState(Context ctx) {
        try {
            if (isOwner(ctx) && Build.VERSION.SDK_INT >= 24) {
                String p = dpm(ctx).getAlwaysOnVpnPackage(admin(ctx));
                if (!ctx.getPackageName().equals(p)) return "off";
                return Build.VERSION.SDK_INT >= 29 && dpm(ctx).isAlwaysOnVpnLockdownEnabled(admin(ctx)) ? "lockdown" : "on";
            }
            String app = Settings.Secure.getString(ctx.getContentResolver(), "always_on_vpn_app");
            if (app == null) return "unknown";
            if (!ctx.getPackageName().equals(app)) return "off";
            return Settings.Secure.getInt(ctx.getContentResolver(), "always_on_vpn_lockdown", 0) == 1 ? "lockdown" : "on";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Applies strong mode. Safe to call often; does nothing without device owner. */
    public static void apply(Context ctx) {
        if (!isOwner(ctx)) return;
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        try { d.setUninstallBlocked(a, ctx.getPackageName(), true); } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 24) {
            try { d.setAlwaysOnVpnPackage(a, ctx.getPackageName(), true); } catch (Exception ignored) { }
        }
        String[] restrictions = {UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_DEBUGGING_FEATURES};
        for (String r : restrictions) {
            try { d.addUserRestriction(a, r); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_PRIVATE_DNS); } catch (Exception ignored) { }
        }
    }

    /** Ends strong mode (only after the code): everything is released and the app can be removed. */
    public static void release(Context ctx) {
        DevicePolicyManager d = dpm(ctx);
        ComponentName a = admin(ctx);
        if (isOwner(ctx)) {
            try { d.setUninstallBlocked(a, ctx.getPackageName(), false); } catch (Exception ignored) { }
            if (Build.VERSION.SDK_INT >= 24) {
                try { d.setAlwaysOnVpnPackage(a, null, false); } catch (Exception ignored) { }
            }
            String[] restrictions = {UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_DEBUGGING_FEATURES};
            for (String r : restrictions) {
                try { d.clearUserRestriction(a, r); } catch (Exception ignored) { }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_PRIVATE_DNS); } catch (Exception ignored) { }
            }
            try { d.clearDeviceOwnerApp(ctx.getPackageName()); } catch (Exception ignored) { }
        }
        try { d.removeActiveAdmin(a); } catch (Exception ignored) { }
    }

    public static Intent askAdmin(Context ctx) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "ההרשאה מונעת הסרה של הסינון בלי הקוד.");
    }
}
