package uk.smti.lock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** All settings: the PIN, which apps are locked and how, and temporary unlocks. */
public final class Store {

    public static final int OPEN = 0;
    public static final int PIN = 1;
    public static final int BLOCK = 2;

    /** Pseudo package used for the "protect removal" guard. */
    public static final String GUARD = "#guard";

    private static final String P = "lock";
    private static Store instance;

    private final SharedPreferences prefs;
    private final Map<String, Integer> rules = new HashMap<>();
    /** package -> unlocked until (ms); Long.MAX_VALUE = until another app opens */
    private final Map<String, Long> unlocked = new HashMap<>();

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx.getApplicationContext());
        return instance;
    }

    private Store(Context ctx) {
        prefs = ctx.getSharedPreferences(P, Context.MODE_PRIVATE);
        // First run: take the PIN chosen on the website.
        if (!prefs.contains("pin_hash")) {
            try {
                JSONObject c = new JSONObject(readAsset(ctx, "config.json"));
                String h = c.optString("pinHash", "");
                if (!h.isEmpty()) {
                    prefs.edit().putString("pin_hash", h).putString("pin_salt", c.optString("pinSalt", "")).apply();
                }
            } catch (Exception ignored) {
            }
        }
        try {
            JSONObject r = new JSONObject(prefs.getString("rules", "{}"));
            for (Iterator<String> it = r.keys(); it.hasNext(); ) {
                String k = it.next();
                rules.put(k, r.optInt(k));
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- rules ----------

    public synchronized int rule(String pkg) {
        Integer r = rules.get(pkg);
        return r == null ? OPEN : r;
    }

    public synchronized Map<String, Integer> rules() {
        return new HashMap<>(rules);
    }

    public synchronized void setRule(String pkg, int rule) {
        if (rule == OPEN) rules.remove(pkg);
        else rules.put(pkg, rule);
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Integer> e : rules.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("rules", o.toString()).apply();
    }

    // ---------- temporary unlock after the right PIN ----------

    /** Minutes an app stays open after the PIN; 0 = until you leave it. */
    public int unlockMinutes() {
        return prefs.getInt("unlock_minutes", 0);
    }

    public void setUnlockMinutes(int m) {
        prefs.edit().putInt("unlock_minutes", m).apply();
    }

    public synchronized void unlock(String pkg) {
        int m = GUARD.equals(pkg) ? 3 : unlockMinutes();
        unlocked.put(pkg, m == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + m * 60_000L);
    }

    public synchronized boolean isUnlocked(String pkg) {
        Long until = unlocked.get(pkg);
        if (until == null) return false;
        if (until != Long.MAX_VALUE && until < System.currentTimeMillis()) {
            unlocked.remove(pkg);
            return false;
        }
        return true;
    }

    /** Another app came to the front: "until you leave it" unlocks end. */
    public synchronized void leftApp(String pkg) {
        Long until = unlocked.get(pkg);
        if (until != null && until == Long.MAX_VALUE) unlocked.remove(pkg);
    }

    public synchronized void lockAll() {
        unlocked.clear();
    }

    // ---------- options ----------

    public boolean guardEnabled() {
        return prefs.getBoolean("guard", true);
    }

    public void setGuard(boolean on) {
        prefs.edit().putBoolean("guard", on).apply();
    }

    /** Set while the user is removing the app with the PIN, so the guard steps aside. */
    public boolean removing() {
        return prefs.getLong("removing_until", 0) > System.currentTimeMillis();
    }

    public void setRemoving(boolean on) {
        prefs.edit().putLong("removing_until", on ? System.currentTimeMillis() + 5 * 60_000L : 0).apply();
    }

    public boolean flag(String key, boolean def) {
        return prefs.getBoolean("opt_" + key, def);
    }

    public void setFlag(String key, boolean on) {
        prefs.edit().putBoolean("opt_" + key, on).apply();
    }

    /** Apps strong mode suspended last time, so removed ones can be released. */
    public java.util.List<String> previouslySuspended() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : prefs.getString("suspended", "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    public void setPreviouslySuspended(java.util.List<String> list) {
        prefs.edit().putString("suspended", android.text.TextUtils.join(",", list)).apply();
    }

    // ---------- PIN ----------

    public boolean hasPin() {
        return !prefs.getString("pin_hash", "").isEmpty();
    }

    public long lockRemaining() {
        return Math.max(0, prefs.getLong("pin_lock_until", 0) - System.currentTimeMillis());
    }

    public boolean checkPin(String pin) {
        if (lockRemaining() > 0) return false;
        boolean ok = hash(prefs.getString("pin_salt", ""), pin).equalsIgnoreCase(prefs.getString("pin_hash", ""));
        int fails = ok ? 0 : prefs.getInt("pin_fails", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("pin_fails", fails);
        if (!ok && fails % 5 == 0) {
            e.putLong("pin_lock_until", System.currentTimeMillis() + (60_000L << Math.min(6, fails / 5 - 1)));
        }
        e.apply();
        return ok;
    }

    public void setPin(String pin) {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String salt = hex(b);
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply();
    }

    // ---------- helpers ----------

    static String hash(String salt, String pin) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
