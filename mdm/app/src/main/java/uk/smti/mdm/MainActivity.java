package uk.smti.mdm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Status, first setup (managed device: QR / root / computer), and the code-protected actions: exit kiosk, full removal. */
public class MainActivity extends Activity {

    private LinearLayout col;
    private final Handler h = new Handler(Looper.getMainLooper());

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
        Agent.start(this);
        if (Owner.isOwner(this)) Owner.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        new Thread(() -> {
            Agent.checkIn(this);
            h.post(this::render);
        }).start();
    }

    private void render() {
        Store s = Store.get(this);
        col.removeAllViews();
        boolean owner = Owner.isOwner(this);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable hb = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.NAVY, Ui.NAVY2});
        float r = Ui.dp(this, 28);
        hb.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        hero.setBackground(hb);
        hero.setPadding(Ui.dp(this, 22), Ui.dp(this, 26), Ui.dp(this, 22), Ui.dp(this, 26));
        hero.addView(Ui.text(this, getString(R.string.app_name), 14, 0xB3FFFFFF, true));
        FrameLayout disc = new FrameLayout(this);
        GradientDrawable dd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, owner ? new int[]{0xFF34D399, 0xFF059669} : new int[]{0xFFFCD34D, 0xFFD97706});
        dd.setShape(GradientDrawable.OVAL);
        disc.setBackground(dd);
        disc.setElevation(Ui.dp(this, 10));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_shield);
        ic.setColorFilter(Color.WHITE);
        disc.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88));
        dlp.topMargin = Ui.dp(this, 16);
        hero.addView(disc, dlp);
        hero.addView(Ui.text(this, owner ? "המכשיר מנוהל" : "צריך להפעיל ניהול", 25, Color.WHITE, true), Ui.wideCenter(this, 14));
        String sub = owner ? (s.kioskActive() ? "מצב קיוסק פעיל" : s.kiosk().optBoolean("enabled") ? "הקיוסק מושהה במכשיר" : "שליטה מלאה מרחוק")
                : "הניהול המלא נדלק פעם אחת: ברקוד, רוט או מחשב";
        TextView st = Ui.text(this, sub, 14, 0xD9FFFFFF, false);
        st.setGravity(Gravity.CENTER);
        hero.addView(st, Ui.wideCenter(this, 4));
        TextView sync = Ui.text(this, "חיבור למרכז השליטה: " + (s.lastSync() > 0 ? Ui.ago(s.lastSync()) : "עדיין לא"), 12.5f, 0x99FFFFFF, false);
        hero.addView(sync, Ui.wideCenter(this, 10));
        col.addView(hero);

        int p = Ui.dp(this, 16);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, 0, p, 0);
        col.addView(body);

        if (!owner) {
            body.addView(section("הפעלת ניהול מלא"));
            LinearLayout set = Ui.card(this);
            set.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            if (Root.present()) {
                LinearLayout rootRow = Ui.row(this, R.drawable.ic_bolt, Ui.GOOD, "הפעלה עם רוט", "לחיצה אחת, בלי מחשב", null);
                rootRow.setOnClickListener(v -> withRoot());
                set.addView(rootRow);
            }
            LinearLayout adb = Ui.row(this, R.drawable.ic_key, Ui.BLUE, "הפעלה ממחשב", "פקודה אחת (העתקה)", null);
            adb.setOnClickListener(v -> adbHelp());
            set.addView(adb);
            LinearLayout qr = Ui.row(this, R.drawable.ic_star, Ui.VIOLET, "התקנה בברקוד", "בטלפון אחרי איפוס: 6 נגיעות במסך הפתיחה וסריקת הברקוד מהאתר", null);
            qr.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("התקנה בברקוד")
                    .setMessage("1. מאפסים את הטלפון להגדרות יצרן.\n2. במסך \"ברוכים הבאים\" נוגעים 6 פעמים באותו מקום.\n3. מתחברים ל-WiFi וסורקים את הברקוד שבמרכז השליטה.\n\nהאפליקציה מותקנת לבד כמנהלת המכשיר, ומצב הקיוסק נדלק מייד אם הוגדר.")
                    .setPositiveButton("הבנתי", null).show());
            set.addView(qr);
            TextView note = Ui.text(this, "הפעלה ברוט עובדת גם כשיש חשבון Google מחובר — אם צריך, היא תבקש הפעלה מחדש. הפעלה ממחשב דורשת שלא יהיה חשבון מחובר באותו רגע.", 12.5f, Ui.TEXT3, false);
            note.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
            set.addView(note);
            LinearLayout diag = Ui.row(this, R.drawable.ic_eye, Ui.SLATE, "שגיאות ופרטי מכשיר", "להעתקה ושליחה אם משהו נכשל", null);
            diag.setOnClickListener(v -> showError("פרטי מכשיר", null));
            set.addView(diag);
            body.addView(set);
        }

        body.addView(section("פעולות (עם קוד)"));
        LinearLayout act = Ui.card(this);
        act.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        if (s.kiosk().optBoolean("enabled") && owner) {
            if (s.kioskPaused()) {
                LinearLayout back = Ui.row(this, R.drawable.ic_lock, Ui.BLUE, "חזרה לקיוסק", "נעילה מחדש לאפליקציות שנבחרו", null);
                back.setOnClickListener(v -> {
                    s.setKioskPaused(false);
                    Owner.applyKiosk(this);
                    Agent.kick(this);
                });
                act.addView(back);
            } else {
                LinearLayout exit = Ui.row(this, R.drawable.ic_lock, Ui.WARN, "יציאה מהקיוסק", "עד שתחזיר או עד הוראה ממרכז השליטה", null);
                exit.setOnClickListener(v -> Pin.ask(this, "יציאה מהקיוסק", () -> {
                    s.setKioskPaused(true);
                    Owner.applyKiosk(this);
                    Agent.kick(this);
                    render();
                }));
                act.addView(exit);
            }
        }
        LinearLayout now = Ui.row(this, R.drawable.ic_refresh, Ui.GOOD, "בדיקה עכשיו", "מקבל את ההגדרות והפקודות ממרכז השליטה", null);
        now.setOnClickListener(v -> new Thread(() -> {
            String err = Agent.checkIn(this);
            h.post(() -> {
                Toast.makeText(this, err == null ? "עודכן ✓" : err, Toast.LENGTH_SHORT).show();
                render();
            });
        }).start());
        act.addView(now);
        LinearLayout pin = Ui.row(this, R.drawable.ic_key, Ui.GOLD, "שינוי הקוד במכשיר", "אפשר גם ממרכז השליטה", null);
        pin.setOnClickListener(v -> Pin.change(this));
        act.addView(pin);
        LinearLayout remove = Ui.row(this, R.drawable.ic_trash, Ui.BAD, "הסרה מלאה", "משחרר את כל ההגבלות, יוצא מהקיוסק ומסיר את האפליקציה", null);
        remove.setOnClickListener(v -> Pin.ask(this, "הסרה מלאה", () -> new AlertDialog.Builder(this)
                .setTitle("להסיר לגמרי?")
                .setMessage("כל ההגבלות והקיוסק יבוטלו, הניהול מרחוק יסתיים והאפליקציה תוסר מהטלפון.")
                .setPositiveButton("הסרה", (d, w) -> {
                    String res = Owner.removeCompletely(this, this);
                    Toast.makeText(this, res, Toast.LENGTH_LONG).show();
                    render();
                })
                .setNegativeButton("ביטול", null).show()));
        act.addView(remove);
        body.addView(act);

        TextView foot = Ui.text(this, "גרסה " + Info.versionName(this) + " · אנדרואיד " + android.os.Build.VERSION.RELEASE
                + " · מזהה " + s.deviceId().substring(0, 6) + (Root.present() ? " · רוט זמין" : ""), 12, Ui.TEXT3, false);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot, Ui.wide(this, 18, 0));
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 14, Ui.TEXT2, true);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        return t;
    }

    private void withRoot() {
        Toast.makeText(this, "מבקש הרשאת רוט…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = Root.granted();
            Root.Result r = ok ? Owner.becomeOwnerWithRoot(this) : null;
            h.post(() -> {
                if (!ok) {
                    showError("אין הרשאת רוט", "לא התקבלה הרשאת רוט מאפליקציית הרוט (Magisk / KernelSU וכו׳). אשרו את הבקשה ונסו שוב.");
                } else if (Owner.isOwner(this)) {
                    Owner.apply(this);
                    Agent.kick(this);
                    Toast.makeText(this, "הניהול המלא הופעל ✓", Toast.LENGTH_LONG).show();
                } else if (r.out != null && r.out.startsWith("NEEDS-REBOOT")) {
                    // bypass prepared (accounts were present): needs a restart to take effect
                    new AlertDialog.Builder(this)
                            .setTitle("כמעט סיימנו — צריך הפעלה מחדש")
                            .setMessage("יש חשבון מחובר בטלפון, אז הגדרנו את הניהול בדרך עוקפת (בלי למחוק חשבונות). ההפעלה תושלם אחרי הפעלה מחדש של הטלפון.")
                            .setPositiveButton("הפעלה מחדש עכשיו", (d, w) -> new Thread(() -> {
                                String m = Owner.rootReboot(this);
                                h.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
                            }).start())
                            .setNegativeButton("אפעיל בעצמי", null)
                            .show();
                } else {
                    showError("ההפעלה לא הצליחה", r.out == null || r.out.isEmpty() ? "לא התקבלה תשובה מהמערכת." : r.out);
                }
                render();
            });
        }).start();
    }

    /** Diagnostics the user can read and copy, with device details added automatically. */
    private String diagnostics(String error) {
        String accounts;
        try {
            android.accounts.Account[] a = android.accounts.AccountManager.get(this).getAccounts();
            accounts = String.valueOf(a.length);
        } catch (Throwable e) {
            accounts = "לא ידוע";
        }
        return "אפליקציה: " + getString(R.string.app_name) + " · גרסה " + Info.versionName(this) + " (" + Info.versionCode(this) + ")\n"
                + "אנדרואיד: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")\n"
                + "מכשיר: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n"
                + "רוט: " + (Root.present() ? (Root.granted() ? "מאושר" : "קיים, לא אושר") : "לא נמצא") + "\n"
                + "מנהל מכשיר: " + (Owner.isOwner(this) ? "כן" : "לא") + " · חשבונות בטלפון: " + accounts + "\n"
                + "-----\n" + (error == null ? "" : error);
    }

    /** An error dialog whose full text (including device details) can be copied. */
    private void showError(String title, String error) {
        String full = diagnostics(error);
        TextView tv = new TextView(this);
        tv.setText(full);
        tv.setTextIsSelectable(true);
        tv.setTextSize(13);
        tv.setTextColor(Ui.TEXT);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = Ui.dp(this, 18);
        tv.setPadding(pad, Ui.dp(this, 8), pad, 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("העתקה", (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("שגיאה", full));
                    Toast.makeText(this, "הועתק. אפשר להדביק ולשלוח.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("סגירה", null)
                .show();
    }

    private void adbHelp() {
        String cmd = Owner.adbCommand(this);
        new AlertDialog.Builder(this)
                .setTitle("הפעלה ממחשב")
                .setMessage("1. מסירים זמנית את החשבונות מהטלפון.\n2. מפעילים \"אפשרויות מפתחים\" ו\"ניפוי באגים ב-USB\".\n3. מחברים למחשב ומריצים:\n\n" + cmd)
                .setPositiveButton("העתקה", (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb", cmd));
                    Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("סגירה", null).show();
    }
}
