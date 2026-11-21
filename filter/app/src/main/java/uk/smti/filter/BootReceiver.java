package uk.smti.filter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** After a restart or an update: the filter comes back on by itself. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store s = Store.get(ctx);
        Sync.schedule(ctx);
        if (s.wanted() && VpnService.prepare(ctx) == null) {
            try { FilterVpn.start(ctx); } catch (Exception e) { Notify.filterOff(ctx); }
        } else if (s.wanted()) {
            Notify.filterOff(ctx);
        }
        Owner.apply(ctx);
    }
}
