package uk.smti.filter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** The filter's home screen: status, protections checklist, recently blocked sites and actions. */
public class MainActivity extends Activity {

    private static final int REQ_VPN = 11, REQ_ADMIN = 12, REQ_NOTIFY = 13;
    private LinearLayout col;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            h.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.NAVY);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 28));
        scroll.addView(col);
        setContentView(scroll);
        Sync.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
        // first open: switch the filter on straight away
        if (!Store.get(this).wanted() && !FilterVpn.isRunning()) startFilter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store s = Store.get(this);
        if (s.wanted() && !FilterVpn.isRunning() && VpnService.prepare(this) == null) FilterVpn.start(this);
        if (FilterVpn.isRunning()) Notify.clearOff(this);
        Sync.now(this, (ok, err) -> render());
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        Store s = Store.get(this);
        col.removeAllViews();
        boolean on = FilterVpn.isRunning(), paused = s.paused();

        // ---- hero ----
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        int top = !on ? 0xFF7F1D1D : paused ? 0xFF78350F : Ui.NAVY;
        int bottom = !on ? 0xFFB91C1C : paused ? 0xFFB45309 : Ui.NAVY2;
        GradientDrawable hb = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        float r = Ui.dp(this, 28);
        hb.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        hero.setBackground(hb);
        hero.setPadding(Ui.dp(this, 22), Ui.dp(this, 26), Ui.dp(this, 22), Ui.dp(this, 26));
        TextView name = Ui.text(this, getString(R.string.app_name), 14, 0xB3FFFFFF, true);
        hero.addView(name);
        FrameLayout disc = new FrameLayout(this);
        GradientDrawable dd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                on && !paused ? new int[]{0xFF34D399, 0xFF059669} : new int[]{0xFFFCA5A5, 0xFFDC2626});
        dd.setShape(GradientDrawable.OVAL);
        disc.setBackground(dd);
        disc.setElevation(Ui.dp(this, 10));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_shield);
        ic.setColorFilter(Color.WHITE);
        disc.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46), Gravity.CENTER));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92));
        dlp.topMargin = Ui.dp(this, 18);
        hero.addView(disc, dlp);
        TextView st = Ui.text(this, !on ? "הסינון כבוי" : paused ? "הסינון מושהה" : "הסינון פעיל", 26, Color.WHITE, true);
        hero.addView(st, Ui.wideCenter(this, 14));
        String sub = !on ? "האתרים לא מסוננים. לחצו להפעלה." : paused ? "מושהה עד " + time(s.pausedUntil()) : "רק אתרים מאושרים נפתחים במכשיר";
        TextView sb = Ui.text(this, sub, 14, 0xD9FFFFFF, false);
        sb.setGravity(Gravity.CENTER);
        hero.addView(sb, Ui.wideCenter(this, 4));

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, Ui.dp(this, 18), 0, 0);
        stats.addView(stat(String.valueOf(s.blockedToday()), "נחסמו היום"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(stat(String.valueOf(s.rules().allowCount()), "אתרים מאושרים"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(stat(Ui.ago(s.lastSync()), "עדכון אחרון"), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(stats, new LinearLayout.LayoutParams(-1, -2));

        Button main;
        if (!on) {
            main = whiteButton("הפעלת הסינון");
            main.setOnClickListener(v -> startFilter());
        } else if (paused && s.pausedUntil() > System.currentTimeMillis()) {
            main = whiteButton("חידוש הסינון עכשיו");
            main.setOnClickListener(v -> {
                s.setPausedUntil(0);
                FilterVpn.refreshOngoing(this);
                render();
            });
        } else {
            main = whiteButton("השהיה זמנית (עם קוד)");
            main.setOnClickListener(v -> pause());
        }
        LinearLayout.LayoutParams mlp = Ui.wide(this, 20, 0);
        hero.addView(main, mlp);
        col.addView(hero);

        int p = Ui.dp(this, 16);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, Ui.dp(this, 4), p, 0);
        col.addView(body);

        // ---- message from the manager ----
        if (!s.message().isEmpty()) {
            LinearLayout msg = Ui.card(this);
            msg.setBackground(Ui.round(0xFFFFF7E0, Ui.dp(this, 18)));
            msg.addView(Ui.text(this, "📣 הודעה מהמנהל", 14, Ui.WARN, true));
            msg.addView(Ui.text(this, s.message(), 15, Ui.TEXT, false), Ui.wide(this, 4, 0));
            body.addView(msg, Ui.wide(this, 16, 0));
        }

        // ---- protections ----
        body.addView(section("הגנות"));
        LinearLayout prot = Ui.card(this);
        prot.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        prot.addView(check(R.drawable.ic_shield, Ui.BLUE, "סינון (VPN)", on ? "פעיל" : "כבוי", on, v -> startFilter()));
        String ao = Owner.alwaysOnState(this);
        boolean aoOk = "lockdown".equals(ao);
        prot.addView(check(R.drawable.ic_lock, Ui.VIOLET, "VPN תמידי + חסימה בלי VPN",
                aoOk ? "מופעל: בלי הסינון אין אינטרנט" : "on".equals(ao) ? "תמידי מופעל, צריך גם \"חסימת חיבורים ללא VPN\"" : "מומלץ: מונע כיבוי וגלישה בלי סינון",
                aoOk, v -> alwaysOnHelp()));
        boolean ov = BlockMonitor.canOverlay(this);
        prot.addView(check(R.drawable.ic_block, Ui.BAD, "דף חסימה מעל הדפדפן", ov ? "מאושר" : "צריך לאשר \"הצגה מעל אפליקציות אחרות\"", ov, v -> {
            if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }));
        boolean us = BlockMonitor.hasUsageAccess(this);
        prot.addView(check(R.drawable.ic_eye, Ui.TEAL, "דף חסימה חכם", us ? "מוצג רק כשגולשים בדפדפן" : "אשרו \"גישה לנתוני שימוש\" כדי שלא יקפוץ סתם", us, v -> {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception ignored) { }
        }));
        boolean ad = Owner.isAdmin(this);
        prot.addView(check(R.drawable.ic_lock, Ui.GOLD, "הגנה מהסרה", ad ? "אי אפשר להסיר בלי הקוד" : "לחצו לאישור הרשאת ניהול", ad, v -> {
            if (!ad) startActivityForResult(Owner.askAdmin(this), REQ_ADMIN);
        }));
        boolean bt = Sync.batteryExempt(this);
        prot.addView(check(R.drawable.ic_bolt, Ui.WARN, "פועל ברקע תמיד", bt ? "חיסכון בסוללה לא עוצר את הסינון" : "לחצו כדי שחיסכון בסוללה לא יכבה", bt, v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { } }
        }));
        boolean ow = Owner.isOwner(this);
        prot.addView(check(R.drawable.ic_star, Ui.NAVY2, "מצב חזק (מכשיר מנוהל)", ow ? "פעיל: אי אפשר לכבות או להסיר" : "ההגנה החזקה ביותר · הפעלה חד-פעמית ממחשב", ow, v -> strongHelp()));
        body.addView(prot);

        // ---- recently blocked ----
        JSONArray rec = s.recent();
        body.addView(section("נחסמו לאחרונה"));
        LinearLayout rc = Ui.card(this);
        rc.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        if (rec.length() == 0) {
            TextView none = Ui.text(this, "עדיין לא נחסם אף אתר", 14, Ui.TEXT3, false);
            none.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), 0, Ui.dp(this, 12));
            rc.addView(none);
        }
        for (int i = 0; i < rec.length() && i < 12; i++) {
            JSONObject o = rec.optJSONObject(i);
            if (o == null) continue;
            String d = o.optString("d");
            TextView when = Ui.text(this, Ui.ago(o.optLong("t")), 12, Ui.TEXT3, false);
            LinearLayout row = Ui.row(this, R.drawable.ic_block, Ui.SLATE, d, "לחצו לבקשת אישור", when);
            row.setOnClickListener(v -> startActivity(new Intent(this, BlockActivity.class).putExtra(BlockActivity.EXTRA_DOMAIN, d)));
            rc.addView(row);
        }
        body.addView(rc);

        // ---- actions ----
        body.addView(section("פעולות"));
        LinearLayout act = Ui.card(this);
        act.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        LinearLayout sync = Ui.row(this, R.drawable.ic_refresh, Ui.GOOD, "עדכון רשימת האתרים", "מקבל עכשיו את השינויים ממרכז השליטה", null);
        sync.setOnClickListener(v -> Sync.now(this, (ok, err) -> {
            Toast.makeText(this, ok ? "הרשימה עודכנה ✓" : err, Toast.LENGTH_SHORT).show();
            render();
        }));
        act.addView(sync);
        LinearLayout pin = Ui.row(this, R.drawable.ic_key, Ui.GOLD, "שינוי הקוד", "במכשיר הזה", null);
        pin.setOnClickListener(v -> Pin.change(this));
        act.addView(pin);
        if (on) {
            LinearLayout stop = Ui.row(this, R.drawable.ic_power, Ui.BAD, "כיבוי הסינון", "דורש קוד · המנהל יקבל דיווח", null);
            stop.setOnClickListener(v -> Pin.ask(this, "כיבוי הסינון", () -> {
                if (Owner.isOwner(this)) {
                    Toast.makeText(this, "במצב חזק אי אפשר לכבות. אפשר להשהות זמנית.", Toast.LENGTH_LONG).show();
                    return;
                }
                startService(new Intent(this, FilterVpn.class).setAction(FilterVpn.ACTION_STOP));
                Sync.soon(this);
                h.postDelayed(this::render, 600);
            }));
            act.addView(stop);
        }
        LinearLayout remove = Ui.row(this, R.drawable.ic_trash, Ui.SLATE, "הסרת הסינון מהמכשיר", "דורש קוד", null);
        remove.setOnClickListener(v -> Pin.ask(this, "הסרת הסינון", this::uninstall));
        act.addView(remove);
        body.addView(act);

        TextView foot = Ui.text(this, "גרסה " + Sync.versionName(this) + " · מזהה מכשיר " + s.deviceId().substring(0, 6), 12, Ui.TEXT3, false);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot, Ui.wide(this, 18, 0));
    }

    private View stat(String value, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, value, 19, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        l.addView(v);
        TextView t = Ui.text(this, label, 12, 0xB3FFFFFF, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        return l;
    }

    private Button whiteButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15.5f);
        b.setTextColor(Ui.NAVY);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackground(Ui.round(Color.WHITE, Ui.dp(this, 16)));
        b.setStateListAnimator(null);
        b.setMinHeight(Ui.dp(this, 52));
        return b;
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 14, Ui.TEXT2, true);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        return t;
    }

    private LinearLayout check(int icon, int hue, String title, String sub, boolean ok, View.OnClickListener fix) {
        LinearLayout r = Ui.row(this, icon, hue, title, sub, Ui.chip(this, ok ? "✓" : "לתיקון", ok ? Ui.GOOD : Ui.WARN));
        if (!ok) r.setOnClickListener(fix);
        return r;
    }

    private static String time(long t) {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date(t));
    }

    // ------------------------------------------------------------------ actions

    private void startFilter() {
        Intent i = VpnService.prepare(this);
        if (i != null) {
            try {
                startActivityForResult(i, REQ_VPN);
            } catch (Exception e) {
                Toast.makeText(this, "לא ניתן להפעיל VPN במכשיר הזה", Toast.LENGTH_LONG).show();
            }
        } else {
            FilterVpn.start(this);
            Notify.clearOff(this);
            h.postDelayed(this::render, 700);
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_VPN) {
            if (result == RESULT_OK) {
                FilterVpn.start(this);
                Notify.clearOff(this);
                h.postDelayed(this::render, 700);
            } else {
                Toast.makeText(this, "צריך לאשר את ה-VPN כדי שהסינון יעבוד", Toast.LENGTH_LONG).show();
            }
        } else if (code == REQ_ADMIN) {
            Sync.soon(this);
            render();
        }
    }

    private void pause() {
        Pin.ask(this, "השהיית הסינון", () -> {
            String[] opts = {"15 דקות", "30 דקות", "שעה", "3 שעות"};
            long[] mins = {15, 30, 60, 180};
            new AlertDialog.Builder(this).setTitle("להשהות את הסינון ל…").setItems(opts, (d, w) -> {
                Store.get(this).setPausedUntil(System.currentTimeMillis() + mins[w] * 60_000L);
                FilterVpn.refreshOngoing(this);
                Sync.soon(this);
                render();
            }).show();
        });
    }

    private void alwaysOnHelp() {
        new AlertDialog.Builder(this)
                .setTitle("VPN תמידי")
                .setMessage("כך הסינון לא נכבה, ובלעדיו אין אינטרנט בכלל:\n\n"
                        + "1. במסך שייפתח, לחצו על ⚙️ ליד \"" + getString(R.string.app_name) + "\".\n"
                        + "2. הפעילו \"VPN תמידי\" (Always-on VPN).\n"
                        + "3. הפעילו \"חסימת חיבורים ללא VPN\".\n\nבסיום חזרו לאפליקציה.")
                .setPositiveButton("פתיחת הגדרות VPN", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); } catch (Exception ignored) { }
                })
                .setNegativeButton("סגירה", null)
                .show();
    }

    private void strongHelp() {
        String cmd = Owner.adbCommand(this);
        new AlertDialog.Builder(this)
                .setTitle("מצב חזק")
                .setMessage("במצב חזק הסינון קבוע: אי אפשר לכבות אותו, להסיר את האפליקציה, לשנות הגדרות VPN או להיכנס למצב בטוח.\n\n"
                        + "ההפעלה נעשית פעם אחת ממחשב, בטלפון שאין בו חשבונות (בדרך כלל אחרי איפוס):\n"
                        + "1. מפעילים בטלפון \"אפשרויות מפתחים\" ו\"ניפוי באגים ב-USB\".\n"
                        + "2. מחברים למחשב ומריצים את הפקודה:\n\n" + cmd)
                .setPositiveButton("העתקת הפקודה", (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb", cmd));
                    Toast.makeText(this, "הפקודה הועתקה", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("סגירה", null)
                .show();
    }

    private void uninstall() {
        Store.get(this).setWanted(false);
        startService(new Intent(this, FilterVpn.class).setAction(FilterVpn.ACTION_STOP));
        Owner.release(this);
        Sync.soon(this);
        h.postDelayed(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "אפשר עכשיו להסיר את האפליקציה מההגדרות", Toast.LENGTH_LONG).show();
            }
        }, 800);
    }
}
