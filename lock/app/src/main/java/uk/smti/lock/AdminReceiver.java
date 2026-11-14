package uk.smti.lock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device admin. Active admin = can't simply uninstall; device owner = can't uninstall at all. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context ctx, Intent intent) {
        Owner.apply(ctx);
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        return "ביטול ההרשאה יאפשר להסיר את הנעילה. כדי להסיר את האפליקציה, פתחו אותה והזינו את הקוד.";
    }
}
