package uk.smti.mdm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Updates of this app itself: when a newer version was built on the site, the phone downloads it and
 * installs it (silently on a managed device). Also on demand with the "update" command.
 */
final class Updates {

    private Updates() {
    }

    /** @return what happened, for the command result. */
    static String maybe(Context ctx, JSONObject latest, boolean force) {
        int have = Info.versionCode(ctx);
        int want = latest == null ? 0 : latest.optInt("versionCode");
        String url = latest == null ? "" : latest.optString("url");
        if (want <= have || url.isEmpty()) return "האפליקציה מעודכנת (גרסה " + Info.versionName(ctx) + ")";
        SharedPreferences p = ctx.getSharedPreferences("mdm_upd", Context.MODE_PRIVATE);
        long last = p.getLong("tried_" + want, 0);
        if (!force && System.currentTimeMillis() - last < 3600_000L) return "עדכון לגרסה " + latest.optString("version") + " כבר בדרך";
        p.edit().putLong("tried_" + want, System.currentTimeMillis()).apply();
        return "מעדכן לגרסה " + latest.optString("version") + ": " + Owner.install(ctx, url);
    }
}
