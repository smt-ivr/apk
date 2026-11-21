package uk.smti.filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The block page: shown on top of the browser when someone opens a site that is not approved. */
public class BlockActivity extends Activity {

    public static final String EXTRA_DOMAIN = "domain";
    private String domain;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        domain = getIntent().getStringExtra(EXTRA_DOMAIN);
        if (domain == null) domain = "";
        getWindow().setStatusBarColor(Ui.NAVY);
        getWindow().setNavigationBarColor(Ui.NAVY2);
        build();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        String d = intent.getStringExtra(EXTRA_DOMAIN);
        if (d != null) {
            domain = d;
            build();
        }
    }

    private void build() {
        Store s = Store.get(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.NAVY, Ui.NAVY2}));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 28);
        col.setPadding(p, Ui.dp(this, 70), p, Ui.dp(this, 32));
        scroll.addView(col);

        // shield in a soft glowing circle
        FrameLayout glow = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColors(new int[]{0x553B82F6, 0x003B82F6});
        ring.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ring.setGradientRadius(Ui.dp(this, 80));
        glow.setBackground(ring);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3B82F6, 0xFF1F5FAD}) {{ setShape(OVAL); }});
        disc.setElevation(Ui.dp(this, 8));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_shield);
        ic.setColorFilter(Color.WHITE);
        disc.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52), Gravity.CENTER));
        glow.addView(disc, new FrameLayout.LayoutParams(Ui.dp(this, 104), Ui.dp(this, 104), Gravity.CENTER));
        col.addView(glow, new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 170)));

        TextView title = Ui.text(this, "האתר הזה חסום", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, Ui.wide(this, 10, 0));

        TextView d = Ui.text(this, domain, 15, 0xFFBFD7FF, true);
        d.setTextDirection(View.TEXT_DIRECTION_LTR);
        d.setPadding(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
        d.setBackground(Ui.round(0x22FFFFFF, Ui.dp(this, 99)));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(this, 14);
        col.addView(d, dl);

        String msg = s.rules().blockMessage.isEmpty()
                ? "הגלישה במכשיר הזה מוגבלת לאתרים מאושרים בלבד. אם אתם צריכים את האתר, אפשר לבקש אישור."
                : s.rules().blockMessage;
        if (!s.rules().inHours()) msg = "הגלישה סגורה בשעות האלה. אפשר לגלוש שוב בשעות הגלישה שנקבעו.";
        if (s.rules().blockAll) msg = "הגלישה במכשיר הזה חסומה כרגע על ידי המנהל.";
        TextView m = Ui.text(this, msg, 15.5f, 0xFFD5DEEA, false);
        m.setGravity(Gravity.CENTER);
        col.addView(m, Ui.wide(this, 20, 28));

        Button req = Ui.button(this, "📨  בקשת אישור לאתר", Ui.BLUE2, true);
        req.setOnClickListener(v -> askApproval());
        col.addView(req, Ui.wide(this, 0, 10));

        status = Ui.text(this, "", 14, 0xFF86EFAC, true);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        col.addView(status, Ui.wide(this, 0, 10));

        Button back = new Button(this);
        back.setText("חזרה");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(15);
        back.setBackground(Ui.round(0x22FFFFFF, Ui.dp(this, 14)));
        back.setStateListAnimator(null);
        back.setOnClickListener(v -> finish());
        col.addView(back, Ui.wide(this, 0, 10));

        if (s.rules().allowLocalPin && s.hasPin()) {
            TextView pin = Ui.text(this, "🔑  פתיחה עם קוד", 14, 0xFF9DB4D6, true);
            pin.setGravity(Gravity.CENTER);
            pin.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            pin.setOnClickListener(v -> Pin.ask(this, "פתיחת האתר במכשיר הזה", () -> {
                Store.get(this).addLocalAllow(domain);
                Toast.makeText(this, "האתר נפתח במכשיר הזה. רעננו את הדף.", Toast.LENGTH_LONG).show();
                Sync.soon(this);
                finish();
            }));
            col.addView(pin, Ui.wide(this, 6, 0));
        }

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView foot = Ui.text(this, "🛡️ " + getString(R.string.app_name), 12.5f, 0xFF6B7FA0, false);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, Ui.wide(this, 24, 0));
        setContentView(scroll);
    }

    private void askApproval() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(Ui.text(this, "הבקשה תישלח למנהל הסינון. אם תאושר, האתר ייפתח אוטומטית.", 14, Ui.TEXT2, false));
        EditText note = new EditText(this);
        note.setHint("למה צריך את האתר? (לא חובה)");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(note, Ui.wide(this, 8, 0));
        new AlertDialog.Builder(this)
                .setTitle("בקשת אישור · " + domain)
                .setView(box)
                .setPositiveButton("שליחה", (dlg, w) -> Sync.request(this, domain, note.getText().toString().trim(), (ok, err) -> {
                    if (isFinishing()) return;
                    status.setVisibility(View.VISIBLE);
                    status.setTextColor(ok ? 0xFF86EFAC : 0xFFFCA5A5);
                    status.setText(ok ? "✓ הבקשה נשלחה. נעדכן כשהאתר יאושר." : "השליחה נכשלה: " + err);
                }))
                .setNegativeButton("ביטול", null)
                .show();
    }
}
