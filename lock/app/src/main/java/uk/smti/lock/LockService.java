package uk.smti.lock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sees which app comes to the front. Locked apps get the lock screen; blocked apps are sent home.
 * It also guards the screens that could remove the protection (app info, device admin,
 * accessibility, the uninstall dialog) unless the PIN was entered.
 * Nothing is collected or sent anywhere.
 */
public class LockService extends AccessibilityService {

    /** Apps where the protection could be switched off or the app removed. */
    private static final Set<String> GUARDED = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
            "com.coloros.safecenter", "com.huawei.systemmanager",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.packageinstaller"));

    /** Words on those screens that mean "remove / turn off / reset". Hebrew and English. */
    private static final String[] DANGER = {
            "הסר", "הסרה", "הסרת", "ביטול הפעלה", "השבת", "עצירה בכפייה", "עצור", "ניקוי", "נקה",
            "נגישות", "שימוש ב", "מנהל מכשיר", "אפליקציות לניהול",
            "uninstall", "deactivate", "disable", "force stop", "clear", "accessibility",
            "use ", "device admin", "turn off"};

    private static LockService running;

    private Store store;
    private String current = "";
    private final Set<String> ignored = new HashSet<>();
    private String label = "";
    private long lastGuardCheck;

    public static boolean isRunning() {
        return running != null;
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        store = Store.get(this);
        label = getString(getApplicationInfo().labelRes).toLowerCase(Locale.ROOT);
        refreshIgnored();
        Owner.apply(this);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    /** Our own screens, the status bar, keyboards and the home screen are never locked. */
    private void refreshIgnored() {
        ignored.clear();
        ignored.add(getPackageName());
        ignored.add("com.android.systemui");
        ignored.add("android");
        try {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) ignored.add(i.getPackageName());
        } catch (Exception ignoredEx) {
        }
    }

    private boolean isHome(String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(home, 0);
        return r != null && r.activityInfo != null && pkg.equals(r.activityInfo.packageName);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (ignored.contains(pkg)) return;

        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.equals(current)) {
                store.leftApp(current);
                store.leftApp(Store.GUARD);
                current = pkg;
            }
            int rule = store.rule(pkg);
            if (rule == Store.BLOCK && !isHome(pkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                showLock(LockActivity.MODE_BLOCK, pkg);
                return;
            }
            if (rule == Store.PIN && !store.isUnlocked(pkg) && !isHome(pkg)) {
                showLock(LockActivity.MODE_PIN, pkg);
                return;
            }
        }

        if (GUARDED.contains(pkg) && store.guardEnabled() && !store.removing() && !store.isUnlocked(Store.GUARD)) {
            long now = System.currentTimeMillis();
            if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastGuardCheck < 400) return;
            lastGuardCheck = now;
            if (dangerOnScreen()) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
                showLock(LockActivity.MODE_GUARD, pkg);
            }
        }
    }

    /** Is a screen about THIS app showing a remove / turn-off option? */
    private boolean dangerOnScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        collect(root, sb, new int[]{0});
        String text = sb.toString().toLowerCase(Locale.ROOT);
        if (label.isEmpty() || !text.contains(label)) return false;
        for (String w : DANGER) if (text.contains(w)) return true;
        return false;
    }

    private static void collect(AccessibilityNodeInfo n, StringBuilder sb, int[] count) {
        if (n == null || count[0]++ > 500) return;
        if (n.getText() != null) sb.append(n.getText()).append('\n');
        if (n.getContentDescription() != null) sb.append(n.getContentDescription()).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb, count);
    }

    private void showLock(String mode, String pkg) {
        Intent i = new Intent(this, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(LockActivity.EXTRA_MODE, mode)
                .putExtra(LockActivity.EXTRA_PKG, pkg);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
    }

    /** Called when keyboards may have changed. */
    static void refresh() {
        LockService s = running;
        if (s != null) s.refreshIgnored();
    }

    static List<String> guardedPackages() {
        return Arrays.asList(GUARDED.toArray(new String[0]));
    }
}
