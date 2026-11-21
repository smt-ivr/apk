package uk.smti.mdm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view helpers: one look for the whole app (deep navy + blue, rounded cards). */
final class Ui {

    static final int BG = 0xFFF2F5FA, CARD = 0xFFFFFFFF, TEXT = 0xFF0F1A2B, TEXT2 = 0xFF475569, TEXT3 = 0xFF7A8699, LINE = 0xFFE3E8F0,
            BLUE = 0xFF1F5FAD, BLUE2 = 0xFF3B82F6, NAVY = 0xFF0F1A2B, NAVY2 = 0xFF17325C, GOOD = 0xFF16A34A, WARN = 0xFFD97706, BAD = 0xFFDC2626,
            GOLD = 0xFFB8860B, VIOLET = 0xFF7C3AED, TEAL = 0xFF0D9488, SLATE = 0xFF475569;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    static GradientDrawable round(int fill, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        return g;
    }

    static GradientDrawable gradient(int a, int b, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(r);
        return g;
    }

    static RippleDrawable ripple(android.graphics.drawable.Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), content, content == null ? new android.graphics.drawable.ColorDrawable(Color.WHITE) : null);
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(c, 18)));
        l.setElevation(dp(c, 1.5f));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static Button button(Context c, CharSequence label, int color, boolean filled) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? Color.WHITE : color);
        GradientDrawable bg = filled ? gradient(blend(color, Color.WHITE, 0.12f), blend(color, Color.BLACK, 0.12f), dp(c, 14)) : round(blend(color, Color.WHITE, 0.9f), dp(c, 14));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 50));
        return b;
    }

    /** Gradient tile with a white icon. */
    static FrameLayout tile(Context c, int icon, int hue, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{blend(hue, Color.WHITE, 0.18f), blend(hue, Color.BLACK, 0.18f)});
        bg.setCornerRadius(dp(c, sizeDp * 0.32f));
        f.setBackground(bg);
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        int s = Math.round(sizeDp * 0.55f);
        f.addView(iv, new FrameLayout.LayoutParams(dp(c, s), dp(c, s), Gravity.CENTER));
        return f;
    }

    /** Row: tile, title, subtitle, and an optional status on the end. */
    static LinearLayout row(Context c, int icon, int hue, String title, String sub, View end) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
        r.setBackground(ripple(null));
        r.addView(tile(c, icon, hue, 40), new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        t.addView(text(c, title, 15, TEXT, true));
        if (sub != null && !sub.isEmpty()) t.addView(text(c, sub, 12.5f, TEXT3, false));
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (end != null) r.addView(end);
        return r;
    }

    static TextView chip(Context c, String s, int color) {
        TextView t = text(c, s, 12, color, true);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(round(blend(color, Color.WHITE, 0.88f), dp(c, 99)));
        return t;
    }

    static LinearLayout.LayoutParams wide(Context c, int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, top);
        lp.bottomMargin = dp(c, bottom);
        return lp;
    }

    static LinearLayout.LayoutParams wideCenter(Context c, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, top);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static String ago(long t) {
        if (t <= 0) return "—";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "עכשיו";
        if (m < 60) return "לפני " + m + " דק׳";
        if (m < 24 * 60) return "לפני " + (m / 60) + " שע׳";
        return "לפני " + (m / 1440) + " ימים";
    }
}
