package uk.smti.filter;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device admin: the filter can't simply be uninstalled; as device owner it can't be removed or switched off at all. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context ctx, Intent intent) {
        Owner.apply(ctx);
        Sync.soon(ctx);
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        Sync.soon(ctx);
        return "ביטול ההרשאה יאפשר להסיר את הסינון, והמנהל יקבל על כך דיווח. כדי להסיר בצורה מסודרת: פתחו את האפליקציה והזינו את הקוד.";
    }

    @Override
    public void onDisabled(Context ctx, Intent intent) {
        Sync.soon(ctx);
    }
}
