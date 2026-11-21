package uk.smti.mdm;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** The device admin / owner. After QR setup, management (and kiosk, if set) starts right away. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context ctx, Intent intent) {
        Owner.apply(ctx);
        Agent.start(ctx);
    }

    @Override
    public void onProfileProvisioningComplete(Context ctx, Intent intent) {
        Owner.apply(ctx);
        Agent.start(ctx);
        Intent i = new Intent(ctx, Store.get(ctx).kioskActive() ? KioskActivity.class : MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        Agent.kick(ctx);
        return "כדי להסיר בצורה מסודרת: פתחו את האפליקציה ← \"הסרה מלאה\" עם הקוד.";
    }
}
