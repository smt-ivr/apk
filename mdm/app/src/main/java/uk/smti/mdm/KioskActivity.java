package uk.smti.mdm;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The kiosk home screen: only the allowed apps. With one app it opens it straight away (and again after a
 * few seconds if the person comes back here). Seven quick taps in the top corner + the code = exit,
 * always available, so nobody gets stuck.
 */
public class KioskActivity extends Activity {

    private static WeakReference<KioskActivity> current = new WeakReference<>(null);
    private final Handler h = new Handler(Looper.getMainLooper());
    private int taps;
    private long firstTap;
    private TextView clock, countdown;
    private Runnable relaunch;

    static void finishIfOpen() {
        KioskActivity k = current.get();
        if (k != null) {
            try { k.stopLockTask(); } catch (Exception ignored) { }
            k.finish();
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = new WeakReference<>(this);
        getWindow().setStatusBarColor(Ui.NAVY);
        getWindow().setNavigationBarColor(Ui.NAVY2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store s = Store.get(this);
        if (!s.kioskActive() || !Owner.isOwner(this)) {
            // kiosk is off: act as a normal home button would
            try { stopLockTask(); } catch (Exception ignored) { }
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        try { startLockTask(); } catch (Exception ignored) { }
        build();
        JSONArray apps = s.kiosk().optJSONArray("apps");
        if (apps != null && apps.length() == 1) {
            String pkg = apps.optString(0);
            int wait = getIntent().getBooleanExtra("first", true) ? 600 : 5000;
            getIntent().putExtra("first", false);
            if (s.kiosk().optBoolean("autoRelaunch", true)) scheduleOpen(pkg, wait);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (relaunch != null) h.removeCallbacks(relaunch);
    }

    @Override
    public void onBackPressed() {
        // stays on the kiosk screen
    }

    private void scheduleOpen(String pkg, int ms) {
        if (relaunch != null) h.removeCallbacks(relaunch);
        final int[] left = {ms / 1000};
        relaunch = new Runnable() {
            @Override
            public void run() {
                if (left[0] <= 0) {
                    open(pkg);
                    return;
                }
                if (countdown != null) countdown.setText("נפתח בעוד " + left[0] + "…");
                left[0]--;
                h.postDelayed(this, 1000);
            }
        };
        h.postDelayed(relaunch, ms < 1000 ? ms : 0);
    }

    private void open(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) {
            try { startActivity(i); } catch (Exception ignored) { }
        }
    }

    private void build() {
        Store s = Store.get(this);
        JSONObject k = s.kiosk();
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.NAVY, Ui.NAVY2}));

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 22);
        col.setPadding(p, Ui.dp(this, 48), p, p);
        scroll.addView(col);
        root.addView(scroll);

        clock = Ui.text(this, now(), 52, Color.WHITE, false);
        clock.setGravity(Gravity.CENTER);
        col.addView(clock);
        String title = k.optString("title", "");
        TextView t = Ui.text(this, title.isEmpty() ? getString(R.string.app_name) : title, 16, 0xCCFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, Ui.wideCenter(this, 4));
        tickClock();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = Ui.dp(this, 36);
        col.addView(grid, glp);
        PackageManager pm = getPackageManager();
        JSONArray apps = k.optJSONArray("apps");
        int shown = 0;
        for (int i = 0; apps != null && i < apps.length(); i++) {
            String pkg = apps.optString(i);
            Intent li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) continue;
            String label;
            Drawable icon;
            try {
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                icon = pm.getApplicationIcon(pkg);
            } catch (Exception e) {
                continue;
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
            cell.setBackground(Ui.round(0x14FFFFFF, Ui.dp(this, 20)));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
            TextView lt = Ui.text(this, label, 13, Color.WHITE, false);
            lt.setGravity(Gravity.CENTER);
            lt.setMaxLines(2);
            cell.addView(lt, new LinearLayout.LayoutParams(Ui.dp(this, 86), ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> open(pkg));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            grid.addView(cell, lp);
            shown++;
        }
        if (shown == 0) {
            TextView none = Ui.text(this, "לא הוגדרו אפליקציות לקיוסק.\nאפשר להגדיר אותן ממרכז השליטה.", 15, 0xCCFFFFFF, false);
            none.setGravity(Gravity.CENTER);
            col.addView(none, Ui.wideCenter(this, 30));
        }
        countdown = Ui.text(this, "", 14, 0x99FFFFFF, false);
        countdown.setGravity(Gravity.CENTER);
        col.addView(countdown, Ui.wideCenter(this, 18));

        // hidden exit: seven quick taps in the top corner, then the code (always on)
        View corner = new View(this);
        corner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) tapCorner();
            return true;
        });
        root.addView(corner, new FrameLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 80), Gravity.TOP | Gravity.END));
        setContentView(root);
    }

    private void tapCorner() {
        long now = System.currentTimeMillis();
        if (now - firstTap > 3000) {
            firstTap = now;
            taps = 0;
        }
        if (++taps >= 7) {
            taps = 0;
            if (relaunch != null) h.removeCallbacks(relaunch);
            Pin.ask(this, "יציאה מהקיוסק", () -> {
                Store.get(this).setKioskPaused(true);
                Owner.applyKiosk(this);
                Agent.kick(this);
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
        }
    }

    private void tickClock() {
        h.postDelayed(() -> {
            if (clock != null && !isFinishing()) {
                clock.setText(now());
                tickClock();
            }
        }, 20_000);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }
}
