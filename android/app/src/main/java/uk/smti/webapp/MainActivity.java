package uk.smti.webapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_STORAGE = 2;
    private static final int REQ_SETTINGS = 3;
    private static final int REQ_MEDIA = 4;
    private static final int REQ_LOCATION = 5;
    private static final int REQ_NOTIFY = 6;

    private static final int M_COPY = 1, M_SHARE = 2, M_DESKTOP = 3, M_HIDE_BAR = 4, M_FIND = 5,
            M_RELOAD = 6, M_HOME = 7, M_SETTINGS = 8, M_BROWSER = 9, M_UPDATE = 10;

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    // Force a desktop-width viewport so responsive sites render their desktop layout, not just the UA.
    private static final String DESKTOP_VIEWPORT_JS =
            "(function(){var v=document.querySelector('meta[name=viewport]');if(!v){v=document.createElement('meta');v.name='viewport';(document.head||document.documentElement).appendChild(v);}v.setAttribute('content','width=1024');})()";
    private static final String EXTRA_TAG = "notif_tag";

    private Config config;
    private SharedPreferences prefs;
    private String bridgeJs = "";
    private String mobileUa;

    private LinearLayout root;
    private LinearLayout bar;
    private ImageButton btnBack, btnForward;
    private TextView barTitle, barHost;
    private ProgressBar progress;
    private FrameLayout content;
    private WebView web;
    private final java.util.List<WebView> tabs = new java.util.ArrayList<>();
    private int activeTab = 0;
    private Client client;
    private Chrome chrome;
    private TextView tabCount;
    private ScrollView home;
    private LinearLayout errorView;
    private TextView errorText;
    private ImageButton showBarButton;
    private LinearLayout findBar;

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ValueCallback<Uri[]> fileCallback;
    private DownloadManager.Request pendingDownload;
    private String pendingDownloadName;
    private PermissionRequest pendingMedia;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeo;
    private int pendingNotifyId;
    private boolean clearHistoryOnNextPage;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(this);
        prefs = getSharedPreferences("browser", MODE_PRIVATE);
        // One-time restore tool: a build with the old package name, opened just to export its data.
        if (config.restoreTool) {
            showRestoreTool();
            return;
        }
        // The app may not be used without a local code: force setting one first.
        if (!config.hasPin()) {
            showSetCodeScreen();
            return;
        }
        bridgeJs = readAsset("bridge.js");
        applyOrientation();
        buildUi();
        if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            showWeb();
        } else if (!openViewIntent(getIntent()) && !restoreTabs()) {
            showStart();
        }
        handleNotificationTap(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!openViewIntent(intent)) handleNotificationTap(intent);
    }

    /** A link opened from another app (Gmail etc.) when this is the device browser. true = handled. */
    private boolean openViewIntent(Intent intent) {
        if (intent == null || web == null) return false;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        Uri data = intent.getData();
        if (data == null) return false;
        String url = data.toString();
        String scheme = data.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false;
        if (config.isAllowed(url)) {
            open(url);
        } else {
            showHomeSafe();
            onBlocked(url);
        }
        return true;
    }

    private void showHomeSafe() {
        if (config.sites.size() != 1) showHome();
    }

    /** Mandatory on first open: a full screen that forces setting a local code before the app can be used. */
    private void showSetCodeScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setBackgroundColor(color(R.color.page));
        int p = dp(26);
        col.setPadding(p, dp(70), p, p);

        TextView h = new TextView(this);
        h.setText(getString(R.string.app_name));
        h.setTextSize(24);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(color(R.color.bar_text));
        h.setGravity(Gravity.CENTER);
        col.addView(h);
        TextView m = new TextView(this);
        m.setText("כדי להשתמש באפליקציה צריך לקבוע קוד (4 ספרות ומעלה). הקוד מגן על ההגדרות במכשיר הזה.");
        m.setTextColor(color(R.color.bar_muted));
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, dp(12), 0, dp(22));
        col.addView(m);

        final EditText c1 = codeField("קוד חדש");
        final EditText c2 = codeField("שוב את הקוד");
        col.addView(c1, codeLp());
        col.addView(c2, codeLp());

        Button save = new Button(this);
        save.setText("שמירה והתחלה");
        save.setAllCaps(false);
        save.setTextSize(16);
        LinearLayout.LayoutParams sp = codeLp();
        sp.topMargin = dp(18);
        col.addView(save, sp);
        setContentView(col);
        c1.requestFocus();

        save.setOnClickListener(v -> {
            String a = c1.getText().toString().trim(), b = c2.getText().toString().trim();
            if (a.length() < 4) { Toast.makeText(this, "הקוד צריך להיות לפחות 4 ספרות", Toast.LENGTH_SHORT).show(); return; }
            if (!a.equals(b)) { Toast.makeText(this, "הקודים לא תואמים", Toast.LENGTH_SHORT).show(); return; }
            config.setPin(a);
            Toast.makeText(this, "הקוד נשמר", Toast.LENGTH_SHORT).show();
            recreate();
        });
    }

    private EditText codeField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(20);
        e.setTextColor(color(R.color.bar_text));
        return e;
    }

    private LinearLayout.LayoutParams codeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    /** The one-time restore tool screen: shows the export code from the app it updated in place. */
    private void showRestoreTool() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(color(R.color.page));
        int p = dp(22);
        col.setPadding(p, dp(36), p, p);
        TextView h = new TextView(this);
        h.setText("כלי שחזור הגדרות");
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(color(R.color.bar_text));
        col.addView(h);
        TextView m = new TextView(this);
        m.setText("זהו העתק זמני של האפליקציה הקיימת. הקוד למטה מכיל את האתרים והקוד שלה. העתיקו אותו, ובאפליקציה החדשה: תפריט ← הגדרות ← ייבוא. אחר כך אפשר למחוק את הכלי הזה.");
        m.setTextColor(color(R.color.bar_muted));
        m.setPadding(0, dp(10), 0, dp(16));
        col.addView(m);
        final EditText codeBox = new EditText(this);
        codeBox.setText(config.exportCode());
        codeBox.setKeyListener(null);
        codeBox.setTextSize(12);
        codeBox.setTextColor(color(R.color.bar_text));
        codeBox.setBackground(rounded(color(R.color.bar), dp(12)));
        codeBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        col.addView(codeBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
        Button copy = new Button(this);
        copy.setText("העתקת הקוד");
        copy.setOnClickListener(v -> {
            copyText(config.exportCode(), "הקוד הועתק");
            Toast.makeText(this, "הקוד הועתק. הדביקו אותו בייבוא באפליקציה החדשה.", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        col.addView(copy, lp);
        Button share = new Button(this);
        share.setText("שיתוף הקוד");
        share.setOnClickListener(v -> share(config.exportCode(), "קוד הגדרות"));
        col.addView(share, lp);
        setContentView(col);
    }

    /** A blocked page: offer to open it, in admin mode (with the code), by adding it to the allowed list. */
    /** A blocked link the user tried to open. In admin mode: a small prompt to add it; otherwise a short message. */
    private void onBlocked(String url) {
        final String host = Config.hostOf(url);
        if (config.adminMode()) {
            new AlertDialog.Builder(this)
                    .setTitle(config.blockMessage())
                    .setMessage("להוסיף את " + host + " לרשימה המותרת?")
                    .setPositiveButton("הוספה", (d, w) -> {
                        if (config.allowHost(url)) {
                            Toast.makeText(this, "נוסף לרשימה", Toast.LENGTH_SHORT).show();
                            open(url);
                        } else {
                            Toast.makeText(this, "הכתובת לא תקינה", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("סגירה", null)
                    .show();
        } else {
            blocked();
        }
    }

    /** Ask for the admin code (or the hidden master code), then run the action. */
    private void askCode(String title, Runnable onOk) {
        long wait = config.lockRemaining();
        if (wait > 0) {
            Toast.makeText(this, "יותר מדי ניסיונות. נסה שוב בעוד " + (wait / 1000 + 1) + " שניות", Toast.LENGTH_LONG).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("אישור", (d, w) -> {
                    if (config.checkPin(input.getText().toString())) onOk.run();
                    else Toast.makeText(this, "קוד שגוי", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        if (web != null) saveTabs();
    }

    @Override
    protected void onDestroy() {
        for (WebView t : tabs) { try { t.destroy(); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    private void applyOrientation() {
        if ("portrait".equals(config.orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else if ("landscape".equals(config.orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void showStart() {
        if (config.sites.size() == 1) open(config.sites.get(0).url);
        else showHome();
    }

    // ------------------------------------------------------------------ UI

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.page));

        // ---- browser bar ----
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(color(R.color.bar));
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // browser order: back, forward, home …
        btnBack = barButton(R.drawable.ic_back, "אחורה", v -> goBack());
        btnForward = barButton(R.drawable.ic_forward, "קדימה", v -> {
            if (web.canGoForward()) web.goForward();
        });
        ImageButton btnHome = barButton(R.drawable.ic_home, "דף הבית", v -> goHome());
        bar.addView(btnBack);
        bar.addView(btnForward);
        bar.addView(btnHome);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(8), dp(4), dp(8), dp(4));
        titles.setBackground(rounded(color(R.color.page), dp(18)));
        titles.setClickable(true);
        titles.setOnClickListener(v -> copyText(web.getUrl(), "הקישור הועתק"));
        titles.setOnLongClickListener(v -> {
            showPageMenu(v);
            return true;
        });
        barTitle = new TextView(this);
        barTitle.setTextColor(color(R.color.bar_text));
        barTitle.setTextSize(14);
        barTitle.setTypeface(Typeface.DEFAULT_BOLD);
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(TextUtils.TruncateAt.END);
        barTitle.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        barHost = new TextView(this);
        barHost.setTextColor(color(R.color.bar_muted));
        barHost.setTextSize(11);
        barHost.setSingleLine(true);
        barHost.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        barHost.setTextDirection(View.TEXT_DIRECTION_LTR);
        titles.addView(barTitle);
        titles.addView(barHost);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(42), 1);
        tp.setMargins(dp(4), dp(6), dp(4), dp(6));
        titles.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titles, tp);

        bar.addView(barButton(R.drawable.ic_refresh, "רענון", v -> reload()));
        bar.addView(tabButton());
        bar.addView(barButton(R.drawable.ic_more, "תפריט", this::showPageMenu));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        View line = new View(this);
        line.setBackgroundColor(color(R.color.bar_line));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        barLine = line;

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(color(R.color.primary)));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        // ---- find in page ----
        findBar = new LinearLayout(this);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackgroundColor(color(R.color.bar));
        findBar.setPadding(dp(8), 0, dp(4), 0);
        findBar.setVisibility(View.GONE);
        root.addView(findBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        // ---- content ----
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        client = new Client();
        chrome = new Chrome();
        mobileUa = new WebView(this).getSettings().getUserAgentString().replace("; wv)", ")");
        web = createTab();
        tabs.add(web);

        home = new ScrollView(this);
        home.setBackgroundColor(color(R.color.page));
        content.addView(home, match());

        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorView.setBackgroundColor(color(R.color.page));
        errorText = new TextView(this);
        errorText.setTextSize(18);
        errorText.setTextColor(color(R.color.bar_text));
        errorText.setGravity(Gravity.CENTER);
        errorView.addView(errorText);
        Button retry = new Button(this);
        retry.setText("נסה שוב");
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            web.reload();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        errorView.addView(retry, lp);
        errorView.setVisibility(View.GONE);
        content.addView(errorView, match());

        // Small button to bring the bar back when it is hidden.
        showBarButton = new ImageButton(this);
        showBarButton.setImageResource(R.drawable.ic_expand);
        showBarButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        showBarButton.setContentDescription("הצגת הסרגל");
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x99202833);
        showBarButton.setBackground(circle);
        showBarButton.setAlpha(0.75f);
        showBarButton.setOnClickListener(v -> setBarVisible(true));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP | Gravity.START);
        sp.setMargins(dp(10), dp(10), dp(10), dp(10));
        content.addView(showBarButton, sp);

        setContentView(root);
        setBarVisible(prefs.getBoolean("bar", config.toolbar));
    }

    private View barLine;

    private ImageButton barButton(int icon, String label, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setColorFilter(color(R.color.bar_text), PorterDuff.Mode.SRC_IN);
        b.setContentDescription(label);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, null));
        b.setOnClickListener(l);
        b.setOnLongClickListener(v -> {
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
            return true;
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(46)));
        return b;
    }

    private void setBarVisible(boolean visible) {
        bar.setVisibility(visible ? View.VISIBLE : View.GONE);
        barLine.setVisibility(visible ? View.VISIBLE : View.GONE);
        showBarButton.setVisibility(visible || isHomeShown() ? View.GONE : View.VISIBLE);
        prefs.edit().putBoolean("bar", visible).apply();
    }

    private void updateBar() {
        boolean onHome = isHomeShown();
        btnBack.setEnabled(!onHome && (web.canGoBack() || config.sites.size() > 1));
        btnBack.setAlpha(btnBack.isEnabled() ? 1f : 0.35f);
        btnForward.setEnabled(!onHome && web.canGoForward());
        btnForward.setAlpha(btnForward.isEnabled() ? 1f : 0.35f);
        if (onHome) {
            barTitle.setText(getString(R.string.app_name));
            barHost.setText(config.sites.size() + " אתרים");
        } else {
            String t = web.getTitle();
            String u = web.getUrl();
            barTitle.setText(TextUtils.isEmpty(t) || (u != null && u.contains(t)) ? getString(R.string.app_name) : t);
            barHost.setText(u == null ? "" : prettyUrl(u));
        }
        if (bar.getVisibility() != View.VISIBLE) showBarButton.setVisibility(onHome ? View.GONE : View.VISIBLE);
    }

    private static String prettyUrl(String u) {
        return u.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", "");
    }

    // ------------------------------------------------------------------ menu

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private void showPageMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        boolean onHome = isHomeShown();
        if (!onHome) {
            m.add(0, M_COPY, 0, "העתקת קישור הדף");
            m.add(0, M_SHARE, 1, "שיתוף");
            m.add(0, M_FIND, 2, "חיפוש בדף");
            m.add(0, M_DESKTOP, 3, "מצב מחשב").setCheckable(true).setChecked(isDesktop());
            m.add(0, M_RELOAD, 4, "רענון");
        }
        m.add(0, M_HIDE_BAR, 5, "הסתרת הסרגל");
        if (config.sites.size() > 1 && !onHome) m.add(0, M_HOME, 6, "מסך הבית");
        m.add(0, M_SETTINGS, 7, "הגדרות");
        m.add(0, M_UPDATE, 8, "בדיקת עדכון");
        m.add(0, 0, 99, "גרסה " + versionName()).setEnabled(false);
        pm.setOnMenuItemClickListener(this::onMenu);
        pm.show();
    }

    private boolean onMenu(MenuItem item) {
        switch (item.getItemId()) {
            case M_COPY:
                copyText(web.getUrl(), "הקישור הועתק");
                return true;
            case M_SHARE:
                share(web.getUrl(), web.getTitle());
                return true;
            case M_FIND:
                showFind();
                return true;
            case M_DESKTOP:
                applyDesktop(!isDesktop(), true);
                return true;
            case M_RELOAD:
                reload();
                return true;
            case M_HIDE_BAR:
                setBarVisible(false);
                Toast.makeText(this, "להצגה: הכפתור הקטן בפינה", Toast.LENGTH_SHORT).show();
                return true;
            case M_HOME:
                showHome();
                return true;
            case M_SETTINGS:
                askPin();
                return true;
            case M_UPDATE:
                Updater.check(this, true);
                return true;
        }
        return false;
    }

    private boolean isDesktop() {
        return prefs.getBoolean("desktop", config.desktop);
    }

    private void applyDesktop(boolean desktop, boolean reload) {
        prefs.edit().putBoolean("desktop", desktop).apply();
        WebSettings s = web.getSettings();
        s.setUserAgentString(desktop ? DESKTOP_UA : mobileUa);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // apply to every open tab's settings too
        for (WebView t : tabs) {
            if (t == web) continue;
            t.getSettings().setUserAgentString(desktop ? DESKTOP_UA : mobileUa);
            t.getSettings().setUseWideViewPort(true);
            t.getSettings().setLoadWithOverviewMode(true);
        }
        if (reload && !isHomeShown()) {
            web.reload();
            Toast.makeText(this, desktop ? "מצב מחשב" : "מצב טלפון", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFind() {
        findBar.removeAllViews();
        EditText q = new EditText(this);
        q.setHint("חיפוש בדף");
        q.setSingleLine(true);
        q.setTextColor(color(R.color.bar_text));
        q.setHintTextColor(color(R.color.bar_muted));
        q.setBackground(null);
        TextView count = new TextView(this);
        count.setTextColor(color(R.color.bar_muted));
        count.setPadding(dp(8), 0, dp(8), 0);
        web.setFindListener((active, matches, done) -> {
            if (done) count.setText(matches == 0 ? "0" : (active + 1) + "/" + matches);
        });
        q.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                web.findAllAsync(s.toString());
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        Button next = new Button(this, null, android.R.attr.borderlessButtonStyle);
        next.setText("הבא");
        next.setOnClickListener(v -> web.findNext(true));
        Button close = new Button(this, null, android.R.attr.borderlessButtonStyle);
        close.setText("סגירה");
        close.setOnClickListener(v -> {
            web.clearMatches();
            findBar.setVisibility(View.GONE);
        });
        findBar.addView(q, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        findBar.addView(count);
        findBar.addView(next);
        findBar.addView(close);
        findBar.setVisibility(View.VISIBLE);
        q.requestFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(q, 0);
    }

    // ------------------------------------------------------------------ long press on links / images

    private boolean onLongPress() {
        WebView.HitTestResult r = web.getHitTestResult();
        int type = r.getType();
        final String extra = r.getExtra();
        if (extra == null) return false; // plain text: let the page select it
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Handler h = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    String url = msg.getData().getString("url");
                    String title = msg.getData().getString("title");
                    if (url == null) url = extra;
                    linkMenu(url, title, type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ? extra : null);
                }
            };
            web.requestFocusNodeHref(h.obtainMessage());
            return true;
        }
        if (type == WebView.HitTestResult.IMAGE_TYPE) {
            linkMenu(null, null, extra);
            return true;
        }
        return false;
    }

    private void linkMenu(String url, String text, String image) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (url != null) {
            labels.add("העתקת הקישור");
            actions.add(() -> copyText(url, "הקישור הועתק"));
            if (!TextUtils.isEmpty(text)) {
                labels.add("העתקת טקסט הקישור");
                actions.add(() -> copyText(text.trim(), "הטקסט הועתק"));
            }
            labels.add("פתיחה");
            actions.add(() -> { if (config.isAllowed(url)) web.loadUrl(url); else onBlocked(url); });
            labels.add("פתיחה בכרטיסייה חדשה");
            actions.add(() -> { if (config.isAllowed(url)) newTab(url); else onBlocked(url); });
            labels.add("שיתוף הקישור");
            actions.add(() -> share(url, text));
        }
        if (image != null && image.startsWith("http")) {
            labels.add("העתקת כתובת התמונה");
            actions.add(() -> copyText(image, "הכתובת הועתקה"));
            labels.add("הורדת התמונה");
            actions.add(() -> onDownload(image, web.getSettings().getUserAgentString(), null, "image/*", 0));
        }
        if (labels.isEmpty()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        if (url != null) b.setTitle(prettyUrl(url));
        b.setItems(labels.toArray(new String[0]), (d, i) -> actions.get(i).run());
        b.show();
    }

    private void copyText(String text, String done) {
        if (TextUtils.isEmpty(text)) return;
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("link", text));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, done, Toast.LENGTH_SHORT).show();
    }

    private void share(String url, String title) {
        if (TextUtils.isEmpty(url)) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
        if (!TextUtils.isEmpty(title)) i.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(i, "שיתוף"));
    }

    // ------------------------------------------------------------------ home screen (several sites)

    /** The round icon for a home card: a custom image, else the fetched favicon, else the first letter. */
    private View siteIconView(Config.Site site) {
        FrameLayout box = new FrameLayout(this);
        TextView letter = new TextView(this);
        letter.setText(site.title.trim().isEmpty() ? "?" : site.title.trim().substring(0, 1));
        letter.setTextSize(20);
        letter.setTypeface(Typeface.DEFAULT_BOLD);
        letter.setTextColor(Color.WHITE);
        letter.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color(R.color.primary));
        letter.setBackground(dot);
        box.addView(letter, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setClipToOutline(true);
        GradientDrawable r = new GradientDrawable();
        r.setShape(GradientDrawable.OVAL);
        r.setColor(Color.WHITE);
        img.setBackground(r);
        int pad = dp(6);
        img.setPadding(pad, pad, pad, pad);
        img.setVisibility(View.GONE);
        box.addView(img, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (site.icon != null && !site.icon.isEmpty()) {
            try {
                byte[] b = android.util.Base64.decode(site.icon, android.util.Base64.DEFAULT);
                Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                if (bm != null) { img.setImageBitmap(bm); img.setVisibility(View.VISIBLE); }
            } catch (Exception ignored) { }
        } else {
            Favicons.load(img, site.url);
        }
        return box;
    }

    private void showHome() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(20), dp(16), dp(24));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(color(R.color.bar_text));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, dp(18));
        list.addView(title);

        if (config.sites.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(config.hasPin() ? "אין אתרים ברשימה. אפשר להוסיף בתפריט ← הגדרות." : "אין אתרים ברשימה.");
            empty.setTextColor(color(R.color.bar_muted));
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
        }

        for (Config.Site site : config.sites) {
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable bg = rounded(color(R.color.bar), dp(16));
            bg.setStroke(dp(1), color(R.color.bar_line));
            card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), bg, null));
            card.setClickable(true);

            card.addView(siteIconView(site), new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setPadding(dp(14), 0, dp(14), 0);
            TextView name = new TextView(this);
            name.setText(site.title);
            name.setTextSize(17);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(color(R.color.bar_text));
            txt.addView(name);
            TextView host = new TextView(this);
            host.setText(Config.hostOf(site.url));
            host.setTextColor(color(R.color.bar_muted));
            host.setTextDirection(View.TEXT_DIRECTION_LTR);
            txt.addView(host);
            card.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            final String url = site.url;
            card.setOnClickListener(v -> open(url));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            list.addView(card, lp);
        }

        TextView ver = new TextView(this);
        ver.setText("גרסה " + versionName());
        ver.setTextColor(color(R.color.bar_muted));
        ver.setTextSize(12);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(24), 0, 0);
        list.addView(ver);

        home.removeAllViews();
        home.addView(list);
        home.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        findBar.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        if (web != null) web.stopLoading();
        for (WebView t : tabs) t.setVisibility(View.INVISIBLE);
        updateBar();
    }

    // ------------------------------------------------------------------ tabs

    /** A square button in the bar showing how many tabs are open; tap to open the tab switcher. */
    private View tabButton() {
        tabCount = new TextView(this);
        tabCount.setText("1");
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTextColor(color(R.color.bar_text));
        tabCount.setTextSize(13);
        tabCount.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(dp(5));
        box.setStroke(Math.max(2, dp(2)), color(R.color.bar_text));
        tabCount.setBackground(box);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, null));
        wrap.addView(tabCount, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        wrap.setOnClickListener(v -> openTabSwitcher());
        wrap.setContentDescription("כרטיסיות");
        wrap.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(46)));
        return wrap;
    }

    private void updateTabCount() {
        if (tabCount != null) tabCount.setText(String.valueOf(tabs.size()));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView createTab() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(prefs.getBoolean("desktop", config.desktop) ? DESKTOP_UA : mobileUa);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.addJavascriptInterface(new Bridge(), "SmtApp");
        w.setWebViewClient(client);
        w.setWebChromeClient(chrome);
        w.setDownloadListener(this::onDownload);
        w.setOnLongClickListener(v -> onLongPress());
        content.addView(w, 0, match()); // below the home / error / overlays
        return w;
    }

    /** Opens a fresh tab. url != null loads it; url == null shows the home screen. */
    private void newTab(String url) {
        web = createTab();
        tabs.add(web);
        activeTab = tabs.size() - 1;
        updateTabCount();
        if (url != null) open(url);
        else goHome();
    }

    private void closeAllTabs() {
        for (WebView t : tabs) { content.removeView(t); try { t.destroy(); } catch (Exception ignored) { } }
        tabs.clear();
        web = createTab();
        tabs.add(web);
        activeTab = 0;
        updateTabCount();
        goHome();
    }

    /** Remember the open tabs so they come back after the app is closed. */
    private void saveTabs() {
        try {
            org.json.JSONArray a = new org.json.JSONArray();
            for (WebView t : tabs) {
                String u = t.getUrl();
                if (u != null && !u.isEmpty() && !u.equals("about:blank")) a.put(u);
            }
            prefs.edit().putString("open_tabs", a.toString()).putInt("open_tab_active", activeTab).apply();
        } catch (Exception ignored) {
        }
    }

    /** Reopen the tabs from the last session. true = something was restored. */
    private boolean restoreTabs() {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("open_tabs", "[]"));
            if (a.length() == 0) return false;
            for (int i = 0; i < a.length(); i++) {
                String u = a.optString(i);
                if (u == null || u.isEmpty() || !config.isAllowed(u)) continue;
                WebView w = (i == 0) ? tabs.get(0) : createTab();
                if (i != 0) tabs.add(w);
                w.loadUrl(u);
            }
            if (tabs.isEmpty()) return false;
            int act = prefs.getInt("open_tab_active", 0);
            activeTab = Math.max(0, Math.min(act, tabs.size() - 1));
            web = tabs.get(activeTab);
            updateTabCount();
            showWeb();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Show the active tab's page, or the home screen if that tab is empty. */
    private void showActiveTab() {
        errorView.setVisibility(View.GONE);
        String u = web == null ? null : web.getUrl();
        if (u == null || u.isEmpty() || u.equals("about:blank")) showHome();
        else showWeb();
    }

    private void switchTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        activeTab = i;
        web = tabs.get(i);
        showActiveTab();
        updateBar();
    }

    private void closeTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        if (tabs.size() == 1) {
            WebView only = tabs.get(0);
            only.stopLoading();
            only.loadUrl("about:blank");
            only.clearHistory();
            goHome();
            return;
        }
        WebView w = tabs.remove(i);
        content.removeView(w);
        w.destroy();
        if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
        else if (i < activeTab) activeTab--;
        web = tabs.get(activeTab);
        updateTabCount();
        showActiveTab();
        updateBar();
    }

    private void openTabSwitcher() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        AlertDialog.Builder bld = new AlertDialog.Builder(this)
                .setTitle("כרטיסיות (" + tabs.size() + ")")
                .setView(sc)
                .setPositiveButton("כרטיסייה חדשה", (dd, w) -> newTab(null))
                .setNegativeButton("סגירה", null);
        if (tabs.size() > 1) bld.setNeutralButton("סגירת הכל", (dd, w) -> closeAllTabs());
        AlertDialog d = bld.create();
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            WebView t = tabs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, null));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            String title = t.getTitle();
            String url = t.getUrl();
            boolean blank = url == null || url.equals("about:blank") || url.isEmpty();
            TextView tt = new TextView(this);
            tt.setText(blank ? "כרטיסייה חדשה" : (TextUtils.isEmpty(title) ? prettyUrl(url) : title));
            tt.setTextColor(color(R.color.bar_text));
            tt.setTextSize(15);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            if (i == activeTab) tt.setTypeface(Typeface.DEFAULT_BOLD);
            tx.addView(tt);
            if (!blank) {
                TextView tu = new TextView(this);
                tu.setText(prettyUrl(url));
                tu.setTextColor(color(R.color.bar_muted));
                tu.setTextSize(12);
                tu.setSingleLine(true);
                tu.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tu.setTextDirection(View.TEXT_DIRECTION_LTR);
                tx.addView(tu);
            }
            row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextSize(18);
            x.setTextColor(color(R.color.bar_muted));
            x.setGravity(Gravity.CENTER);
            x.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, null));
            x.setOnClickListener(v -> { d.dismiss(); closeTab(idx); openTabSwitcher(); });
            row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
            row.setOnClickListener(v -> { d.dismiss(); switchTab(idx); });
            list.addView(row);
        }
        d.show();
    }

    private void showWeb() {
        home.setVisibility(View.GONE);
        for (WebView t : tabs) t.setVisibility(t == web ? View.VISIBLE : View.INVISIBLE);
        updateBar();
    }

    private boolean isHomeShown() {
        return home != null && home.getVisibility() == View.VISIBLE;
    }

    private void open(String url) {
        showWeb();
        errorView.setVisibility(View.GONE);
        web.clearHistory();
        web.loadUrl(url);
        clearHistoryOnNextPage = true;
    }

    private void goHome() {
        if (config.sites.size() > 1) showHome();
        else if (!config.sites.isEmpty()) open(config.sites.get(0).url);
    }

    private void goBack() {
        if (web == null) { // set-code / restore screen: back just leaves the app
            moveTaskToBack(true);
            return;
        }
        if (fullscreenView != null) hideFullscreen();
        else if (findBar.getVisibility() == View.VISIBLE) {
            web.clearMatches();
            findBar.setVisibility(View.GONE);
        } else if (!isHomeShown() && web.canGoBack()) {
            errorView.setVisibility(View.GONE);
            web.goBack();
        } else if (!isHomeShown() && config.sites.size() > 1) showHome();
        else moveTaskToBack(true);
    }

    private void reload() {
        if (isHomeShown()) return;
        errorView.setVisibility(View.GONE);
        web.reload();
    }

    private void blocked() {
        Toast.makeText(this, config.blockMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ------------------------------------------------------------------ settings (PIN)

    private void askPin() {
        long wait = config.lockRemaining();
        if (wait > 0) {
            Toast.makeText(this, "יותר מדי ניסיונות. נסה שוב בעוד " + (wait / 1000 + 1) + " שניות", Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("קוד הגדרות")
                .setView(box)
                .setPositiveButton("כניסה", (d, w) -> {
                    if (config.checkPin(input.getText().toString())) {
                        startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
                    } else {
                        Toast.makeText(this, "קוד שגוי", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
        input.requestFocus();
    }

    // ------------------------------------------------------------------ web clients

    private class Client extends WebViewClient {
        private boolean check(String url) {
            if (config.isAllowed(url)) return false;
            onBlocked(url);
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return check(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return check(request.getUrl().toString());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (!config.isAllowed(url)) {
                view.stopLoading();
                if (view == web) blocked();
                if (view.canGoBack()) view.goBack();
                else if (view == web) showHome();
                return;
            }
            if (view == web) progress.setVisibility(View.VISIBLE);
            view.evaluateJavascript(bridgeJs, null);
            if (view == web) updateBar();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(bridgeJs, null);
            if (isDesktop()) view.evaluateJavascript(DESKTOP_VIEWPORT_JS, null);
            if (view == web) {
                progress.setVisibility(View.GONE);
                if (clearHistoryOnNextPage) {
                    view.clearHistory();
                    clearHistoryOnNextPage = false;
                }
                updateBar();
            }
            updateTabCount();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (view == web) updateBar();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame() && view == web) showError();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
            if (Build.VERSION.SDK_INT < 23 && view == web) showError();
        }
    }

    private void showError() {
        errorText.setText("לא ניתן לטעון את הדף.\nבדוק את החיבור לאינטרנט.");
        errorView.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            if (view != web) return;
            progress.setProgress(p);
            if (p >= 100) progress.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (view == web) updateBar();
        }

        /** window.open / target=_blank: load in this same view (still checked against the allowed sites). */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            // target=_blank / window.open: open it in a NEW tab (checked against the allowed sites)
            WebView w = createTab();
            tabs.add(w);
            activeTab = tabs.size() - 1;
            web = w;
            showWeb();
            updateTabCount();
            ((WebView.WebViewTransport) resultMsg.obj).setWebView(w);
            resultMsg.sendToTarget();
            return true;
        }

        /** Microphone / camera, like a browser: Android asks the first time. */
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (!config.isAllowed(request.getOrigin().toString())) {
                    request.deny();
                    return;
                }
                List<String> need = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) need.add(Manifest.permission.RECORD_AUDIO);
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) need.add(Manifest.permission.CAMERA);
                }
                if (need.isEmpty()) {
                    request.deny();
                    return;
                }
                List<String> missing = new ArrayList<>();
                for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
                if (missing.isEmpty()) {
                    grantMedia(request);
                } else {
                    if (pendingMedia != null) pendingMedia.deny();
                    pendingMedia = request;
                    requestPermissions(missing.toArray(new String[0]), REQ_MEDIA);
                }
            });
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (!config.isAllowed(origin)) {
                callback.invoke(origin, false, false);
                return;
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false);
            } else {
                pendingGeoOrigin = origin;
                pendingGeo = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            ((FrameLayout) getWindow().getDecorView()).addView(view, match());
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            hideFullscreen();
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                Intent i = params.createIntent();
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "בחירת קובץ"), REQ_FILE);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
        }
    }

    private void grantMedia(PermissionRequest request) {
        List<String> ok = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ok.add(r);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ok.add(r);
        }
        if (ok.isEmpty()) request.deny();
        else request.grant(ok.toArray(new String[0]));
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(fullscreenView);
        fullscreenView = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    // ------------------------------------------------------------------ notifications from the site

    private class Bridge {
        @JavascriptInterface
        public void notify(String title, String body, String tag) {
            runOnUiThread(() -> showNotification(title, body, tag));
        }

        @JavascriptInterface
        public void cancel(String tag) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(tag, 1);
        }

        @JavascriptInterface
        public String permission() {
            if (notificationsAllowed()) return "granted";
            if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notify_denied", false)) return "default";
            return "denied";
        }

        @JavascriptInterface
        public void requestPermission(int id) {
            runOnUiThread(() -> {
                if (notificationsAllowed() || Build.VERSION.SDK_INT < 33) {
                    resolveNotify(id);
                    return;
                }
                pendingNotifyId = id;
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            });
        }
    }

    private boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    private void resolveNotify(int id) {
        web.evaluateJavascript("window.__smtPermission&&window.__smtPermission(" + id + ",'"
                + (notificationsAllowed() ? "granted" : "denied") + "')", null);
    }

    private void showNotification(String title, String body, String tag) {
        if (!notificationsAllowed()) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("site", "התראות מהאתר", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_TAG, tag);
        PendingIntent pi = PendingIntent.getActivity(this, tag.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "site")
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        b.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(tag, 1, b.build());
    }

    private void handleNotificationTap(Intent intent) {
        String tag = intent == null ? null : intent.getStringExtra(EXTRA_TAG);
        if (tag == null) return;
        String js = "window.__smtNotificationClick&&window.__smtNotificationClick('"
                + tag.replace("\\", "\\\\").replace("'", "\\'") + "')";
        web.evaluateJavascript(js, null);
    }

    // ------------------------------------------------------------------ results

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_MEDIA && pendingMedia != null) {
            grantMedia(pendingMedia);
            pendingMedia = null;
        } else if (requestCode == REQ_LOCATION && pendingGeo != null) {
            boolean any = false;
            for (int r : results) any |= r == PackageManager.PERMISSION_GRANTED;
            pendingGeo.invoke(pendingGeoOrigin, any, false);
            pendingGeo = null;
        } else if (requestCode == REQ_NOTIFY) {
            if (!ok) prefs.edit().putBoolean("notify_denied", true).apply();
            resolveNotify(pendingNotifyId);
        } else if (requestCode == REQ_STORAGE && pendingDownload != null) {
            if (ok) enqueue(pendingDownload, pendingDownloadName);
            else Toast.makeText(this, "אין הרשאה לשמור קבצים", Toast.LENGTH_SHORT).show();
            pendingDownload = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int k = 0; k < n; k++) result[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            config = new Config(this);
            showStart();
        }
    }

    // ------------------------------------------------------------------ downloads

    private void onDownload(String url, String userAgent, String disposition, String mime, long length) {
        if (!url.startsWith("http")) {
            Toast.makeText(this, "לא ניתן להוריד קובץ זה", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!config.isAllowed(url) && !config.isAllowed(web.getUrl())) {
            blocked();
            return;
        }
        String name = URLUtil.guessFileName(url, disposition, mime);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) r.addRequestHeader("Cookie", cookies);
        r.addRequestHeader("User-Agent", userAgent);
        r.setTitle(name);
        if (mime != null && !mime.endsWith("/*")) r.setMimeType(mime);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = r;
            pendingDownloadName = name;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        enqueue(r, name);
    }

    private void enqueue(DownloadManager.Request r, String name) {
        try {
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "מוריד: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ההורדה נכשלה", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ utils

    private int color(int res) {
        return getColor(res);
    }

    private GradientDrawable rounded(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        return d;
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
