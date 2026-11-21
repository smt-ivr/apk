package uk.smti.mdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart or an update: management (and kiosk) come back by themselves. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Agent.start(ctx);
        if (Owner.isOwner(ctx)) Owner.apply(ctx);
    }
}
