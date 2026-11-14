package uk.smti.lock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-applies strong-mode rules after a restart or an update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Owner.apply(ctx);
    }
}
