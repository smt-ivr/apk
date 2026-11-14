package uk.smti.lock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen lock shown over a locked app, a blocked app, or a protected settings screen. */
public class LockActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PKG = "pkg";
    public static final String MODE_PIN = "pin";
    public static final String MODE_BLOCK = "block";
    public static final String MODE_GUARD = "guard";

    private Store store;
    private String mode;
    private String pkg;
    private TextView error;
    private EditText pin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = Store.get(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        if (mode == null) mode = MODE_BLOCK;
        build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mode = intent.getStringExtra(EXTRA_MODE);
        pkg = intent.getStringExtra(EXTRA_PKG);
        if (mode == null) mode = MODE_BLOCK;
        build();
    }

    private void build() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(28), dp(72), dp(28), dp(28));

        ImageView icon = new ImageView(this);
        String name = "";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            Drawable d = pm.getApplicationIcon(ai);
            name = pm.getApplicationLabel(ai).toString();
            icon.setImageDrawable(d);
        } catch (Exception e) {
            icon.setImageResource(R.mipmap.ic_launcher);
        }
        col.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(6));
        TextView sub = new TextView(this);
        sub.setTextColor(Color.parseColor("#AFC1DA"));
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);

        if (MODE_BLOCK.equals(mode)) {
            title.setText(name.isEmpty() ? "האפליקציה חסומה" : name + " חסומה");
            sub.setText("האפליקציה הזו חסומה בטלפון הזה.");
        } else if (MODE_GUARD.equals(mode)) {
            title.setText("המסך הזה מוגן");
            sub.setText("כדי לשנות או להסיר את " + getString(getApplicationInfo().labelRes) + " צריך את הקוד.");
        } else {
            title.setText(name.isEmpty() ? "האפליקציה נעולה" : name + " נעולה");
            sub.setText("הזינו את הקוד כדי לפתוח.");
        }
        col.addView(title);
        col.addView(sub);

        if (!MODE_BLOCK.equals(mode)) {
            pin = new EditText(this);
            pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            pin.setGravity(Gravity.CENTER);
            pin.setTextSize(26);
            pin.setTextColor(Color.WHITE);
            pin.setHint("קוד");
            pin.setHintTextColor(Color.parseColor("#5F7594"));
            pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1A2940"));
            bg.setCornerRadius(dp(12));
            pin.setBackground(bg);
            pin.setPadding(dp(12), dp(12), dp(12), dp(12));
            pin.setOnEditorActionListener((v, id, ev) -> {
                if (id == EditorInfo.IME_ACTION_DONE || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    tryPin();
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(28);
            col.addView(pin, lp);

            error = new TextView(this);
            error.setTextColor(Color.parseColor("#FF8A80"));
            error.setGravity(Gravity.CENTER);
            error.setPadding(0, dp(8), 0, 0);
            col.addView(error);

            Button open = new Button(this);
            open.setText("פתיחה");
            open.setOnClickListener(v -> tryPin());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(8);
            col.addView(open, bp);
            pin.requestFocus();
        }

        Button home = new Button(this);
        home.setText("למסך הבית");
        home.setOnClickListener(v -> goHome());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(8);
        col.addView(home, hp);

        setContentView(col);
    }

    private void tryPin() {
        long wait = store.lockRemaining();
        if (wait > 0) {
            error.setText("יותר מדי ניסיונות. נסו שוב בעוד " + (wait / 1000 + 1) + " שניות");
            return;
        }
        if (store.checkPin(pin.getText().toString())) {
            store.unlock(MODE_GUARD.equals(mode) ? Store.GUARD : pkg);
            finish();
        } else {
            pin.setText("");
            error.setText("קוד שגוי");
        }
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
