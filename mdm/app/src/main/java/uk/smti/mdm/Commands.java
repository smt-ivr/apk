package uk.smti.mdm;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

/** Runs one remote command from the control center and stores the result for the next check-in. */
final class Commands {

    private Commands() {
    }

    static void run(Context ctx, long id, String cmd, JSONObject args, JSONObject latest) {
        if (args == null) args = new JSONObject();
        Store s = Store.get(ctx);
        String res;
        boolean ok = true;
        try {
            switch (cmd) {
                case "lock":
                    res = Owner.lock(ctx);
                    break;
                case "reboot":
                    s.addResult(id, true, "מפעיל מחדש");
                    Agent.checkIn(ctx);
                    res = Owner.reboot(ctx);
                    break;
                case "ring":
                    ui(() -> ctx.startActivity(new Intent(ctx, MessageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("ring", true).putExtra("text", "")));
                    res = "מצלצל";
                    break;
                case "message": {
                    JSONObject a = args;
                    ui(() -> ctx.startActivity(new Intent(ctx, MessageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("title", a.optString("title", "הודעה")).putExtra("text", a.optString("text"))));
                    res = "ההודעה הוצגה";
                    break;
                }
                case "screen_on": {
                    PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                    @SuppressWarnings("deprecation") PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "mdm:screen");
                    wl.acquire(10_000);
                    res = "המסך הודלק";
                    break;
                }
                case "locate": {
                    JSONObject l = Info.locate(ctx);
                    res = l.has("lat") ? "מיקום: " + l.optDouble("lat") + "," + l.optDouble("lng") + " (±" + l.optInt("acc") + "מ׳)" : "מבקש מיקום… " + l.optString("error", "");
                    ok = !l.has("error");
                    break;
                }
                case "info":
                    s.forceAppsReport();
                    res = "הפרטים יישלחו";
                    break;
                case "install":
                    res = Owner.install(ctx, args.optString("url"));
                    ok = !res.startsWith("נכשל") && !res.startsWith("ההורדה");
                    break;
                case "update":
                    res = Updates.maybe(ctx, latest, true);
                    break;
                case "uninstall":
                    res = Owner.uninstall(ctx, args.optString("pkg"));
                    break;
                case "launch": {
                    Intent li = ctx.getPackageManager().getLaunchIntentForPackage(args.optString("pkg"));
                    if (li == null) { res = "האפליקציה לא נמצאה"; ok = false; }
                    else { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ui(() -> ctx.startActivity(li)); res = "נפתחה"; }
                    break;
                }
                case "clear_data":
                    res = Owner.clearData(ctx, args.optString("pkg"));
                    break;
                case "kiosk_pause":
                    s.setKioskPaused(true);
                    ui(() -> Owner.applyKiosk(ctx));
                    res = "הקיוסק הושהה במכשיר";
                    break;
                case "kiosk_resume":
                    s.setKioskPaused(false);
                    ui(() -> Owner.applyKiosk(ctx));
                    res = s.kiosk().optBoolean("enabled") ? "הקיוסק חזר" : "הקיוסק כבוי במדיניות";
                    break;
                case "wipe":
                    s.addResult(id, true, "מאפס את המכשיר");
                    Agent.checkIn(ctx);
                    res = Owner.wipe(ctx);
                    break;
                case "remove":
                    s.addResult(id, true, "מסיר את הניהול ואת האפליקציה");
                    Agent.checkIn(ctx);
                    res = Owner.removeCompletely(ctx, null);
                    break;
                case "root":
                    if (!s.policy().optBoolean("useRoot", true)) { res = "שימוש ברוט כבוי במדיניות"; ok = false; }
                    else if (!Root.granted()) { res = "אין רוט במכשיר"; ok = false; }
                    else { Root.Result r = Root.run(args.optString("cmd")); res = r.out.isEmpty() ? (r.ok ? "בוצע" : "נכשל") : r.out; ok = r.ok; }
                    break;
                default:
                    res = "פקודה לא מוכרת";
                    ok = false;
            }
        } catch (Exception e) {
            res = "שגיאה: " + e.getMessage();
            ok = false;
        }
        s.addResult(id, ok, res);
    }

    private static void ui(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
}
