package uk.smti.lock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Settings screen, behind the PIN: status, which apps are locked, options, removal. */
public class MainActivity extends Activity {

    private static final int[] MINUTES = {0, 1, 5, 15, 60};
    private static final String[] MINUTE_LABELS = {"עד שיוצאים מהאפליקציה", "דקה אחת", "5 דקות", "15 דקות", "שעה"};
    private static final String[] RULE_LABELS = {"פתוחה", "בקוד", "חסומה"};
    private static final int[] RULE_COLORS = {0xFF2E7D32, 0xFFEF6C00, 0xFFC62828};

    private static class App {
        String pkg;
        String label;
        Drawable icon;
    }

    private Store store;
    private boolean unlocked;
    private final List<App> apps = new ArrayList<>();
    private LinearLayout appList;
    private String filter = "";

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!store.hasPin()) showSetPin();
        else if (!unlocked) showGate();
        else showDashboard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the screen locks it again.
        if (!isChangingConfigurations()) unlocked = false;
    }

    // ------------------------------------------------------------------ PIN screens

    private void showGate() {
        LinearLayout col = centered();
        col.addView(title("הזינו את הקוד"));
        EditText pin = pinField("קוד");
        col.addView(pin, fixedWidth(240));
        TextView err = note("");
        err.setTextColor(0xFFC62828);
        col.addView(err);
        Button go = new Button(this);
        go.setText("כניסה");
        go.setOnClickListener(v -> {
            long wait = store.lockRemaining();
            if (wait > 0) {
                err.setText("יותר מדי ניסיונות. נסו שוב בעוד " + (wait / 1000 + 1) + " שניות");
            } else if (store.checkPin(pin.getText().toString())) {
                unlocked = true;
                showDashboard();
            } else {
                pin.setText("");
                err.setText("קוד שגוי");
            }
        });
        col.addView(go, fixedWidth(240));
        setContentView(col);
        pin.requestFocus();
    }

    private void showSetPin() {
        LinearLayout col = centered();
        col.addView(title("בחירת קוד"));
        col.addView(note("הקוד נדרש כדי לשנות הגדרות, לפתוח אפליקציות נעולות ולהסיר את ההגנה. שמרו אותו במקום בטוח."));
        EditText p1 = pinField("קוד (4–8 ספרות)");
        EditText p2 = pinField("שוב את הקוד");
        col.addView(p1, fixedWidth(240));
        col.addView(p2, fixedWidth(240));
        Button save = new Button(this);
        save.setText("שמירה");
        save.setOnClickListener(v -> {
            String a = p1.getText().toString();
            if (!a.matches("\\d{4,8}")) toast("הקוד צריך להיות 4 עד 8 ספרות");
            else if (!a.equals(p2.getText().toString())) toast("הקודים לא תואמים");
            else {
                store.setPin(a);
                unlocked = true;
                showDashboard();
            }
        });
        col.addView(save, fixedWidth(240));
        setContentView(col);
    }

    // ------------------------------------------------------------------ dashboard

    private void showDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(40));
        scroll.addView(col);

        // ---- status ----
        col.addView(heading("מצב ההגנה"));
        boolean service = LockService.isRunning();
        boolean admin = Owner.isAdmin(this);
        boolean owner = Owner.isOwner(this);

        if (!service) {
            TextView warn = note("הנעילה לא פועלת עד שמפעילים את שירות הנעילה.");
            warn.setTextColor(0xFFC62828);
            warn.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(warn);
        }

        col.addView(statusCard("1. שירות הנעילה", service,
                service ? "פועל" : "בהגדרות הנגישות בוחרים ב\"" + appName() + "\" ומפעילים."
                        + (Build.VERSION.SDK_INT >= 33 ? "\nאם האפשרות אפורה: הגדרות ← אפליקציות ← " + appName()
                        + " ← תפריט ⋮ ← \"התרת הגדרות מוגבלות\", ואז שוב לנגישות." : ""),
                service ? null : "פתיחת הגדרות נגישות",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        col.addView(statusCard("2. הרשאת ניהול", admin,
                admin ? "פעילה: אי אפשר להסיר את האפליקציה בלי לבטל קודם את ההרשאה, ומסך הביטול מוגן בקוד."
                        : "מונעת הסרה רגילה של האפליקציה.",
                admin ? null : "הפעלת הרשאת ניהול",
                v -> {
                    Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Owner.admin(this))
                            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "ההרשאה מונעת הסרה של האפליקציה בלי הקוד.");
                    startActivity(i);
                }));

        LinearLayout strong = statusCard("3. הגנה חזקה (לא חובה)", owner,
                owner ? "פעילה: אי אפשר להסיר את האפליקציה בכלל בלי הקוד, ואפליקציות חסומות מושבתות במערכת."
                        : "דורשת חיבור למחשב פעם אחת, בטלפון שאין בו חשבון Google מחובר (בדרך כלל אחרי איפוס).\n"
                        + "במחשב, עם ADB מותקן וניפוי באגים מופעל בטלפון, מריצים:",
                null, null);
        if (!owner) {
            TextView cmd = new TextView(this);
            cmd.setText(Owner.adbCommand(this));
            cmd.setTextIsSelectable(true);
            cmd.setTextDirection(View.TEXT_DIRECTION_LTR);
            cmd.setTypeface(Typeface.MONOSPACE);
            cmd.setTextSize(12);
            cmd.setPadding(dp(8), dp(8), dp(8), dp(8));
            cmd.setBackgroundColor(Color.parseColor("#EEF1F6"));
            strong.addView(cmd);
            Button copy = new Button(this);
            copy.setText("העתקת הפקודה");
            copy.setOnClickListener(v -> {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("adb", Owner.adbCommand(this)));
                toast("הועתק");
            });
            strong.addView(copy);
        }
        col.addView(strong);

        // ---- apps ----
        col.addView(heading("אפליקציות"));
        col.addView(note("לכל אפליקציה: פתוחה, נפתחת בקוד, או חסומה לגמרי."));
        EditText search = new EditText(this);
        search.setHint("חיפוש אפליקציה");
        search.setSingleLine(true);
        search.setText(filter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                renderApps();
            }
        });
        col.addView(search);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        col.addView(appList);
        if (apps.isEmpty()) loadApps();
        else renderApps();

        // ---- options ----
        col.addView(heading("הגדרות"));
        LinearLayout opts = card();

        TextView ml = note("אחרי הקוד, אפליקציה נשארת פתוחה:");
        opts.addView(ml);
        Spinner minutes = new Spinner(this);
        minutes.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MINUTE_LABELS));
        int cur = 0;
        for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == store.unlockMinutes()) cur = i;
        minutes.setSelection(cur);
        minutes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                store.setUnlockMinutes(MINUTES[pos]);
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        opts.addView(minutes);

        Switch guard = new Switch(this);
        guard.setText("הגנה על מסכי ההסרה וההגדרות של האפליקציה (מומלץ)");
        guard.setChecked(store.guardEnabled());
        guard.setPadding(0, dp(12), 0, dp(4));
        guard.setOnCheckedChangeListener((b, on) -> store.setGuard(on));
        opts.addView(guard);

        TextView sh = note(owner ? "הגבלות בהגנה החזקה:" : "הגבלות נוספות (זמינות רק בהגנה החזקה):");
        sh.setPadding(0, dp(14), 0, dp(2));
        opts.addView(sh);
        for (String[] r : Owner.RESTRICTIONS) {
            Switch s = new Switch(this);
            s.setText(r[2]);
            s.setChecked(store.flag(r[0], "1".equals(r[3])));
            s.setEnabled(owner);
            s.setPadding(0, dp(6), 0, dp(6));
            final String key = r[0];
            s.setOnCheckedChangeListener((b, on) -> {
                if (on && key.equals("reset")) {
                    new AlertDialog.Builder(this)
                            .setMessage("עם חסימת איפוס, אם הקוד יישכח לא תהיה דרך לשחזר את הטלפון. להמשיך?")
                            .setPositiveButton("כן, לחסום", (d, w) -> {
                                store.setFlag(key, true);
                                Owner.apply(this);
                            })
                            .setNegativeButton("ביטול", (d, w) -> b.setChecked(false))
                            .show();
                    return;
                }
                store.setFlag(key, on);
                Owner.apply(this);
            });
            opts.addView(s);
        }
        col.addView(opts);

        Button lockNow = new Button(this);
        lockNow.setText("לנעול שוב את כל מה שנפתח");
        lockNow.setOnClickListener(v -> {
            store.lockAll();
            toast("ננעל");
        });
        col.addView(lockNow);

        Button changePin = new Button(this);
        changePin.setText("שינוי קוד");
        changePin.setOnClickListener(v -> changePin());
        col.addView(changePin);

        Button remove = new Button(this);
        remove.setText("הסרת ההגנה והאפליקציה");
        remove.setTextColor(0xFFC62828);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("כל הנעילות יבוטלו והאפליקציה תוסר מהטלפון. להמשיך?")
                .setPositiveButton("הסרה", (d, w) -> removeAll())
                .setNegativeButton("ביטול", null)
                .show());
        col.addView(remove);

        setContentView(scroll);
    }

    private void loadApps() {
        TextView loading = note("טוען אפליקציות…");
        appList.addView(loading);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<App> list = new ArrayList<>();
            for (ResolveInfo r : found) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                App a = new App();
                a.pkg = p;
                a.label = r.loadLabel(pm).toString();
                a.icon = r.loadIcon(pm);
                list.add(a);
            }
            Collator c = Collator.getInstance(new Locale("he"));
            list.sort((x, y) -> {
                int rx = store.rule(x.pkg) == Store.OPEN ? 1 : 0, ry = store.rule(y.pkg) == Store.OPEN ? 1 : 0;
                return rx != ry ? rx - ry : c.compare(x.label, y.label);
            });
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(list);
                renderApps();
            });
        }).start();
    }

    private void renderApps() {
        if (appList == null) return;
        appList.removeAllViews();
        for (App a : apps) {
            if (!filter.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(filter) && !a.pkg.contains(filter)) continue;
            appList.addView(appRow(a));
        }
    }

    private View appRow(App a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(a.icon);
        top.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView name = new TextView(this);
        name.setText(a.label);
        name.setTextSize(16);
        name.setTextColor(Color.parseColor("#1B2430"));
        name.setPadding(dp(12), 0, dp(12), 0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(top);

        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(0, dp(8), 0, 0);
        TextView[] opts = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView t = new TextView(this);
            t.setText(RULE_LABELS[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(7), 0, dp(7));
            final int rule = i;
            t.setOnClickListener(v -> {
                store.setRule(a.pkg, rule);
                Owner.apply(this);
                paint(opts, rule);
            });
            opts[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginEnd(i < 2 ? dp(6) : 0);
            seg.addView(t, lp);
        }
        paint(opts, store.rule(a.pkg));
        row.addView(seg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void paint(TextView[] opts, int selected) {
        for (int i = 0; i < opts.length; i++) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(8));
            if (i == selected) {
                d.setColor(RULE_COLORS[i]);
                opts[i].setTextColor(Color.WHITE);
                opts[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                d.setColor(Color.parseColor("#EEF1F6"));
                opts[i].setTextColor(Color.parseColor("#4A5568"));
                opts[i].setTypeface(Typeface.DEFAULT);
            }
            opts[i].setBackground(d);
        }
    }

    private void changePin() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText p1 = pinField("קוד חדש");
        EditText p2 = pinField("שוב את הקוד החדש");
        box.addView(p1);
        box.addView(p2);
        new AlertDialog.Builder(this)
                .setTitle("שינוי קוד")
                .setView(box)
                .setPositiveButton("שמירה", (d, w) -> {
                    String a = p1.getText().toString();
                    if (!a.matches("\\d{4,8}")) toast("הקוד צריך להיות 4 עד 8 ספרות");
                    else if (!a.equals(p2.getText().toString())) toast("הקודים לא תואמים");
                    else {
                        store.setPin(a);
                        toast("הקוד שונה");
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void removeAll() {
        store.setRemoving(true);
        for (String p : store.rules().keySet()) store.setRule(p, Store.OPEN);
        Owner.release(this);
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("ההגנה הוסרה. אפשר להסיר את האפליקציה מההגדרות.");
        }
    }

    // ------------------------------------------------------------------ view helpers

    private String appName() {
        return getString(getApplicationInfo().labelRes);
    }

    private LinearLayout statusCard(String name, boolean ok, String text, String action, View.OnClickListener onAction) {
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#1B2430"));
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = new TextView(this);
        badge.setText(ok ? "פעיל ✓" : "כבוי");
        badge.setTextColor(ok ? 0xFF2E7D32 : 0xFFC62828);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(badge);
        c.addView(top);
        c.addView(note(text));
        if (action != null) {
            Button b = new Button(this);
            b.setText(action);
            b.setOnClickListener(onAction);
            c.addView(b);
        }
        return c;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#DDE3EC"));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout centered() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(64), dp(24), dp(24));
        return col;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(16));
        return t;
    }

    private TextView heading(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#1B2430"));
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#5B6577"));
        t.setPadding(0, dp(4), 0, dp(6));
        return t;
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private LinearLayout.LayoutParams fixedWidth(int w) {
        return new LinearLayout.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
